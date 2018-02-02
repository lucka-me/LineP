package lab.lucka.linep

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.*

import kotlinx.android.synthetic.main.activity_main.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private enum class PermissionRequest(val code: Int, val permission: String) {
        locationCoarse(1, android.Manifest.permission.ACCESS_COARSE_LOCATION),
        locationFine(2, android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private enum class ActivityRequest(val code: Int) {
        reportIssue(1)
    }

    // MainListView
    private lateinit var mainListView: ListView
    private lateinit var mainListViewAdapter: MainListViewAdapter

    // LocationManager and Listener
    private lateinit var locationManager: LocationManager
    // To avoid multi alert when reaching a waypoint
    private var isChecking = false

    private val locationListener: LocationListener = object :LocationListener {

        override fun onLocationChanged(location: Location?) {

            mainListViewAdapter.refreshWith(locationManager)
            if (mission.isStarted and (location != null) and !isChecking) {
                isChecking = true
                var reachedList = mission.reach(location as Location)
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
                                if (!mission.waypointList[checkIndex].isChecked and !mission.waypointList[checkIndex].isAbnormal) {
                                    isAllChecked = false
                                    break
                                }
                            }
                            isChecking = if (isAllChecked) false else true
                        })
                        alert.setNegativeButton(getString(R.string.alert_reach_waypoint_report), DialogInterface.OnClickListener { _, _ ->
                            mission.waypointList[index].isAbnormal = true
                            var isAllChecked = true
                            for (checkIndex: Int in reachedList) {
                                if (!mission.waypointList[checkIndex].isChecked and !mission.waypointList[checkIndex].isAbnormal) {
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
    var mission: MissionManager = MissionManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // Handel the Main List View
        mainListViewAdapter = MainListViewAdapter(this, mission.waypointList)
        mainListView = findViewById<ListView>(R.id.mainListView)
        mainListView.adapter = mainListViewAdapter

        buttonTakePhoto.hide()
        buttonTakePhoto.setOnClickListener { _ ->
            reportIssue()
        }

        // Handel the Location Service
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // Check the location permission
        //   Refrence: https://developer.android.com/training/permissions/requesting.html?hl=zh-cn#perm-request
        if (ContextCompat.checkSelfPermission(this, PermissionRequest.locationFine.permission) != PackageManager.PERMISSION_GRANTED) {
            // Explain if the permission was denied before
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, PermissionRequest.locationFine.permission)) {
                // Explain
                TODO("Add listeners")
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

    }

    override fun onPause() {

        if (ContextCompat.checkSelfPermission(this, PermissionRequest.locationFine.permission) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(locationListener)
        }
        super.onPause()
    }
    // Set Location Update
    //   Refrence: https://kotlintc.com/articles/921
    override fun onResume() {

        if (ContextCompat.checkSelfPermission(this, PermissionRequest.locationFine.permission) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000.toLong(), 0.toFloat(), locationListener)
            mainListViewAdapter.refreshWith(locationManager)
        }
        super.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    // Handel the selection on Menu
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_start_stop -> {
                if (mission.isStarted) {
                    mission.stop()
                    mainListViewAdapter.refreshWith(mission.waypointList)
                    item.setTitle(getString(R.string.action_start))
                    buttonTakePhoto.hide()
                } else {
                    mission.start()
                    mainListViewAdapter.refreshWith(mission.waypointList)
                    item.setTitle(getString(R.string.action_stop))
                    buttonTakePhoto.show()
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
                        data as Intent
                        val extras = data.getExtras()
                        val imageBitmap = extras.get("data") as Bitmap
                        val location: Location?
                        if (ContextCompat.checkSelfPermission(this, PermissionRequest.locationFine.permission) == PackageManager.PERMISSION_GRANTED &&
                                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        } else {
                            location = null
                        }
                        submit(location, imageBitmap)

                    }

                }

            }

        }

    }

    // Take photo to report issue
    //   Refrence: https://developer.android.com/training/camera/photobasics.html#TaskPhotoView
    fun reportIssue() {
        val takePhotoIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePhotoIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePhotoIntent, ActivityRequest.reportIssue.code)
        }
    }

    // Submit
    fun submit(location: Location?, imageBitmap: Bitmap) {

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
        val current = Calendar.getInstance()
        val dateFormat = SimpleDateFormat(getString(R.string.format_date))
        timeText.text = dateFormat.format(current.time)
        imageView.setImageBitmap(imageBitmap)

        dialog.setView(dialogView)
        dialog.setTitle(getString(R.string.submit))
        dialog.setPositiveButton(getString(R.string.confirm), null)
        dialog.setNegativeButton(getString(R.string.cancel),null)
        dialog.show()

    }

}
