package lab.chd.linep

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.support.v4.app.ActivityCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView



/**
 * Created by lucka on 21/2/2018.
 */

class MainRecyclerViewAdapter(val context: Context, var waypointList: ArrayList<Waypoint>, var onItemClickListener: OnItemClickListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), View.OnClickListener {
    enum class ItemIndex(val row: Int, val viewType: Int, val resource: Int) {
        location(0, 0, R.layout.main_card_location),
        mission(1, 1, R.layout.main_card_mission),
        waypoint(2, 2, R.layout.main_card_waypoint)
    }

    private var location: Location? = null
    private var isLoading: Boolean = false
    //private var onItemClickListener: OnItemClickListener? = null

    // Listener
    public interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    override fun onClick(view: View?) {
        if (view != null) {
            onItemClickListener.onItemClick(view.tag as Int)
        }
    }

    // View Holders
    class MainRecyclerViewHolderLocation(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var longitudeText: TextView
        var latitudeText: TextView

        init {
            longitudeText = itemView.findViewById<TextView>(R.id.mainCardLocationLongitudeText)
            latitudeText = itemView.findViewById<TextView>(R.id.mainCardLocationLatitudeText)
        }
    }

    class MainRecyclerViewHolderMission(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var progressBar: ProgressBar
        var progressText: TextView
        var percentText: TextView

        init {
            progressBar = itemView.findViewById<ProgressBar>(R.id.mainCardMissionProgressBar)
            progressText = itemView.findViewById<TextView>(R.id.mainCardMissionProgressText)
            percentText = itemView.findViewById<TextView>(R.id.mainCardMissionPercentText)
        }
    }

    class MainRecyclerViewHolderWaypoint(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var title: TextView
        var distanceText: TextView
        var checkBox: CheckBox

        init {
            title = itemView.findViewById<TextView>(R.id.mainCardWaypointTitle)
            distanceText = itemView.findViewById<TextView>(R.id.mainCardWaypointDistanceText)
            checkBox = itemView.findViewById<CheckBox>(R.id.mainCardWaypointCheckBox)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater: LayoutInflater = LayoutInflater.from(context)
        val view: View = when(viewType) {
            ItemIndex.location.viewType -> layoutInflater.inflate(ItemIndex.location.resource, parent, false)
            ItemIndex.mission.viewType -> layoutInflater.inflate(ItemIndex.mission.resource, parent, false)
            ItemIndex.waypoint.viewType -> layoutInflater.inflate(ItemIndex.waypoint.resource, parent, false)
            else -> layoutInflater.inflate(R.layout.main_card_location, parent, false)
        }
        val viewHolder = when(viewType) {
            ItemIndex.location.viewType -> MainRecyclerViewHolderLocation(view)
            ItemIndex.mission.viewType -> MainRecyclerViewHolderMission(view)
            ItemIndex.waypoint.viewType -> MainRecyclerViewHolderWaypoint(view)
            else -> MainRecyclerViewHolderLocation(view)
        }
        view.setOnClickListener(this)

        return viewHolder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
        if (holder == null) {
            return
        }
        when(holder.itemViewType) {

            ItemIndex.location.viewType -> {
                holder as MainRecyclerViewHolderLocation
                if (location == null) {
                    holder.longitudeText.text = context.getString(R.string.unavailable)
                    holder.latitudeText.text = context.getString(R.string.unavailable)
                } else {
                    val location: Location = this.location as Location
                    holder.longitudeText.text = String.format(context.getString(R.string.format_angle),
                            location.longitude.toInt(),
                            ((location.longitude - location.longitude.toInt()) * 60).toInt(),
                            (((location.longitude - location.longitude.toInt()) * 60) - ((location.longitude - location.longitude.toInt()) * 60).toInt()) * 60
                    )
                    holder.latitudeText.text = String.format(context.getString(R.string.format_angle),
                            location.latitude.toInt(),
                            ((location.latitude - location.latitude.toInt()) * 60).toInt(),
                            (((location.latitude - location.latitude.toInt()) * 60) - ((location.latitude - location.latitude.toInt()) * 60).toInt()) * 60
                    )
                }
            }

            ItemIndex.mission.viewType -> {
                holder as MainRecyclerViewHolderMission
                if (waypointList.isEmpty() && isLoading) {
                    holder.progressBar.isIndeterminate = true
                    holder.progressText.setText(context.getString(R.string.loading))
                    holder.percentText.setText("")
                } else {
                    holder.progressBar.isIndeterminate = false
                    var finishedCount = 0
                    for (waypoint in waypointList) {
                        finishedCount += if (waypoint.isChecked) 1 else 0
                    }

                    holder.progressBar.max = waypointList.size
                    holder.progressBar.incrementProgressBy(finishedCount - holder.progressBar.progress)
                    holder.progressText.setText(String.format("%d/%d", finishedCount, waypointList.size))
                    holder.percentText.setText(String.format("%.2f%%", (finishedCount.toDouble() / waypointList.size.toDouble()) * 100.0))
                }
            }

            ItemIndex.waypoint.viewType -> {
                holder as MainRecyclerViewHolderWaypoint
                holder.title.text = waypointList[position - ItemIndex.waypoint.row].title
                if ((waypointList[position - ItemIndex.waypoint.row].location() != null) and
                        (location != null)) {
                    val tempLocation: Location = waypointList[position - ItemIndex.waypoint.row].location() as Location
                    if (tempLocation.distanceTo(location) < 1000.0) {
                        holder.distanceText.text = String.format(context.getString(R.string.distanceMetre), tempLocation.distanceTo(location))
                    } else {
                        holder.distanceText.text = String.format(context.getString(R.string.distanceKM), tempLocation.distanceTo(location) / 1000.0)
                    }
                } else {
                    holder.distanceText.text = context.getString(R.string.unavailable)
                }
                holder.checkBox.isChecked = waypointList[position - ItemIndex.waypoint.row].isChecked
            }

            else -> return
        }
        holder.itemView.tag = position
    }

    override fun getItemCount(): Int {
        var itemCount = 1
        itemCount += if (waypointList.isEmpty() && isLoading) 1 else 0
        itemCount += if (waypointList.isNotEmpty()) waypointList.size + 1 else 0
        return itemCount
    }

    override fun getItemViewType(position: Int): Int {
        val viewType: Int = when {
            position == ItemIndex.location.row -> ItemIndex.location.viewType
            position == ItemIndex.mission.row  -> ItemIndex.mission.viewType
            position >= ItemIndex.waypoint.row -> ItemIndex.waypoint.viewType
            else -> -1
        }
        return viewType
    }

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