package labs.zero_one.patroute

import android.location.Location
import java.io.Serializable

/**
 * 检查点类
 *
 * 用于存储检查点信息，并提供序列化/反序列化功能
 *
 * ## 属性列表
 * - [title]
 * - [description]
 * - [checked]
 * - [longitude]
 * - [latitude]
 *
 * ## 访问器列表
 * - [location]
 *
 * ## Changelog
 * ### 1.5.0
 * - 属性和访问器全部更换为非空类型
 *
 * @param [title] 检查点名称
 * @param [description] 检查点的详细描述
 * @param [checked] 是否已被检查
 * @param [longitude] 经度
 * @param [latitude] 纬度
 *
 * @author lucka
 * @since 0.1
 *
 * @property [title] 检查点名称
 * @property [description] 检查点的详细描述
 * @property [checked] 是否已被检查
 * @property [longitude] 经度
 * @property [latitude] 纬度
 * @property [location] 位置（[Location]）访问器
 */
class Waypoint(
    var title: String,
    var description: String,
    var checked: Boolean,
    var longitude: Double,
    var latitude: Double
) : Serializable {

    /**
     * 位置的 [Location] 类属性访问器
     *
     * 由于通常实际使用的是 [Location] 实体而非直接使用经纬度属性，而 Location 类无法序列化/反序列化，因此使用
     * 访问器获得/设置它。
     *
     * ## Changelog
     * ### 1.5.0
     * - 改为非空类型
     *
     * @author lucka
     * @since 1.1.0
     */
    var location: Location
        set(value) {
            this.longitude = value.longitude
            this.latitude = value.latitude
        }
        get() {
            val location = Location("")
            location.longitude = this.longitude
            location.latitude = this.latitude
            return location
        }

    /**
     * [Waypoint] 的次构造函数
     *
     * @param [title] 检查点名称
     * @param [description] 检查点的详细描述
     * @param [checked] 是否已被检查
     * @param [location] 位置
     *
     * @author lucka
     * @since 1.5.0
     *
     * @see [Waypoint]
     */
    constructor(title: String, description: String, checked: Boolean, location: Location) :
        this(title, description, checked, location.longitude, location.latitude)
}