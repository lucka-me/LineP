package lab.chd.linep

import android.location.Location
import com.amap.api.maps2d.CoordinateConverter
import com.amap.api.maps2d.model.LatLng

/**
 * Created by lucka on 10/3/2018.
 */

class CoordinateKit {

    enum class CoordinateType {
        WGS84,
        GCJ02
    }

    companion object {
        fun convert(location: Location, from: CoordinateType, to: CoordinateType): Location {
            var fixedLocation = Location(location)
            when (from) {
                CoordinateType.WGS84 -> {
                    when (to) {
                        CoordinateType.GCJ02 -> {
                            val fixedLatLng = CoordinateConverter().from(CoordinateConverter.CoordType.GPS).coord(LatLng(location.latitude, location.longitude)).convert()
                            val fixedLocation = Location(location)
                            fixedLocation.latitude = fixedLatLng.latitude
                            fixedLocation.longitude = fixedLatLng.longitude
                        }
                    }
                }
            }
            return fixedLocation
        }
    }

}