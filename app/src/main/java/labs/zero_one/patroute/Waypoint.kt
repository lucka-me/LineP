package labs.zero_one.patroute

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
 * @property [location] [Location] 类型的访问器
 *
 * @author lucka
 * @since 0.1
 */
class Waypoint(
    var title: String,
    var description: String,
    var isChecked: Boolean,
    location: Location?
) : Serializable {
    private var longitude: Double? = null
    private var latitude: Double? = null
    /**
     * Waypoint 的位置属性访问器
     *
     * 由于实际使用的是 Location 实体而非直接使用经纬度属性，而 Location 类无法序列化/反序列化
     * 因此使用 Location 作为 Location 类属性访问器
     *
     * @author lucka
     * @since 1.1.0
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
     * Changelog
     * [1.1.0] - 2018-03-17
     *   废弃，由访问器 location 代替
     * [1.2.1] - 2018-06-18
     *   返回值改为 null
     *
     * @return null（已废弃）
     *
     * @author lucka
     * @since 0.1
     */
    @Deprecated("This method should be replaced with accessor \"location\".",
        ReplaceWith("location"))
    fun location(): Location? {
        /*
        var location: Location? = null
        if (this.longitude != null && this.latitude != null) {
            location = Location("")
            location.longitude = this.longitude!!
            location.latitude = this.latitude!!
        }
        return location
        */
        return null
    }

}