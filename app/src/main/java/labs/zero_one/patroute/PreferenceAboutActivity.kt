package labs.zero_one.patroute

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat

/**
 * 关于页面的 Activity
 *
 * ## 子类列表
 * - [PreferenceAboutFragment]
 *
 * ## 重写方法列表
 * - [onCreate]
 * - [onOptionsItemSelected]
 *
 * @author lucka
 * @since 0.1
 */
class PreferenceAboutActivity : AppCompatActivity() {

    /**
     * 关于页面的 Fragment
     *
     * ## 重写方法列表
     * - [onCreatePreferencesFix]
     *
     * @author lucka
     * @since 0.1
     */
    class PreferenceAboutFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_about, rootKey)
            // Set the version information
            val versionName =
                context!!.packageManager.getPackageInfo(context!!.packageName, 0).versionName
            val versionCode =
                context!!.packageManager.getPackageInfo(context!!.packageName, 0).versionCode
            findPreference(getString(R.string.pref_about_summary_version_key)).summary =
                String.format(
                    getString(R.string.pref_about_summary_version_summary),
                    versionName,
                    versionCode
                )
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preference_about)

        if (savedInstanceState == null) {
            val preferenceAboutFragment = PreferenceAboutFragment()
            supportFragmentManager
                .beginTransaction()
                .add(R.id.preferenceAboutFrame, preferenceAboutFragment)
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item != null) {
            when (item.itemId) {
                android.R.id.home -> {
                    onBackPressed()
                    return true
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

}
