package lab.chd.linep

import android.location.Location
import java.io.Serializable

/**
 * Waypoint 类
 *
 * 用于存储 Waypoint 信息，并提供序列化/反序列化功能
 *
 * @property [title] Waypoint 的名称
 * @property [description] Waypoint 的详细描述
 * @property [isChecked] Waypoint 是否已被检查
 * @property [longitude] Waypoint 的经度，181.0 意为无经度
 * @property [latitude] Waypoint 的纬度，91.0 意为无纬度
 *
 * @param [location] Waypoint 的位置，会被转换为经纬度存储
 *
 * @author lucka
 * @since 0.1
 */

class Waypoint(var title: String, var description: String, var isChecked: Boolean, location: Location?): Serializable {
    var longitude: Double
    var latitude: Double

    init {
        if (location == null) {
            this.longitude = 181.0
            this.latitude = 91.0
        } else {
            this.longitude = location.longitude
            this.latitude = location.latitude
        }
    }

    /**
     * 获取包含 Waypoint 经纬度的 Location 类实体
     *
     * @return 包含 Waypoint 经纬度的 Location 类实体，若无经纬度则返回 null
     *
     * @author lucka
     * @since 0.1
     */
    fun location(): Location? {
        var location: Location? = null
        if (this.longitude < 180.5 && this.latitude < 90.5) {
            location = Location("")
            location.longitude = this.longitude
            location.latitude = this.latitude
        }
        return location
    }

}