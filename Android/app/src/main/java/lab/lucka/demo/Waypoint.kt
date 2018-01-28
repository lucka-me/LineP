package lab.lucka.demo

import android.location.Location

/**
 * Created by lucka on 24/1/2018.
 */
class Waypoint(title: String, description: String, isChecked: Boolean, location: Location?) {
    var title: String
    var description: String
    var isChecked: Boolean
    var isAbnormal: Boolean = false
    var location: Location? = null

    init {
        this.title = title
        this.description = description
        this.isChecked = isChecked
        this.location = location
    }
}