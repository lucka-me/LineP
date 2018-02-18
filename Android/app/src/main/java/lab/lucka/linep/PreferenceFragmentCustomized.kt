package lab.lucka.linep

import android.os.Bundle
import android.preference.PreferenceFragment


/**
 * Created by lucka on 18/2/2018.
 */
// Preference Activity
//   Reference: https://developer.android.com/guide/topics/ui/settings.html
//   Reference: https://www.jianshu.com/p/f5f8834ee9af

class PreferenceFragmentCustomized: PreferenceFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preference)
    }
}