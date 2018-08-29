package labs.zero_one.patroute

import android.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.preference.Preference
import android.view.MenuItem
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.jetbrains.anko.uiThread

/**
 * 主设置页面的 Activity
 *
 * ## 子类列表
 * - [PreferenceMainFragment]
 *
 * ## 重写方法列表
 * - [onCreate]
 * - [onOptionsItemSelected]
 *
 * @see <a href="https://developer.android.com/guide/topics/ui/settings.html">Preference Activity | Android Developers</a>
 * @see <a href="https://www.jianshu.com/p/f5f8834ee9af">详解如何创建Google风格的SettingsActivity</a>
 * @see <a href="https://developer.android.com/training/implementing-navigation/ancestral.html">Call onBackPress() when tap the back button on the toolbar instead of finish() | Android Developers</a>
 *
 * @author lucka
 * @since 0.1
 */

class PreferenceMainActivity : AppCompatActivity() {

    /**
     * 主设置页面的 Fragment
     *
     * ## 重写方法列表
     * [onCreatePreferencesFix]
     *
     * @see <a href="https://twitter.com/mariotaku/status/965522876546740224">Use PreferenceFragmentCompact instead of PreferenceFragment | @mariotaku's tweet</a>
     * @see <a href="https://medium.com/@JakobUlbrich/building-a-settings-screen-for-android-part-1-5959aa49337c">Building an Android Settings Screen</a>
     * @see <a href="http://www.jcodecraeer.com/a/anzhuokaifa/androidkaifa/2017/0502/7901.html">创建 Android 设置界面</a>
     * @see <a href="https://stackoverflow.com/q/18588670/">Handle the click | Stack Overflow</a>
     * @see <a href="https://github.com/KoFuk/ftp-upload/blob/master/src/main/kotlin/com/chronoscoper/ftpupload/Main.kt">Connect to FTP Server via Apache Commons Net API | Sample Code</a>
     *
     * @author lucka
     * @since 0.1
     */
    class PreferenceMainFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preference, rootKey)

            // Handle the click
            findPreference(getString(R.string.pref_server_test_key)).onPreferenceClickListener =
                object : Preference.OnPreferenceClickListener {
                    override fun onPreferenceClick(preference: Preference?): Boolean {
                        if (preference == null) return false
                        preference.isEnabled = false
                        preference.summary = getString(R.string.pref_server_test_summary_testing)

                        var message: String = getString(R.string.pref_server_test_message_success)
                        doAsync {
                            val serverURL: String
                            val serverPort: Int
                            val serverTimeout: Int
                            val username: String
                            val password: String
                            val enableFTPS: Boolean
                            try {
                                // Get Server URL, username and password from SharedPreferences

                                serverURL = defaultSharedPreferences.getString(
                                    getString(R.string.pref_server_url_key),
                                    ""
                                )
                                serverPort = defaultSharedPreferences
                                    .getString(
                                        getString(R.string.pref_server_port_key),
                                        getString(R.string.pref_server_port_default)
                                    )
                                    .toInt()
                                serverTimeout = defaultSharedPreferences
                                    .getString(
                                        getString(R.string.pref_server_timeout_key),
                                        getString(R.string.pref_server_timeout_default)
                                    )
                                    .toInt()
                                username = defaultSharedPreferences
                                    .getString(getString(R.string.pref_user_id_key), "")
                                password = defaultSharedPreferences
                                    .getString(getString(R.string.pref_user_token_key), "")
                                enableFTPS = defaultSharedPreferences
                                    .getBoolean(
                                        getString(R.string.pref_server_enableFTPS_key),
                                        false
                                    )
                            } catch (error: Exception) {
                                message = (
                                    getString(R.string.err_get_preference_failed)
                                        + "\n"
                                        + error.message
                                    )
                                uiThread {
                                    AlertDialog.Builder(this@PreferenceMainFragment.context)
                                        .setTitle(getString(R.string.pref_server_test_title))
                                        .setIcon(R.drawable.ic_pref_server_test)
                                        .setMessage(message)
                                        .setPositiveButton(getString(R.string.confirm),null)
                                        .show()
                                    preference.title = getString(R.string.pref_server_test_title)
                                    preference.summary =
                                        getString(R.string.pref_server_test_summary)
                                    preference.isEnabled = true
                                }
                                return@doAsync
                            }
                            val ftpClient = if (enableFTPS) {
                                FTPSClient()
                            } else {
                                FTPClient()
                            }

                            // Connect to FTP Server via Apache Commons Net API
                            try {
                                ftpClient.connectTimeout = serverTimeout
                                ftpClient.connect(serverURL, serverPort)
                            } catch (error: Exception) {
                                message =
                                    getString(R.string.err_connect_failed) + "\n" + error.message
                                uiThread {
                                    AlertDialog.Builder(this@PreferenceMainFragment.context)
                                        .setTitle(getString(R.string.pref_server_test_title))
                                        .setIcon(R.drawable.ic_pref_server_test)
                                        .setMessage(message)
                                        .setPositiveButton(getString(R.string.confirm),null)
                                        .show()
                                    preference.title = getString(R.string.pref_server_test_title)
                                    preference.summary =
                                        getString(R.string.pref_server_test_summary)
                                    preference.isEnabled = true
                                }
                                return@doAsync
                            }
                            ftpClient.enterLocalPassiveMode()
                            try {
                                ftpClient.login(username, password)
                            } catch (error: Exception) {
                                ftpClient.disconnect()
                                message =
                                    getString(R.string.err_login_failed) + "\n" + error.message
                                uiThread {
                                    AlertDialog.Builder(this@PreferenceMainFragment.context)
                                        .setTitle(getString(R.string.pref_server_test_title))
                                        .setIcon(R.drawable.ic_pref_server_test)
                                        .setMessage(message)
                                        .setPositiveButton(getString(R.string.confirm),null)
                                        .show()
                                    preference.title = getString(R.string.pref_server_test_title)
                                    preference.summary =
                                        getString(R.string.pref_server_test_summary)
                                    preference.isEnabled = true
                                }
                                return@doAsync
                            }
                            ftpClient.logout()
                            ftpClient.disconnect()
                            uiThread {
                                AlertDialog.Builder(this@PreferenceMainFragment.context)
                                    .setTitle(getString(R.string.pref_server_test_title))
                                    .setIcon(R.drawable.ic_pref_server_test)
                                    .setMessage(message)
                                    .setPositiveButton(getString(R.string.confirm),null)
                                    .show()
                                preference.title = getString(R.string.pref_server_test_title)
                                preference.summary = getString(R.string.pref_server_test_summary)
                                preference.isEnabled = true
                            }
                        }
                        return true
                    }
                }

        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preference)

        if (savedInstanceState == null) {
            val preferenceFragment = PreferenceMainFragment()
            supportFragmentManager
                .beginTransaction()
                .add(R.id.preferenceFrame, preferenceFragment)
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item != null) {
            when (item.itemId) {
                android.R.id.home -> {
                    // Call onBackPress() when tap the back button on the toolbar instead of finish()
                    onBackPressed()
                    return true
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }


}
