package labs.zero_one.patroute

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
 * 任务管理器，用于存取、管理任务信息、状态和提交工单。
 *
 * ## 属性列表
 * - [waypointList]
 * - [data]
 * - [state]
 * - [isUploading]
 * - [isChecking]
 * - [ticketSN]
 * - [ticketImagePath]
 * - [lastLocation]
 *
 * ## 子类列表
 * - [MissionListener]
 * - [MissionData]
 *
 * ## 方法列表
 * - [start]
 * - [stop]
 * - [pause]
 * - [resume]
 * - [decodeGPX]
 * - [reach]
 * - [checkAt]
 * - [uploadTicket]
 * - [_log]
 * - [logMissionInfo]
 * - [log]
 *
 * @param [context] 环境
 * @param [missionListener] 任务消息监听器
 *
 * @author lucka-me
 * @since 0.1
 *
 * @property [waypointList] 任务 Waypoint 列表
 * @property [data] 任务基本信息
 * @property [state] 任务状态
 * @property [isUploading] 是否正在提交工单
 * @property [isChecking] 是否正在检查检查点
 * @property [ticketSN] 问题序列号
 * @property [ticketImagePath] 问题的照片文件路径
 * @property [lastLocation] 最新位置，供日志使用
 */
class MissionManager(private var context: Context, private var missionListener: MissionListener) {

    var data: MissionData = MissionData("", "")
    var waypointList: ArrayList<Waypoint> = ArrayList(0)

    var state: MissionState = MissionState.Stopped
    var isUploading: Boolean = false
    var isChecking = false  // To avoid multi alert when reaching a waypoint

    var ticketSN: Int = 0
    var ticketImagePath: String = ""

    private var lastLocation = Location("")

    /**
     * 任务消息监听器
     *
     * ## 消息列表
     * - [onCheckedAll]
     * - [onStarted]
     * - [onStartFailed]
     * - [onStopped]
     * - [onStopFailed]
     * - [onChecking]
     * - [onUploadTicketSuccess]
     * - [onUploadTicketFailed]
     *
     * ## Changelog
     * ### 1.5.0
     * - 方法名更换为 Android 风格
     *
     * @author lucka-me
     * @since 0.1
     */
    interface MissionListener {
        /**
         * 全部 Waypoint 检查完成时调用
         *
         * @author lucka-me
         * @since 0.1
         */
        fun onCheckedAll()

        /**
         * 开始任务成功时调用
         *
         * ## Changelog
         * ### 1.5.0
         * - 添加参数 [isResumed]
         *
         * @param [isResumed] 是否从恢复（[resume]）中开始的
         *
         * @author lucka-me
         * @since 0.1
         */
        fun onStarted(isResumed: Boolean)

        /**
         * 开始任务失败时调用
         *
         * @param [error] 错误信息
         *
         * @author lucka-me
         * @since 0.1
         */
        fun onStartFailed(error: Exception)

        /**
         * 停止任务成功时调用
         *
         * @param [oldListSize] 旧 [waypointList] 长度
         *
         * @author lucka-me
         * @since 0.1
         */
        fun onStopped(oldListSize: Int)

        /**
         * 停止任务失败时调用
         *
         * @param [error] 错误信息
         *
         * @author lucka-me
         * @since 0.1
         */
        fun onStopFailed(error: Exception)

        /**
         * 需要检查
         *
         * @param [indexList] 抵达的检查点的序号列表
         *
         * @author lucka-me
         * @since 1.5.0
         */
        fun onChecking(indexList: List<Int>)

        /**
         * 提交报告成功时调用
         *
         * @author lucka-me
         * @since 0.1
         */
        fun onUploadTicketSuccess()

        /**
         * 提交报告失败时调用
         *
         * @param [error] 错误信息
         *
         * @author lucka-me
         * @since 0.1
         */
        fun onUploadTicketFailed(error: Exception)
    }

    /**
     * 任务状态
     *
     * ## 列表
     * - [Starting]
     * - [Started]
     * - [Stopping]
     * - [Stopped]
     * - [Paused]
     *
     * @author lucka-me
     * @since 1.5.0
     *
     */
    enum class MissionState {
        /**
         * 正在开始
         */
        Starting,
        /**
         * 已开始（进行中）
         */
        Started,
        /**
         * 正在结束
         */
        Stopping,
        /**
         * 已结束（未开始）
         */
        Stopped,
        /**
         * 已暂停，即 Activity 不在顶层时（onPause() 后）
         */
        Paused
    }

    /**
     * 任务基本信息类
     *
     * 可供序列化存储
     *
     * @property [id] 任务 ID
     * @property [description] 任务描述
     *
     * @author lucka-me
     * @since 0.1
     */
    data class MissionData(val id: String, val description: String): Serializable

    /**
     * 开始任务，包括更新状态、连接服务器下载解码基本信息和 GPX 文件。
     *
     * 成功后调用 [MissionListener.onStarted]
     *
     * 失败时调用 [MissionListener.onStartFailed]
     *
     * @author lucka-me
     * @since 0.1
     *
     * @see <a href="https://github.com/KoFuk/ftp-upload/blob/master/src/main/kotlin/com/chronoscoper/ftpupload/Main.kt">Connect to FTP Server via Apache Commons Net API | Sample Code</a>
     * @see <a href="http://www.kochnielsen.dk/kurt/blog/?p=162">Must set protection buffer size and data channel protection when using FTPS! | Sample Code</a>
     * @see <a href="http://www.codejava.net/java-se/networking/ftp/java-ftp-file-download-tutorial-and-example">Download file from FTP Server via Apache Commons Net API | Sample Code</a>
     */
    fun start() {
        ticketSN = 1
        state = MissionState.Starting

        doAsync {
            val sharedPreference: SharedPreferences
            val serverURL: String
            val serverPort: Int
            val serverTimeout: Int
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
                serverTimeout = sharedPreference
                    .getString(
                        context.getString(R.string.pref_server_timeout_key),
                        context.getString(R.string.pref_server_timeout_default)
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
                    context.getString(R.string.err_get_preference_failed)
                        + "\n"
                        + error.message
                )
                uiThread {
                    state = MissionState.Stopped
                    missionListener.onStartFailed(newError)
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
                ftpClient.connectTimeout = serverTimeout
                ftpClient.connect(serverURL, serverPort)
                ftpClient.enterLocalPassiveMode()
                ftpClient.login(username, password)
            } catch (error: Exception) {
                val newError = Exception(
                    context.getString(R.string.err_login_failed)
                        + "\n"
                        + error.message
                )
                uiThread {
                    state = MissionState.Stopped
                    missionListener.onStartFailed(newError)
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
                    val error = Exception(context.getString(R.string.err_request_json_failed))
                    uiThread {
                        state = MissionState.Stopped
                        missionListener.onStartFailed(error)
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
                    val error = Exception(context.getString(R.string.err_request_gpx_failed))
                    uiThread {
                        state = MissionState.Stopped
                        missionListener.onStartFailed(error)
                    }
                    return@doAsync
                }
                fileOutputStream.close()
                waypointList = decodeGPX(localGPXFile)
            } catch (error: Exception) {
                val newError = Exception(
                    context.getString(R.string.err_request_mission_failed)
                        + "\n"
                        + error.message
                )
                uiThread {
                    state = MissionState.Stopped
                    missionListener.onStartFailed(newError)
                }
                return@doAsync
            }
            uiThread {
                File(context.filesDir, data.id + ".log")
                logMissionInfo()
                log(
                    context.getString(R.string.log_head_sta),
                    context.getString(R.string.log_mission_started)
                )
                state = MissionState.Started
                missionListener.onStarted(false)
            }
        }
    }

    /**
     * 停止任务，包括更新状态、生成日志文件、连接服务器上传日志文件。
     *
     * 成功后调用 [MissionListener.onStopped]
     *
     * 失败时调用 [MissionListener.onStopFailed]
     *
     * @author lucka-me
     * @since 0.1
     */
    fun stop() {
        state = MissionState.Stopping
        doAsync {
            val sharedPreference: SharedPreferences
            val serverURL: String
            val serverPort: Int
            val serverTimeout: Int
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
                serverTimeout = sharedPreference
                    .getString(
                        context.getString(R.string.pref_server_timeout_key),
                        context.getString(R.string.pref_server_timeout_default)
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
                    context.getString(R.string.err_get_preference_failed)
                        + "\n"
                        + error.message
                )
                uiThread {
                    state = MissionState.Started
                    missionListener.onStopFailed(newError)
                }
                return@doAsync
            }
            val ftpClient = if (enableFTPS) {
                FTPSClient(false)
            } else {
                FTPClient()
            }
            try {
                ftpClient.connectTimeout = serverTimeout
                ftpClient.connect(serverURL, serverPort)
                ftpClient.enterLocalPassiveMode()
                ftpClient.login(username, password)
            } catch (error: Exception) {
                val newError = Exception(
                    context.getString(R.string.err_login_failed)
                        + "\n"
                        + error.message
                )
                uiThread {
                    state = MissionState.Started
                    missionListener.onStopFailed(newError)
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
                uiThread {
                    state = MissionState.Started
                    val newError = Exception(
                        context.getString(R.string.stop_failed)
                            + "\n"
                            + error.message
                    )
                    missionListener.onStartFailed(newError)
                }
                return@doAsync
            }
            uiThread {
                ticketSN = 0
                data = MissionData("", "")
                val oldListSize = waypointList.size
                waypointList.clear()
                state = MissionState.Stopped
                missionListener.onStopped(oldListSize)
            }
        }
    }

    /**
     * 暂停任务，应当在主页面 Activity 将被销毁时调用，将任务数据存入文件。
     *
     * @see <a href="https://developer.android.com/training/basics/data-storage/files.html">Serialize and save the waypointList | Android Developers</a>
     * @see <a href="http://blog.csdn.net/u011240877/article/details/72455715">两种序列化方式 Serializable 和 Parcelable</a>
     *
     * @author lucka-me
     * @since 0.1
     */
    fun pause() {
        state = MissionState.Paused
        // Serialize and save the waypointList
        val filename = context.getString(R.string.mission_temp_file)
        val file = File(context.filesDir, filename)
        val fileOutputStream: FileOutputStream
        val objectOutputStream: ObjectOutputStream

        try {
            fileOutputStream = FileOutputStream(file)
            objectOutputStream = ObjectOutputStream(fileOutputStream)
            objectOutputStream.writeObject(data)
            objectOutputStream.writeInt(ticketSN)
            objectOutputStream.writeObject(waypointList)
            objectOutputStream.close()
            fileOutputStream.close()
        } catch (error: Exception) {
            DialogKit.showSimpleAlert(context, error.message)
        }

    }

    /**
     * 继续任务，应当在主页面 Activity 即将显示时调用，从文件中读取恢复任务数据
     *
     * @return 旧 Waypoint 列表的大小
     *
     * @author lucka
     * @since 0.1
     */
    fun resume() {
        val filename = context.getString(R.string.mission_temp_file)
        val file = File(context.filesDir, filename)
        val fileInputStream: FileInputStream
        val objectInputStream: ObjectInputStream

        if (!file.exists()) {
            state = MissionState.Stopped
            return
        }

        try {
            fileInputStream = FileInputStream(file)
            objectInputStream = ObjectInputStream(fileInputStream)
            data = objectInputStream.readObject() as MissionData
            ticketSN = objectInputStream.readInt()
            @Suppress("UNCHECKED_CAST")
            waypointList = objectInputStream.readObject() as ArrayList<Waypoint>
            objectInputStream.close()
            fileInputStream.close()
            state = if (data.id != "") MissionState.Started else MissionState.Stopped
            if (state == MissionState.Started) {
                missionListener.onStarted(true)
            }
        } catch (error: Exception) {
            state = MissionState.Stopped
            DialogKit.showSimpleAlert(context, error.message)
        }
    }

    /**
     * 抵达某位置时检查是否有需要检查的检查点，同时记录
     *
     * ## Changelog
     * ### 1.5.0
     * - 取消返回，修改为调用 [MissionListener.onChecking]
     *
     * @param [location] 抵达的位置
     *
     * @author lucka-me
     * @since 0.1
     */
    fun reach(location: Location) {
        lastLocation = location
        if (state != MissionState.Started || isChecking) return
        isChecking = true

        log(context.getString(R.string.log_head_loc), "")

        val reachedIndexList: ArrayList<Int> = ArrayList(0)
        for (i: Int in 0 until waypointList.size)
            if (!waypointList[i].checked && location.distanceTo(waypointList[i].location) <= 30)
                reachedIndexList.add(i)

        if (reachedIndexList.isNotEmpty())
            missionListener.onChecking(reachedIndexList.toList())
    }

    /**
     * 指定检查点检查完成
     *
     * 如果全部 Waypoint 检查完成则调用 [MissionListener.onCheckedAll]
     *
     * @param [index] 完成检查的检查点序号
     *
     * @author lucka-me
     * @since 0.1
     */
    fun checkAt(index: Int) {
        waypointList[index].checked = true
        log(context.getString(R.string.log_head_chk), waypointList[index].title)
        var isAllChecked = true
        for (waypoint in waypointList) {
            if (!waypoint.checked) {
                isAllChecked = false
                break
            }
        }
        if (isAllChecked) missionListener.onCheckedAll()
    }

    /**
     * 提交工单，包括更新状态、生成报告文件、连接服务器上传报告文件和照片。
     *
     * 成功后调用 [MissionListener.onUploadTicketSuccess]
     *
     * 失败时调用 [MissionListener.onUploadTicketFailed]
     *
     * @param [location] 报告的位置
     * @param [time] 报告的时间
     * @param [description] 报告的描述
     *
     * @author lucka-me
     * @since 0.1
     */
    fun uploadTicket(location: Location, time: Date, description: String) {
        isUploading = true
        doAsync {
            val issueFile = File(context.filesDir, "ISS_" + data.id + "_" + ticketSN + ".txt")
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
                        context.getString(R.string.err_create_issue_file_failed)
                            + "\n"
                            + error.message
                    )
                uiThread {
                    isUploading = false
                    missionListener.onUploadTicketFailed(newError)
                }
                return@doAsync
            }

            val sharedPreference: SharedPreferences
            val serverURL: String
            val serverPort: Int
            val serverTimeout: Int
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
                serverTimeout = sharedPreference
                    .getString(
                        context.getString(R.string.pref_server_timeout_key),
                        context.getString(R.string.pref_server_timeout_default)
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
                    context.getString(R.string.err_get_preference_failed)
                        + "\n"
                        + error.message
                )
                uiThread {
                    isUploading = false
                    missionListener.onUploadTicketFailed(newError)
                }
                return@doAsync
            }
            val ftpClient = if (enableFTPS) {
                FTPSClient(false)
            } else {
                FTPClient()
            }
            try {
                ftpClient.connectTimeout = serverTimeout
                ftpClient.connect(serverURL, serverPort)
                ftpClient.enterLocalPassiveMode()
                ftpClient.login(username, password)
            } catch (error: Exception) {
                val newError = Exception(
                    context.getString(R.string.err_login_failed)
                        + "\n"
                        + error.message
                )
                uiThread {
                    isUploading = false
                    missionListener.onUploadTicketFailed(newError)
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
                    .storeFile("ISS_" + data.id + "_" + ticketSN + ".txt", fileInputStream)
                fileInputStream.close()
                // Upload image
                fileInputStream = FileInputStream(ticketImagePath)
                ftpClient
                    .storeFile("ISS_" + data.id + "_" + ticketSN + ".jpg", fileInputStream)
                fileInputStream.close()
                ftpClient.logout()
                issueFile.delete()
            } catch (error: Exception) {
                uiThread {
                    isUploading = false
                    missionListener.onUploadTicketFailed(error)
                }
                return@doAsync
            }
            File(ticketImagePath).delete()
            uiThread {
                log(
                    context.getString(R.string.log_head_rep),
                    "ISS_" + data.id + "_" + ticketSN + " " + description
                )
                ticketSN += 1
                isUploading = false
                missionListener.onUploadTicketSuccess()
            }
        }
    }

    /**
     * 解码 GPX 文件
     *
     * GPX 文件样例
     * ```
     * <wpt lat="xxx" lon="xxx">
     *     <name> title </name>
     *     <desc> description </desc>
     * </wpt>
     * ```
     *
     * @see <a href="http://www.topografix.com/GPX/1/1/#type_trkType">GPX 1.1 Schema Documentation</a>
     *
     * @author lucka-me
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
     * 在日志中添加一条记录，幕后方法
     *
     * 会将换行改为 <br/>
     *
     * ## Changelog
     * ### 1.3.0
     * - 重写日志功能，本方法转为幕后方法
     * - 处理换行
     * ### 1.3.1
     * - 改名为 _log，作为幕后方法与 [log] 区别
     *
     * @param [line] 添加的新记录
     *
     * @author lucka-me
     * @since 0.1
     */
    private fun _log(line: String) {
        File(context.filesDir, data.id + ".log")
            .appendText(line.replace("\n", "<br/>") + "\n")
    }

    /**
     * 在日志中添加任务信息，包括 VER，MID 和 UID
     *
     * @author lucka-me
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
     * @param [head] 标头
     * @param [message] 消息，默认为无
     *
     * @author lucka-me
     * @since 1.3.0
     */
    fun log(head: String, message: String = "") {
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