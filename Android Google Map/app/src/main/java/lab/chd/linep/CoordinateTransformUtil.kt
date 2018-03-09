package lab.chd.linep

import com.google.android.gms.maps.model.LatLng

/**
 * Created by lucka on 8/3/2018.
 *
 * 百度坐标（BD09）、国测局坐标（火星坐标，GCJ02）、和WGS84坐标系之间的转换的工具
 *
 * @see 参考 https://github.com/wandergis/coordtransform 实现的Java版本
 *
 * @author geosmart
 */
object CoordinateTransformUtil {

    internal var x_pi = 3.14159265358979324 * 3000.0 / 180.0
    // π
    internal var pi = 3.1415926535897932384626
    // 长半轴
    internal var a = 6378245.0
    // 扁率
    internal var ee = 0.00669342162296594323


    /**
     * WGS84转GCJ02(火星坐标系)
     *
     * @param lng WGS84坐标系的经度
     * @param lat WGS84坐标系的纬度
     * @return 火星坐标数组
     */
    fun wgs84togcj02(latLng: LatLng): LatLng {
        if (out_of_china(latLng)) {
            return latLng
        }
        var dlat = transformlat(latLng.longitude - 105.0, latLng.latitude - 35.0)
        var dlng = transformlng(latLng.longitude - 105.0, latLng.latitude - 35.0)
        val radlat = latLng.latitude / 180.0 * pi
        var magic = Math.sin(radlat)
        magic = 1 - ee * magic * magic
        val sqrtmagic = Math.sqrt(magic)
        dlat = dlat * 180.0 / (a * (1 - ee) / (magic * sqrtmagic) * pi)
        dlng = dlng * 180.0 / (a / sqrtmagic * Math.cos(radlat) * pi)
        val mglat = latLng.latitude + dlat
        val mglng = latLng.longitude + dlng
        return LatLng(mglat, mglng)
    }

    /**
     * GCJ02(火星坐标系)转GPS84
     *
     * @param lng 火星坐标系的经度
     * @param lat 火星坐标系纬度
     * @return WGS84坐标数组
     */
    fun gcj02towgs84(latLng: LatLng): LatLng {
        if (out_of_china(latLng)) {
            return latLng
        }
        var dlat = transformlat(latLng.longitude - 105.0, latLng.latitude - 35.0)
        var dlng = transformlng(latLng.longitude - 105.0, latLng.latitude - 35.0)
        val radlat = latLng.latitude / 180.0 * pi
        var magic = Math.sin(radlat)
        magic = 1 - ee * magic * magic
        val sqrtmagic = Math.sqrt(magic)
        dlat = dlat * 180.0 / (a * (1 - ee) / (magic * sqrtmagic) * pi)
        dlng = dlng * 180.0 / (a / sqrtmagic * Math.cos(radlat) * pi)
        val mglat = latLng.latitude + dlat
        val mglng = latLng.longitude + dlng
        return LatLng(latLng.latitude * 2 - mglat, latLng.longitude * 2 - mglng)
    }

    /**
     * 纬度转换
     *
     * @param lng
     * @param lat
     * @return
     */
    fun transformlat(lng: Double, lat: Double): Double {
        var ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat + 0.1 * lng * lat + 0.2 * Math.sqrt(Math.abs(lng))
        ret += (20.0 * Math.sin(6.0 * lng * pi) + 20.0 * Math.sin(2.0 * lng * pi)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(lat * pi) + 40.0 * Math.sin(lat / 3.0 * pi)) * 2.0 / 3.0
        ret += (160.0 * Math.sin(lat / 12.0 * pi) + 320 * Math.sin(lat * pi / 30.0)) * 2.0 / 3.0
        return ret
    }

    /**
     * 经度转换
     *
     * @param lng
     * @param lat
     * @return
     */
    fun transformlng(lng: Double, lat: Double): Double {
        var ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng + 0.1 * lng * lat + 0.1 * Math.sqrt(Math.abs(lng))
        ret += (20.0 * Math.sin(6.0 * lng * pi) + 20.0 * Math.sin(2.0 * lng * pi)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(lng * pi) + 40.0 * Math.sin(lng / 3.0 * pi)) * 2.0 / 3.0
        ret += (150.0 * Math.sin(lng / 12.0 * pi) + 300.0 * Math.sin(lng / 30.0 * pi)) * 2.0 / 3.0
        return ret
    }

    /**
     * 判断是否在国内，不在国内不做偏移
     *
     * @param lng
     * @param lat
     * @return
     */
    fun out_of_china(latLng: LatLng): Boolean {
        if (latLng.longitude < 72.004 || latLng.longitude > 137.8347) {
            return true
        } else if (latLng.latitude < 0.8293 || latLng.latitude > 55.8271) {
            return true
        }
        return false
    }
}