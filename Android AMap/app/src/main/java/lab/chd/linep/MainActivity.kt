package lab.chd.linep

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
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

class MainActivity : AppCompatActivity() {

    enum class PermissionRequest(val code: Int, val permission: String) {
        locationCoarse(1, android.Manifest.permission.ACCESS_COARSE_LOCATION),
        locationFine(2, android.Manifest.permission.ACCESS_FINE_LOCATION),
        internet(3, android.Manifest.permission.INTERNET)
    }

    private enum class ActivityRequest(val code: Int) {
        reportIssue(1)
    }

    private enum class MainMenu(val index: Int, val id: Int) {
        startStop(0, R.id.action_start_stop),
        preference(1, R.id.action_preference)
    }

    // MainList
    //private lateinit var mainList: ListView
    //lateinit var mainListAdapter: MainListAdapter
    private lateinit var mainRecyclerView: RecyclerView
    lateinit var mainRecyclerViewAdapter: MainRecyclerViewAdapter

    // LocationManager and Listener
    private lateinit var locationManager: LocationManager

    private val locationListener: LocationListener = object :LocationListener {

        override fun onLocationChanged(location: Location?) {

            if (location == null) return
            if (ActivityCompat.checkSelfPermission(this@MainActivity, PermissionRequest.locationFine.permission) == PackageManager.PERMISSION_GRANTED) {
                mainRecyclerViewAdapter.refreshWith(CoordinateKit.convert(location, CoordinateKit.CoordinateType.WGS84, CoordinateKit.CoordinateType.GCJ02))
            }
            // Log location per 10 seconds
            if (mission.isStarted && (Date().time - mission.lastLocationLogDate.time >= 10000)) {
                mission.log(String.format(getString(R.string.log_locationUpdate), location.longitude, location.latitude))
                mission.lastLocationLogDate = Date()
            }
            if (mission.isStarted && !mission.isChecking) {
                mission.isChecking = true
                val reachedList = mission.reach(location)
                if (reachedList.isNotEmpty()) {
                    for (index: Int in reachedList) {
                        val alert = AlertDialog.Builder(this@MainActivity)
                        alert.setTitle(getString(R.string.alert_reach_waypoint_title))
                        alert.setMessage(String.format(getString(R.string.alert_reach_waypoint_message), mission.waypointList[index].title))
                        alert.setCancelable(false)
                        alert.setPositiveButton(getString(R.string.alert_reach_waypoint_checked), DialogInterface.OnClickListener { _, _ ->
                            mission.checkAt(index)
                            mainRecyclerViewAdapter.refreshAt(MainRecyclerViewAdapter.ItemIndex.waypoint.row + index)
                            var isAllChecked = true
                            for (checkIndex: Int in reachedList) {
                                if (!mission.waypointList[checkIndex].isChecked) {
                                    isAllChecked = false
                                    break
                                }
                            }
                            if (isAllChecked) {
                                mission.isChecking = false
                                //mainRecyclerViewAdapter.refreshWith(mission.waypointList)
                            } else {
                                mission.isChecking = true
                            }
                        })
                        alert.setNegativeButton(getString(R.string.alert_reach_waypoint_report), DialogInterface.OnClickListener { _, _ ->
                            mission.checkAt(index)
                            mainRecyclerViewAdapter.refreshAt(MainRecyclerViewAdapter.ItemIndex.waypoint.row + index)
                            var isAllChecked = true
                            for (checkIndex: Int in reachedList) {
                                if (!mission.waypointList[checkIndex].isChecked) {
                                    isAllChecked = false
                                    break
                                }
                            }
                            reportIssue()
                            if (isAllChecked) {
                                mission.isChecking = false
                            } else {
                                mission.isChecking = true
                            }
                        })
                        alert.show()
                    }
                } else {
                    mission.isChecking = false
                }
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            if (ActivityCompat.checkSelfPermission(this@MainActivity, PermissionRequest.locationFine.permission) == PackageManager.PERMISSION_GRANTED) {
                mainRecyclerViewAdapter.refreshWith(CoordinateKit.convert(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER), CoordinateKit.CoordinateType.WGS84, CoordinateKit.CoordinateType.GCJ02))
            }
        }

        override fun onProviderEnabled(provider: String?) {
            if (ActivityCompat.checkSelfPermission(this@MainActivity, PermissionRequest.locationFine.permission) == PackageManager.PERMISSION_GRANTED) {
                mainRecyclerViewAdapter.refreshWith(CoordinateKit.convert(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER), CoordinateKit.CoordinateType.WGS84, CoordinateKit.CoordinateType.GCJ02))
            }
        }

        override fun onProviderDisabled(provider: String?) {
            val alert = AlertDialog.Builder(this@MainActivity)
            alert.setTitle(getString(R.string.alert_warning_title))
            alert.setMessage(getString(R.string.alert_location_service_unavailable))
            alert.setCancelable(false)
            alert.setPositiveButton(getString(R.string.confirm), null)
            alert.show()
            if (ActivityCompat.checkSelfPermission(this@MainActivity, PermissionRequest.locationFine.permission) == PackageManager.PERMISSION_GRANTED) {
                mainRecyclerViewAdapter.refreshWith(CoordinateKit.convert(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER), CoordinateKit.CoordinateType.WGS84, CoordinateKit.CoordinateType.GCJ02))
            }
        }
    }

    // MissionManager
    val missionListener: MissionManager.MissionListener = object : MissionManager.MissionListener {

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

        override fun didStoppedSccess(oldListSize: Int) {
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

        override fun didReportedSccess() {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // Handel the Main List View
        mainRecyclerView = findViewById<RecyclerView>(R.id.mainRecyclerView)
        mainRecyclerViewAdapter = MainRecyclerViewAdapter(this, mission.waypointList, object : MainRecyclerViewAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                when {
                    position == MainRecyclerViewAdapter.ItemIndex.location.row -> {}
                    position == MainRecyclerViewAdapter.ItemIndex.mission.row -> {
                        if (!mission.isStarted) {
                            return
                        }
                        showMissionDialog()
                    }
                    position >= MainRecyclerViewAdapter.ItemIndex.waypoint.row -> {
                        showWaypointDialog(position - MainRecyclerViewAdapter.ItemIndex.waypoint.row)
                    }
                    else -> {}
                }
            }
        })
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
                reportIssue()
            }
        }
        // FAB hide when scrolling
        //   Reference: https://stackoverflow.com/questions/31617398/floatingactionbutton-hide-on-list-scroll
        // Use object to define a listener directly
        //   Reference: https://wangjiegulu.gitbooks.io/kotlin-for-android-developers-zh/zai_wo_men_de_app_zhong_shi_xian_yi_ge_li_zi.html
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
        // Check the location permission
        //   Reference: https://developer.android.com/training/permissions/requesting.html?hl=zh-cn#perm-request
        if (ActivityCompat.checkSelfPermission(this, PermissionRequest.locationFine.permission) != PackageManager.PERMISSION_GRANTED) {
            // Explain if the permission was denied before
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, PermissionRequest.locationFine.permission)) {
                // Explain
                val alert = AlertDialog.Builder(this)
                alert.setTitle(getString(R.string.alert_permission_title))
                alert.setMessage(getString(R.string.alert_permission_location))
                alert.setCancelable(false)
                alert.setNegativeButton(getString(R.string.system_settings), DialogInterface.OnClickListener { _, _ ->
                    // Open the application settings page
                    //   Reference: https://stackoverflow.com/questions/32822101/how-to-programmatically-open-the-permission-screen-for-a-specific-app-on-android
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
                        arrayOf(PermissionRequest.locationFine.permission),
                        PermissionRequest.locationFine.code)
            }
        } else if (ActivityCompat.checkSelfPermission(this@MainActivity, PermissionRequest.locationFine.permission) == PackageManager.PERMISSION_GRANTED) {
            mainRecyclerViewAdapter.refreshWith(CoordinateKit.convert(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER), CoordinateKit.CoordinateType.WGS84, CoordinateKit.CoordinateType.GCJ02))
        }
        // Check the Internet permission
        /*
        if (ActivityCompat.checkSelfPermission(this, PermissionRequest.internet.permission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, PermissionRequest.internet.permission)) {
                val alert = AlertDialog.Builder(this)
                alert.setTitle(getString(R.string.alert_permission_title))
                alert.setMessage(getString(R.string.alert_permission_internet))
                alert.setCancelable(false)
                alert.setNegativeButton(getString(R.string.cancel), null)
                alert.setPositiveButton(getString(R.string.confirm), null)
                alert.show()
            } else {
                ActivityCompat.requestPermissions(this,
                        arrayOf(PermissionRequest.internet.permission),
                        PermissionRequest.internet.code)
            }
        }
        */


    }

    override fun onPause() {

        if (ContextCompat.checkSelfPermission(this, PermissionRequest.locationFine.permission) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(locationListener)
        }

        mission.pause()
        super.onPause()
    }

    // Set Location Update
    //   Reference: https://kotlintc.com/articles/921
    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        if (ContextCompat.checkSelfPermission(this, PermissionRequest.locationFine.permission) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000.toLong(), 5.toFloat(), locationListener)
        }
        mainRecyclerViewAdapter.startLoading()
        val oldListSize = mission.resume()
        mainRecyclerViewAdapter.finishLoading(mission.waypointList, oldListSize)
        invalidateOptionsMenu()
        super.onResume()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    // Setup the menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        val menuStartStop: MenuItem = menu.getItem(MainMenu.startStop.index)
        if (mission.isStarted) {
            menuStartStop.setTitle(R.string.action_stop)
        } else {
            menuStartStop.setTitle(R.string.action_start)
        }
        return true
    }

    // Update the menu when calling invalidateOptionsMenu()
    //   Reference: http://blog.csdn.net/q4878802/article/details/51160424
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (menu != null) {
            if (mission.isStarted and !mission.isStopping) {
                menu.getItem(MainMenu.startStop.index).isEnabled = true
                menu.getItem(MainMenu.startStop.index).setTitle(getString(R.string.action_stop))
            } else if (mission.isStarted and mission.isStopping) {
                menu.getItem(MainMenu.startStop.index).isEnabled = false
            } else if (!mission.isStarted and mission.isLoading) {
                menu.getItem(MainMenu.startStop.index).isEnabled = false
            } else if (!mission.isStarted and !mission.isLoading) {
                menu.getItem(MainMenu.startStop.index).isEnabled = true
                menu.getItem(MainMenu.startStop.index).setTitle(getString(R.string.action_start))
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
            MainMenu.startStop.id -> {
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

            MainMenu.preference.id -> {
                val intent: Intent = Intent(this, PreferenceActivity::class.java).apply {  }
                startActivity(intent)
            }
        }
        return when (item.itemId) {
            MainMenu.startStop.id, MainMenu.preference.id -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Handle the permissions request response
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PermissionRequest.locationFine.code -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this@MainActivity, PermissionRequest.locationFine.permission) == PackageManager.PERMISSION_GRANTED) {
                    mainRecyclerViewAdapter.refreshWith(CoordinateKit.convert(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER), CoordinateKit.CoordinateType.WGS84, CoordinateKit.CoordinateType.GCJ02))
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

            ActivityRequest.reportIssue.code -> {

                when (resultCode) {

                    Activity.RESULT_OK -> {
                        submitIssue()
                    }

                }

            }

        }

    }

    // Take photo to report issue
    //   Reference: https://developer.android.com/training/camera/photobasics.html#TaskPhotoView
    fun reportIssue() {
        // Take photo and get full size photo
        //   Reference: https://developer.android.com/training/camera/photobasics.html
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            var imageFile: File? = null
            try {
                val imageFilename = "ISS_" + mission.data.id + "_" + mission.issueSN
                val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                imageFile = File.createTempFile(imageFilename, ".jpg", storageDir)
                mission.issueImagePath = imageFile.absolutePath
            } catch (error: Exception) {

            }
            if (imageFile != null) {
                val photoURI = FileProvider.getUriForFile(this, getString(R.string.imageProviderAthority), imageFile)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(takePictureIntent, ActivityRequest.reportIssue.code)
            }
        }
    }

    // Submit
    fun submitIssue() {
        // Get location and image
        val location: Location?
        if (ContextCompat.checkSelfPermission(this, PermissionRequest.locationFine.permission) == PackageManager.PERMISSION_GRANTED &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            location = CoordinateKit.convert(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER), CoordinateKit.CoordinateType.WGS84, CoordinateKit.CoordinateType.GCJ02)
        } else {
            location = null
        }
        if (!File(mission.issueImagePath).exists()) {
            return
        }
        val imageBitmap = BitmapFactory.decodeFile(mission.issueImagePath)

        val dialog = AlertDialog.Builder(this)
        // Get the layout inflater
        val layoutInflater = this.getLayoutInflater()

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_submit, null)
        val longitudeTextView = dialogView.findViewById<TextView>(R.id.longitudeText)
        val latitudeTextView = dialogView.findViewById<TextView>(R.id.latitudeText)
        val timeTextView = dialogView.findViewById<TextView>(R.id.timeText)
        val imageView = dialogView.findViewById<ImageView>(R.id.imageView)
        val longitudeText: String = if (location == null) getString(R.string.unavailable) else CoordinateKit.getDegreeString(location.longitude)
        val latitudeText: String = if (location == null) getString(R.string.unavailable) else CoordinateKit.getDegreeString(location.latitude)
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
        dialog.setPositiveButton(getString(R.string.confirm), DialogInterface.OnClickListener { _, _ ->
            reportProgressCircle.show()
            val description: String = dialogView.findViewById<TextView>(R.id.descriptionText).text.toString()
            mission.submitIssue(location, currentTime, description) })
        dialog.setNegativeButton(getString(R.string.cancel), DialogInterface.OnClickListener { _, _ ->
            File(mission.issueImagePath).delete()
        })
        dialog.show()

    }

    // Show some specific dialogs
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
        progressText.setText(String.format("%d/%d", finishedCount, mission.waypointList.size))
        percentText.setText(String.format("%.2f%%", (finishedCount.toDouble() / mission.waypointList.size.toDouble()) * 100.0))
        missionIDText.text = mission.data.id
        descriptionText.text = mission.data.description

        dialog.setView(dialogView)
        dialog.setTitle(getString(R.string.mission_title))

        dialog.setPositiveButton(getString(R.string.confirm),null)
        if (!mission.isStopping) {
            dialog.setNegativeButton(getString(R.string.action_stop), DialogInterface.OnClickListener { _, _ ->
                mission.stop()
                buttonReportIssue.hide()
                invalidateOptionsMenu()
            })
        }
        dialog.show()
    }

    fun showWaypointDialog(index: Int) {
        val isMapEnable: Boolean = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.pref_geo_mapEnable_key), false)
        val dialog = AlertDialog.Builder(this)
        val dialogView = if (isMapEnable) layoutInflater.inflate(R.layout.dialog_waypoint_map, null) else layoutInflater.inflate(R.layout.dialog_waypoint, null)
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
            val location = mission.waypointList[index].location()
            if (location != null) {
                aMap.moveCamera(CameraUpdateFactory.zoomTo(17.toFloat()))
                aMap.moveCamera(CameraUpdateFactory.changeLatLng(LatLng(location.latitude, location.longitude)))
                aMap.addMarker(MarkerOptions().position(LatLng(location.latitude, location.longitude)))
            }
        }
        if (mission.waypointList[index].isChecked) {
            finishedText.text = getString(R.string.finished)
            finishedText.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary))
        } else {
            finishedText.text = getString(R.string.unfinished)
            finishedText.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
        }
        if (mission.waypointList[index].location() == null) {
            longitudeText.text = getString(R.string.unavailable)
            latitudeText.text = getString(R.string.unavailable)
            distanceText.text = getString(R.string.unavailable)
        } else {
            val location: Location = mission.waypointList[index].location() as Location
            longitudeText.text = CoordinateKit.getDegreeString(location.longitude)
            latitudeText.text = CoordinateKit.getDegreeString(location.latitude)

            if ((ActivityCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) and
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                val currentLocation = CoordinateKit.convert(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER), CoordinateKit.CoordinateType.WGS84, CoordinateKit.CoordinateType.GCJ02)
                if (currentLocation.distanceTo(location) < 1000.0) {
                    distanceText.text = String.format(getString(R.string.distanceMetre), currentLocation.distanceTo(location))
                } else {
                    distanceText.text = String.format(getString(R.string.distanceKM), currentLocation.distanceTo(location) / 1000.0)
                }
                if (currentLocation.distanceTo(location) < 30.0 && !mission.waypointList[index].isChecked) {
                    dialog.setNegativeButton(getString(R.string.alert_reach_waypoint_checked), DialogInterface.OnClickListener { _, _ ->
                        mission.checkAt(index)
                        mainRecyclerViewAdapter.refreshAt(index + MainRecyclerViewAdapter.ItemIndex.waypoint.row)
                    })
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
