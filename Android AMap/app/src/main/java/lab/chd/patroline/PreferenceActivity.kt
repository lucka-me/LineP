package lab.chd.patroline

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem

/**
 * 主设置页面的 Activity
 *
 * 重写方法列表
 * [onCreate]
 * [onOptionsItemSelected]
 *
 * 注释参考
 * Preference Activity
 * @see <a href="https://developer.android.com/guide/topics/ui/settings.html">Android Developers</a>
 * @see <a href="https://www.jianshu.com/p/f5f8834ee9af">详解如何创建Google风格的SettingsActivity</a>
 * Call onBackPress() when tap the back button on the toolbar instead of finish()
 * @see <a href="https://developer.android.com/training/implementing-navigation/ancestral.html">Android Developers</a>
 *
 * @author lucka
 * @since 0.1
 */

class PreferenceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preference)

        if (savedInstanceState == null) {
            val preferenceFragment = PreferenceMainFragment()
            supportFragmentManager
                .beginTransaction()
                .add(R.id.preferenceFrame, preferenceFragment).commit()
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
