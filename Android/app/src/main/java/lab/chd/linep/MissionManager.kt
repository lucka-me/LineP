package lab.chd.linep

import android.app.AlertDialog
import android.content.Context
import android.location.Location
import android.support.v7.preference.PreferenceManager
import com.google.gson.Gson
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.*
import java.text.DateFormat
import java.util.*


/**
 * Created by lucka on 27/1/2018.
 */

class MissionManager(context: Context, missionListener: MissionListener) {

    private var context: Context
    var waypointList: ArrayList<Waypoint> = ArrayList(0)
    var isStarted: Boolean = false
    var isLoading: Boolean = false
    var isStopping: Boolean = false
    var isReporting: Boolean = false
    // To avoid multi alert when reaching a waypoint
    var isChecking = false
    var issueSN: Int = 0
    var issueImagePath: String = ""
    var lastLocationLogDate: Date = Date()
    var data: MissionData = MissionData("", "")
    private var missionListener: MissionListener

    init {
        this.context = context
        this.missionListener = missionListener
    }

    // Listener
    interface MissionListener {
        fun onAllChecked()
        fun didStartedSuccess(missionData: MissionData)
        fun didStartedFailed(error: Exception)
        fun didStoppedSccess()
        fun didStoppedFailed(error: Exception)
        fun didReportedSccess()
        fun didReportedFailed(error: Exception)
    }

    // Data class
    data class MissionData(val id: String, val description: String): Serializable

    fun start() {
        issueSN = 1
        isLoading = true

        doAsync {
            try {
                val sharedPreference = PreferenceManager.getDefaultSharedPreferences(context)
                val serverURL: String = sharedPreference.getString(context.getString(R.string.pref_server_url_key), "")
                val serverPort: Int = sharedPreference.getString(context.getString(R.string.pref_server_port_key), context.getString(R.string.pref_server_port_default)).toInt()
                val username: String = sharedPreference.getString(context.getString(R.string.pref_user_id_key), "")
                val password: String = sharedPreference.getString(context.getString(R.string.pref_user_token_key), "")
                // Connect to FTP Server via Apache Commons Net API
                //   Reference: https://github.com/KoFuk/ftp-upload/blob/master/src/main/kotlin/com/chronoscoper/ftpupload/Main.kt
                // Download file from FTP Server via Apache Commons Net API
                //   Reference: http://www.codejava.net/java-se/networking/ftp/java-ftp-file-download-tutorial-and-example
                val ftpClient = FTPClient()
                ftpClient.connect(serverURL, serverPort)
                ftpClient.enterLocalPassiveMode()
                ftpClient.login(username, password)
                ftpClient.changeWorkingDirectory("Mission")
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                val localJSONFile = File(context.filesDir, "mission.json")
                var fileOutputStream = FileOutputStream(localJSONFile)
                if (!ftpClient.retrieveFile(username + ".json", fileOutputStream)) {
                    val error = Exception(context.getString(R.string.error_request_mission_failed))
                    uiThread {
                        isLoading = false
                        missionListener.didStartedFailed(error)
                    }
                    return@doAsync
                }
                fileOutputStream.close()
                val jsonString = localJSONFile.readText()
                data = Gson().fromJson(jsonString, MissionData::class.java)
                ftpClient.changeWorkingDirectory("GPX")
                val localGPXFile = File(context.filesDir, data.id + ".gpx")
                fileOutputStream = FileOutputStream(localGPXFile)
                if (!ftpClient.retrieveFile(data.id + ".gpx", fileOutputStream)) {
                    val error = Exception(context.getString(R.string.error_request_gpx_failed))
                    uiThread {
                        isLoading = false
                        missionListener.didStartedFailed(error)
                    }
                    return@doAsync
                }
                fileOutputStream.close()
                waypointList = decodeGPX(localGPXFile)
            } catch (error: Exception) {
                val newError = Exception(context.getString(R.string.error_request_mission_failed) + "\n" + error.message)
                uiThread {
                    isLoading = false
                    missionListener.didStartedFailed(newError)
                }
                return@doAsync
            }
            uiThread {
                isLoading = false
                isStarted = true
                File(context.filesDir, data.id + ".log").writeText(context.getString(R.string.log_headline))
                log(context.getString(R.string.log_mission_started))
                missionListener.didStartedSuccess(data)
            }
        }
    }

    fun stop() {
        isStopping = true
        doAsync {
            try {
                val sharedPreference = PreferenceManager.getDefaultSharedPreferences(context)
                val serverURL: String = sharedPreference.getString(context.getString(R.string.pref_server_url_key), "")
                val serverPort: Int = sharedPreference.getString(context.getString(R.string.pref_server_port_key), context.getString(R.string.pref_server_port_default)).toInt()
                val username: String = sharedPreference.getString(context.getString(R.string.pref_user_id_key), "")
                val password: String = sharedPreference.getString(context.getString(R.string.pref_user_token_key), "")
                val ftpClient = FTPClient()
                ftpClient.connect(serverURL, serverPort)
                ftpClient.enterLocalPassiveMode()
                ftpClient.login(username, password)
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                ftpClient.changeWorkingDirectory(username)
                ftpClient.changeWorkingDirectory(data.id)
                // Upload log file
                log(context.getString(R.string.log_mission_stopped))
                val logFile = File(context.filesDir, data.id + ".log")
                val fileInputStream = FileInputStream(logFile)
                ftpClient.storeFile(data.id + ".log", fileInputStream)
                fileInputStream.close()
                logFile.delete()
                ftpClient.logout()
            } catch (error: Exception) {
                isStopping = false
                uiThread {
                    val newError: Exception = Exception(context.getString(R.string.stop_failed) + "\n" + error.message)
                    missionListener.didStartedFailed(newError)
                }
                return@doAsync
            }
            uiThread {
                issueSN = 0
                data = MissionData("", "")
                waypointList.clear()
                isStarted = false
                isStopping = false
                missionListener.didStoppedSccess()
            }
        }
    }

    fun pause() {
        // Serialize and save the waypointList
        //   Refrence: https://developer.android.com/training/basics/data-storage/files.html
        //   Refrence: http://blog.csdn.net/u011240877/article/details/72455715
        val filename = "mission.temp"
        val file: File = File(context.filesDir, filename)
        val fileOutputStream: FileOutputStream
        val objectOutputStream: ObjectOutputStream

        try {
            fileOutputStream = FileOutputStream(file)
            objectOutputStream = ObjectOutputStream(fileOutputStream)
            objectOutputStream.writeObject(data)
            objectOutputStream.writeInt(issueSN)
            objectOutputStream.writeObject(waypointList)
            objectOutputStream.close()
            fileOutputStream.close()
            log(context.getString(R.string.log_mission_paused))
        } catch (error: Exception) {
            val alert = AlertDialog.Builder(context)
            alert.setTitle(context.getString(R.string.alert_warning_title))
            alert.setMessage(error.message)
            alert.setCancelable(false)
            alert.setPositiveButton(context.getString(R.string.confirm), null)
            alert.show()
        }

    }

    fun resume() {
        val filename = "mission.temp"
        val file: File = File(context.filesDir, filename)
        val fileInputStream: FileInputStream
        val objectInputStream: ObjectInputStream

        if (!file.exists()) {
            isStarted = false
            return
        }

        try {
            fileInputStream = FileInputStream(file)
            objectInputStream = ObjectInputStream(fileInputStream)
            data = objectInputStream.readObject() as MissionData
            issueSN = objectInputStream.readInt()
            waypointList = objectInputStream.readObject() as ArrayList<Waypoint>
            objectInputStream.close()
            fileInputStream.close()
            if (data.id == "") {
                isStarted = false
            } else {
                isStarted = true
            }
            log(context.getString(R.string.log_mission_resume))
        } catch (error: Exception) {
            val alert = AlertDialog.Builder(context)
            alert.setTitle(context.getString(R.string.alert_warning_title))
            alert.setMessage(error.message)
            alert.setCancelable(false)
            alert.setPositiveButton(context.getString(R.string.confirm), null)
            alert.show()
            isStarted = false
        }
    }

    fun decodeGPX(file: File): ArrayList<Waypoint> {
        /*
        Decode a GPX file
        GPX Refrence: http://www.topografix.com/GPX/1/1/#type_trkType

        <wpt lat="xxx" lon="xxx">
            <name> title </name>
            <desc> description </desc>
        </wpt>

        */

        waypointList = ArrayList(0)
        val lineList: List<String> = file.readLines()

        var title: String = ""
        var description: String = ""
        var location: Location = Location("")
        var wptBegin: Boolean = false

        for (line in lineList) {

            // Scan untill wpt begin
            if (!wptBegin) {
                if (line.contains("")) {
                    wptBegin = true
                } else {
                    continue
                }
            }
            if (line.contains("<wpt lat=")) {
                var locationString: String = line
                locationString = locationString.replace("<wpt lat=", "")
                locationString = locationString.replace("lon=", "")
                locationString = locationString.replace(">", "")
                locationString = locationString.replace("\"", "")
                locationString = locationString.trim()
                val locationStringList: List<String> =  locationString.split(" ")
                if (locationStringList.size == 2) {
                    location.latitude = locationStringList[0].toDouble()
                    location.longitude = locationStringList[1].toDouble()
                }
            } else if (line.contains("<name>")) {
                title = line
                title = title.replace("<name>", "")
                title = title.replace("</name>", "")
                title = title.trim()
                continue
            } else if (line.contains("<desc>")) {
                description = line
                description = description.replace("<desc>", "")
                description = description.replace("</desc>", "")
                description = description.trim()
                continue
            } else if (line.contains("</wpt>")) {
                val waypoint: Waypoint = Waypoint(title, description, false, location)
                waypointList.add(waypoint)
                title = ""
                description = ""
                location = Location("")
                wptBegin = false
            }
        }
        return waypointList
    }

    fun reach(location: Location): ArrayList<Int> {
        val reachedList: ArrayList<Int> = ArrayList(0)
        for (scanner: Int in 0 until waypointList.size) {
            if (waypointList[scanner].location() != null) {
                if (!waypointList[scanner].isChecked && (location.distanceTo(waypointList[scanner].location()) <= 30)) {
                    reachedList.add(scanner)
                }
            }
        }

        return reachedList
    }

    fun checkAt(index: Int) {
        waypointList[index].isChecked = true
        log(String.format(context.getString(R.string.log_checked), waypointList[index].title))
        var isAllChecked = true
        for (waypoint in waypointList) {
            if (!waypoint.isChecked) {
                isAllChecked = false
                break
            }
        }
        if (isAllChecked) {
            missionListener.onAllChecked()
        }
    }

    fun submitIssue(location: Location?, time: Date, description: String) {
        isReporting = true
        doAsync {
            try {
                // Create issue txt file
                val issueFile = File(context.filesDir, "ISS_" + data.id + "_" + issueSN + ".txt")
                val longitudeText: String = if (location == null) context.getString(R.string.unavailable) else String.format("%f", location.longitude)
                val latitudeText: String = if (location == null) context.getString(R.string.unavailable) else String.format("%f", location.latitude)
                issueFile.writeText(context.getString(R.string.location)
                        + " (" + longitudeText + " " + latitudeText + ")\n"
                        + context.getString(R.string.time)
                        + " "
                        + DateFormat.getDateTimeInstance().format(time)
                        + "\n"
                        + context.getString(R.string.description)
                        + " "
                        + description)

                val sharedPreference = PreferenceManager.getDefaultSharedPreferences(context)
                val serverURL: String = sharedPreference.getString(context.getString(R.string.pref_server_url_key), "")
                val serverPort: Int = sharedPreference.getString(context.getString(R.string.pref_server_port_key), context.getString(R.string.pref_server_port_default)).toInt()
                val username: String = sharedPreference.getString(context.getString(R.string.pref_user_id_key), "")
                val password: String = sharedPreference.getString(context.getString(R.string.pref_user_token_key), "")
                // Connect to FTP Server via Apache Commons Net API
                //   Reference: https://github.com/KoFuk/ftp-upload/blob/master/src/main/kotlin/com/chronoscoper/ftpupload/Main.kt
                // Download file from FTP Server via Apache Commons Net API
                //   Reference: http://www.codejava.net/java-se/networking/ftp/java-ftp-file-download-tutorial-and-example
                val ftpClient = FTPClient()
                ftpClient.connect(serverURL, serverPort)
                ftpClient.enterLocalPassiveMode()
                ftpClient.login(username, password)
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                ftpClient.changeWorkingDirectory(username)
                ftpClient.changeWorkingDirectory(data.id)
                // Upload issue txt file
                var fileInputStream = FileInputStream(issueFile)
                ftpClient.storeFile("ISS_" + data.id + "_" + issueSN + ".txt", fileInputStream)
                fileInputStream.close()
                // Upload image
                fileInputStream = FileInputStream(issueImagePath)
                ftpClient.storeFile("ISS_" + data.id + "_" + issueSN + ".jpg", fileInputStream)
                fileInputStream.close()
                ftpClient.logout()
                issueFile.delete()
                log(String.format(context.getString(R.string.log_issueSubmitted), "ISS_" + data.id + "_" + issueSN, longitudeText, latitudeText, description))
            } catch (error: Exception) {
                uiThread {
                    isReporting = false
                    missionListener.didReportedFailed(error)
                }
                return@doAsync
            }
            issueSN += 1
            File(issueImagePath).delete()
            uiThread {
                missionListener.didReportedSccess()
            }
        }
    }

    fun log(message: String) {
        File(context.filesDir, data.id + ".log").appendText(String.format(context.getString(R.string.log_lineFormat), DateFormat.getDateTimeInstance().format(Date()), message))
    }
}