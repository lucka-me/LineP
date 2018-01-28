package lab.lucka.demo

import android.app.AlertDialog
import android.content.DialogInterface
import android.location.Location
import java.io.File

/**
 * Created by lucka on 27/1/2018.
 */

class MissionManager() {

    var waypointList: ArrayList<Waypoint> = ArrayList(0)
    var isStarted: Boolean = false

    init {

    }

    fun start() {
        isStarted = true
        // For test only
        var isChecked = false
        for (i: Int in 0 until 5) {
            var waypoint = Waypoint("位置 ${i}", "", isChecked, null)
            isChecked = !isChecked
            waypointList.add(waypoint)
        }
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
                if (line.contains("<wpt")) {
                    wptBegin = true
                } else {
                    continue
                }
            }
            if (line.contains("</wpt lat=")) {
                var locationString: String = line
                locationString = locationString.replace("</wpt lat=", "")
                locationString = locationString.replace("lon=", "")
                locationString = locationString.replace(">", "")
                locationString = locationString.replace("\"", "")
                var locationStringList: List<String> =  locationString.split(" ")
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
                var waypoint: Waypoint = Waypoint(title, description, false, location)
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
                if (!waypointList[scanner].isChecked and !waypointList[scanner].isAbnormal and (location.distanceTo(waypointList[scanner].location) <= 10)) {
                    reachedList.add(scanner)
                }
            }
        }

        return reachedList
    }
}