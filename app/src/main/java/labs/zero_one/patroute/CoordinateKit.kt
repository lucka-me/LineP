package labs.zero_one.patroute

import android.content.Context
import android.location.Location
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.model.LatLng

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
         * Changelog
         * [1.2.1] - 2018-06-19
         *   使用 3D 地图 SDK
         *   添加参数 [context]
         *
         * @param [location] 待转换位置
         * @param [from] 待转换位置的坐标系类型
         * @param [to] 目标坐标系类型
         * @param [context] 高德坐标转换器需要的 Context
         *
         * @return [Location] 转换后的位置
         *
         * @see CoordinateConverter
         *
         * @author lucka
         * @since 0.1
         *
         */
        fun convert(
            location: Location?,
            from: CoordinateType,
            to: CoordinateType,
            context: Context
        ): Location? {
            if (location == null) return location
            val fixedLocation = Location(location)
            when (from) {
                CoordinateType.WGS84 -> {
                    when (to) {
                        CoordinateType.GCJ02 -> {
                            val fixedLatLng = CoordinateConverter(context)
                                .from(CoordinateConverter.CoordType.GPS)
                                .coord(LatLng(location.latitude, location.longitude))
                                .convert()
                            fixedLocation.latitude = fixedLatLng.latitude
                            fixedLocation.longitude = fixedLatLng.longitude
                        }
                        else -> {
                        }
                    }
                }
                else -> {
                }
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
        fun getDegreeString(degree: Double): String = String.format(
            "%d° %d′ %.2f″",
            degree.toInt(),
            ((degree - degree.toInt()) * 60).toInt(),
            (((degree - degree.toInt()) * 60) - ((degree - degree.toInt()) * 60).toInt()) * 60
        )

    }

}