package labs.zero_one.patroute

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.support.v7.preference.PreferenceManager
import com.google.gson.Gson
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


/**
 * 任务管理器
 *
 * 用于存取、管理任务信息、状态和提交报告
 *
 * 属性列表
 * @property [context] 主页面的 Context
 * @property [missionListener] 任务消息监听器
 * @property [waypointList] 任务 Waypoint 列表
 * @property [data] 任务基本信息
 * @property [isStarted] 是否已开始任务
 * @property [isLoading] 是否在载入任务
 * @property [isStopping] 是否在停止任务
 * @property [isReporting] 是否在提交问题
 * @property [isChecking] 是否正在检查 Waypoint
 * @property [issueSN] 问题序列号
 * @property [issueImagePath] 问题的照片文件路径
 * @property [lastLocation] 最后记录的位置
 * @property [isLocationAvailable] 位置是否可用
 *
 * 子类列表
 * [MissionListener] 任务消息监听器
 * [MissionData] 任务基本信息类
 *
 * 方法列表
 * [update] 更新位置
 * [start] 开始任务
 * [stop] 停止任务
 * [pause] 暂停任务
 * [resume] 继续任务
 * [decodeGPX] 解码 GPX 文件
 * [reach] 抵达某位置时检查是否有需要检查的 Waypoint
 * [checkAt] 指定 Waypoint 检查完成
 * [submitIssue] 提交报告
 * [_log] 在日志中添加记录（幕后方法）
 * [logMissionInfo] 在日志中添加任务信息，包括 VER，MID 和 UID
 * [log] 在日志中添加记录
 *
 * @author lucka
 * @since 0.1
 */
class MissionManager(private var context: Context, private var missionListener: MissionListener) {

    var data: MissionData = MissionData("", "")
    var waypointList: ArrayList<Waypoint> = ArrayList(0)
    var isStarted: Boolean = false
    var isLoading: Boolean = false
    var isStopping: Boolean = false
    var isReporting: Boolean = false
    var isChecking = false  // To avoid multi alert when reaching a waypoint
    var issueSN: Int = 0
    var issueImagePath: String = ""
    var lastLocation: Location = Location("")
    var isLocationAvailable: Boolean = false

    /**
     * 任务消息监听器
     *
     * @author lucka
     * @since 0.1
     */
    interface MissionListener {
        /**
         * 全部 Waypoint 检查完成时调用
         *
         * @author lucka
         * @since 0.1
         */
        fun onAllChecked()

        /**
         * 开始任务成功时调用
         *
         * @param [missionData] 新任务的基本信息
         *
         * @author lucka
         * @since 0.1
         */
        fun didStartedSuccess(missionData: MissionData)

        /**
         * 开始任务失败时调用
         *
         * @param [error] 错误信息
         *
         * @author lucka
         * @since 0.1
         */
        fun didStartedFailed(error: Exception)

        /**
         * 停止任务成功时调用
         *
         * @param [oldListSize] 旧 [waypointList] 长度
         *
         * @author lucka
         * @since 0.1
         */
        fun didStoppedSuccess(oldListSize: Int)

        /**
         * 停止任务失败时调用
         *
         * @param [error] 错误信息
         *
         * @author lucka
         * @since 0.1
         */
        fun didStoppedFailed(error: Exception)

        /**
         * 提交报告成功时调用
         *
         * @author lucka
         * @since 0.1
         */
        fun didReportedSuccess()

        /**
         * 提交报告失败时调用
         *
         * @param [error] 错误信息
         *
         * @author lucka
         * @since 0.1
         */
        fun didReportedFailed(error: Exception)
    }

    /**
     * 任务基本信息类
     *
     * 可供序列化存储
     *
     * @property [id] 任务 ID
     * @property [description] 任务描述
     *
     * @author lucka
     * @since 0.1
     */
    data class MissionData(val id: String, val description: String): Serializable

    /**
     * 更新位置
     *
     * 会将坐标系转换为 GCJ-02 并更新位置可用状态 [isLocationAvailable]
     * 应用中所有对位置的调用均应从 MissionManager 实体而非直接从 LocationManager 实体中获得
     *
     * @param [location] WGS-84 坐标位置
     *
     * @author lucka
     * @since 1.3.0
     */
    fun update(location: Location?) {
        val fixedLocation = CoordinateKit.convert(
            location,
            CoordinateKit.CoordinateType.WGS84,
            CoordinateKit.CoordinateType.GCJ02,
            context
        )
        if (fixedLocation == null) {
            isLocationAvailable = false
        } else {
            lastLocation = fixedLocation
            isLocationAvailable = true
            log(context.getString(R.string.log_head_loc), "")
        }
    }

    /**
     * 开始任务
     *
     * 包括更新状态、连接服务器下载解码基本信息和 GPX 文件
     * 成功后调用 [MissionListener.didStartedSuccess]
     * 失败时调用 [MissionListener.didStartedFailed]
     *
     * 注释参考
     * Connect to FTP Server via Apache Commons Net API
     * @see <a href="https://github.com/KoFuk/ftp-upload/blob/master/src/main/kotlin/com/chronoscoper/ftpupload/Main.kt">Sample Code</a>
     * Must set protection buffer size and data channel protection when using FTPS!
     * @see <a href="http://www.kochnielsen.dk/kurt/blog/?p=162">Sample Code</a>
     * Download file from FTP Server via Apache Commons Net API
     * @see <a href="http://www.codejava.net/java-se/networking/ftp/java-ftp-file-download-tutorial-and-example">Sample Code</a>
     *
     * @author lucka
     * @since 0.1
     */
    fun start() {
        issueSN = 1
        isLoading = true

        doAsync {
            val sharedPreference: SharedPreferences
            val serverURL: String
            val serverPort: Int
            val username: String
            val password: String
            val enableFTPS: Boolean
            try {
                sharedPreference = PreferenceManager.getDefaultSharedPreferences(context)
                serverURL = sharedPreference
                    .getString(context.getString(R.string.pref_server_url_key), "")
                serverPort = sharedPreference
                    .getString(
                        context.getString(R.string.pref_server_port_key),
                        context.getString(R.string.pref_server_port_default)
                    )
                    .toInt()
                username = sharedPreference
                    .getString(context.getString(R.string.pref_user_id_key), "")
                password = sharedPreference
                    .getString(context.getString(R.string.pref_user_token_key), "")
                enableFTPS = sharedPreference
                    .getBoolean(context.getString(R.string.pref_server_enableFTPS_key), false)
            } catch (error: Exception) {
                val newError = Exception(
                    context.getString(R.string.error_get_preference_failed)
                        + "\n"
                        + error.message
                )
                uiThread {
                    isLoading = false
                    missionListener.didStartedFailed(newError)
                }
                return@doAsync
            }
            val ftpClient = if (enableFTPS) {
                FTPSClient(false)
            } else {
                FTPClient()
            }
            try {
                // Connect to FTP Server via Apache Commons Net API
                ftpClient.connect(serverURL, serverPort)
                ftpClient.enterLocalPassiveMode()
                ftpClient.login(username, password)
            } catch (error: Exception) {
                val newError = Exception(
                    context.getString(R.string.error_login_failed)
                        + "\n"
                        + error.message
                )
                uiThread {
                    isLoading = false
                    missionListener.didStartedFailed(newError)
                }
                return@doAsync
            }
            // Must set protection buffer size and data channel protection when using FTPS!
            if (enableFTPS) {
                ftpClient as FTPSClient
                ftpClient.execPBSZ(0)
                ftpClient.execPROT("P")
            }
            try {
                // Download file from FTP Server via Apache Commons Net API
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                ftpClient.changeWorkingDirectory("Mission")
                val localJSONFile = File(context.filesDir, "Mission.json")
                var fileOutputStream = FileOutputStream(localJSONFile)
                if (!ftpClient.retrieveFile("$username.json", fileOutputStream)) {
                    val error = Exception(context.getString(R.string.error_request_json_failed))
                    uiThread {
                        isLoading = false
                        missionListener.didStartedFailed(error)
                    }
                    return@doAsync
                }
                fileOutputStream.close()
                val jsonString = localJSONFile.readText()
                data = Gson().fromJson(jsonString, MissionData::class.java)
                ftpClient.changeWorkingDirectory("GPX")
                val localGPXFile = File(context.filesDir, data.id + ".gpx")
                fileOutputStream = FileOutputStream(localGPXFile)
                if (!ftpClient.retrieveFile(data.id + ".gpx", fileOutputStream)) {
                    val error = Exception(context.getString(R.string.error_request_gpx_failed))
                    uiThread {
                        isLoading = false
                        missionListener.didStartedFailed(error)
                    }
                    return@doAsync
                }
                fileOutputStream.close()
                waypointList = decodeGPX(localGPXFile)
            } catch (error: Exception) {
                val newError = Exception(
                    context.getString(R.string.error_request_mission_failed)
                        + "\n"
                        + error.message
                )
                uiThread {
                    isLoading = false
                    missionListener.didStartedFailed(newError)
                }
                return@doAsync
            }
            uiThread {
                isLoading = false
                isStarted = true
                File(context.filesDir, data.id + ".log")
                logMissionInfo()
                log(
                    context.getString(R.string.log_head_sta),
                    context.getString(R.string.log_mission_started)
                )
                missionListener.didStartedSuccess(data)
            }
        }
    }

    /**
     * 停止任务
     *
     * 包括更新状态、生成日志文件、连接服务器上传日志文件
     * 成功后调用 [MissionListener.didStoppedSuccess]
     * 失败时调用 [MissionListener.didStoppedFailed]
     *
     * @author lucka
     * @since 0.1
     */
    fun stop() {
        isStopping = true
        doAsync {
            val sharedPreference: SharedPreferences
            val serverURL: String
            val serverPort: Int
            val username: String
            val password: String
            val enableFTPS: Boolean
            try {
                sharedPreference = PreferenceManager.getDefaultSharedPreferences(context)
                serverURL = sharedPreference
                    .getString(context.getString(R.string.pref_server_url_key), "")
                serverPort = sharedPreference
                    .getString(
                        context.getString(R.string.pref_server_port_key),
                        context.getString(R.string.pref_server_port_default)
                    )
                    .toInt()
                username = sharedPreference
                    .getString(context.getString(R.string.pref_user_id_key), "")
                password = sharedPreference
                    .getString(context.getString(R.string.pref_user_token_key), "")
                enableFTPS = sharedPreference
                    .getBoolean(context.getString(R.string.pref_server_enableFTPS_key), false)
            } catch (error: Exception) {
                val newError = Exception(
                    context.getString(R.string.error_get_preference_failed)
                        + "\n"
                        + error.message
                )
                uiThread {
                    isLoading = false
                    missionListener.didStoppedFailed(newError)
                }
                return@doAsync
            }
            val ftpClient = if (enableFTPS) {
                FTPSClient(false)
            } else {
                FTPClient()
            }
            try {
                ftpClient.connect(serverURL, serverPort)
                ftpClient.enterLocalPassiveMode()
                ftpClient.login(username, password)
            } catch (error: Exception) {
                val newError = Exception(
                    context.getString(R.string.error_login_failed)
                        + "\n"
                        + error.message
                )
                uiThread {
                    isStopping = false
                    missionListener.didStoppedFailed(newError)
                }
                return@doAsync
            }
            try {
                if (enableFTPS) {
                    ftpClient as FTPSClient
                    ftpClient.execPBSZ(0)
                    ftpClient.execPROT("P")
                }
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                ftpClient.changeWorkingDirectory(username)
                ftpClient.changeWorkingDirectory(data.id)
                // Upload log file
                log(
                    context.getString(R.string.log_head_sta),
                    context.getString(R.string.log_mission_stopped)
                )
                val logFile = File(context.filesDir, data.id + ".log")
                val fileInputStream = FileInputStream(logFile)
                ftpClient.storeFile(data.id + ".log", fileInputStream)
                fileInputStream.close()
                logFile.delete()
                ftpClient.logout()
            } catch (error: Exception) {
                isStopping = false
                uiThread {
                    val newError = Exception(
                        context.getString(R.string.stop_failed)
                            + "\n"
                            + error.message
                    )
                    missionListener.didStartedFailed(newError)
                }
                return@doAsync
            }
            uiThread {
                issueSN = 0
                data = MissionData("", "")
                val oldListSize = waypointList.size
                waypointList.clear()
                isStarted = false
                isStopping = false

                missionListener.didStoppedSuccess(oldListSize)
            }
        }
    }

    /**
     * 暂停任务
     *
     * 应当在主页面 Activity 将被销毁时调用，将任务数据存入文件
     *
     * 注释参考
     * Serialize and save the waypointList
     * @see <a href="https://developer.android.com/training/basics/data-storage/files.html">Android Developers</a>
     * @see <a href="http://blog.csdn.net/u011240877/article/details/72455715">两种序列化方式 Serializable 和 Parcelable</a>
     *
     * @author lucka
     * @since 0.1
     */
    fun pause() {
        // Serialize and save the waypointList
        val filename = "Mission.temp"
        val file = File(context.filesDir, filename)
        val fileOutputStream: FileOutputStream
        val objectOutputStream: ObjectOutputStream

        try {
            fileOutputStream = FileOutputStream(file)
            objectOutputStream = ObjectOutputStream(fileOutputStream)
            objectOutputStream.writeObject(data)
            objectOutputStream.writeInt(issueSN)
            objectOutputStream.writeObject(waypointList)
            objectOutputStream.close()
            fileOutputStream.close()
        } catch (error: Exception) {
            val alert = AlertDialog.Builder(context)
            alert.setTitle(context.getString(R.string.alert_warning_title))
            alert.setMessage(error.message)
            alert.setCancelable(false)
            alert.setPositiveButton(context.getString(R.string.confirm), null)
            alert.show()
        }

    }

    /**
     * 继续任务
     *
     * 应当在主页面 Activity 即将显示时调用，从文件中读取恢复任务数据
     *
     * @return 旧 Waypoint 列表的大小
     *
     * @author lucka
     * @since 0.1
     */
    fun resume(): Int {
        val filename = "Mission.temp"
        val file = File(context.filesDir, filename)
        val fileInputStream: FileInputStream
        val objectInputStream: ObjectInputStream

        if (!file.exists()) {
            isStarted = false
            return waypointList.size
        }

        val oldListSize = waypointList.size
        try {
            fileInputStream = FileInputStream(file)
            objectInputStream = ObjectInputStream(fileInputStream)
            data = objectInputStream.readObject() as MissionData
            issueSN = objectInputStream.readInt()
            @Suppress("UNCHECKED_CAST")
            waypointList = objectInputStream.readObject() as ArrayList<Waypoint>
            objectInputStream.close()
            fileInputStream.close()
            isStarted = data.id != ""
        } catch (error: Exception) {
            val alert = AlertDialog.Builder(context)
            alert.setTitle(context.getString(R.string.alert_warning_title))
            alert.setMessage(error.message)
            alert.setCancelable(false)
            alert.setPositiveButton(context.getString(R.string.confirm), null)
            alert.show()
            isStarted = false
        }
        return oldListSize
    }

    /**
     * 解码 GPX 文件
     *
     * @see <a href="http://www.topografix.com/GPX/1/1/#type_trkType">GPX 1.1 Schema Documentation</a>
     *
     * GPX 文件样例
     * <wpt lat="xxx" lon="xxx">
     *     <name> title </name>
     *     <desc> description </desc>
     * </wpt>
     *
     * @author lucka
     * @since 0.1
     */
    private fun decodeGPX(file: File): ArrayList<Waypoint> {

        waypointList = ArrayList(0)
        val lineList: List<String> = file.readLines()

        var title = ""
        var description = ""
        var location = Location("")
        var wptBegin = false

        for (line in lineList) {

            // Scan until wpt begin
            if (!wptBegin) {
                if (line.contains("")) {
                    wptBegin = true
                } else {
                    continue
                }
            }
            if (line.contains("<wpt lat=")) {
                var locationString: String = line
                locationString = locationString.replace("<wpt lat=", "")
                locationString = locationString.replace("lon=", "")
                locationString = locationString.replace(">", "")
                locationString = locationString.replace("\"", "")
                locationString = locationString.trim()
                val locationStringList: List<String> =  locationString.split(" ")
                if (locationStringList.size == 2) {
                    location.latitude = locationStringList[0].toDouble()
                    location.longitude = locationStringList[1].toDouble()
                }
            } else if (line.contains("<name>")) {
                title = line
                title = title.replace("<name>", "")
                title = title.replace("</name>", "")
                title = title.trim()
                continue
            } else if (line.contains("<desc>")) {
                description = line
                description = description.replace("<desc>", "")
                description = description.replace("</desc>", "")
                description = description.trim()
                description = description.replace("<br/>", "\n")
                continue
            } else if (line.contains("</wpt>")) {
                val waypoint = Waypoint(title, description, false, location)
                waypointList.add(waypoint)
                title = ""
                description = ""
                location = Location("")
                wptBegin = false
            }
        }
        return waypointList
    }

    /**
     * 抵达某位置时检查是否有需要检查的 Waypoint
     *
     * @param [location] 抵达的位置
     *
     * @return 在此处需要检查的 Waypoint 列表
     *
     * @author lucka
     * @since 0.1
     */
    fun reach(location: Location): ArrayList<Int> {
        val reachedList: ArrayList<Int> = ArrayList(0)
        for (scanner: Int in 0 until waypointList.size) {
            if (waypointList[scanner].location != null) {
                if (!waypointList[scanner].isChecked &&
                    location.distanceTo(waypointList[scanner].location) <= 30
                ) {
                    reachedList.add(scanner)
                }
            }
        }

        return reachedList
    }

    /**
     * 指定 Waypoint 检查完成
     *
     * 如果全部 Waypoint 检查完成则调用 [MissionListener.onAllChecked]
     *
     * @param [index] 完成检查的 Waypoint 序号
     *
     * @author lucka
     * @since 0.1
     */
    fun checkAt(index: Int) {
        waypointList[index].isChecked = true
        log(context.getString(R.string.log_head_chk), waypointList[index].title)
        var isAllChecked = true
        for (waypoint in waypointList) {
            if (!waypoint.isChecked) {
                isAllChecked = false
                break
            }
        }
        if (isAllChecked) {
            missionListener.onAllChecked()
        }
    }

    /**
     * 提交报告
     *
     * 包括更新状态、生成报告文件、连接服务器上传报告文件和照片
     * 成功后调用 [MissionListener.didReportedSuccess]
     * 失败时调用 [MissionListener.didReportedFailed]
     *
     * @param [location] 报告的位置
     * @param [time] 报告的时间
     * @param [description] 报告的描述
     *
     * @author lucka
     * @since 0.1
     */
    fun submitIssue(location: Location, time: Date, description: String) {
        isReporting = true
        doAsync {
            val issueFile = File(context.filesDir, "ISS_" + data.id + "_" + issueSN + ".txt")
            val longitudeText: String = String.format("%f", location.longitude)
            val latitudeText: String = String.format("%f", location.latitude)
            try {
                // Create issue txt file
                issueFile.writeText(
                    context.getString(R.string.location)
                        + " (" + longitudeText + " " + latitudeText + ")\n"
                        + context.getString(R.string.time)
                        + " "
                        + DateFormat.getDateTimeInstance().format(time)
                        + "\n"
                        + context.getString(R.string.description)
                        + " "
                        + description
                )
            } catch (error: Exception) {
                val newError =
                    Exception(
                        context.getString(R.string.error_create_issue_file_failed)
                            + "\n"
                            + error.message
                    )
                uiThread {
                    isReporting = false
                    missionListener.didReportedFailed(newError)
                }
                return@doAsync
            }

            val sharedPreference: SharedPreferences
            val serverURL: String
            val serverPort: Int
            val username: String
            val password: String
            val enableFTPS: Boolean
            try {
                sharedPreference = PreferenceManager.getDefaultSharedPreferences(context)
                serverURL = sharedPreference
                    .getString(context.getString(R.string.pref_server_url_key), "")
                serverPort = sharedPreference
                    .getString(
                        context.getString(R.string.pref_server_port_key),
                        context.getString(R.string.pref_server_port_default)
                    )
                    .toInt()
                username = sharedPreference
                    .getString(context.getString(R.string.pref_user_id_key), "")
                password = sharedPreference
                    .getString(context.getString(R.string.pref_user_token_key), "")
                enableFTPS = sharedPreference
                    .getBoolean(context.getString(R.string.pref_server_enableFTPS_key), false)
            } catch (error: Exception) {
                val newError = Exception(
                    context.getString(R.string.error_get_preference_failed)
                        + "\n"
                        + error.message
                )
                uiThread {
                    isReporting = false
                    missionListener.didReportedFailed(newError)
                }
                return@doAsync
            }
            val ftpClient = if (enableFTPS) {
                FTPSClient(false)
            } else {
                FTPClient()
            }
            try {
                ftpClient.connect(serverURL, serverPort)
                ftpClient.enterLocalPassiveMode()
                ftpClient.login(username, password)
            } catch (error: Exception) {
                val newError = Exception(
                    context.getString(R.string.error_login_failed)
                        + "\n"
                        + error.message
                )
                uiThread {
                    isReporting = false
                    missionListener.didReportedFailed(newError)
                }
                return@doAsync
            }

            try {
                if (enableFTPS) {
                    ftpClient as FTPSClient
                    ftpClient.execPBSZ(0)
                    ftpClient.execPROT("P")
                }
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                ftpClient.changeWorkingDirectory(username)
                ftpClient.changeWorkingDirectory(data.id)
                // Upload issue txt file
                var fileInputStream = FileInputStream(issueFile)
                ftpClient
                    .storeFile("ISS_" + data.id + "_" + issueSN + ".txt", fileInputStream)
                fileInputStream.close()
                // Upload image
                fileInputStream = FileInputStream(issueImagePath)
                ftpClient
                    .storeFile("ISS_" + data.id + "_" + issueSN + ".jpg", fileInputStream)
                fileInputStream.close()
                ftpClient.logout()
                issueFile.delete()
            } catch (error: Exception) {
                uiThread {
                    isReporting = false
                    missionListener.didReportedFailed(error)
                }
                return@doAsync
            }
            File(issueImagePath).delete()
            uiThread {
                log(
                    context.getString(R.string.log_head_rep),
                    "ISS_" + data.id + "_" + issueSN + " " + description
                )
                issueSN += 1
                isReporting = false
                missionListener.didReportedSuccess()
            }
        }
    }

    /**
     * 在日志中添加一条记录，幕后方法
     *
     * 会将换行改为 <br/>
     *
     * @param [line] 添加的新记录
     *
     * Changelog
     * [1.3.0] - 2018-07-10
     *  重写日志功能，本方法转为幕后方法
     *  处理换行
     * [1.3.1] - 2018-07-29
     *  改名为 _log，作为幕后方法与 [log] 区别
     *
     * @author lucka
     * @since 0.1
     */
    private fun _log(line: String) {
        File(context.filesDir, data.id + ".log")
            .appendText(line.replace("\n", "<br/>") + "\n")
    }

    /**
     * 在日志中添加任务信息，包括 VER，MID 和 UID
     *
     * @author lucka
     * @since 1.3.0
     */
    private fun logMissionInfo() {
        _log(
            String.format(
                context.getString(R.string.log_ver),
                context.packageManager.getPackageInfo(context.packageName, 0).versionName,
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            )
        )
        _log(String.format(context.getString(R.string.log_mid), data.id))
        _log(
            String.format(
                context.getString(R.string.log_uid),
                PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getString(context.getString(R.string.pref_user_id_key), "")
            )
        )
    }

    /**
     * 在日志中添加记录
     *
     * @param [message] 消息
     *
     * @author lucka
     * @since 1.3.0
     */
    fun log(head: String, message: String) {
        val dateFormat = SimpleDateFormat(context.getString(R.string.iso_datatime), Locale.CHINA)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        _log(
            String.format(
                context.getString(R.string.log_msg),
                head,
                dateFormat.format(Date()),
                lastLocation.longitude,
                lastLocation.latitude,
                message
            )
        )
    }
}