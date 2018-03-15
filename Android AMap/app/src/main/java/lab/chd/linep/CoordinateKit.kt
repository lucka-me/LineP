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

        fun convert(location: Location?, from: CoordinateType, to: CoordinateType): Location? {
            if (location == null) return location
            val fixedLocation = Location(location)
            when (from) {
                CoordinateType.WGS84 -> {
                    when (to) {
                        CoordinateType.GCJ02 -> {
                            val fixedLatLng = CoordinateConverter().from(CoordinateConverter.CoordType.GPS).coord(LatLng(location.latitude, location.longitude)).convert()
                            fixedLocation.latitude = fixedLatLng.latitude
                            fixedLocation.longitude = fixedLatLng.longitude
                        }
                    }
                }
            }
            return fixedLocation
        }

        fun getDegreeString(degree: Double): String {
            return String.format("%d° %d′ %.2f″",
                    degree.toInt(),
                    ((degree - degree.toInt()) * 60).toInt(),
                    (((degree - degree.toInt()) * 60) - ((degree - degree.toInt()) * 60).toInt()) * 60)
        }

    }

}