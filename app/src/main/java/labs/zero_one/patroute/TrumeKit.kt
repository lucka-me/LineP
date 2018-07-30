package labs.zero_one.patroute

import android.content.Context
import android.location.Location
import android.os.Build
import android.os.CountDownTimer
import org.apache.commons.net.time.TimeTCPClient
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*

/**
 * 反作弊工具
 *
 * 提供一些反作弊功能
 *
 * 属性列表
 * @property [timer] 计时器，用于时间篡改检测
 *
 * 子类列表
 * [TrumeListener] 工具监听器
 *
 * 方法列表
 * [checkMock] 检测位置是否来自模拟
 *
 * @author lucka
 * @since 1.4.0
 */
class TrumeKit(private val context: Context, private val trumeListener: TrumeListener) {

    // Check every 2 min
    private val timer: CountDownTimer =
        object : CountDownTimer(120000, 1000) {
            override fun onFinish() {
                checkTimeTrick()
                this.start()
            }

            override fun onTick(millisUntilFinished: Long) {

            }

        }.start()

    /**
     * 反作弊工具消息监听器
     *
     * @author lucka
     * @since 1.4.0
     */
    interface TrumeListener {

        /**
         * 发现时间篡改时调用
         *
         * @param [internetTime] 网络时间
         * @param [deviceTime] 设备时间
         *
         * @author lucka
         * @since 1.4.0
         */
        fun onTimeTrickDetected(internetTime: Long, deviceTime: Long)

        /**
         * 出现错误时调用
         *
         * @param [error] 错误
         *
         * @author lucka
         * @since 1.4.0
         */
        fun onException(error: Exception)
    }

    /**
     * 检测时间篡改
     *
     * 对比网络时间和设备时间
     *
     * 注释参考
     * @see <a href="https://stackoverflow.com/a/13066268">Stack Overflow</a>
     *
     * @author lucka
     * @since 1.4.0
     */
    private fun checkTimeTrick() {
        doAsync {
            try {
                val timeClient = TimeTCPClient()
                try {
                    timeClient.defaultTimeout = 60000
                    timeClient.connect(context.getString(R.string.internetTimeHost))
                    // 10+ min diff between device and internet time could be regarded as time trick
                    val internetTime = timeClient.time
                    val deviceTime = Date().time
                    if (Math.abs(deviceTime - internetTime) > 10 * 60000) {
                        uiThread {
                            trumeListener.onTimeTrickDetected(internetTime, deviceTime)
                        }
                    }
                } catch (error: Exception) {
                    trumeListener.onException(error)
                    return@doAsync
                } finally {
                    timeClient.disconnect()
                }
            } catch (error: Exception) {
                trumeListener.onException(error)
                return@doAsync
            }
        }
    }

    /**
     * 模拟器检测
     *
     * @return [Boolean] 是否为模拟器
     *
     * 注释参考
     * @see <a href="https://stackoverflow.com/a/21505193">Stack Overflow</a>
     *
     * @author lucka
     * @since 1.4.0
     */
    fun checkEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            (
                Build.BRAND.startsWith("generic") &&
                Build.DEVICE.startsWith("generic")
            ) ||
            "google_sdk" == Build.PRODUCT
    }

    /**
     * 模拟位置检测
     *
     * @param [location] 待检测位置
     *
     * @return [Boolean] 是否为模拟位置
     *
     * @author lucka
     * @since 1.4.0
     */
    fun checkMock(location: Location?): Boolean {
        if (location == null) return false
        return location.isFromMockProvider
    }
}