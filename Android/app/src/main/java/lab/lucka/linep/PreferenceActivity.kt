package lab.lucka.linep

import android.support.v7.app.AppCompatActivity
import android.os.Bundle

class PreferenceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preference)
        val preferenceFragment = PreferenceFragmentCustomized()
        fragmentManager.beginTransaction().replace(android.R.id.content, preferenceFragment).commit()
    }
}
