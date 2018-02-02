package lab.lucka.linep

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.location.Location
import java.io.File
import java.io.FileOutputStream
import android.content.Context.MODE_PRIVATE
import java.nio.charset.Charset


/**
 * Created by lucka on 27/1/2018.
 */

class MissionManager(context: Context) {

    private var context: Context
    var waypointList: ArrayList<Waypoint> = ArrayList(0)
    var isStarted: Boolean = false

    init {
        this.context = context
    }

    fun start() {
        isStarted = true

        // Download the GPX file from server
        // Save the GPX file
        //   Refrence: https://developer.android.com/guide/topics/data/data-storage.html?hl=zh-cn
        //   Refrence: https://developer.android.com/training/data-storage/files.html#WriteInternalStorage
        // For test only
        val testGPXStr: String = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:gpsies=\"http://www.gpsies.com/GPX/1/0\" creator=\"GPSies http://www.gpsies.com - GPSies Track\" version=\"1.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.gpsies.com/GPX/1/0 http://www.gpsies.com/gpsies.xsd\">\n" +
                "    <metadata>\n" +
                "        <name>MissionID</name>\n" +
                "        <time>2018-02-02T09:20:58Z</time>\n" +
                "    </metadata>\n" +
                "    <wpt lat=\"25.84987276\" lon=\"114.9045065\">\n" +
                "        <name>Waypoint 01</name>\n" +
                "        <desc>Description 01</desc>\n" +
                "    </wpt>\n" +
                "    <wpt lat=\"25.84995483\" lon=\"114.9040451\">\n" +
                "        <name>Waypoint 02</name>\n" +
                "        <desc>Description 02</desc>\n" +
                "    </wpt>\n" +
                "    <wpt lat=\"25.85006104\" lon=\"114.9034389\">\n" +
                "        <name>Waypoint 03</name>\n" +
                "        <desc>Description 03</desc>\n" +
                "    </wpt>\n" +
                "    <wpt lat=\"25.85036518\" lon=\"114.9035033\">\n" +
                "        <name>Waypoint 04</name>\n" +
                "        <desc>Description 04</desc>\n" +
                "    </wpt>\n" +
                "    <wpt lat=\"25.85070311\" lon=\"114.9035409\">\n" +
                "        <name>Waypoint 05</name>\n" +
                "        <desc>Description 05</desc>\n" +
                "    </wpt>\n" +
                "    <wpt lat=\"25.85110380\" lon=\"114.9035462\">\n" +
                "        <name>Waypoint 06</name>\n" +
                "        <desc>Description 06</desc>\n" +
                "    </wpt>\n" +
                "</gpx>"
        var filename: String = "MissionID.gpx"
        var outputFile: File = File(context.filesDir, filename)
        outputFile.writeText(testGPXStr, Charset.defaultCharset())
        var inputFile: File = File(context.filesDir, filename)
        // Decode the GPX file
        waypointList = decodeGPX(inputFile)
    }

    fun stop() {
        isStarted = false
        waypointList.clear()
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
        var lineList: List<String> = file.readLines()

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
                var locationStringList: List<String> =  locationString.split(" ")
                if (locationStringList.size == 2) {
                    location.latitude = locationStringList[0].toDouble()
                    location.longitude = locationStringList[1].toDouble()
                } else {
                    val alert = AlertDialog.Builder(context)
                    alert.setTitle(context.getString(R.string.alert_warning_title))
                    alert.setMessage(locationString)
                    alert.setCancelable(false)
                    alert.setPositiveButton(context.getString(R.string.confirm), null)
                    alert.show()
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
                var waypoint: Waypoint = Waypoint(title, description, false, location)
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
        var reachedList: ArrayList<Int> = ArrayList(0)
        for (scanner: Int in 0 until waypointList.size) {
            if (waypointList[scanner].location != null) {
                if (!waypointList[scanner].isChecked and !waypointList[scanner].isAbnormal and (location.distanceTo(waypointList[scanner].location) <= 30)) {
                    reachedList.add(scanner)
                }
            }
        }

        return reachedList
    }
}