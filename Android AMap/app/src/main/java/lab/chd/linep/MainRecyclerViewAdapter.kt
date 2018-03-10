package lab.chd.linep

import android.content.Context
import android.location.Location
import android.support.v7.preference.PreferenceManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import com.amap.api.maps2d.AMap
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.MapView
import com.amap.api.maps2d.model.MyLocationStyle

/**
 * Created by lucka on 21/2/2018.
 */

class MainRecyclerViewAdapter(val context: Context, var waypointList: ArrayList<Waypoint>, var onItemClickListener: OnItemClickListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), View.OnClickListener {

    enum class ItemIndex(val row: Int, val viewType: Int, val resource: Int) {
        location(0, 0, R.layout.main_card_location),
        locationWithMap(0, 1, R.layout.main_card_location_map),
        mission(1, 2, R.layout.main_card_mission),
        waypoint(2, 3, R.layout.main_card_waypoint)
    }

    private var location: Location? = null
    private var isLoading: Boolean = false
    //private var mapView: MapView? = null
    private var aMap: AMap? = null
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

        var longitudeText: TextView = itemView.findViewById<TextView>(R.id.mainCardLocationLongitudeText)
        var latitudeText: TextView = itemView.findViewById<TextView>(R.id.mainCardLocationLatitudeText)

    }

    class MainRecyclerViewHolderLocationWithMap(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var mapView: MapView = itemView.findViewById<MapView>(R.id.mainCardLocationMapView)

    }

    class MainRecyclerViewHolderMission(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var progressBar: ProgressBar = itemView.findViewById<ProgressBar>(R.id.mainCardMissionProgressBar)
        var progressText: TextView = itemView.findViewById<TextView>(R.id.mainCardMissionProgressText)
        var percentText: TextView = itemView.findViewById<TextView>(R.id.mainCardMissionPercentText)

    }

    class MainRecyclerViewHolderWaypoint(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var title: TextView = itemView.findViewById<TextView>(R.id.mainCardWaypointTitle)
        var distanceText: TextView = itemView.findViewById<TextView>(R.id.mainCardWaypointDistanceText)
        var checkBox: CheckBox = itemView.findViewById<CheckBox>(R.id.mainCardWaypointCheckBox)

    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater: LayoutInflater = LayoutInflater.from(context)
        val view: View = when(viewType) {
            ItemIndex.location.viewType -> layoutInflater.inflate(ItemIndex.location.resource, parent, false)
            ItemIndex.locationWithMap.viewType -> layoutInflater.inflate(ItemIndex.locationWithMap.resource, parent, false)
            ItemIndex.mission.viewType -> layoutInflater.inflate(ItemIndex.mission.resource, parent, false)
            ItemIndex.waypoint.viewType -> layoutInflater.inflate(ItemIndex.waypoint.resource, parent, false)
            else -> layoutInflater.inflate(R.layout.main_card_location, parent, false)
        }
        val viewHolder = when(viewType) {
            ItemIndex.location.viewType -> MainRecyclerViewHolderLocation(view)
            ItemIndex.locationWithMap.viewType -> MainRecyclerViewHolderLocationWithMap(view)
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

            ItemIndex.locationWithMap.viewType -> {
                holder as MainRecyclerViewHolderLocationWithMap
                holder.mapView.onCreate(null)
                this.aMap = holder.mapView.map
                this.aMap!!.uiSettings.isScrollGesturesEnabled = false
                this.aMap!!.mapType = AMap.MAP_TYPE_SATELLITE

                // Setup My Location
                val myLocationStyle = MyLocationStyle()
                //myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_FOLLOW)
                //myLocationStyle.showMyLocation(true)
                this.aMap!!.setMyLocationStyle(myLocationStyle)
                this.aMap!!.isMyLocationEnabled = true
                this.aMap!!.moveCamera(CameraUpdateFactory.zoomTo(17.toFloat()))
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

    /*
    override fun onMapReady(googleMap: GoogleMap?) {
        if (googleMap == null || location == null) return
        googleMap.uiSettings.isMapToolbarEnabled = false

        if (ActivityCompat.checkSelfPermission(context, MainActivity.PermissionRequest.locationFine.permission) == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
        }

        val setLocation: Location = location as Location
        val fixedLatLng: LatLng = CoordinateTransformUtil.wgs84togcj02(LatLng(setLocation.latitude, setLocation.longitude))
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(fixedLatLng))
        //googleMap.addMarker(MarkerOptions().position(fixedLatLng))
    }
    */

    override fun getItemCount(): Int {
        var itemCount = 1
        itemCount += if (waypointList.isEmpty() && isLoading) 1 else 0
        itemCount += if (waypointList.isNotEmpty()) waypointList.size + 1 else 0
        return itemCount
    }

    override fun getItemViewType(position: Int): Int {
        val isMapEnable: Boolean = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.pref_geo_mapEnable_key), false)
        val viewType: Int = when {
            position == ItemIndex.location.row -> if (isMapEnable) ItemIndex.locationWithMap.viewType else ItemIndex.location.viewType
            position == ItemIndex.mission.row  -> ItemIndex.mission.viewType
            position >= ItemIndex.waypoint.row -> ItemIndex.waypoint.viewType
            else -> -1
        }
        return viewType
    }

    fun refreshWith(location: Location?) {

        this.location = location
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.pref_geo_mapEnable_key), false)) {
            this.refreshAt(ItemIndex.location.row)
        } else if (this.aMap != null) {
        }
        //this.refreshAt(ItemIndex.location.row)
        if (waypointList.isNotEmpty()) {
            this.notifyItemRangeChanged(ItemIndex.waypoint.row, waypointList.size)
        }
    }

    /*
    fun refreshWith(waypointList: ArrayList<Waypoint>) {
        this.waypointList = waypointList
        this.notifyDataSetChanged()
    }
    */

    fun refreshAt(position: Int) {
        this.notifyItemChanged(position)
    }

    /*
    fun refreshAt(position: Int, waypointList: ArrayList<Waypoint>) {
        this.waypointList = waypointList
        this.notifyItemChanged(ItemIndex.mission.row)
        this.notifyItemChanged(position)
    }
    */

    fun clearList(oldListSize: Int) {
        this.notifyItemRangeRemoved(ItemIndex.mission.row, oldListSize + 1)
    }

    fun startLoading() {
        isLoading = true
        if (waypointList.size > 0) {
            this.notifyItemChanged(ItemIndex.mission.row)
        } else {
            this.notifyItemInserted(ItemIndex.mission.row)
        }
    }

    fun finishLoading(waypointList: ArrayList<Waypoint>, oldListSize: Int = 0) {
        this.waypointList = waypointList
        isLoading = false
        if (waypointList.size > 0) {
            this.notifyItemChanged(ItemIndex.mission.row)
            if (oldListSize == 0) {
                this.notifyItemRangeInserted(ItemIndex.waypoint.row, waypointList.size)
            } else {
                this.notifyItemRangeChanged(ItemIndex.waypoint.row, waypointList.size)
            }
        } else {
            this.notifyItemRemoved(ItemIndex.mission.row)
        }
    }
}