package lab.chd.linep

import android.app.AlertDialog
import android.content.Context
import android.location.Location
import com.google.gson.Gson
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
    var issueSN: Int = 0
    var issueImagePath: String = ""
    var missionData: MissionData = MissionData("", "", "")
    private var missionListener: MissionListener

    init {
        this.context = context
        this.missionListener = missionListener
    }

    fun start() {
        issueSN = 1
        isLoading = true
        /*
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            val alert = AlertDialog.Builder(context)
            alert.setTitle(context.getString(R.string.alert_warning_title))
            alert.setMessage(context.getString(R.string.alert_permission_internet))
            alert.setPositiveButton(context.getString(R.string.confirm), null)
            alert.show()
            return
        }*/


        doAsync {
            // Get the Mission Data from the server
            missionData = requestMission()
            // Get the GPX file
            if (missionData.ID == "") {
                uiThread {
                    val error: Exception = Exception(context.getString(R.string.error_request_mission_failed))
                    isLoading = false
                    missionListener.didStartedFailed(error)
                }
                return@doAsync
            }
            val gpxFile: File = requestMissionGPX(missionData as MissionData)
            uiThread {
                waypointList = decodeGPX(gpxFile)
                isLoading = false
                isStarted = true
                missionListener.didStartedSuccess(missionData as MissionData)
            }
        }
    }

    private fun requestMission(): MissionData {
        var missionData: MissionData = MissionData("", "", "")
        val jsonURL = context.getString(R.string.server_url) + "Mission.json"
        var jsonString: String = ""
        try {
            jsonString = URL(jsonURL).readText()
        } catch (error: Exception) {
            return missionData
        }
        try {
            missionData = Gson().fromJson(jsonString, MissionData::class.java)
        } catch (error: Exception) {
            return missionData
        }
        return missionData
    }

    private fun requestMissionGPX(missionData: MissionData): File {
        // Download the GPX file from server
        val gpxURL = context.getString(R.string.server_url) + "GPX/" + missionData.ID + ".gpx"
        val gpxString = URL(gpxURL).readText()
        // Save the GPX file
        //   Refrence: https://developer.android.com/guide/topics/data/data-storage.html?hl=zh-cn
        //   Refrence: https://developer.android.com/training/data-storage/files.html#WriteInternalStorage
        val filename: String = missionData.ID + ".gpx"
        val gpxFile: File = File(context.filesDir, filename)
        gpxFile.writeText(gpxString, Charset.defaultCharset())
        return gpxFile
    }

    fun stop() {
        isStarted = false
        issueSN = 0
        missionData = MissionData("", "", "")
        waypointList.clear()
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
            objectOutputStream.writeObject(missionData)
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
            missionData = objectInputStream.readObject() as MissionData
            issueSN = objectInputStream.readInt()
            waypointList = objectInputStream.readObject() as ArrayList<Waypoint>
            objectInputStream.close()
            fileInputStream.close()
            if (missionData.ID == "") {
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
        issueSN += 1
    }
}

public interface MissionListener {
    fun didStartedSuccess(missionData: MissionData)
    fun didStartedFailed(error: Exception)
}

data class MissionData(val ID: String, val Token: String, val Description: String): Serializable