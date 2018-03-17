package lab.chd.patroline.gmap

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.support.v4.app.ActivityCompat
import android.support.v7.preference.PreferenceManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng


/**
 * @author lucka
 * @since 0.1
 */
class MainRecyclerViewAdapter(val context: Context, private var waypointList: ArrayList<Waypoint>, private var onItemClickListener: OnItemClickListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), View.OnClickListener, OnMapReadyCallback {

    private var location: Location? = null
    private var isLoading: Boolean = false
    private var mapView: MapView? = null

    enum class ItemIndex(val row: Int, val viewType: Int, val resource: Int) {
        Location(0, 0, R.layout.main_card_location),
        LocationWithMap(0, 1, R.layout.main_card_location_map),
        Mission(1, 2, R.layout.main_card_mission),
        Waypoint(2, 3, R.layout.main_card_waypoint)
    }

    // Listener
    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    // View Holders
    class MainRecyclerViewHolderLocation(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var longitudeText: TextView = itemView.findViewById(R.id.mainCardLocationLongitudeText)
        var latitudeText: TextView = itemView.findViewById(R.id.mainCardLocationLatitudeText)

    }

    class MainRecyclerViewHolderLocationWithMap(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var mapView: MapView = itemView.findViewById(R.id.mainCardLocationMapView)

    }

    class MainRecyclerViewHolderMission(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var progressBar: ProgressBar = itemView.findViewById(R.id.mainCardMissionProgressBar)
        var progressText: TextView = itemView.findViewById(R.id.mainCardMissionProgressText)
        var percentText: TextView = itemView.findViewById(R.id.mainCardMissionPercentText)

    }

    class MainRecyclerViewHolderWaypoint(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var title: TextView = itemView.findViewById(R.id.mainCardWaypointTitle)
        var distanceText: TextView = itemView.findViewById(R.id.mainCardWaypointDistanceText)
        var checkBox: CheckBox = itemView.findViewById(R.id.mainCardWaypointCheckBox)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater: LayoutInflater = LayoutInflater.from(context)
        val view: View = when(viewType) {
            ItemIndex.Location.viewType -> layoutInflater.inflate(ItemIndex.Location.resource, parent, false)
            ItemIndex.LocationWithMap.viewType -> layoutInflater.inflate(ItemIndex.LocationWithMap.resource, parent, false)
            ItemIndex.Mission.viewType -> layoutInflater.inflate(ItemIndex.Mission.resource, parent, false)
            ItemIndex.Waypoint.viewType -> layoutInflater.inflate(ItemIndex.Waypoint.resource, parent, false)
            else -> layoutInflater.inflate(R.layout.main_card_location, parent, false)
        }
        val viewHolder = when(viewType) {
            ItemIndex.Location.viewType -> MainRecyclerViewHolderLocation(view)
            ItemIndex.LocationWithMap.viewType -> MainRecyclerViewHolderLocationWithMap(view)
            ItemIndex.Mission.viewType -> MainRecyclerViewHolderMission(view)
            ItemIndex.Waypoint.viewType -> MainRecyclerViewHolderWaypoint(view)
            else -> MainRecyclerViewHolderLocation(view)
        }
        view.setOnClickListener(this)

        return viewHolder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(holder.itemViewType) {

            ItemIndex.Location.viewType -> {
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

            ItemIndex.LocationWithMap.viewType -> {
                holder as MainRecyclerViewHolderLocationWithMap
                this.mapView = holder.mapView
                holder.mapView.onCreate(null)
                holder.mapView.getMapAsync(this)
            }

            ItemIndex.Mission.viewType -> {
                holder as MainRecyclerViewHolderMission
                if (waypointList.isEmpty() && isLoading) {
                    holder.progressBar.isIndeterminate = true
                    holder.progressText.text = context.getString(R.string.loading)
                    holder.percentText.text = ""
                } else {
                    holder.progressBar.isIndeterminate = false
                    var finishedCount = 0
                    for (waypoint in waypointList) {
                        finishedCount += if (waypoint.isChecked) 1 else 0
                    }

                    holder.progressBar.max = waypointList.size
                    holder.progressBar.incrementProgressBy(finishedCount - holder.progressBar.progress)
                    holder.progressText.text = String.format("%d/%d", finishedCount, waypointList.size)
                    holder.percentText.text = String.format("%.2f%%", (finishedCount.toDouble() / waypointList.size.toDouble()) * 100.0)
                }
            }

            ItemIndex.Waypoint.viewType -> {
                holder as MainRecyclerViewHolderWaypoint
                holder.title.text = waypointList[position - ItemIndex.Waypoint.row].title
                val waypointLocation = waypointList[position - ItemIndex.Waypoint.row].location
                holder.distanceText.text = if (waypointLocation != null && location != null) {
                    if (waypointLocation.distanceTo(location) < 1000.0) {
                        String.format(context.getString(R.string.distanceMetre), waypointLocation.distanceTo(location))
                    } else {
                        String.format(context.getString(R.string.distanceKM), waypointLocation.distanceTo(location) / 1000.0)
                    }
                } else {
                    context.getString(R.string.unavailable)
                }
                holder.checkBox.isChecked = waypointList[position - ItemIndex.Waypoint.row].isChecked
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
        val isMapEnable: Boolean = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.pref_geo_mapEnable_key), false)
        return when {
            position == ItemIndex.Location.row ->
                if (isMapEnable) ItemIndex.LocationWithMap.viewType else ItemIndex.Location.viewType
            position == ItemIndex.Mission.row  -> ItemIndex.Mission.viewType
            position >= ItemIndex.Waypoint.row -> ItemIndex.Waypoint.viewType
            else -> -1
        }
    }

    override fun onClick(view: View?) {
        if (view != null) {
            onItemClickListener.onItemClick(view.tag as Int)
        }
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        if (googleMap == null || location == null) return
        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.mapType = when(PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.pref_geo_mapType_key), context.getString(R.string.pref_geo_mapType_Hybird))) {
            context.getString(R.string.pref_geo_mapType_Normal) -> GoogleMap.MAP_TYPE_NORMAL
            context.getString(R.string.pref_geo_mapType_Satellite) -> GoogleMap.MAP_TYPE_SATELLITE
            context.getString(R.string.pref_geo_mapType_Terrain) -> GoogleMap.MAP_TYPE_TERRAIN
            context.getString(R.string.pref_geo_mapType_Hybird) -> GoogleMap.MAP_TYPE_HYBRID
            else -> GoogleMap.MAP_TYPE_HYBRID
        }

        if (ActivityCompat.checkSelfPermission(context, MainActivity.PermissionRequest.LocationFine.permission) == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = true
        }

        val setLocation: Location = location as Location
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(LatLng(setLocation.latitude, setLocation.longitude)))
        //googleMap.addMarker(MarkerOptions().position(fixedLatLng))
    }

    fun refreshWith(location: Location?) {

        this.location = location
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.pref_geo_mapEnable_key), false)) {
            this.refreshAt(ItemIndex.Location.row)
        } else if (this.mapView != null) {
            this.mapView!!.getMapAsync(this)
        }
        //this.refreshAt(ItemIndex.Location.row)
        if (waypointList.isNotEmpty()) {
            this.notifyItemRangeChanged(ItemIndex.Waypoint.row, waypointList.size)
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
        this.notifyItemChanged(ItemIndex.Mission.row)
        this.notifyItemChanged(position)
    }
    */

    fun clearList(oldListSize: Int) {
        this.notifyItemRangeRemoved(ItemIndex.Mission.row, oldListSize + 1)
    }

    fun startLoading() {
        isLoading = true
        if (waypointList.size > 0) {
            this.notifyItemChanged(ItemIndex.Mission.row)
        } else {
            this.notifyItemInserted(ItemIndex.Mission.row)
        }
    }

    fun finishLoading(waypointList: ArrayList<Waypoint>, oldListSize: Int = 0) {
        this.waypointList = waypointList
        isLoading = false
        if (waypointList.size > 0) {
            this.notifyItemChanged(ItemIndex.Mission.row)
            if (oldListSize == 0) {
                this.notifyItemRangeInserted(ItemIndex.Waypoint.row, waypointList.size)
            } else {
                this.notifyItemRangeChanged(ItemIndex.Waypoint.row, waypointList.size)
            }
        } else {
            this.notifyItemRemoved(ItemIndex.Mission.row)
        }
    }
}