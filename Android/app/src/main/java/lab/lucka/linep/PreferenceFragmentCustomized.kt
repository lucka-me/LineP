package lab.lucka.linep

import android.os.Bundle
import android.support.v7.preference.PreferenceFragmentCompat


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
    }
}
