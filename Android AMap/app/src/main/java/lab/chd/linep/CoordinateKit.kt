package lab.chd.linep

import android.location.Location
import com.amap.api.maps2d.CoordinateConverter
import com.amap.api.maps2d.model.LatLng

/**
 * 坐标工具箱
 *
 * 提供一些坐标转换和处理的功能
 *
 * 子类列表
 * [CoordinateType] 坐标系类型
 *
 * 方法列表
 * [convert] 坐标系转换器
 * [getDegreeString] 获取小数角度的角度制字符串
 *
 * @author lucka
 * @since 0.1
 */

class CoordinateKit {

    /**
     * 坐标系类型
     *
     * @property [WGS84] WGS-84 坐标系
     * @property [GCJ02] GCJ-02 坐标系
     *
     * @author lucka
     * @since 0.1
     */
    enum class CoordinateType {
        WGS84,
        GCJ02
    }

    companion object {

        /**
         * 坐标系转换器
         *
         * 基于高德 API 实现，目前仅提供 WGS-84 到 GCJ-02 的转换
         *
         * @param [location] 待转换位置
         * @param [from] 待转换位置的坐标系类型
         * @param [to] 目标坐标系类型
         *
         * @return [Location] 转换后的位置
         *
         * @author lucka
         * @since 0.1
         */
        fun convert(location: Location?, from: CoordinateType, to: CoordinateType): Location? {
            if (location == null) return location
            val fixedLocation = Location(location)
            when (from) {
                CoordinateType.WGS84 -> {
                    when (to) {
                        CoordinateType.GCJ02 -> {
                            val fixedLatLng = CoordinateConverter()
                                    .from(CoordinateConverter.CoordType.GPS)
                                    .coord(LatLng(location.latitude, location.longitude))
                                    .convert()
                            fixedLocation.latitude = fixedLatLng.latitude
                            fixedLocation.longitude = fixedLatLng.longitude
                        }
                        else -> {}
                    }
                }
                else -> {}
            }
            return fixedLocation
        }

        /**
         * 获取小数角度的角度制字符串
         *
         * @param [degree] 角度
         *
         * @return [String] 对应的角度制字符串
         *
         * @author lucka
         * @since 1.0.0
         */
        fun getDegreeString(degree: Double): String {
            return String.format("%d° %d′ %.2f″",
                    degree.toInt(),
                    ((degree - degree.toInt()) * 60).toInt(),
                    (((degree - degree.toInt()) * 60) - ((degree - degree.toInt()) * 60).toInt())
                        * 60
            )
        }

    }

}