package labs.zero_one.patroute

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
 * 主页面的 Recycler View 适配器
 *
 * @property [context] 主页面的 Context
 * @property [waypointList] 任务的 Waypoint 列表
 * @property [onItemClickListener] 点击监听器 [OnItemClickListener]
 * @property [location] 刷新时传入的位置
 * @property [isLoading] 是否在载入任务
 * @property [aMap] 位置地图卡片中的高德地图控制器
 *
 * 子类列表
 * [ItemIndex]
 * [OnItemClickListener]
 * [MainRecyclerViewHolderLocation]
 * [MainRecyclerViewHolderLocationWithMap]
 * [MainRecyclerViewHolderMission]
 * [MainRecyclerViewHolderWaypoint]
 *
 * 重写方法列表
 * [onCreateViewHolder]
 * [onBindViewHolder]
 * [getItemCount]
 * [getItemViewType]
 * [onClick]
 *
 * 自定义方法列表
 * [refreshWith] 以位置信息刷新视图
 * [refreshAt] 刷新指定位置的卡片
 * [clearList] 清空 Waypoint 卡片
 * [startLoading] 开始显示载入动画
 * [finishLoading] 结束载入动画
 *
 * @author lucka
 * @since 0.1
 */
class MainRecyclerViewAdapter(
    val context: Context,
    private var waypointList: ArrayList<Waypoint>,
    private var onItemClickListener: OnItemClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
    View.OnClickListener {

    private var location: Location? = null
    private var isLoading: Boolean = false
    private var aMap: AMap? = null

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
    enum class ItemIndex(val row: Int, val viewType: Int, val resource: Int) {
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
     * [ItemIndex.Location] 的 Holder
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
     * [ItemIndex.LocationWithMap] 的 Holder
     *
     * @property [mapView] 地图视图
     *
     * @author lucka
     * @since 0.1
     */
    class MainRecyclerViewHolderLocationWithMap(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        var mapView: MapView = itemView.findViewById(R.id.mainCardLocationMapView)

    }

    /**
     * [ItemIndex.Mission] 的 Holder
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
     * [ItemIndex.Waypoint] 的 Holder
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater: LayoutInflater = LayoutInflater.from(context)
        val view: View = when(viewType) {
            ItemIndex.Location.viewType ->
                layoutInflater.inflate(ItemIndex.Location.resource, parent, false)
            ItemIndex.LocationWithMap.viewType ->
                layoutInflater
                    .inflate(ItemIndex.LocationWithMap.resource, parent, false)
            ItemIndex.Mission.viewType ->
                layoutInflater.inflate(ItemIndex.Mission.resource, parent, false)
            ItemIndex.Waypoint.viewType ->
                layoutInflater.inflate(ItemIndex.Waypoint.resource, parent, false)
            else ->
                layoutInflater.inflate(R.layout.main_card_location, parent, false)
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
                    holder.longitudeText.text = CoordinateKit.getDegreeString(location.longitude)
                    holder.latitudeText.text = CoordinateKit.getDegreeString(location.latitude)
                }
            }

            ItemIndex.LocationWithMap.viewType -> {
                holder as MainRecyclerViewHolderLocationWithMap
                holder.mapView.onCreate(null)
                this.aMap = holder.mapView.map
                this.aMap!!.uiSettings.isScrollGesturesEnabled = false

                this.aMap!!.mapType =
                    when(PreferenceManager
                        .getDefaultSharedPreferences(context)
                        .getString(context.getString(R.string.pref_geo_mapType_key),
                        context.getString(R.string.pref_geo_mapType_Satellite))
                    ) {
                    context.getString(R.string.pref_geo_mapType_Normal) ->
                        AMap.MAP_TYPE_NORMAL
                    context.getString(R.string.pref_geo_mapType_Satellite) ->
                        AMap.MAP_TYPE_SATELLITE
                    else -> AMap.MAP_TYPE_SATELLITE
                }

                // Setup My Location
                val myLocationStyle = MyLocationStyle()
                this.aMap!!.setMyLocationStyle(myLocationStyle)
                this.aMap!!.isMyLocationEnabled = true
                this.aMap!!.moveCamera(CameraUpdateFactory.zoomTo(17.toFloat()))
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
                    holder
                        .progressBar
                        .incrementProgressBy(finishedCount - holder.progressBar.progress)
                    holder.progressText.text = String.format("%d/%d", finishedCount, waypointList.size)
                    holder.percentText.text =
                        String
                            .format(
                                "%.2f%%",
                                (finishedCount.toDouble() / waypointList.size.toDouble()) * 100.0
                            )
                }
            }

            ItemIndex.Waypoint.viewType -> {
                holder as MainRecyclerViewHolderWaypoint
                holder.title.text = waypointList[position - ItemIndex.Waypoint.row].title
                val waypointLocation = waypointList[position - ItemIndex.Waypoint.row].location
                holder.distanceText.text = if (waypointLocation != null && location != null) {
                    if (waypointLocation.distanceTo(location) < 1000.0) {
                        String
                            .format(
                                context.getString(R.string.distanceMetre),
                                waypointLocation.distanceTo(location)
                            )
                    } else {
                        String
                            .format(
                                context.getString(R.string.distanceKM),
                                waypointLocation.distanceTo(location) / 1000.0
                            )
                    }
                } else {
                    context.getString(R.string.unavailable)
                }
                holder.checkBox.isChecked =
                    waypointList[position - ItemIndex.Waypoint.row].isChecked
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
        val isMapEnable: Boolean =
            PreferenceManager.
                getDefaultSharedPreferences(context).
                getBoolean(context.getString(R.string.pref_geo_mapEnable_key), false)
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

    /**
     * 以位置信息刷新视图
     *
     * 刷新位置卡片的坐标，如果有 Waypoint 列表不为空则会刷新 Waypoint 卡片，更新距离。
     *
     * @param [location] 用以刷新的位置
     *
     * @author lucka
     * @since 0.1
     */
    fun refreshWith(location: Location?) {

        this.location = location
        if (!PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.pref_geo_mapEnable_key), false)
        ) {
            this.refreshAt(ItemIndex.Location.row)
        }
        if (waypointList.isNotEmpty()) {
            this.notifyItemRangeChanged(ItemIndex.Waypoint.row, waypointList.size)
        }
    }

    /**
     * 以 Waypoint 列表刷新视图
     *
     * 刷新任务卡片和 Waypoint 卡片的内容
     *
     * @Deprecated 0.1 [MainRecyclerViewAdapter.waypointList] 与 [MissionManager.waypointList] 在大
     * 多数情况下为同一实例（引用），不必要刷新。
     *
     * @param [waypointList] 用以刷新的 Waypoint 列表
     *
     * @author lucka
     * @since 0.1
     */
    @Deprecated("This method should be replaced by refreshAt() and clearList()")
    fun refreshWith(waypointList: ArrayList<Waypoint>) {
        this.waypointList = waypointList
        this.notifyDataSetChanged()
    }

    /**
     * 刷新指定位置的卡片
     *
     * @param [position] 要刷新的卡片位置
     *
     * @author lucka
     * @since 0.1
     */
    fun refreshAt(position: Int) {
        this.notifyItemChanged(position)
    }

    /**
     * 刷新指定位置的卡片同时更新 Waypoint 列表
     *
     * @Deprecated 0.1 [MainRecyclerViewAdapter.waypointList] 与 [MissionManager.waypointList] 在大
     * 多数情况下为同一实例（引用），不必要刷新。
     *
     * @param [position] 要刷新的卡片位置
     *
     * @author lucka
     * @since 0.1
     */
    @Deprecated("This method should be replaced by refreshAt(position: Int)")
    fun refreshAt(position: Int, waypointList: ArrayList<Waypoint>) {
        this.waypointList = waypointList
        this.notifyItemChanged(ItemIndex.Mission.row)
        this.notifyItemChanged(position)
    }

    /**
     * 清空 Waypoint 卡片
     *
     * 通常在任务停止并清空 [MissionManager.waypointList] 后调用
     *
     * @param [oldListSize] Waypoint 卡片数量
     *
     * @author lucka
     * @since 0.1
     */
    fun clearList(oldListSize: Int) {
        this.notifyItemRangeRemoved(ItemIndex.Mission.row, oldListSize + 1)
    }

    /**
     * 开始显示载入动画
     *
     * 显示任务卡片并让进度条显示载入动画
     *
     * @author lucka
     * @since 0.1
     */
    fun startLoading() {
        isLoading = true
        if (waypointList.size > 0) {
            this.notifyItemChanged(ItemIndex.Mission.row)
        } else {
            this.notifyItemInserted(ItemIndex.Mission.row)
        }
    }

    /**
     * 结束载入动画
     *
     * 结束任务卡片的载入动画并加载新的 [waypointList]
     *
     * @param [waypointList] 新的 Waypoint 列表
     * @param [oldListSize] 旧 Waypoint 列表的大小，默认为0
     *
     * @author lucka
     * @since 0.1
     */
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