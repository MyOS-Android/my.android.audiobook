package my.android.audiobook.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import my.android.audiobook.R;
import my.android.audiobook.utils.Utils;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences.OnSharedPreferenceChangeListener mPrefListener;
    private SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (mPrefListener == null) {
            mPrefListener = (prefs, key) -> {
                if (key.equals(getString(R.string.settings_dark_key))) {
                    recreate();
                }
            };
        }

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(mPrefListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPreferences.unregisterOnSharedPreferenceChangeListener(mPrefListener);
    }

    public static class BookPreferenceFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings);

            // Make sure the autoplay position preference is enabled iff autoplay is
            final SwitchPreference autoplayPref = (SwitchPreference) findPreference(getString(R.string.settings_autoplay_key));
            final SwitchPreference autoplayPositionPref = (SwitchPreference) findPreference(getString(R.string.settings_autoplay_restart_key));

            // This is needed for cases where autoplay was checked while a previous version of
            // AudioBook was installed
            if (autoplayPref.isChecked()) {
                autoplayPositionPref.setEnabled(true);
            } else {
                autoplayPositionPref.setEnabled(false);
            }

            initSummary(getPreferenceScreen());
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
            Preference pref = findPreference(s);

            updatePrefSummary(pref);

            if (s.equals(getString(R.string.settings_autoplay_key))) {
                boolean isChecked = ((SwitchPreference) pref).isChecked();
                final SwitchPreference autoplayPositionPref = (SwitchPreference) findPreference(getString(R.string.settings_autoplay_restart_key));
                if (isChecked) {
                    autoplayPositionPref.setEnabled(true);
                } else {
                    autoplayPositionPref.setEnabled(false);
                }
            }
        }

        private void initSummary(Preference p) {
            if (p instanceof PreferenceGroup) {
                PreferenceGroup pGrp = (PreferenceGroup) p;
                for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                    initSummary(pGrp.getPreference(i));
                }
            } else {
                updatePrefSummary(p);
            }
        }

        private void updatePrefSummary(Preference p) {
            if (p instanceof EditTextPreference) {
                EditTextPreference editTextPref = (EditTextPreference) p;
                p.setSummary(editTextPref.getText());
            } else if (p instanceof ListPreference) {
                p.setSummary(((ListPreference)p).getEntry());
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }
    }
}
