package lab.chd.linep

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.support.v4.app.ActivityCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.TextView



/**
 * Created by lucka on 25/1/2018.
 */


class MainListAdapter(context: Context, waypointList: ArrayList<Waypoint>): BaseAdapter() {

    private enum class MainListIndex(val row: Int, val resource: Int) {
        coordinate(0, R.layout.row_location),
        mission(1, R.layout.row_mission),
        waypoint(2, R.layout.row_waypoint)
    }

    private val context: Context
    private var location: Location?
    private var waypointList: ArrayList<Waypoint>
    private var isLoading: Boolean

    init {
        this.context = context
        this.location = null
        this.waypointList = waypointList
        isLoading = false
    }

    override fun getCount(): Int {
        var rowCount = 1
        rowCount += if (waypointList.isEmpty() and isLoading) 1 else 0
        rowCount += if (waypointList.isNotEmpty()) waypointList.size + 1 else 0
        return rowCount
    }

    override fun getItem(position: Int): Any {
        return ""
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, viewGroup: ViewGroup?): View {
        val layoutInflater = LayoutInflater.from(context)
        val rowView: View = when {
            position == MainListIndex.coordinate.row -> layoutInflater.inflate(MainListIndex.coordinate.resource, viewGroup, false)
            position == MainListIndex.mission.row    -> layoutInflater.inflate(MainListIndex.mission.resource,    viewGroup, false)
            position >= MainListIndex.waypoint.row   -> layoutInflater.inflate(MainListIndex.waypoint.resource,   viewGroup, false)
            else -> View(context)
        }
        // Set up the rows
        when {
            position == MainListIndex.coordinate.row -> {
                val longitudeText = rowView.findViewById<TextView>(R.id.longitudeText)
                val latitudeText = rowView.findViewById<TextView>(R.id.latitudeText)
                if (location == null) {
                    longitudeText.text = context.getText(R.string.unavailable)
                    latitudeText.text = context.getString(R.string.unavailable)
                } else {
                    val setLocation: Location = location as Location
                    String.format("")
                    longitudeText.text = String.format(context.getString(R.string.format_angle),
                            setLocation.longitude.toInt(),
                            ((setLocation.longitude - setLocation.longitude.toInt()) * 60).toInt(),
                            (((setLocation.longitude - setLocation.longitude.toInt()) * 60) - ((setLocation.longitude - setLocation.longitude.toInt()) * 60).toInt()) * 60
                    )

                    latitudeText.text = String.format(context.getString(R.string.format_angle),
                            setLocation.latitude.toInt(),
                            ((setLocation.latitude - setLocation.latitude.toInt()) * 60).toInt(),
                            (((setLocation.latitude - setLocation.latitude.toInt()) * 60) - ((setLocation.latitude - setLocation.latitude.toInt()) * 60).toInt()) * 60
                    )
                }

            }
            position == MainListIndex.mission.row -> {
                if (waypointList.isEmpty() and isLoading) {
                    val progressBar = rowView.findViewById<ProgressBar>(R.id.missionProgressBar)
                    progressBar.isIndeterminate = true
                    val progressText = rowView.findViewById<TextView>(R.id.progressText)
                    progressText.setText(context.getString(R.string.loading))
                    val percentText = rowView.findViewById<TextView>(R.id.percentText)
                    percentText.setText("")
                } else {
                    var finishedCount = 0
                    for (waypoint in waypointList) {
                        finishedCount += if (waypoint.isChecked) 1 else 0
                    }

                    val progressBar = rowView.findViewById<ProgressBar>(R.id.missionProgressBar)
                    progressBar.max = waypointList.size
                    progressBar.incrementProgressBy(finishedCount - progressBar.progress)
                    val progressText = rowView.findViewById<TextView>(R.id.progressText)
                    progressText.setText(String.format("%d/%d", finishedCount, waypointList.size))
                    val percentText = rowView.findViewById<TextView>(R.id.percentText)
                    percentText.setText(String.format("%.2f%%", (finishedCount.toDouble() / waypointList.size.toDouble()) * 100.0))
                }
            }
            position >= MainListIndex.waypoint.row -> {
                val waypointTitle = rowView.findViewById<TextView>(R.id.waypointTitle)
                val distanceText = rowView.findViewById<TextView>(R.id.distanceText)
                val checkBox = rowView.findViewById<CheckBox>(R.id.checkBox)
                waypointTitle.text = waypointList[position - MainListIndex.waypoint.row].title
                if ((waypointList[position - MainListIndex.waypoint.row].location() != null) and
                        (location != null)) {
                    val tempLocation: Location = waypointList[position - MainListIndex.waypoint.row].location() as Location
                    if (tempLocation.distanceTo(location) < 1000.0) {
                        distanceText.text = String.format(context.getString(R.string.distanceMetre), tempLocation.distanceTo(location))
                    } else {
                        distanceText.text = String.format(context.getString(R.string.distanceKM), tempLocation.distanceTo(location) / 1000.0)
                    }
                } else {
                    distanceText.text = context.getString(R.string.unavailable)
                }
                checkBox.isChecked = waypointList[position - MainListIndex.waypoint.row].isChecked
            }
        }

        return rowView
    }

    // Refresh the list with something
    fun refreshWith(locationManager: LocationManager) {

        if ((ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) and
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } else {
            location = null
        }
        this.notifyDataSetChanged()
    }

    fun refreshWith(waypointList: ArrayList<Waypoint>) {
        this.waypointList = waypointList
        this.notifyDataSetChanged()
    }

    fun startLoading() {
        isLoading = true
        this.notifyDataSetChanged()
    }

    fun finishLoading() {
        isLoading = false
        this.notifyDataSetChanged()
    }
}