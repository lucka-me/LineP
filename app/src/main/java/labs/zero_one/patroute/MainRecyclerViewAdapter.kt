package labs.zero_one.patroute

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.support.v7.preference.PreferenceManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.MyLocationStyle

/**
 * 主页面的 Recycler View 适配器
 *
 * ## Changelog
 * ### 1.5.0
 * - 接入 [missionManager] 和 [locationKit] 以直接利用数据
 * - 优化各个方法
 *
 * ## 子类列表
 * - [CardIndex]
 * - [OnItemClickListener]
 * - [MainRecyclerViewHolderLocation]
 * - [MainRecyclerViewHolderLocationWithMap]
 * - [MainRecyclerViewHolderMission]
 * - [MainRecyclerViewHolderWaypoint]
 *
 * ## 重写方法列表
 * - [onCreateViewHolder]
 * - [onBindViewHolder]
 * - [getItemCount]
 * - [getItemViewType]
 * - [onClick]
 *
 * ## 自定义方法列表
 * - [notifyRefreshLocation]
 * - [notifyRefreshAt]
 * - [notifyMissionStopped]
 * - [notifyMissionStarted]
 *
 * @param [context] 环境
 * @param [missionManager] 任务管理器
 * @param [locationKit] 位置工具
 * @param [onItemClickListener] 点击监听器 [OnItemClickListener]
 *
 * @author lucka
 * @since 0.1
 *
 * @property [aMap] 位置地图卡片中的高德地图控制器
 * @property [isMapEnabled] 地图是否开启
 */
class MainRecyclerViewAdapter(
    val context: Context,
    private val missionManager: MissionManager,
    private val locationKit: LocationKit,
    private var onItemClickListener: OnItemClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
    View.OnClickListener {

    private var aMap: AMap? = null
    private var isMapEnabled: Boolean = PreferenceManager
        .getDefaultSharedPreferences(context)
        .getBoolean(context.getString(R.string.pref_geo_mapEnable_key), false)
    private val onSharedPreferenceChangeListener:
        SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (sharedPreferences == null || key == null)
                return@OnSharedPreferenceChangeListener
            if (key == context.getString(R.string.pref_geo_mapType_key) ||
                key == context.getString(R.string.pref_geo_mapEnable_key)) {

                notifyMapEnabled(sharedPreferences.getBoolean(
                    context.getString(R.string.pref_geo_mapEnable_key),
                        false
                ))
            }
        }

    /**
     * 卡片类型
     *
     * @param [row] 卡片所在行
     * @param [viewType] 卡片的类型标识
     * @param [resource] 卡片所对应的布局资源
     *
     * @property [Location] 显示当前位置的卡片
     * @property [LocationWithMap] 显示当前位置地图的卡片，不显示经纬度
     * @property [Mission] 任务卡片
     * @property [Waypoint] Waypoint 卡片
     *
     * @author lucka
     * @since 0.1
     */
    enum class CardIndex(val row: Int, val viewType: Int, val resource: Int) {
        Location(0, 0, R.layout.main_card_location),
        LocationWithMap(0, 1, R.layout.main_card_location_map),
        Mission(1, 2, R.layout.main_card_mission),
        Waypoint(2, 3, R.layout.main_card_waypoint)
    }

    /**
     * 卡片点击监听器
     *
     * @author lucka
     * @since 0.1
     */
    interface OnItemClickListener {

        /**
         * 点击事件
         *
         * @param [position] 被点击卡片的位置
         *
         * @author lucka
         * @since 0.1
         */
        fun onItemClick(position: Int)
    }

    // View Holders
    /**
     * [CardIndex.Location] 的 Holder
     *
     * @property [longitudeText] 经度文本视图
     * @property [latitudeText] 纬度文本视图
     *
     * @author lucka
     * @since 0.1
     */
    class MainRecyclerViewHolderLocation(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var longitudeText: TextView = itemView.findViewById(R.id.mainCardLocationLongitudeText)
        var latitudeText: TextView = itemView.findViewById(R.id.mainCardLocationLatitudeText)

    }

    /**
     * [CardIndex.LocationWithMap] 的 Holder
     *
     * @property [mapView] 地图视图
     *
     * @author lucka
     * @since 0.1
     */
    class MainRecyclerViewHolderLocationWithMap(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        var mapView: TextureMapView = itemView.findViewById(R.id.mainCardLocationMapView)

    }

    /**
     * [CardIndex.Mission] 的 Holder
     *
     * @property [progressBar] 进度条视图
     * @property [progressText] 进度文本视图
     * @property [percentText] 进度百分比文本视图
     *
     * @author lucka
     * @since 0.1
     */
    class MainRecyclerViewHolderMission(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var progressBar: ProgressBar = itemView.findViewById(R.id.mainCardMissionProgressBar)
        var progressText: TextView = itemView.findViewById(R.id.mainCardMissionProgressText)
        var percentText: TextView = itemView.findViewById(R.id.mainCardMissionPercentText)

    }

    /**
     * [CardIndex.Waypoint] 的 Holder
     *
     * @property [title] Waypoint 标题文本视图
     * @property [distanceText] Waypoint 距离文本视图
     * @property [checkBox] Waypoint 是否已检查的确认框视图
     *
     * @author lucka
     * @since 0.1
     */
    class MainRecyclerViewHolderWaypoint(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var title: TextView = itemView.findViewById(R.id.mainCardWaypointTitle)
        var distanceText: TextView = itemView.findViewById(R.id.mainCardWaypointDistanceText)
        var checkBox: CheckBox = itemView.findViewById(R.id.mainCardWaypointCheckBox)

    }

    init {
        PreferenceManager
            .getDefaultSharedPreferences(context)
            .registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater: LayoutInflater = LayoutInflater.from(context)
        val view: View = when(viewType) {
            CardIndex.Location.viewType ->
                layoutInflater.inflate(CardIndex.Location.resource, parent, false)
            CardIndex.LocationWithMap.viewType ->
                layoutInflater
                    .inflate(CardIndex.LocationWithMap.resource, parent, false)
            CardIndex.Mission.viewType ->
                layoutInflater.inflate(CardIndex.Mission.resource, parent, false)
            CardIndex.Waypoint.viewType ->
                layoutInflater.inflate(CardIndex.Waypoint.resource, parent, false)
            else ->
                layoutInflater.inflate(R.layout.main_card_location, parent, false)
        }
        val viewHolder = when(viewType) {
            CardIndex.Location.viewType -> MainRecyclerViewHolderLocation(view)
            CardIndex.LocationWithMap.viewType -> MainRecyclerViewHolderLocationWithMap(view)
            CardIndex.Mission.viewType -> MainRecyclerViewHolderMission(view)
            CardIndex.Waypoint.viewType -> MainRecyclerViewHolderWaypoint(view)
            else -> MainRecyclerViewHolderLocation(view)
        }
        view.setOnClickListener(this)

        return viewHolder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(holder.itemViewType) {

            CardIndex.Location.viewType -> {
                holder as MainRecyclerViewHolderLocation
                if (locationKit.isLocationAvailable) {
                    holder.longitudeText.text =
                        Location.convert(locationKit.lastLocation.longitude, Location.FORMAT_SECONDS)
                    holder.latitudeText.text =
                        Location.convert(locationKit.lastLocation.latitude, Location.FORMAT_SECONDS)
                } else {
                    holder.longitudeText.text = context.getString(R.string.unavailable)
                    holder.latitudeText.text = context.getString(R.string.unavailable)
                }
            }

            CardIndex.LocationWithMap.viewType -> {
                holder as MainRecyclerViewHolderLocationWithMap
                holder.mapView.onCreate(null)
                aMap = holder.mapView.map
                aMap!!.uiSettings.isScrollGesturesEnabled = false

                aMap!!.mapType =
                    when (PreferenceManager
                        .getDefaultSharedPreferences(context)
                        .getString(
                            context.getString(R.string.pref_geo_mapType_key),
                            context.getString(R.string.pref_geo_mapType_Satellite)
                        )
                    ) {
                        context.getString(R.string.pref_geo_mapType_Normal) ->
                            AMap.MAP_TYPE_NORMAL
                        context.getString(R.string.pref_geo_mapType_Satellite) ->
                            AMap.MAP_TYPE_SATELLITE
                        context.getString(R.string.pref_geo_mapType_Night) ->
                            AMap.MAP_TYPE_NIGHT
                        else -> AMap.MAP_TYPE_SATELLITE
                    }

                // Setup My Location
                val myLocationStyle = MyLocationStyle()
                aMap!!.myLocationStyle = myLocationStyle
                aMap!!.isMyLocationEnabled = true
                aMap!!.moveCamera(CameraUpdateFactory.zoomTo(17.toFloat()))
            }

            CardIndex.Mission.viewType -> {
                holder as MainRecyclerViewHolderMission
                if (missionManager.state == MissionManager.MissionState.Starting ||
                    missionManager.state == MissionManager.MissionState.Stopping
                ) {
                    holder.progressBar.isIndeterminate = true
                    holder.progressText.text = context.getString(R.string.loading)
                    holder.percentText.text = ""
                } else {
                    holder.progressBar.isIndeterminate = false
                    var finishedCount = 0
                    for (waypoint in missionManager.waypointList) {
                        finishedCount += if (waypoint.checked) 1 else 0
                    }

                    holder.progressBar.max = missionManager.waypointList.size
                    holder
                        .progressBar
                        .incrementProgressBy(finishedCount - holder.progressBar.progress)
                    holder.progressText.text =
                        String.format("%d / %d", finishedCount, missionManager.waypointList.size)
                    holder.percentText.text = String.format(
                        "%.2f%%",
                        100.0 * finishedCount / missionManager.waypointList.size
                    )
                }
            }

            CardIndex.Waypoint.viewType -> {
                holder as MainRecyclerViewHolderWaypoint
                holder.title.text =
                    missionManager.waypointList[position - CardIndex.Waypoint.row].title
                val waypointLocation =
                    missionManager.waypointList[position - CardIndex.Waypoint.row].location
                holder.distanceText.text = if (locationKit.isLocationAvailable) {
                    if (waypointLocation.distanceTo(locationKit.lastLocation) < 1000.0) {
                        String.format(
                            context.getString(R.string.distanceMetre),
                            waypointLocation.distanceTo(locationKit.lastLocation)
                        )
                    } else {
                        String.format(
                            context.getString(R.string.distanceKM),
                            waypointLocation.distanceTo(locationKit.lastLocation) / 1000.0
                        )
                    }
                } else {
                    context.getString(R.string.unavailable)
                }
                holder.checkBox.isChecked =
                    missionManager.waypointList[position - CardIndex.Waypoint.row].checked
            }

            else -> return
        }
        holder.itemView.tag = position
    }

    override fun getItemCount(): Int {
        var itemCount = 1
        itemCount += if (missionManager.state == MissionManager.MissionState.Stopped) 0 else 1
        itemCount += missionManager.waypointList.size
        return itemCount
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            position == CardIndex.Location.row -> {
                if (isMapEnabled) CardIndex.LocationWithMap.viewType
                else CardIndex.Location.viewType
            }
            position == CardIndex.Mission.row  -> CardIndex.Mission.viewType
            position >= CardIndex.Waypoint.row -> CardIndex.Waypoint.viewType
            else -> -1
        }
    }

    override fun onClick(view: View?) {
        if (view != null) {
            onItemClickListener.onItemClick(view.tag as Int)
        }
    }

    /**
     * 通知刷新卡片的位置信息，刷新位置卡片显示的坐标，如果任务进行中则会更新卡片的距离。
     *
     * @author lucka
     * @since 0.1
     */
    fun notifyRefreshLocation() {

        if (!isMapEnabled) {
            notifyRefreshAt(CardIndex.Location.row)
        }
        if (missionManager.state == MissionManager.MissionState.Started) {
            notifyItemRangeChanged(CardIndex.Waypoint.row, missionManager.waypointList.size)
        }
    }

    /**
     * 通知刷新指定位置的卡片
     *
     * @param [position] 要刷新的卡片位置
     *
     * @author lucka
     * @since 0.1
     */
    fun notifyRefreshAt(position: Int) {
        notifyItemChanged(position)
    }

    /**
     * 通知任务停止，移除任务卡片和检查点卡片
     *
     * @param [oldWaypointListSize] 检查点卡片数量
     *
     * @author lucka
     * @since 0.1
     */
    fun notifyMissionStopped(oldWaypointListSize: Int) {
        notifyItemRangeRemoved(CardIndex.Mission.row, oldWaypointListSize + 1)
    }

    /**
     * 通知任务开始，结束任务卡片的载入动画并加入新的任务点卡片
     *
     * @author lucka
     * @since 0.1
     */
    fun notifyMissionStarted() {
        notifyItemChanged(CardIndex.Mission.row)
        notifyItemRangeInserted(CardIndex.Waypoint.row, missionManager.waypointList.size)
    }

    fun notifyMapEnabled(enabled: Boolean) {
        isMapEnabled = enabled
        notifyRefreshAt(MainRecyclerViewAdapter.CardIndex.LocationWithMap.row)
    }
}