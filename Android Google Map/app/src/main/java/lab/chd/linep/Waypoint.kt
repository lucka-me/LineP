package lab.chd.linep

import android.location.Location
import java.io.Serializable

/**
 * Created by lucka on 24/1/2018.
 */

class Waypoint(title: String, description: String, isChecked: Boolean, location: Location?): Serializable {
    var title: String
    var description: String
    var isChecked: Boolean
    var longitude: Double
    var latitude: Double

    init {
        this.title = title
        this.description = description
        this.isChecked = isChecked
        if (location != null) {
            longitude = location.longitude
            latitude = location.latitude
        } else {
            longitude = 181.0
            latitude = 91.0
        }
    }

    fun location(): Location? {
        var location: Location? = null
        if ((longitude < 180.5) and (latitude < 90.5)) {
            location = Location("")
            location.longitude = longitude
            location.latitude = latitude
        }
        return location
    }
}