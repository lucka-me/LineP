package lab.chd.patroline.gmap

import android.location.Location
import java.io.Serializable

/**
 * 检查点类
 *
 * 用于存储检查点信息，并提供序列化/反序列化功能
 *
 * @property [title] 检查点的名称
 * @property [description] 检查点的详细描述
 * @property [isChecked] 检查点是否已被检查
 * @property [longitude] 检查点的经度
 * @property [latitude] 检查点的纬度
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
     * 检查点的位置属性访问器
     *
     * 由于实际使用的是 Location 属性而非直接使用经纬度属性，而 Location 属性无法序列化/反序列化存储，因此使用
     * 属性访问器来访问属性
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
     * 获取包含检查点经纬度的 Location 类实体
     *
     * @Deprecated 1.0.4 由访问器 location 代替
     *
     * @return 包含检查点经纬度的 Location 实体，若无经纬度则返回 null
     *
     * @author lucka
     * @since 0.1
     */
    @Deprecated("This method should be replaced by accessor \"location\".")
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