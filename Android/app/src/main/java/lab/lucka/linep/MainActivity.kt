package lab.lucka.linep

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.*

import kotlinx.android.synthetic.main.activity_main.*
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.support.v4.content.FileProvider




class MainActivity : AppCompatActivity() {

    private enum class PermissionRequest(val code: Int, val permission: String) {
        locationCoarse(1, android.Manifest.permission.ACCESS_COARSE_LOCATION),
        locationFine(2, android.Manifest.permission.ACCESS_FINE_LOCATION),
        internet(3, android.Manifest.permission.INTERNET)
    }

    private enum class ActivityRequest(val code: Int) {
        reportIssue(1)
    }

    private enum class MainMenu(val index: Int, val id: Int) {
        startStop(0, R.id.action_start_stop),
        settings(1, R.id.action_settings)
    }

    // MainListView
    private lateinit var mainListView: ListView
    lateinit var mainListViewAdapter: MainListViewAdapter

    // LocationManager and Listener
    private lateinit var locationManager: LocationManager
    // To avoid multi alert when reaching a waypoint
    private var isChecking = false

    private val locationListener: LocationListener = object :LocationListener {

        override fun onLocationChanged(location: Location?) {

            mainListViewAdapter.refreshWith(locationManager)
            if (mission.isStarted and (location != null) and !isChecking) {
                isChecking = true
                val reachedList = mission.reach(location as Location)
                if (reachedList.isNotEmpty()) {
                    for (index: Int in reachedList) {
                        val alert = AlertDialog.Builder(this@MainActivity)
                        alert.setTitle(getString(R.string.alert_reach_waypoint_title))
                        alert.setMessage(String.format(getString(R.string.alert_reach_waypoint_message), mission.waypointList[index].title))
                        alert.setCancelable(false)
                        alert.setPositiveButton(getString(R.string.alert_reach_waypoint_checked), DialogInterface.OnClickListener { _, _ ->
                            mission.waypointList[index].isChecked = true
                            var isAllChecked = true
                            for (checkIndex: Int in reachedList) {
                                if (!mission.waypointList[checkIndex].isChecked) {
                                    isAllChecked = false
                                    break
                                }
                            }
                            isChecking = if (isAllChecked) false else true
                        })
                        alert.setNegativeButton(getString(R.string.alert_reach_waypoint_report), DialogInterface.OnClickListener { _, _ ->
                            mission.waypointList[index].isChecked = true
                            var isAllChecked = true
                            for (checkIndex: Int in reachedList) {
                                if (!mission.waypointList[checkIndex].isChecked) {
                                    isAllChecked = false
                                    break
                                }
                            }
                            isChecking = if (isAllChecked) false else true
                            reportIssue() })
                        alert.show()
                    }
                    mainListViewAdapter.refreshWith(mission.waypointList)
                }
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            mainListViewAdapter.refreshWith(locationManager)
        }

        override fun onProviderEnabled(provider: String?) {
            mainListViewAdapter.refreshWith(locationManager)
        }

        override fun onProviderDisabled(provider: String?) {
            val alert = AlertDialog.Builder(this@MainActivity)
            alert.setTitle(getString(R.string.alert_warning_title))
            alert.setMessage(getString(R.string.alert_location_service_unavailable))
            alert.setCancelable(false)
            alert.setPositiveButton(getString(R.string.confirm), null)
            alert.show()
            mainListViewAdapter.refreshWith(locationManager)
        }
    }

    // MissionManager
    val missionListener: MissionListener = object :MissionListener {
        override fun didStartedSuccess(missionData: MissionData) {
            mainListViewAdapter.finishLoading()
            mainListViewAdapter.refreshWith(mission.waypointList)
            buttonReportIssue.show()
        }

        override fun didStartedFailed(error: Exception) {
            mainListViewAdapter.finishLoading()
            val alert = AlertDialog.Builder(this@MainActivity)
            alert.setTitle(getString(R.string.alert_warning_title))
            alert.setMessage(error.message)
            alert.setCancelable(false)
            alert.setPositiveButton(getString(R.string.confirm), null)
            alert.show()
        }
    }
    var mission: MissionManager = MissionManager(this, missionListener)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // Try to resume the misssion
        mission.resume()

        // Handel the Main List View
        mainListViewAdapter = MainListViewAdapter(this, mission.waypointList)
        mainListView = findViewById<ListView>(R.id.mainListView)
        mainListView.adapter = mainListViewAdapter

        if (mission.isStarted) {
            buttonReportIssue.show()
        } else {
            buttonReportIssue.hide()
        }
        buttonReportIssue.setOnClickListener { _ ->
            reportIssue()
        }

        // Handel the Location Service
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // Check the location permission
        //   Refrence: https://developer.android.com/training/permissions/requesting.html?hl=zh-cn#perm-request
        if (ActivityCompat.checkSelfPermission(this, PermissionRequest.locationFine.permission) != PackageManager.PERMISSION_GRANTED) {
            // Explain if the permission was denied before
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, PermissionRequest.locationFine.permission)) {
                // Explain
                val alert = AlertDialog.Builder(this)
                alert.setTitle(getString(R.string.alert_permission_title))
                alert.setMessage(getString(R.string.alert_permission_location))
                alert.setCancelable(false)
                alert.setNegativeButton(getString(R.string.cancel), null)
                alert.setPositiveButton(getString(R.string.confirm), null)
                alert.show()
            } else {
                ActivityCompat.requestPermissions(this,
                        arrayOf(PermissionRequest.locationFine.permission),
                        PermissionRequest.locationFine.code)
            }
        } else {
            mainListViewAdapter.refreshWith(locationManager)
        }
        // Check the Internet permission
        /*
        if (ContextCompat.checkSelfPermission(this, PermissionRequest.internet.permission) != PackageManager.PERMISSION_GRANTED) {
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
    //   Refrence: https://kotlintc.com/articles/921
    override fun onResume() {

        if (ContextCompat.checkSelfPermission(this, PermissionRequest.locationFine.permission) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000.toLong(), 0.toFloat(), locationListener)
            mainListViewAdapter.refreshWith(locationManager)
        }

        mission.resume()
        super.onResume()
    }

    override fun onDestroy() {
        mission.pause()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        var menuStartStop: MenuItem = menu.getItem(MainMenu.startStop.index)
        if (mission.isStarted) {
            menuStartStop.setTitle(R.string.action_stop)
        } else {
            menuStartStop.setTitle(R.string.action_start)
        }
        return true
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
                    mainListViewAdapter.refreshWith(mission.waypointList)
                    item.setTitle(getString(R.string.action_start))
                    buttonReportIssue.hide()
                } else {
                    mainListViewAdapter.startLoading()
                    mission.start()
                    item.setTitle(getString(R.string.action_stop))
                }
            }
        }
        return when (item.itemId) {
            R.id.action_start_stop, R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Handle the permissions request response
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PermissionRequest.locationFine.code -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mainListViewAdapter.refreshWith(locationManager)
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
    //   Refrence: https://developer.android.com/training/camera/photobasics.html#TaskPhotoView
    fun reportIssue() {
        // Take photo and get full size photo
        //   Refrence: https://developer.android.com/training/camera/photobasics.html
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            var imageFile: File? = null
            try {
                val imageFilename = "ISS_" + mission.missionData.ID + mission.issueSN
                val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                imageFile = File.createTempFile(imageFilename, ".jpg", storageDir)
                mission.issueImagePath = imageFile.absolutePath
            } catch (error: Exception) {

            }
            if (imageFile != null) {
                val photoURI = FileProvider.getUriForFile(this, "lab.lucka.linep.fileprovider", imageFile)
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
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
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
        val dialogView = layoutInflater.inflate(R.layout.submit_dialog, null)
        val longitudeText = dialogView.findViewById<TextView>(R.id.longitudeText)
        val latitudeText = dialogView.findViewById<TextView>(R.id.latitudeText)
        val timeText = dialogView.findViewById<TextView>(R.id.timeText)
        val imageView = dialogView.findViewById<ImageView>(R.id.imageView)
        if (location == null) {
            longitudeText.text = getString(R.string.unavailable)
            latitudeText.text = getString(R.string.unavailable)
        } else {
            val setLocation: Location = location as Location
            longitudeText.text = String.format(getString(R.string.format_angle),
                    setLocation.longitude.toInt(),
                    ((setLocation.longitude - setLocation.longitude.toInt()) * 60).toInt(),
                    (((setLocation.longitude - setLocation.longitude.toInt()) * 60) - ((setLocation.longitude - setLocation.longitude.toInt()) * 60).toInt()) * 60
            )

            latitudeText.text = String.format(getString(R.string.format_angle),
                    setLocation.latitude.toInt(),
                    ((setLocation.latitude - setLocation.latitude.toInt()) * 60).toInt(),
                    (((setLocation.latitude - setLocation.latitude.toInt()) * 60) - ((setLocation.latitude - setLocation.latitude.toInt()) * 60).toInt()) * 60
            )
        }
        timeText.text = SimpleDateFormat(getString(R.string.format_date)).format(Date())
        if (imageBitmap != null) {
            imageView.setImageBitmap(imageBitmap)
        }

        dialog.setView(dialogView)
        dialog.setTitle(getString(R.string.submit))
        dialog.setPositiveButton(getString(R.string.confirm), DialogInterface.OnClickListener { _, _ ->
            mission.submitIssue() })
        dialog.setNegativeButton(getString(R.string.cancel),null)
        dialog.show()

    }

}
