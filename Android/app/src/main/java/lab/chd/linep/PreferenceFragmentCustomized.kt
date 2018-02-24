package lab.chd.linep

import android.app.AlertDialog
import android.os.Bundle
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.PreferenceManager
import org.apache.commons.net.ftp.FTPClient
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
class PreferenceFragmentCustomized : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preference)
        // Handle the click
        //   Reference: https://stackoverflow.com/questions/18588670/onpreferenceclick-listener-not-working-onpreferenceclick-not-being-called
        findPreference("pref_server_test").setOnPreferenceClickListener(object :Preference.OnPreferenceClickListener {
            override fun onPreferenceClick(preference: Preference?): Boolean {
                if (preference == null) return false
                preference.summary = getString(R.string.pref_server_test_summary_testing)

                var message: String = getString(R.string.pref_server_test_message_success)
                // Get Server URL, username and password from SharedPreferences
                val sharedPreference = PreferenceManager.getDefaultSharedPreferences(this@PreferenceFragmentCustomized.context)
                val serverURL: String = sharedPreference.getString(getString(R.string.pref_server_url_key), "")
                val serverPort: Int = sharedPreference.getString(getString(R.string.pref_server_port_key), "2121").toInt()
                val username: String = sharedPreference.getString(getString(R.string.pref_user_id_key), "")
                val password: String = sharedPreference.getString(getString(R.string.pref_user_token_key), "")
                // Connect to FTP Server via Apache Commons Net API
                //   Reference: https://github.com/KoFuk/ftp-upload/blob/master/src/main/kotlin/com/chronoscoper/ftpupload/Main.kt
                val ftpClient = FTPClient()
                doAsync {
                    try {
                        ftpClient.connect(serverURL, serverPort)
                    } catch (error: Exception) {
                        message = getString(R.string.error_connect_failed) + "\n" + error.message
                        return@doAsync
                    }
                    ftpClient.enterLocalPassiveMode()
                    try {
                        ftpClient.login(username, password)
                    } catch (error: Exception) {
                        message = getString(R.string.error_login_failed) + "\n" + error.message
                        ftpClient.disconnect()
                        return@doAsync
                    }
                    ftpClient.logout()
                    ftpClient.disconnect()
                    uiThread {
                        val dialog = AlertDialog.Builder(this@PreferenceFragmentCustomized.context)
                        dialog.setTitle(getString(R.string.pref_server_test_title))
                        dialog.setIcon(R.drawable.ic_pref_server_test)
                        dialog.setMessage(message)
                        dialog.setPositiveButton(getString(R.string.confirm),null)
                        dialog.show()
                        preference.title = getString(R.string.pref_server_test_title)
                        preference.summary = getString(R.string.pref_server_test_summary)
                    }
                }
                return true
            }
        })
    }

}

class PreferenceAboutFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_about, rootKey)
    }

}