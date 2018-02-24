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
import java.net.URL
import java.nio.charset.Charset
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
    var issueSN: Int = 0
    var issueImagePath: String = ""
    var data: MissionData = MissionData("", "")
    private var missionListener: MissionListener

    init {
        this.context = context
        this.missionListener = missionListener
    }

    // Listener
    interface MissionListener {
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

        val sharedPreference = PreferenceManager.getDefaultSharedPreferences(context)
        val serverURL: String = sharedPreference.getString(context.getString(R.string.pref_server_url_key), "")
        val serverPort: Int = sharedPreference.getString(context.getString(R.string.pref_server_port_key), "2121").toInt()
        val username: String = sharedPreference.getString(context.getString(R.string.pref_user_id_key), "")
        val password: String = sharedPreference.getString(context.getString(R.string.pref_user_token_key), "")
        // Connect to FTP Server via Apache Commons Net API
        //   Reference: https://github.com/KoFuk/ftp-upload/blob/master/src/main/kotlin/com/chronoscoper/ftpupload/Main.kt
        val ftpClient = FTPClient()
        doAsync {
            try {
                ftpClient.connect(serverURL, serverPort)
                ftpClient.enterLocalPassiveMode()
                ftpClient.login(username, password)
                ftpClient.changeWorkingDirectory("Mission")
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                val localJSONFile = File(context.filesDir, "mission.json")
                var fileOutputStream = FileOutputStream(localJSONFile)
                if (!ftpClient.retrieveFile(username + ".json", fileOutputStream)) {
                    val error = Exception(context.getString(R.string.error_request_mission_failed))
                    isLoading = false
                    uiThread {
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
                    isLoading = false
                    uiThread {
                        missionListener.didStartedFailed(error)
                    }
                    return@doAsync
                }
                fileOutputStream.close()
                waypointList = decodeGPX(localGPXFile)
            } catch (error: Exception) {
                isLoading = false
                val newError = Exception(context.getString(R.string.error_request_mission_failed) + "\n" + error.message)
                uiThread {
                    missionListener.didStartedFailed(error)
                }
                return@doAsync
            }
            uiThread {
                isLoading = false
                isStarted = true
                missionListener.didStartedSuccess(data as MissionData)
            }
        }
    }

    fun stop() {
        isStopping = true
        doAsync {
            Thread.sleep(5000)
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
                if (!waypointList[scanner].isChecked and (location.distanceTo(waypointList[scanner].location()) <= 30)) {
                    reachedList.add(scanner)
                }
            }
        }

        return reachedList
    }

    fun submitIssue() {
        isReporting = true
        issueSN += 1
        doAsync {
            Thread.sleep(5000)
            isReporting = false
            missionListener.didReportedSccess()
        }

    }
}