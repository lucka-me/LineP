package lab.chd.linep

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.*
import kotlinx.android.synthetic.main.activity_main.*
import android.widget.*
import java.io.File
import java.util.*
import android.support.v4.content.FileProvider
import android.support.v7.preference.PreferenceManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import com.amap.api.maps2d.AMap
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.MapView
import com.amap.api.maps2d.model.LatLng
import com.amap.api.maps2d.model.MarkerOptions
import java.text.DateFormat

/**
 * 主页面 Activity
 *
 * @property [mainRecyclerView] 主页面的 Recycler View
 * @property [mainRecyclerViewAdapter] 主页面 Recycler View 的适配器
 * @property [locationManager] 位置管理器
 * @property [locationListener] 位置消息监听器
 * @property [mission] 任务管理器
 * @property [missionListener] 任务消息监听器
 * @property [onPreferenceChangedListener] 首选项更改监听器
 *
 * 子类列表
 * [PermissionRequest] 权限请求相关
 * [ActivityRequest] 跨 Activity 活动
 * [MainMenu] 主菜单项
 *
 * 重写方法列表
 * [onCreate]
 * [onPause]
 * [onResume]
 * [onCreateOptionsMenu]
 * [onPrepareOptionsMenu]
 * [onOptionsItemSelected]
 * [onRequestPermissionsResult]
 * [onActivityResult]
 *
 * 自定义方法列表
 * [takeIssuePhoto] 拍摄报告相片
 * [submitIssue] 预览、编辑和提交报告
 * [showMissionDialog] 显示任务详情对话框
 * [showWaypointDialog] 显示 Waypoint 详情对话框
 *
 * @author lucka
 * @since 0.1
 */
class MainActivity : AppCompatActivity() {

    // MainRecyclerView
    private lateinit var mainRecyclerView: RecyclerView
    lateinit var mainRecyclerViewAdapter: MainRecyclerViewAdapter

    // LocationManager and Listener
    private lateinit var locationManager: LocationManager
    private val locationListener: LocationListener = object :LocationListener {

        override fun onLocationChanged(location: Location?) {

            val fixedLocation = CoordinateKit.convert(
                    location,
                    CoordinateKit.CoordinateType.WGS84,
                    CoordinateKit.CoordinateType.GCJ02
            ) ?: return
            if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            PermissionRequest.LocationFine.permission
                    ) ==
                PackageManager.PERMISSION_GRANTED) {
                mainRecyclerViewAdapter.refreshWith(fixedLocation)
            }
            // Log Location per 10 seconds
            if (mission.isStarted && (Date().time - mission.lastLocationLogDate.time >= 10000)) {
                mission.log(String.format(
                        getString(R.string.log_locationUpdate),
                        fixedLocation.longitude,
                        fixedLocation.latitude
                ))
                mission.lastLocationLogDate = Date()
            }
            if (mission.isStarted && !mission.isChecking) {
                mission.isChecking = true
                val reachedList = mission.reach(fixedLocation)
                if (reachedList.isNotEmpty()) {
                    for (index: Int in reachedList) {
                        val alert = AlertDialog.Builder(this@MainActivity)
                        alert.setTitle(getString(R.string.alert_reach_waypoint_title))
                        alert.setMessage(String.format(
                                getString(R.string.alert_reach_waypoint_message),
                                mission.waypointList[index].title
                        ))
                        alert.setCancelable(false)
                        alert.setPositiveButton(
                                getString(R.string.alert_reach_waypoint_checked),
                                { _, _ ->
                                    mission.checkAt(index)
                                    mainRecyclerViewAdapter
                                        .refreshAt(
                                            MainRecyclerViewAdapter.ItemIndex.Waypoint.row
                                                + index
                                        )
                                    mainRecyclerViewAdapter
                                        .refreshAt(MainRecyclerViewAdapter.ItemIndex.Mission.row)
                                    var isAllChecked = true
                                    for (checkIndex: Int in reachedList) {
                                        if (!mission.waypointList[checkIndex].isChecked) {
                                            isAllChecked = false
                                            break
                                        }
                                    }
                                    mission.isChecking = !isAllChecked
                                }
                        )
                        alert
                            .setNegativeButton(
                                getString(R.string.alert_reach_waypoint_report),
                                { _, _ ->
                                    mission.checkAt(index)
                                    mainRecyclerViewAdapter
                                        .refreshAt(
                                            MainRecyclerViewAdapter.ItemIndex.Waypoint.row
                                                + index
                                        )
                                    mainRecyclerViewAdapter
                                        .refreshAt(MainRecyclerViewAdapter.ItemIndex.Mission.row)
                                    var isAllChecked = true
                                    for (checkIndex: Int in reachedList) {
                                        if (!mission.waypointList[checkIndex].isChecked) {
                                            isAllChecked = false
                                            break
                                        }
                                    }
                                    takeIssuePhoto()
                                    mission.isChecking = !isAllChecked
                                }
                            )
                        alert.show()
                    }
                } else {
                    mission.isChecking = false
                }
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            if (ActivityCompat
                    .checkSelfPermission(
                        this@MainActivity,
                        PermissionRequest.LocationFine.permission
                    ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                mainRecyclerViewAdapter
                    .refreshWith(
                        CoordinateKit
                            .convert(
                                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                                CoordinateKit.CoordinateType.WGS84,
                                CoordinateKit.CoordinateType.GCJ02
                            )
                    )
            }
        }

        override fun onProviderEnabled(provider: String?) {
            if (ActivityCompat
                    .checkSelfPermission(
                        this@MainActivity,
                        PermissionRequest.LocationFine.permission
                    ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                mainRecyclerViewAdapter
                    .refreshWith(
                        CoordinateKit
                            .convert(
                                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                                CoordinateKit.CoordinateType.WGS84,
                                CoordinateKit.CoordinateType.GCJ02
                            )
                    )
            }
        }

        override fun onProviderDisabled(provider: String?) {
            val alert = AlertDialog.Builder(this@MainActivity)
            alert.setTitle(getString(R.string.alert_warning_title))
            alert.setMessage(getString(R.string.alert_location_service_unavailable))
            alert.setCancelable(false)
            alert.setPositiveButton(getString(R.string.confirm), null)
            alert.show()
            if (ActivityCompat
                    .checkSelfPermission(
                        this@MainActivity,
                        PermissionRequest.LocationFine.permission
                    ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                mainRecyclerViewAdapter
                    .refreshWith(
                        CoordinateKit
                            .convert(
                                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                                CoordinateKit.CoordinateType.WGS84,
                                CoordinateKit.CoordinateType.GCJ02
                            )
                    )
            }
        }
    }

    // MissionManager
    private val missionListener: MissionManager.MissionListener =
        object : MissionManager.MissionListener {

        override fun onAllChecked() {
            showMissionDialog()
        }

        override fun didStartedSuccess(missionData: MissionManager.MissionData) {
            mainRecyclerViewAdapter.finishLoading(mission.waypointList)
            showMissionDialog()
            invalidateOptionsMenu()
            buttonReportIssue.show()
        }

        override fun didStartedFailed(error: Exception) {
            mainRecyclerViewAdapter.finishLoading(mission.waypointList)
            invalidateOptionsMenu()
            val alert = AlertDialog.Builder(this@MainActivity)
            alert.setTitle(getString(R.string.alert_warning_title))
            alert.setMessage(error.message)
            alert.setCancelable(false)
            alert.setPositiveButton(getString(R.string.confirm), null)
            alert.show()
        }

        override fun didStoppedSuccess(oldListSize: Int) {
            mainRecyclerViewAdapter.clearList(oldListSize)
            invalidateOptionsMenu()
            val alert = AlertDialog.Builder(this@MainActivity)
            alert.setTitle(getString(R.string.success))
            alert.setMessage(getString(R.string.stop_success))
            alert.setCancelable(false)
            alert.setPositiveButton(getString(R.string.confirm), null)
        }

        override fun didStoppedFailed(error: Exception) {
            buttonReportIssue.show()
            invalidateOptionsMenu()
            val alert = AlertDialog.Builder(this@MainActivity)
            alert.setTitle(getString(R.string.alert_warning_title))
            alert.setMessage(error.message)
            alert.setCancelable(false)
            alert.setPositiveButton(getString(R.string.confirm), null)
            alert.show()
        }

        override fun didReportedSuccess() {
            reportProgressCircle.attachListener {
                mission.isReporting = false
            }
            reportProgressCircle.beginFinalAnimation()
        }

        override fun didReportedFailed(error: Exception) {
            val alert = AlertDialog.Builder(this@MainActivity)
            alert.setTitle(getString(R.string.alert_warning_title))
            alert.setMessage(error.message)
            alert.setCancelable(false)
            alert.setPositiveButton(getString(R.string.confirm), null)
            alert.show()
            reportProgressCircle.hide()
        }
    }
    var mission: MissionManager = MissionManager(this, missionListener)

    // Preference Change Listener
    private var onPreferenceChangedListener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        if (preferences == null || key == null) return@OnSharedPreferenceChangeListener
        when (key) {
            getString(R.string.pref_geo_mapType_key) -> {
                mainRecyclerViewAdapter
                    .refreshAt(MainRecyclerViewAdapter.ItemIndex.LocationWithMap.row)
            }
        }
    }

    /**
     * 权限请求相关
     *
     * @param [code] 权限代码
     * @param [permission] 权限字符串
     *
     * @property [LocationCoarse] 低精度定位权限
     * @property [LocationFine] 高精度定位权限
     * @property [Internet] 互联网权限
     *
     * @author lucka
     * @since 0.1
     */
    enum class PermissionRequest(val code: Int, val permission: String) {
        LocationCoarse(1, android.Manifest.permission.ACCESS_COARSE_LOCATION),
        LocationFine(2, android.Manifest.permission.ACCESS_FINE_LOCATION),
        Internet(3, android.Manifest.permission.INTERNET)
    }

    /**
     * 跨 Activity 活动
     *
     * @param [code] 活动代码
     *
     * @property [TakeIssuePhoto] 拍摄报告相片
     *
     * @author lucka
     * @since 0.1
     */
    private enum class ActivityRequest(val code: Int) {
        TakeIssuePhoto(1)
    }

    /**
     * 主菜单项
     *
     * @param [index] 菜单项位置
     * @param [id] 菜单项资源 ID
     *
     * @property [StartStop] 开始/停止
     * @property [Preference] 设置
     *
     * @author lucka
     * @since 0.1
     */
    private enum class MainMenu(val index: Int, val id: Int) {
        StartStop(0, R.id.action_start_stop),
        Preference(1, R.id.action_preference)
    }

    /**
     * 创建 Activity
     *
     * 注释参考
     * FAB hide when scrolling
     * @see <a href="https://stackoverflow.com/questions/31617398/">Stack Overflow</a>
     * Use object to define a listener directly
     * @see <a href="https://wangjiegulu.gitbooks.io/kotlin-for-android-developers-zh/zai_wo_men_de_app_zhong_shi_xian_yi_ge_li_zi.html">Sample Code</a>
     * Check the Location permission
     * @see <a href="https://developer.android.com/training/permissions/requesting.html?hl=zh-cn#perm-request">Android Developers</a>
     * Open the application settings page
     * @see <a href="https://stackoverflow.com/questions/32822101/">Stack Overflow</a>
     *
     * @author lucka
     * @since 0.1
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // Handel the Main List View
        mainRecyclerView = findViewById(R.id.mainRecyclerView)
        mainRecyclerViewAdapter =
            MainRecyclerViewAdapter(
                this,
                mission.waypointList,
                object : MainRecyclerViewAdapter.OnItemClickListener {
                    override fun onItemClick(position: Int) {
                        when {
                            position == MainRecyclerViewAdapter.ItemIndex.Location.row -> {}
                            position == MainRecyclerViewAdapter.ItemIndex.Mission.row -> {
                                if (!mission.isStarted) return
                                showMissionDialog()
                            }
                            position >= MainRecyclerViewAdapter.ItemIndex.Waypoint.row -> {
                                showWaypointDialog(
                                    position - MainRecyclerViewAdapter.ItemIndex.Waypoint.row
                                )
                            }
                            else -> {

                            }
                        }
                    }
                }
            )
        mainRecyclerView.layoutManager = LinearLayoutManager(this)
        mainRecyclerView.adapter = mainRecyclerViewAdapter

        // Setup the fab
        if (mission.isStarted) {
            buttonReportIssue.show()
        } else {
            buttonReportIssue.hide()
        }
        buttonReportIssue.setOnClickListener { _ ->
            if (!mission.isReporting) {
                takeIssuePhoto()
            }
        }
        // FAB hide when scrolling
        // Use object to define a listener directly
        mainRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                if (mission.isStarted and !mission.isReporting) {
                    if ((dy > 0) && buttonReportIssue.isShown) {
                        buttonReportIssue.hide()
                    } else if ((dy < 0) && !buttonReportIssue.isShown) {
                        buttonReportIssue.show()
                    }
                }
                super.onScrolled(recyclerView, dx, dy)
            }
        })

        // Handel the Location Service
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // Check the Location permission
        if (ActivityCompat
                .checkSelfPermission(this, PermissionRequest.LocationFine.permission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Explain if the permission was denied before
            if (
                ActivityCompat
                    .shouldShowRequestPermissionRationale(
                        this,
                        PermissionRequest.LocationFine.permission
                    )
            ) {
                // Explain
                val alert = AlertDialog.Builder(this)
                alert.setTitle(getString(R.string.alert_permission_title))
                alert.setMessage(getString(R.string.alert_permission_location))
                alert.setCancelable(false)
                alert.setNegativeButton(getString(R.string.system_settings), { _, _ ->
                    // Open the application settings page
                    val intent = Intent()
                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                })
                alert.setPositiveButton(getString(R.string.confirm), null)
                alert.show()
            } else {
                ActivityCompat.requestPermissions(this,
                        arrayOf(PermissionRequest.LocationFine.permission),
                        PermissionRequest.LocationFine.code)
            }
        } else if (ActivityCompat
                .checkSelfPermission(
                    this@MainActivity,
                    PermissionRequest.LocationFine.permission
                ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            mainRecyclerViewAdapter
                .refreshWith(
                    CoordinateKit
                        .convert(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                            CoordinateKit.CoordinateType.WGS84,
                            CoordinateKit.CoordinateType.GCJ02
                        )
                )
        }
        // Check the Internet permission
        /*
        if (ActivityCompat.checkSelfPermission(this, PermissionRequest.Internet.permission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, PermissionRequest.Internet.permission)) {
                val alert = AlertDialog.Builder(this)
                alert.setTitle(getString(R.string.alert_permission_title))
                alert.setMessage(getString(R.string.alert_permission_internet))
                alert.setCancelable(false)
                alert.setNegativeButton(getString(R.string.cancel), null)
                alert.setPositiveButton(getString(R.string.confirm), null)
                alert.show()
            } else {
                ActivityCompat.requestPermissions(this,
                        arrayOf(PermissionRequest.Internet.permission),
                        PermissionRequest.Internet.code)
            }
        }
        */

        // Setup the Preference Change Listener
        PreferenceManager
            .getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(onPreferenceChangedListener)

    }

    override fun onPause() {

        if (ContextCompat
                .checkSelfPermission(this, PermissionRequest.LocationFine.permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.removeUpdates(locationListener)
        }

        mission.pause()
        super.onPause()
    }

    override fun onResume() {
        if (ContextCompat
                .checkSelfPermission(this, PermissionRequest.LocationFine.permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            locationManager
                .requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000.toLong(), 5.toFloat(),
                    locationListener
                )
        }
        mainRecyclerViewAdapter.startLoading()
        val oldListSize = mission.resume()
        mainRecyclerViewAdapter.finishLoading(mission.waypointList, oldListSize)
        invalidateOptionsMenu()
        super.onResume()
    }

    // Setup the menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        val menuStartStop: MenuItem = menu.getItem(MainMenu.StartStop.index)
        if (mission.isStarted) {
            menuStartStop.setTitle(R.string.action_stop)
        } else {
            menuStartStop.setTitle(R.string.action_start)
        }
        return true
    }

    /**
     * 准备主菜单项
     * Update the menu when calling invalidateOptionsMenu()
     * @see <a href="http://blog.csdn.net/q4878802/article/details/51160424">Android动态修改ToolBar的Menu菜单</a>
     *
     * @author lucka
     * @since 0.1
     */
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (menu != null) {
            when {
                mission.isStarted && !mission.isStopping -> {
                    menu.getItem(MainMenu.StartStop.index).isEnabled = true
                    menu.getItem(MainMenu.StartStop.index).title = getString(R.string.action_stop)
                }
                mission.isStarted && mission.isStopping ->
                    menu.getItem(MainMenu.StartStop.index).isEnabled = false
                !mission.isStarted && mission.isLoading ->
                    menu.getItem(MainMenu.StartStop.index).isEnabled = false
                !mission.isStarted && !mission.isLoading -> {
                    menu.getItem(MainMenu.StartStop.index).isEnabled = true
                    menu.getItem(MainMenu.StartStop.index).title = getString(R.string.action_start)
                }
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }

    // Handel the selection on Menu
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            MainMenu.StartStop.id -> {
                if (mission.isStarted) {
                    mission.stop()
                    buttonReportIssue.hide()
                    invalidateOptionsMenu()
                } else {
                    mainRecyclerViewAdapter.startLoading()
                    mission.start()
                    invalidateOptionsMenu()
                }
            }

            MainMenu.Preference.id -> {
                val intent: Intent =
                    Intent(this, PreferenceActivity::class.java).apply {  }
                startActivity(intent)
            }
        }
        return when (item.itemId) {
            MainMenu.StartStop.id, MainMenu.Preference.id -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Handle the permissions request response
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PermissionRequest.LocationFine.code -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    (ActivityCompat
                        .checkSelfPermission(
                            this@MainActivity,
                            PermissionRequest.LocationFine.permission
                        ) ==
                        PackageManager.PERMISSION_GRANTED)
                ) {
                    mainRecyclerViewAdapter
                        .refreshWith(
                            CoordinateKit
                                .convert(
                                    locationManager
                                        .getLastKnownLocation(LocationManager.GPS_PROVIDER),
                                    CoordinateKit.CoordinateType.WGS84,
                                    CoordinateKit.CoordinateType.GCJ02
                                )
                        )
                } else {

                }
                return
            }
        }
    }

    // Handle the activity result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {

            ActivityRequest.TakeIssuePhoto.code -> {

                when (resultCode) {

                    Activity.RESULT_OK -> {
                        submitIssue()
                    }

                }

            }

        }

    }

    /**
     * 拍摄报告相片
     *
     * 调用系统自带相机应用
     * @see <a href="https://developer.android.com/training/camera/photobasics.html#TaskPhotoView">Android Developers</a>
     *
     * 注释参考
     * Take photo and get full size photo
     * @see <a href="https://developer.android.com/training/camera/photobasics.html">Android Developers</a>
     *
     * @author lucka
     * @since 0.1
     */
    fun takeIssuePhoto() {
        // Take photo and get full size photo
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            var imageFile: File? = null
            try {
                val imageFilename = "ISS_" + mission.data.id + "_" + mission.issueSN
                val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                imageFile = File.createTempFile(imageFilename, ".jpg", storageDir)
                mission.issueImagePath = imageFile.absolutePath
            } catch (error: Exception) {

            }
            if (imageFile != null) {
                val photoURI =
                    FileProvider
                        .getUriForFile(
                            this,
                            getString(R.string.imageProviderAuthority),
                            imageFile
                        )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(takePictureIntent, ActivityRequest.TakeIssuePhoto.code)
            }
        }
    }

    // Submit
    /**
     * 预览、编辑和提交报告
     *
     * 显示报告照片、位置、时间，并可添加描述、取消或提交
     *
     * @author lucka
     * @since 0.1
     */
    @SuppressLint("InflateParams")
    private fun submitIssue() {
        // Get Location and image
        val location: Location? =
            if ((ContextCompat
                    .checkSelfPermission(this, PermissionRequest.LocationFine.permission) ==
                    PackageManager.PERMISSION_GRANTED) &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            ) {
                CoordinateKit
                    .convert(
                        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                        CoordinateKit.CoordinateType.WGS84,
                        CoordinateKit.CoordinateType.GCJ02
                    )
            } else {
                null
            }
        if (!File(mission.issueImagePath).exists()) {
            return
        }
        val imageBitmap = BitmapFactory.decodeFile(mission.issueImagePath)

        val dialog = AlertDialog.Builder(this)
        // Get the layout inflater
        val layoutInflater = this.layoutInflater

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_submit, null)
        val longitudeTextView = dialogView.findViewById<TextView>(R.id.longitudeText)
        val latitudeTextView = dialogView.findViewById<TextView>(R.id.latitudeText)
        val timeTextView = dialogView.findViewById<TextView>(R.id.timeText)
        val imageView = dialogView.findViewById<ImageView>(R.id.imageView)
        val longitudeText: String =
            if (location == null)
                getString(R.string.unavailable)
            else
                CoordinateKit.getDegreeString(location.longitude)
        val latitudeText: String =
            if (location == null)
                getString(R.string.unavailable)
            else
                CoordinateKit.getDegreeString(location.latitude)
        val currentTime = Date()
        val timeText: String = DateFormat.getDateTimeInstance().format(currentTime)
        longitudeTextView.text = longitudeText
        latitudeTextView.text = latitudeText
        timeTextView.text = timeText
        if (imageBitmap != null) {
            imageView.setImageBitmap(imageBitmap)
        }

        dialog.setView(dialogView)
        dialog.setTitle(getString(R.string.submit))
        dialog.setPositiveButton(getString(R.string.confirm), { _, _ ->
            reportProgressCircle.show()
            val description: String =
                dialogView.findViewById<TextView>(R.id.descriptionText).text.toString()
            mission.submitIssue(location, currentTime, description) })
        dialog.setNegativeButton(getString(R.string.cancel), { _, _ ->
            File(mission.issueImagePath).delete()
        })
        dialog.show()

    }

    // Show some specific dialogs
    /**
     * 显示任务详情对话框
     *
     * @author lucka
     * @since 0.1
     */
    @SuppressLint("InflateParams")
    fun showMissionDialog() {
        val dialog = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_mission, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val progressText = dialogView.findViewById<TextView>(R.id.progressText)
        val percentText = dialogView.findViewById<TextView>(R.id.percentText)
        val missionIDText = dialogView.findViewById<TextView>(R.id.missionIDText)
        val descriptionText = dialogView.findViewById<TextView>(R.id.descriptionText)
        var finishedCount = 0
        for (waypoint in mission.waypointList) {
            finishedCount += if (waypoint.isChecked) 1 else 0
        }
        progressBar.isIndeterminate = false
        progressBar.max = mission.waypointList.size
        progressBar.progress = finishedCount
        progressText.text = String.format("%d/%d", finishedCount, mission.waypointList.size)
        percentText.text =
            String
                .format(
                    "%.2f%%",
                    (finishedCount.toDouble() / mission.waypointList.size.toDouble()) * 100.0
                )
        missionIDText.text = mission.data.id
        descriptionText.text = mission.data.description

        dialog.setView(dialogView)
        dialog.setTitle(getString(R.string.mission_title))

        dialog.setPositiveButton(getString(R.string.confirm),null)
        if (!mission.isStopping) {
            dialog.setNegativeButton(getString(R.string.action_stop), { _, _ ->
                mission.stop()
                buttonReportIssue.hide()
                invalidateOptionsMenu()
            })
        }
        dialog.show()
    }

    /**
     * 显示 Waypoint 详情对话框
     *
     * @param [index] Waypoint 序号
     *
     * @author lucka
     * @since 0.1
     */
    @SuppressLint("InflateParams")
    fun showWaypointDialog(index: Int) {
        val isMapEnable: Boolean =
            PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.pref_geo_mapEnable_key), false)
        val dialog = AlertDialog.Builder(this)
        val dialogView =
            if (isMapEnable)
                layoutInflater.inflate(R.layout.dialog_waypoint_map, null)
            else
                layoutInflater.inflate(R.layout.dialog_waypoint, null)
        val finishedText = dialogView.findViewById<TextView>(R.id.finishedText)
        val longitudeText = dialogView.findViewById<TextView>(R.id.longitudeText)
        val latitudeText = dialogView.findViewById<TextView>(R.id.latitudeText)
        val distanceText = dialogView.findViewById<TextView>(R.id.distanceText)
        val descriptionText = dialogView.findViewById<TextView>(R.id.descriptionText)
        if (isMapEnable) {
            val mapView = dialogView.findViewById<MapView>(R.id.mapView)
            mapView.onCreate(null)
            val aMap = mapView.map
            aMap.mapType = AMap.MAP_TYPE_SATELLITE
            aMap.mapType =
                when(PreferenceManager
                    .getDefaultSharedPreferences(this)
                    .getString(
                        getString(R.string.pref_geo_mapType_key),
                        getString(R.string.pref_geo_mapType_Satellite)
                    )
                ) {
                    getString(R.string.pref_geo_mapType_Normal) -> AMap.MAP_TYPE_NORMAL
                    getString(R.string.pref_geo_mapType_Satellite) -> AMap.MAP_TYPE_SATELLITE
                    else -> AMap.MAP_TYPE_SATELLITE
                }
            val location = mission.waypointList[index].location
            if (location != null) {
                aMap.moveCamera(CameraUpdateFactory.zoomTo(17.toFloat()))
                aMap
                    .moveCamera(
                        CameraUpdateFactory
                            .changeLatLng(LatLng(location.latitude, location.longitude))
                    )
                aMap
                    .addMarker(
                        MarkerOptions().position(LatLng(location.latitude, location.longitude))
                    )
            }
        }
        if (mission.waypointList[index].isChecked) {
            finishedText.text = getString(R.string.finished)
            finishedText.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary))
        } else {
            finishedText.text = getString(R.string.unfinished)
            finishedText.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
        }
        val location = mission.waypointList[index].location
        if (location == null) {
            longitudeText.text = getString(R.string.unavailable)
            latitudeText.text = getString(R.string.unavailable)
            distanceText.text = getString(R.string.unavailable)
        } else {
            longitudeText.text = CoordinateKit.getDegreeString(location.longitude)
            latitudeText.text = CoordinateKit.getDegreeString(location.latitude)

            if ((ActivityCompat
                    .checkSelfPermission(
                        this@MainActivity,
                        PermissionRequest.LocationFine.permission
                    ) ==
                    PackageManager.PERMISSION_GRANTED
                    ) &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            ) {
                val currentLocation =
                    CoordinateKit.
                        convert(
                            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                            CoordinateKit.CoordinateType.WGS84,
                            CoordinateKit.CoordinateType.GCJ02
                        )
                distanceText.text = when {
                    currentLocation == null ->
                        getString(R.string.unavailable)
                    currentLocation.distanceTo(location) < 1000.0 ->
                        String
                            .format(
                                getString(R.string.distanceMetre),
                                currentLocation.distanceTo(location)
                            )
                    else ->
                        String
                            .format(
                                getString(R.string.distanceKM),
                                currentLocation.distanceTo(location) / 1000.0
                            )
                }
                if (currentLocation != null &&
                    currentLocation.distanceTo(location) < 30.0 &&
                    !mission.waypointList[index].isChecked
                ) {
                    dialog
                        .setNegativeButton(
                            getString(R.string.alert_reach_waypoint_checked),
                            { _, _ ->
                                mission.checkAt(index)
                                mainRecyclerViewAdapter
                                    .refreshAt(
                                        index
                                            + MainRecyclerViewAdapter.ItemIndex.Waypoint.row
                                    )
                                mainRecyclerViewAdapter
                                    .refreshAt(MainRecyclerViewAdapter.ItemIndex.Mission.row)
                            }
                        )
                }

            } else {
                distanceText.text = getString(R.string.unavailable)
            }
        }
        descriptionText.text = mission.waypointList[index].description

        dialog.setView(dialogView)
        dialog.setTitle(mission.waypointList[index].title)
        dialog.setPositiveButton(getString(R.string.confirm),null)
        dialog.show()
    }

}
