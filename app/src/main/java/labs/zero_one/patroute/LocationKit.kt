package labs.zero_one.patroute

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.model.LatLng

/**
 * 位置工具，封装 LocationManager，简化相关接口和方法，并提供坐标系转换等特色功能，可以在 GPS 和网络定位间自动切换。
 *
 * ## 属性列表
 * - [lastLocation]
 * - [isLocationAvailable]
 * - [locationManager]
 * - [currentProvider]
 * - [criteria]
 * - [locationListener]
 * - [assistLocationListener]
 * - [ELLIPSOID_A]
 * - [ELLIPSOID_EE]
 * - [EARTH_R]
 * - [FIXED_PROVIDER]
 * - [DEFAULT_LONGITUDE]
 * - [DEFAULT_LATITUDE]
 *
 * ## 子类列表
 * - [locationKitListener]
 *
 * ## 方法列表
 * - [requestPermission]
 * - [startUpdate]
 * - [stopUpdate]
 * - [fixCoordinate]
 * - [showRequestPermissionDialog]
 *
 * @param [context] 环境
 * @param [locationKitListener] 消息监听器
 *
 * @author lucka-me
 * @since 1.5.0
 *
 * @property [lastLocation] 最新位置（已修正）
 * @property [isLocationAvailable] 位置是否可用
 * @property [locationManager] 原生定位管理器
 * @property [currentProvider] 最新的定位源
 * @property [criteria] 定位要求，用于获取最佳定位源
 * @property [locationListener] 原生定位消息监听器
 * @property [assistLocationListener] 辅助监听器，用于切换定位源之后监听原有定位源，当原有定位源可用时向 [locationListener] 发送消息
 * @property [ELLIPSOID_A] 椭球参数：长半轴（米）
 * @property [ELLIPSOID_EE] 椭球参数：扁率
 * @property [EARTH_R] 地球平均半径（米）
 * @property [FIXED_PROVIDER] 修正坐标后位置的 Provider
 * @property [DEFAULT_LONGITUDE] 默认经度
 * @property [DEFAULT_LATITUDE] 默认维度
 */
class LocationKit(
    private var context: Context,
    private val locationKitListener: LocationKitListener
) {

    var lastLocation: Location = Location(FIXED_PROVIDER)
    var isLocationAvailable: Boolean = false
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var currentProvider = LocationManager.GPS_PROVIDER
    private val criteria = Criteria()
    private val locationListener = object : LocationListener {

        override fun onLocationChanged(location: Location?) {

            if (TrumeKit.checkMock(location)) {
                val error = Exception(context.getString(R.string.err_location_mock))
                isLocationAvailable = false
                locationKitListener.onException(error)
                return
            }
            if (location == null) {
                isLocationAvailable = false
                return
            }
            lastLocation = fixCoordinate(location, context)
            isLocationAvailable = true
            locationKitListener.onLocationUpdated(lastLocation)
        }

        override fun onProviderDisabled(provider: String?) {

            if (provider == currentProvider) {
                val newProvider = locationManager.getBestProvider(criteria ,true)
                if (newProvider != LocationManager.GPS_PROVIDER &&
                    newProvider != LocationManager.NETWORK_PROVIDER
                ) {
                    locationKitListener.onProviderDisabled()
                } else {
                    val oldProvider = currentProvider
                    currentProvider = newProvider
                    stopUpdate()
                    startUpdate(false)
                    startUpdateAssist(oldProvider)
                    locationKitListener.onProviderSwitchedTo(currentProvider)
                }
            }
        }

        override fun onProviderEnabled(provider: String?) {

            val newProvider = locationManager.getBestProvider(criteria ,true)
            if (newProvider != LocationManager.GPS_PROVIDER &&
                newProvider != LocationManager.NETWORK_PROVIDER
            ) {
                locationKitListener.onProviderDisabled()
                currentProvider = LocationManager.GPS_PROVIDER
            } else if (newProvider != currentProvider) {
                currentProvider = newProvider
                stopUpdate()
                startUpdate(false)
                locationKitListener.onProviderSwitchedTo(currentProvider)
            } else {
                locationKitListener.onProviderEnabled()
            }

        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {

        }
    }

    private val assistLocationListener = object : LocationListener {
        override fun onProviderEnabled(provider: String?) {
            locationListener.onProviderEnabled(provider)
            locationManager.removeUpdates(this)
        }
        override fun onLocationChanged(location: Location?) {}
        override fun onProviderDisabled(provider: String?) {}
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    /**
     * 位置工具消息监听器
     *
     * ## 消息列表
     * - [onLocationUpdated]
     * - [onProviderDisabled]
     * - [onProviderEnabled]
     * - [onProviderSwitchedTo]
     * - [onException]
     *
     * @author lucka-me
     * @since 1.5.0
     */
    interface LocationKitListener {
        /**
         * 位置更新
         *
         * @author lucka-me
         * @since 1.5.0
         */
        fun onLocationUpdated(location: Location)
        /**
         * 定位被关闭
         *
         * @author lucka-me
         * @since 1.5.0
         */
        fun onProviderDisabled()
        /**
         * 定位开启
         *
         * @author lucka-me
         * @since 1.5.0
         */
        fun onProviderEnabled()
        /**
         * 定位源被切换
         *
         * @author lucka-me
         * @since 1.5.0
         */
        fun onProviderSwitchedTo(newProvider: String)
        /**
         * 返回错误
         *
         * @author lucka-me
         * @since 1.5.0
         */
        fun onException(error: Exception)
    }


    /**
     * 初始化
     *
     * 设置初始坐标（西安）
     *
     * Note: 此处调用 getLastKnownLocation() 并修正坐标，会触发 onLocationChanged 并传入修正的坐标
     *       可通过修改 Provider 进行标记
     *
     * @author lucka-me
     * @since 1.5.0
     */
    init {
        criteria.accuracy = Criteria.ACCURACY_FINE
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            // Must get one whatever the provider is
            val location = locationManager
                .getLastKnownLocation(locationManager.getBestProvider(criteria, true))
            if (location == null) {
                lastLocation.longitude = DEFAULT_LONGITUDE
                lastLocation.latitude = DEFAULT_LATITUDE
                isLocationAvailable = false
            } else {
                lastLocation = fixCoordinate(location, context)
                isLocationAvailable = true
            }
        } else {
            lastLocation.longitude = DEFAULT_LONGITUDE
            lastLocation.latitude = DEFAULT_LATITUDE
            isLocationAvailable = false
        }
    }

    /**
     * 开始定位
     *
     * @return 是否成功
     *
     * @author lucka-me
     * @since 1.5.0
     */
    fun startUpdate(resetProvider: Boolean = true): Boolean {
        if (resetProvider) {
            val newProvider = locationManager.getBestProvider(criteria ,true)
            if (newProvider == LocationManager.GPS_PROVIDER ||
                newProvider == LocationManager.NETWORK_PROVIDER
            ) {
                currentProvider = newProvider
            }
        }
        return if (ActivityCompat
                .checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                currentProvider,
                UPDATE_INTERVAL,
                UPDATE_DISTANCE,
                locationListener
            )
            true
        } else {
            val error = Exception(context.getString(R.string.err_location_permission_denied))
            locationKitListener.onException(error)
            false
        }
    }

    /**
     * 停止定位
     *
     * @author lucka-me
     * @since 1.5.0
     */
    fun stopUpdate() {
        locationManager.removeUpdates(locationListener)
    }

    /**
     * 开始用辅助监听器 [assistLocationListener] 监听定位源
     *
     * @param [provider] 要监听的定位源
     *
     * @author lucka-me
     * @since 1.5.0
     */
    private fun startUpdateAssist(provider: String) {
        if (ActivityCompat
                .checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                provider,
                UPDATE_INTERVAL,
                UPDATE_DISTANCE,
                assistLocationListener
            )
        } else {
            val error = Exception(context.getString(R.string.err_location_permission_denied))
            locationKitListener.onException(error)
        }
    }

    companion object {
        const val ELLIPSOID_A = 6378137.0
        const val ELLIPSOID_EE = 0.00669342162296594323
        const val EARTH_R = 6372796.924
        const val UPDATE_INTERVAL: Long = 1000
        const val UPDATE_DISTANCE: Float = 2.0f
        const val FIXED_PROVIDER = "fixed"
        const val DEFAULT_LONGITUDE = 108.947031
        const val DEFAULT_LATITUDE = 34.259441

        /**
         * 坐标系转换器，将 WGS-84 坐标系位置转换为 GCJ-02 坐标系。
         *
         * 会将 Provider 设置为 [FIXED_PROVIDER] 以供识别，且会通过此识别已转换的位置以避免二次转换。
         *
         * ## Changelog
         * ### 1.2.1
         * - 使用 3D 地图 SDK
         * - 添加参数 [context]
         * ### 1.5.0
         * - 移除 CoordinateKit，转入 LocationKit
         *
         * @param [location] 待转换位置
         * @param [context] 高德坐标转换器需要的环境
         *
         * @return [Location] 转换后的位置
         *
         * @see CoordinateConverter
         *
         * @author lucka
         * @since 0.1
         *
         */
        fun fixCoordinate(location: Location, context: Context): Location {
            // 避免二次修正
            val fixedLocation = Location(location)
            val fixedLatLng =
                CoordinateConverter(context)
                    .from(CoordinateConverter.CoordType.GPS)
                    .coord(LatLng(location.latitude, location.longitude))
                    .convert()
            fixedLocation.latitude = fixedLatLng.latitude
            fixedLocation.longitude = fixedLatLng.longitude
            fixedLocation.provider = FIXED_PROVIDER
            return fixedLocation
        }

        /**
         * 请求权限
         *
         * @param [activity] 应用的 Activity
         * @param [requestCode] 请求代码
         *
         * @return 是否需要显示请求权限对话框
         *
         * @author lucka-me
         * @since 1.5.0
         */
        fun requestPermission(activity: MainActivity, requestCode: Int): Boolean {
            if (ActivityCompat
                    .checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                // Explain if permission denied before
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                ) {
                    return true
                } else {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        requestCode
                    )
                }
            }
            return false
        }

        /**
         * 显示请求权限对话框
         *
         * @param [activity] MainActivity
         *
         * @author lucka-me
         * @since 1.5.0
         */
        fun showRequestPermissionDialog(activity: MainActivity) {
            DialogKit.showDialog(
                activity,
                R.string.permission_request_title,
                R.string.permission_explain_location,
                negativeButtonTextId = R.string.system_settings,
                negativeButtonListener = { _, _ ->
                    activity.startActivity(Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", activity.packageName, null)
                    ))
                },
                cancelable = false
            )
        }
    }
}