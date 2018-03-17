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
 * @property [longitude] Waypoint 的经度
 * @property [latitude] Waypoint 的纬度
 *
 * @param [location] 作为访问器使用，并不作实际存储
 *
 * @author lucka
 * @since 0.1
 */
class Waypoint(var title: String,
               var description: String,
               var isChecked: Boolean,
               location: Location?
): Serializable {
    private var longitude: Double? = null
    private var latitude: Double? = null
    /**
     * Waypoint 的位置属性访问器
     *
     * 由于实际使用的是 Location 实体而非直接使用经纬度属性，而 Location 类无法序列化/反序列化
     * 因此使用 Location 作为 Location 类属性访问器
     *
     * @author lucka
     * @since 1.0.4
     */
    var location: Location?
        set(value) {
            this.longitude = value?.longitude
            this.latitude = value?.latitude
        }
        get() {
            var location: Location? = null
            if (this.longitude != null && this.latitude != null) {
                location = Location("")
                location.longitude = this.longitude!!
                location.latitude = this.latitude!!
            }
            return location
        }

    init {
        this.location = location
    }

    /**
     * 获取包含 Waypoint 经纬度的 Location 类实体
     *
     * @Deprecated 1.0.4 被访问器 Location 代替
     *
     * @return 包含 Waypoint 经纬度的 Location 类实体，若无经纬度则返回 null
     *
     * @author lucka
     * @since 0.1
     */
    @Deprecated("This method should be replaced by accessor \"Location\".")
    fun location(): Location? {
        var location: Location? = null
        if (this.longitude != null && this.latitude != null) {
            location = Location("")
            location.longitude = this.longitude!!
            location.latitude = this.latitude!!
        }
        return location
    }

}