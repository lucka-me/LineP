package lab.chd.linep

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.preference.Preference
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.PreferenceManager
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread


/**
 * Created by lucka on 18/2/2018.
 */
// Preference Activity
//   Reference: https://developer.android.com/guide/topics/ui/settings.html
//   Reference: https://www.jianshu.com/p/f5f8834ee9af
// Use PreferenceFragmentCompact instead of PreferenceFragment
//   Refrence: https://twitter.com/mariotaku/status/965522876546740224
//   Refrence: https://medium.com/@JakobUlbrich/building-a-settings-screen-for-android-part-1-5959aa49337c
//   Refrence: http://www.jcodecraeer.com/a/anzhuokaifa/androidkaifa/2017/0502/7901.html
class PreferenceMainFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference, rootKey)
        // Handle the click
        //   Reference: https://stackoverflow.com/questions/18588670/onpreferenceclick-listener-not-working-onpreferenceclick-not-being-called
        findPreference(getString(R.string.pref_server_test_key)).setOnPreferenceClickListener(object :Preference.OnPreferenceClickListener {
            override fun onPreferenceClick(preference: Preference?): Boolean {
                if (preference == null) return false
                preference.isEnabled = false
                preference.summary = getString(R.string.pref_server_test_summary_testing)

                var message: String = getString(R.string.pref_server_test_message_success)
                doAsync {
                    val sharedPreference: SharedPreferences
                    val serverURL: String
                    val serverPort: Int
                    val username: String
                    val password: String
                    val enableFTPS: Boolean
                    try {
                        // Get Server URL, username and password from SharedPreferences
                        sharedPreference = PreferenceManager.getDefaultSharedPreferences(this@PreferenceMainFragment.context)
                        serverURL = sharedPreference.getString(getString(R.string.pref_server_url_key), "")
                        serverPort = sharedPreference.getString(getString(R.string.pref_server_port_key), getString(R.string.pref_server_port_default)).toInt()
                        username = sharedPreference.getString(getString(R.string.pref_user_id_key), "")
                        password = sharedPreference.getString(getString(R.string.pref_user_token_key), "")
                        enableFTPS = sharedPreference.getBoolean(getString(R.string.pref_server_enableFTPS_key), false)
                    } catch (error: Exception) {
                        message = getString(R.string.error_get_preference_failed) + "\n" + error.message
                        uiThread {
                            val dialog = AlertDialog.Builder(this@PreferenceMainFragment.context)
                            dialog.setTitle(getString(R.string.pref_server_test_title))
                            dialog.setIcon(R.drawable.ic_pref_server_test)
                            dialog.setMessage(message)
                            dialog.setPositiveButton(getString(R.string.confirm),null)
                            dialog.show()
                            preference.title = getString(R.string.pref_server_test_title)
                            preference.summary = getString(R.string.pref_server_test_summary)
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
                    //   Reference: https://github.com/KoFuk/ftp-upload/blob/master/src/main/kotlin/com/chronoscoper/ftpupload/Main.kt
                    try {
                        ftpClient.connect(serverURL, serverPort)
                    } catch (error: Exception) {
                        message = getString(R.string.error_connect_failed) + "\n" + error.message
                        uiThread {
                            val dialog = AlertDialog.Builder(this@PreferenceMainFragment.context)
                            dialog.setTitle(getString(R.string.pref_server_test_title))
                            dialog.setIcon(R.drawable.ic_pref_server_test)
                            dialog.setMessage(message)
                            dialog.setPositiveButton(getString(R.string.confirm),null)
                            dialog.show()
                            preference.title = getString(R.string.pref_server_test_title)
                            preference.summary = getString(R.string.pref_server_test_summary)
                            preference.isEnabled = true
                        }
                        return@doAsync
                    }
                    ftpClient.enterLocalPassiveMode()
                    try {
                        ftpClient.login(username, password)
                    } catch (error: Exception) {
                        ftpClient.disconnect()
                        message = getString(R.string.error_login_failed) + "\n" + error.message
                        uiThread {
                            val dialog = AlertDialog.Builder(this@PreferenceMainFragment.context)
                            dialog.setTitle(getString(R.string.pref_server_test_title))
                            dialog.setIcon(R.drawable.ic_pref_server_test)
                            dialog.setMessage(message)
                            dialog.setPositiveButton(getString(R.string.confirm),null)
                            dialog.show()
                            preference.title = getString(R.string.pref_server_test_title)
                            preference.summary = getString(R.string.pref_server_test_summary)
                            preference.isEnabled = true
                        }
                        return@doAsync
                    }
                    ftpClient.logout()
                    ftpClient.disconnect()
                    uiThread {
                        val dialog = AlertDialog.Builder(this@PreferenceMainFragment.context)
                        dialog.setTitle(getString(R.string.pref_server_test_title))
                        dialog.setIcon(R.drawable.ic_pref_server_test)
                        dialog.setMessage(message)
                        dialog.setPositiveButton(getString(R.string.confirm),null)
                        dialog.show()
                        preference.title = getString(R.string.pref_server_test_title)
                        preference.summary = getString(R.string.pref_server_test_summary)
                        preference.isEnabled = true
                    }
                }
                return true
            }
        })
    }

}

class PreferenceAboutFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_about, rootKey)
        // Set the version information
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        val versionCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        findPreference(getString(R.string.pref_about_summary_version_key)).summary = String.format(getString(R.string.pref_about_summary_version_summary), versionName, versionCode)
    }

}