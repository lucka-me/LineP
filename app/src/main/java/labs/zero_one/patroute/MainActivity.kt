package labs.zero_one.patroute

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.*
import java.io.File
import java.util.*
import android.support.v4.content.FileProvider
import android.support.v7.preference.PreferenceManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.widget.TextView
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_mission.view.*
import kotlinx.android.synthetic.main.dialog_ticket.view.*
import kotlinx.android.synthetic.main.dialog_waypoint.view.*
import kotlinx.android.synthetic.main.dialog_waypoint_map.view.*
import java.text.DateFormat
import java.text.SimpleDateFormat

/**
 * 主页面 Activity
 *
 * ## 属性列表
 * - [mainRecyclerView]
 * - [mainRecyclerViewAdapter]
 * - [locationKit]
 * - [locationKitListener]
 * - [missionManager]
 * - [missionListener]
 * - [trumeKit]
 * - [trumeListener]
 *
 * ## 子类列表
 * - [AppRequest]
 * - [MainMenu]
 *
 * ## 重写方法列表
 * - [onCreate]
 * - [onPause]
 * - [onResume]
 * - [onCreateOptionsMenu]
 * - [onPrepareOptionsMenu]
 * - [onOptionsItemSelected]
 * - [onRequestPermissionsResult]
 * - [onActivityResult]
 *
 * ## 自定义方法列表
 * - [takeTicketPhoto]
 * - [showTicketDialog]
 * - [showMissionDialog]
 * - [showWaypointDialog]
 *
 * @see <a href="https://stackoverflow.com/questions/31617398/">FAB hide when scrolling | Stack Overflow</a>
 *
 * @author lucka-me
 * @since 0.1
 *
 * @property [mainRecyclerView] 主页面的 Recycler View
 * @property [mainRecyclerViewAdapter] 主页面 Recycler View 的适配器
 * @property [LocationKit] 位置管理器
 * @property [locationKitListener] 位置消息监听器
 * @property [missionManager] 任务管理器
 * @property [missionListener] 任务消息监听器
 * @property [trumeListener] 反作弊工具消息监听器
 * @property [trumeKit] 反作弊工具
 */
class MainActivity : AppCompatActivity() {

    // MainRecyclerView
    private lateinit var mainRecyclerView: RecyclerView
    private lateinit var mainRecyclerViewAdapter: MainRecyclerViewAdapter

    // LocationKit
    private val locationKitListener: LocationKit.LocationKitListener =
        object : LocationKit.LocationKitListener {

            override fun onLocationUpdated(location: Location) {
                mainRecyclerViewAdapter.notifyRefreshLocation()
                missionManager.reach(location)
            }

            override fun onProviderDisabled() {
                mainRecyclerViewAdapter.notifyRefreshLocation()
                DialogKit.showDialog(
                    this@MainActivity,
                    R.string.alert_title, R.string.alert_location_service_unavailable,
                    negativeButtonTextId = R.string.system_settings,
                    negativeButtonListener = { _, _ ->
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    cancelable = false
                )
            }

            override fun onProviderSwitchedTo(newProvider: String) {

            }

            override fun onProviderEnabled() {

            }

            override fun onException(error: Exception) {
                when (error.message) {

                    getString(R.string.err_location_permission_denied) -> {
                        LocationKit.showRequestPermissionDialog(this@MainActivity)
                    }

                    else -> {
                        DialogKit.showSimpleAlert(this@MainActivity, error.message)
                    }

                }
            }
    }
    private lateinit var locationKit: LocationKit

    // MissionManager
    private val missionListener: MissionManager.MissionListener =
        object : MissionManager.MissionListener {

            override fun onCheckedAll() {
                showMissionDialog()
            }

            override fun onStarted(isResumed: Boolean) {
                if (!isResumed)
                    mainRecyclerViewAdapter.notifyMissionStarted()
                showMissionDialog()
                invalidateOptionsMenu()
                buttonCreateTicket.show()
            }

            override fun onStartFailed(error: Exception) {
                mainRecyclerViewAdapter.notifyMissionStopped(0)
                invalidateOptionsMenu()
                DialogKit.showSimpleAlert(this@MainActivity, error.message)
            }

            override fun onStopped(oldListSize: Int) {
                mainRecyclerViewAdapter.notifyMissionStopped(oldListSize)
                invalidateOptionsMenu()
                val alert = AlertDialog.Builder(this@MainActivity)
                alert.setTitle(getString(R.string.success))
                alert.setMessage(getString(R.string.stop_success))
                alert.setCancelable(false)
                alert.setPositiveButton(getString(R.string.confirm), null)
            }

            override fun onStopFailed(error: Exception) {
                missionManager.log(
                    this@MainActivity.getString(R.string.log_head_wrn),
                    error.message.toString()
                )
                buttonCreateTicket.show()
                invalidateOptionsMenu()
                DialogKit.showSimpleAlert(this@MainActivity, error.message)
            }

            override fun onChecking(indexList: List<Int>) {

                for (index: Int in indexList) {
                    DialogKit.showDialog(
                        this@MainActivity,
                        R.string.alert_reach_waypoint_title,
                        String.format(
                            getString(R.string.alert_reach_waypoint_message),
                            missionManager.waypointList[index].title
                        ),
                        positiveButtonTextId = R.string.alert_reach_waypoint_checked,
                        positiveButtonListener = { _, _ ->
                            missionManager.checkAt(index)
                            mainRecyclerViewAdapter.notifyRefreshAt(
                                MainRecyclerViewAdapter.CardIndex.Waypoint.row + index
                            )
                            mainRecyclerViewAdapter
                                .notifyRefreshAt(MainRecyclerViewAdapter.CardIndex.Mission.row)
                            var isAllChecked = true
                            for (checkIndex: Int in indexList) {
                                if (!missionManager.waypointList[checkIndex].checked) {
                                    isAllChecked = false
                                    break
                                }
                            }
                            missionManager.isChecking = !isAllChecked
                        },
                        negativeButtonTextId = R.string.alert_reach_waypoint_report,
                        negativeButtonListener = { _, _ ->
                            missionManager.checkAt(index)
                            mainRecyclerViewAdapter.notifyRefreshAt(
                                MainRecyclerViewAdapter.CardIndex.Waypoint.row + index
                            )
                            mainRecyclerViewAdapter
                                .notifyRefreshAt(MainRecyclerViewAdapter.CardIndex.Mission.row)
                            var isAllChecked = true
                            for (checkIndex: Int in indexList) {
                                if (!missionManager.waypointList[checkIndex].checked) {
                                    isAllChecked = false
                                    break
                                }
                            }
                            takeTicketPhoto()
                            missionManager.isChecking = !isAllChecked
                        },
                        cancelable = false
                    )
                }
            }

            override fun onUploadTicketSuccess() {
                reportProgressCircle.attachListener { }
                reportProgressCircle.beginFinalAnimation()
                invalidateOptionsMenu()
            }

            override fun onUploadTicketFailed(error: Exception) {
                missionManager.log(
                    this@MainActivity.getString(R.string.log_head_wrn),
                    error.message.toString()
                )
                reportProgressCircle.hide()
                invalidateOptionsMenu()
                DialogKit.showSimpleAlert(this@MainActivity, error.message)
            }
    }
    private lateinit var missionManager: MissionManager

    // TrumeKit
    private val trumeListener: TrumeKit.TrumeListener = object : TrumeKit.TrumeListener {

        override fun onTimeTrickDetected(internetTime: Long, deviceTime: Long) {
            val dateFormat = SimpleDateFormat(
                this@MainActivity.getString(R.string.iso_datatime),
                Locale.CHINA
            )
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val message = String.format(
                this@MainActivity.getString(R.string.log_trume_time_trick),
                dateFormat.format(Date(internetTime)),
                dateFormat.format(Date(deviceTime))
            )
            if (missionManager.state == MissionManager.MissionState.Started) {
                missionManager.log(
                    getString(R.string.log_head_wrn),
                    message
                )
            }
            DialogKit.showSimpleAlert(this@MainActivity, message)
        }

        override fun onException(error: Exception) {
            if (missionManager.state == MissionManager.MissionState.Started) {
                missionManager.log(
                    this@MainActivity.getString(R.string.log_head_wrn),
                    error.message.toString()
                )
            }
            DialogKit.showSimpleAlert(this@MainActivity, error.message)
        }

    }
    private lateinit var trumeKit: TrumeKit

    /**
     * 请求代码
     *
     * ## 列表
     * - [TakeTicketPhoto]
     * - [PermissionStorage]
     *
     * ## Changelog
     * ### 1.5.0
     * - 与 PermissionRequest 整合
     *
     * @param [code] 请求代码
     *
     * @author lucka-me
     * @since 0.1
     */
    private enum class AppRequest(val code: Int) {
        /**
         * Activity - 拍摄工单照片
         */
        TakeTicketPhoto(101),
        /**
         * 请求权限 - 定位
         */
        PermissionLocation(201),
        /**
         * 请求权限 - 外部存储
         */
        PermissionStorage(202)
    }

    /**
     * 主菜单项
     *
     * @param [index] 菜单项位置
     * @param [id] 菜单项资源 ID
     *
     * @author lucka-me
     * @since 0.1
     */
    private enum class MainMenu(val index: Int, val id: Int) {
        /**
         * 开始/停止
         */
        StartStop(0, R.id.action_start_stop),
        /**
         * 设置
         */
        Preference(1, R.id.action_preference)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // Setup Mission Manager
        missionManager = MissionManager(this, missionListener)

        // Setup the TrumeKit
        trumeKit = TrumeKit(this, trumeListener)

        // Check emulator
        if (TrumeKit.checkEmulator()) {
            DialogKit.showDialog(
                this,
                R.string.alert_title,
                R.string.alert_emulator,
                positiveButtonTextId = R.string.confirm,
                positiveButtonListener = { _, _ ->
                    // Exit the app
                    Process.killProcess(Process.myPid())
                    System.exit(0)
                },
                cancelable = false
            )
        }

        // Handel the Location Service
        locationKit = LocationKit(this, locationKitListener)
        if (LocationKit.requestPermission(this, AppRequest.PermissionLocation.code))
            DialogKit.showDialog(
                this,
                R.string.permission_request_title,
                R.string.permission_explain_location,
                positiveButtonTextId = R.string.confirm,
                negativeButtonTextId = R.string.system_settings,
                negativeButtonListener = { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
                },
                cancelable = false)

        // Check the External Storage permission
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Explain if the permission was denied before
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) {
                // Explain
                DialogKit.showDialog(
                    this,
                    R.string.permission_request_title,
                    R.string.permission_explain_storage,
                    negativeButtonTextId = R.string.system_settings,
                    negativeButtonListener = { _, _ ->
                        startActivity(Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        ))
                    },
                    cancelable = false
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    AppRequest.PermissionStorage.code
                )
            }
        }

        // Handel the Main List View
        mainRecyclerView = findViewById(R.id.mainRecyclerView)
        mainRecyclerViewAdapter = MainRecyclerViewAdapter(
            this,
            missionManager,
            locationKit,
            object : MainRecyclerViewAdapter.OnItemClickListener {

                override fun onItemClick(position: Int) {
                    when {

                        position == MainRecyclerViewAdapter.CardIndex.Location.row -> {}

                        position == MainRecyclerViewAdapter.CardIndex.Mission.row -> {
                            if (missionManager.state != MissionManager.MissionState.Started) return
                            showMissionDialog()
                        }

                        position >= MainRecyclerViewAdapter.CardIndex.Waypoint.row -> {
                            showWaypointDialog(
                                position - MainRecyclerViewAdapter.CardIndex.Waypoint.row
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
        if (missionManager.state == MissionManager.MissionState.Stopped)
            buttonCreateTicket.hide()
        else
            buttonCreateTicket.show()

        buttonCreateTicket.setOnClickListener { _ ->
            if (missionManager.state == MissionManager.MissionState.Started &&
                !missionManager.isUploading
            )
                takeTicketPhoto()
        }
        // FAB hide when scrolling
        // Use object to define a listener directly
        mainRecyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {

                override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                    if (missionManager.state == MissionManager.MissionState.Started &&
                        !missionManager.isUploading
                    ) {
                        if ((dy > 0) && buttonCreateTicket.isShown)
                            buttonCreateTicket.hide()
                        else if ((dy < 0) && !buttonCreateTicket.isShown)
                            buttonCreateTicket.show()
                    }
                    super.onScrolled(recyclerView, dx, dy)
                }
            }
        )

    }

    override fun onPause() {
        locationKit.stopUpdate()
        missionManager.pause()
        super.onPause()
    }

    override fun onResume() {
        if (missionManager.state == MissionManager.MissionState.Paused) {
            missionManager.resume()
        }
        locationKit.startUpdate()
        invalidateOptionsMenu()
        super.onResume()
    }

    // Setup the menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    /**
     * 准备主菜单项
     *
     * ## Changelog
     * ### 1.5.0
     * - 适配 [MissionManager.state]
     *
     * @see <a href="http://blog.csdn.net/q4878802/article/details/51160424">Android动态修改ToolBar的Menu菜单 | CSDN</a>
     *
     * @author lucka
     * @since 0.1
     */
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (menu != null) {
            when (missionManager.state) {
                MissionManager.MissionState.Started -> {
                    if (missionManager.isUploading) {
                        menu.getItem(MainMenu.StartStop.index).isEnabled = false
                        menu.getItem(MainMenu.Preference.index).isEnabled = false
                    } else {
                        menu.getItem(MainMenu.StartStop.index).isEnabled =
                            !missionManager.isUploading
                        menu.getItem(MainMenu.StartStop.index).title =
                            getString(R.string.action_stop)
                        menu.getItem(MainMenu.Preference.index).isEnabled =
                            !missionManager.isUploading
                    }
                }
                MissionManager.MissionState.Stopped -> {
                    menu.getItem(MainMenu.StartStop.index).isEnabled = true
                    menu.getItem(MainMenu.StartStop.index).title = getString(R.string.action_start)
                    menu.getItem(MainMenu.Preference.index).isEnabled = true
                }
                else -> {
                    menu.getItem(MainMenu.StartStop.index).isEnabled = false
                    menu.getItem(MainMenu.Preference.index).isEnabled = false
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

                when (missionManager.state) {
                    MissionManager.MissionState.Started -> {
                        missionManager.stop()
                        buttonCreateTicket.hide()
                        invalidateOptionsMenu()
                    }
                    MissionManager.MissionState.Stopped -> {
                        missionManager.start()
                        mainRecyclerViewAdapter
                            .notifyRefreshAt(MainRecyclerViewAdapter.CardIndex.Mission.row)
                        invalidateOptionsMenu()
                    }
                    else -> {}
                }
            }

            MainMenu.Preference.id -> {
                startActivity(Intent(this, PreferenceMainActivity::class.java))
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
            AppRequest.PermissionLocation.code -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] != PackageManager.PERMISSION_GRANTED
                ) {
                    LocationKit.requestPermission(this, requestCode)
                }
            }
        }
    }

    // Handle the activity result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {

            AppRequest.TakeTicketPhoto.code -> {

                when (resultCode) {

                    Activity.RESULT_OK -> {
                        showTicketDialog()
                    }

                }

            }

        }

    }

    /**
     * 拍摄报告相片
     *
     * @see <a href="https://developer.android.com/training/camera/photobasics.html#TaskPhotoView">调用系统自带相机应用 | Android Developers</a>
     * @see <a href="https://developer.android.com/training/camera/photobasics.html">Take photo and get full size photo | Android Developers</a>
     *
     * @author lucka
     * @since 0.1
     */
    fun takeTicketPhoto() {
        // Take photo and get full size photo
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            var imageFile: File? = null
            try {
                val imageFilename = "ISS_" + missionManager.data.id + "_" + missionManager.ticketSN
                val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                imageFile = File.createTempFile(imageFilename, ".jpg", storageDir)
                missionManager.ticketImagePath = imageFile.absolutePath
            } catch (error: Exception) {
            }
            if (imageFile != null) {
                val photoURI = FileProvider.getUriForFile(
                    this,
                    getString(R.string.imageProviderAuthority),
                    imageFile
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(takePictureIntent, AppRequest.TakeTicketPhoto.code)
            }
        }
    }

    // Submit
    /**
     * 预览、编辑和提交工单，显示报告照片、位置、时间，并可添加描述、取消或提交。
     *
     * @author lucka
     * @since 0.1
     */
    private fun showTicketDialog() {
        // Get Location and image
        val location: Location = locationKit.lastLocation
        if (!File(missionManager.ticketImagePath).exists()) {
            return
        }
        val imageBitmap = BitmapFactory.decodeFile(missionManager.ticketImagePath)

        val dialogView = View.inflate(this, R.layout.dialog_ticket, null)

        val currentTime = Date()
        val timeText: String = DateFormat.getDateTimeInstance().format(currentTime)
        dialogView.ticketLongitudeText.text =
            Location.convert(location.longitude, Location.FORMAT_SECONDS)
        dialogView.ticketLatitudeText.text = Location.convert(location.latitude, Location.FORMAT_SECONDS)
        dialogView.ticketTimeText.text = timeText
        if (imageBitmap != null) {
            dialogView.ticketImageView.setImageBitmap(imageBitmap)
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle(R.string.ticket_title)
            .setPositiveButton(R.string.confirm) { _, _ ->
                reportProgressCircle.show()
                missionManager.uploadTicket(
                    location,
                    currentTime,
                    dialogView.ticketDescriptionText.text.toString()
                )
                invalidateOptionsMenu()
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                File(missionManager.ticketImagePath).delete()
            }
            .show()

    }

    /**
     * 显示任务详情对话框
     *
     * @author lucka
     * @since 0.1
     */
    fun showMissionDialog() {

        val dialogView = View.inflate(this, R.layout.dialog_mission, null)

        var finishedCount = 0
        for (waypoint in missionManager.waypointList) {
            finishedCount += if (waypoint.checked) 1 else 0
        }
        dialogView.progressBar.isIndeterminate = false
        dialogView.progressBar.max = missionManager.waypointList.size
        dialogView.progressBar.progress = finishedCount
        dialogView.progressText.text = String.format("%d/%d", finishedCount, missionManager.waypointList.size)
        dialogView.percentText.text = String.format(
            "%.2f%%",
            (finishedCount.toDouble() / missionManager.waypointList.size.toDouble()) * 100.0
        )
        dialogView.missionIDText.text = missionManager.data.id
        dialogView.missionDescriptionText.text = missionManager.data.description

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle(getString(R.string.mission_title))
            .setPositiveButton(getString(R.string.confirm),null)

        if (missionManager.state != MissionManager.MissionState.Stopping) {
            dialog.setNegativeButton(getString(R.string.action_stop)) { _, _ ->
                missionManager.stop()
                buttonCreateTicket.hide()
                invalidateOptionsMenu()
            }
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
    fun showWaypointDialog(index: Int) {
        val isMapEnable: Boolean = PreferenceManager
            .getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.pref_geo_mapEnable_key), false)
        val dialog = AlertDialog.Builder(this)
        val dialogView =
            if (isMapEnable)
                View.inflate(this, R.layout.dialog_waypoint_map, null)
            else
                View.inflate(this, R.layout.dialog_waypoint, null)
        val waypointFinishedText = dialogView.findViewById<TextView>(R.id.waypointFinishedText)
        val waypointDistanceText = dialogView.findViewById<TextView>(R.id.waypointDistanceText)
        val waypointDescriptionText =
            dialogView.findViewById<TextView>(R.id.waypointDescriptionText)

        val location = missionManager.waypointList[index].location
        if (isMapEnable) {
            dialogView.mapView.onCreate(null)
            val aMap = dialogView.mapView.map
            aMap.mapType = when(PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString(
                    getString(R.string.pref_geo_mapType_key),
                    getString(R.string.pref_geo_mapType_Satellite)
                )
            ) {
                getString(R.string.pref_geo_mapType_Normal)    -> AMap.MAP_TYPE_NORMAL
                getString(R.string.pref_geo_mapType_Satellite) -> AMap.MAP_TYPE_SATELLITE
                getString(R.string.pref_geo_mapType_Night)     -> AMap.MAP_TYPE_NIGHT
                else -> AMap.MAP_TYPE_SATELLITE
            }
            aMap.moveCamera(CameraUpdateFactory.zoomTo(17.toFloat()))
            aMap.moveCamera(
                CameraUpdateFactory.changeLatLng(LatLng(location.latitude, location.longitude))
            )
            aMap.addMarker(
                MarkerOptions().position(LatLng(location.latitude, location.longitude))
            )
        } else {
            dialogView.waypointLongitudeText.text = Location.convert(location.longitude, Location.FORMAT_SECONDS)
            dialogView.waypointLongitudeText.text = Location.convert(location.latitude, Location.FORMAT_SECONDS)
        }
        if (missionManager.waypointList[index].checked) {
            waypointFinishedText.text = getString(R.string.finished)
            waypointFinishedText.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary))
        } else {
            waypointFinishedText.text = getString(R.string.unfinished)
            waypointFinishedText.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
        }


        if (locationKit.isLocationAvailable) {
            val currentLocation = locationKit.lastLocation
            waypointDistanceText.text = when {
                currentLocation.distanceTo(location) < 1000.0 ->
                    String.format(
                        getString(R.string.distanceMetre),
                        currentLocation.distanceTo(location)
                    )
                else ->
                    String.format(
                        getString(R.string.distanceKM),
                        currentLocation.distanceTo(location) / 1000.0
                    )
            }
            if (currentLocation.distanceTo(location) < 30.0 &&
                !missionManager.waypointList[index].checked
            ) {
                dialog.setNegativeButton(
                    getString(R.string.alert_reach_waypoint_checked)
                ) { _, _ ->
                    missionManager.checkAt(index)
                    mainRecyclerViewAdapter.notifyRefreshAt(
                        index + MainRecyclerViewAdapter.CardIndex.Waypoint.row
                    )
                    mainRecyclerViewAdapter
                        .notifyRefreshAt(MainRecyclerViewAdapter.CardIndex.Mission.row)
                }
            }
        } else {
            waypointDistanceText.text = getString(R.string.unavailable)
        }
        waypointDescriptionText.text = missionManager.waypointList[index].description

        dialog.setView(dialogView)
        dialog.setTitle(missionManager.waypointList[index].title)
        dialog.setPositiveButton(getString(R.string.confirm),null)
        dialog.show()
    }

}
