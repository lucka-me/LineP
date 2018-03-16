package lab.chd.linep

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem

/**
 * 关于页面的 Activity
 *
 * 重写方法列表
 * [onCreate]
 * [onOptionsItemSelected]
 *
 * @author lucka
 * @since 0.1
 */
class PreferenceAboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preference_about)

        if (savedInstanceState == null) {
            val preferenceAboutFragment = PreferenceAboutFragment()
            supportFragmentManager
                .beginTransaction()
                .add(R.id.preferenceAboutFrame, preferenceAboutFragment).commit()
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
