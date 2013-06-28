/*
 * Copyright (C) 2013 Thomas Guillem
 *
 * This file is part of aMPD.
 *
 * aMPD is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * aMPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with aMPD. If not, see <http://www.gnu.org/licenses/>.
 */

package be.deadba.ampd;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.os.IBinder;
import android.os.RemoteException;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;

import android.util.Log;

import java.io.File;
import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends PreferenceActivity implements ServiceConnection, Preference.OnPreferenceChangeListener {
	private static final String TAG = "SettingsActivity";
    /**
     * Determines whether to always show the simplified settings UI, where
     * settings are presented in a single list. When false, settings are shown
     * as a master/detail two-pane view on tablets. When true, a single pane is
     * shown on tablets.
     */
    private static final boolean ALWAYS_SIMPLE_PREFS = true;

    private boolean mBound = false;
    private IMPDService mIMPDService = null;

    private TwoStatePreference mRunPreference = null;
    private TwoStatePreference mRunOnBootPreference = null;

    private boolean mRestart= false;
    private boolean mRunning = false;
    private boolean mRun = false;
    private boolean mRunOnBoot = false;
    private boolean mDirValid = false;
    private boolean mPortValid = false;

    /*
     * The machine state is quite complicated.
     * We can't really restart properly MPD,
     * so the impd.stop() call will kill the service itself,
     * the process will be restarted and this activity will be connected again.
     */
    private final int KILL_TIMEOUT = 2000; // in ms

    private final int MSG_ON_CONNECTED = 0;
    private final int MSG_ON_DISCONNECTED = 1;
    private final int MSG_ON_START = 2;
    private final int MSG_ON_STOP = 3;
    private final int MSG_KILL_TIMEOUT = 4;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ON_CONNECTED: {
                    IMPDService impd = getService();
                    try {
                        if (impd.isRunning()) {
                            mRunning = mRun = true;
                        } else if (mRestart) {
                            mRun = true;
                            mRestart = false;
                        }
                    } catch (RemoteException e) {
                    }
                    onMPDStatePreferenceChange(false);
                    break;
                }
                case MSG_ON_DISCONNECTED: {
                    mRunning = false;
                    mRun = false;
                    bindService();
                    break;
                }
                case MSG_ON_START: {
                    mRunning = true;
                    onMPDStatePreferenceChange(false);
                    break;
                }
                case MSG_ON_STOP: {
                    mRunning = false;
                    mRun = false;
                    onMPDStatePreferenceChange(false);
                    IMPDService impd = getService();
                    try {
                        if (impd != null)
                            impd.kill();
                    } catch (RemoteException e) {
                    }
                    break;
                }
                case MSG_KILL_TIMEOUT: {
                    Log.d(TAG, "timeout ! killing service");
                    IMPDService impd = getService();
                    try {
                        if (impd != null)
                            impd.kill();
                    } catch (RemoteException e) {
                    }
                    break;
                }
            }
        }
    };
    private IMPDServiceCallback mIMPDServiceCallback = new IMPDServiceCallback.Stub() {
        @Override
        public void onStart() throws RemoteException {
            Log.d(TAG, "onStart");
            mHandler.removeCallbacksAndMessages(null);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_START));
        }

        @Override
        public void onStop(boolean error) throws RemoteException {
            Log.d(TAG, "onStop");
            mHandler.removeCallbacksAndMessages(null);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_STOP));
        }
    };

    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "onServiceDisconnected");
        synchronized (this) {
            mIMPDService = null;
        }
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_DISCONNECTED));
    }

    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "onServiceConnected: " + name);
        synchronized (this) {
            mIMPDService = IMPDService.Stub.asInterface(service);
            try {
                mIMPDService.registerCallback(mIMPDServiceCallback);
            } catch (RemoteException e) {
            }
        }
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_CONNECTED));
    }

    private IMPDService getService() {
        synchronized (this) {
            return mIMPDService;

        }
    }

    private void bindService() {
        Log.d(TAG, "bindService");
        mBound = bindService(new Intent(this, MPDService.class), this, Context.BIND_AUTO_CREATE);
    }

    private void unbindService() {
        if (mBound) {
            Log.d(TAG, "unbindService");
            mBound = false;
            unbindService(this);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindService();
        Log.d(TAG, "bindService: " + mBound);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        setupSimplePreferencesScreen();
    }

    /**
     * Shows the simplified settings UI if the device configuration if the
     * device configuration dictates that a simplified, single-pane UI should be
     * shown.
     */
    private void setupSimplePreferencesScreen() {
        if (!isSimplePreferences(this)) {
            return;
        }

        // In the simplified UI, fragments are not used at all and we instead
        // use the older PreferenceActivity APIs.

        // Add 'general' preferences.
        addPreferencesFromResource(R.xml.pref_general);
        setupDefaultDirectoryPreference(findPreference("mpd_music_directory"), getPreferenceScreen());

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        bindPreferenceSummaryToValue(findPreference("mpd_port"), this);
        bindPreferenceSummaryToValue(findPreference("mpd_music_directory"), this);
        bindPreferenceSummaryToValue(findPreference("wakelock"), this);
        bindPreferenceSummaryToValue(findPreference("run"), this);
        bindPreferenceSummaryToValue(findPreference("run_on_boot"), this);
    }

    /** {@inheritDoc} */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this) && !isSimplePreferences(this);
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
        & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Determines whether the simplified settings UI should be shown. This is
     * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
     * doesn't have newer APIs like {@link PreferenceFragment}, or the device
     * doesn't have an extra-large screen. In these cases, a single-pane
     * "simplified" settings UI should be shown.
     */
    private static boolean isSimplePreferences(Context context) {
        return ALWAYS_SIMPLE_PREFS
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
                || !isXLargeTablet(context);
    }

    /** {@inheritDoc} */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        if (!isSimplePreferences(this)) {
            loadHeadersFromResource(R.xml.pref_headers, target);
        }
    }

    private boolean onMPDStatePreferenceChange(boolean restart) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        IMPDService impd;
        if (mRunPreference == null || mRunOnBootPreference == null || (impd = getService()) == null)
            return false;

        Log.d(TAG, "onMPDStatePreferenceChange");

        if (!mDirValid || !mPortValid)
            mRun = mRunOnBoot = false;
        try {
            if (mRunning) {
                if (!mRun || restart) {
                    impd.stop();
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_KILL_TIMEOUT), KILL_TIMEOUT);
                    mRestart = restart;
                }
            } else {
                if (mRun)
                    impd.start();
            }
        } catch (RemoteException e) {
            mRun = false;
        }

        if (!mDirValid || !mPortValid) {
            mRunPreference.setEnabled(false);
            mRunOnBootPreference.setEnabled(false);

            Editor editor = sp.edit();
            editor.putBoolean("run_on_boot", false);
            editor.commit();

            mRunPreference.setChecked(false);
            mRunOnBootPreference.setChecked(false);

            if (!mDirValid) {
                mRunPreference.setSummary(R.string.mpd_dir_invalid);
            } else {
                mRunPreference.setSummary(R.string.mpd_port_invalid);
            }
        } else {
            mRunPreference.setEnabled(true);
            mRunOnBootPreference.setEnabled(true);

            if (mRun) {
                mRunPreference.setChecked(true);
                mRunPreference.setSummary(R.string.mpd_state_running);
            } else {
                mRunPreference.setChecked(false);
                mRunPreference.setSummary(R.string.mpd_state_notrunning);
            }
        }

        return true;
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        String stringValue = value.toString();
        String key = preference.getKey();
        Log.d(TAG, "onPreferenceChange: key: " + key + " / value: "+ stringValue);

        if (preference.isPersistent()) {
            Editor editor = MPDConf.getSharedPreferences(this).edit();
            if (value instanceof Boolean)
                editor.putBoolean(key, (Boolean)value);
            else
                editor.putString(key, (String)value);
            editor.commit();
        }
        if (key.equals("run")) {
            mRunPreference = (TwoStatePreference) preference;
            mRun = stringValue.equals("true");
            onMPDStatePreferenceChange(false);
            return true;
        } if (key.equals("run_on_boot")) {
            mRunOnBootPreference = (TwoStatePreference) preference;
            mRunOnBoot = stringValue.equals("true");
            if (mRunOnBoot)
                mRun = true;
            onMPDStatePreferenceChange(false);
            return true;
        } else if (key.equals("wakelock")) {
            onMPDStatePreferenceChange(true);
            return true;
        } else if (key.equals("mpd_music_directory")) {
            File file = new File(stringValue);
            mDirValid = file.exists() && file.isDirectory() && file.canRead() && file.canExecute();
            onMPDStatePreferenceChange(true);
        } else if (key.equals("mpd_port")) {
            int port = 0;
            try {
                port = Integer.parseInt(stringValue);
            } catch (NumberFormatException e) {
            }
            mPortValid = port >= 1024 && port <= 65535;
            onMPDStatePreferenceChange(true);
        }
        if (preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list.
            ListPreference listPreference = (ListPreference) preference;
            int index = listPreference.findIndexOfValue(stringValue);

            // Set the summary to reflect the new value.
            preference.setSummary(
                    index >= 0
                            ? listPreference.getEntries()[index]
                            : null);
        } else {
            // For all other preferences, set the summary to the value's
            // simple string representation.
            preference.setSummary(stringValue);
        }

        return true;
    }

    private static void setupDefaultDirectoryPreference(Preference preference, PreferenceScreen ps) {
        preference.setDefaultValue(MPDConf.DEFAULT_MUSIC_DIRECTORY);
        ps.removePreference(preference);
        ps.addPreference(preference);
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference, Preference.OnPreferenceChangeListener listener) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(listener);

        // Trigger the listener immediately with the preference's
        // current value.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(
                preference.getContext());
        String key = preference.getKey();
        Object value = preference instanceof TwoStatePreference
                ? Boolean.valueOf(sp.getBoolean(key, false))
                : sp.getString(key, "");

        listener.onPreferenceChange(preference, value);
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            setupDefaultDirectoryPreference(findPreference("mpd_music_directory"), getPreferenceScreen());
            SettingsActivity activity = (SettingsActivity) getActivity();

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference("mpd_port"), activity);
            bindPreferenceSummaryToValue(findPreference("mpd_music_directory"), activity);
            bindPreferenceSummaryToValue(findPreference("wakelock"), activity);
            bindPreferenceSummaryToValue(findPreference("run"), activity);
            bindPreferenceSummaryToValue(findPreference("run_on_boot"), activity);
        }
    }
}
