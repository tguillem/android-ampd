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

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

public class MPDService extends Service implements LibMPD.OnErrorListener {
    private static final String TAG = "MPDService";

    static class ServiceStub extends IMPDService.Stub {
        private MPDService mService;
        ServiceStub(MPDService service) {
            mService = service;
        }
        public void start() {
            mService.postStart();
        }
        public void stop() {
            mService.postStop();
        }
        public void kill() {
            /*
             * mpd can't be turned off and on again:
             * Kill current process (used only by this current Service)
             * so that next service start will be on a new clean process
             */
            System.exit(0);
        }
        public boolean isRunning() {
            return mService.isRunning();
        }
        public void registerCallback(IMPDServiceCallback cb) {
            mService.registerCallback(cb);
        }
        public void unregisterCallback(IMPDServiceCallback cb) {
            mService.unregisterCallback(cb);
        }
    }

    private final RemoteCallbackList<IMPDServiceCallback> mCallbacks = new RemoteCallbackList<IMPDServiceCallback>();
    private final IBinder mBinder = new ServiceStub(this);
    private boolean mIsRunning = false;
    private PowerManager.WakeLock mWakelock = null;

    private final int MSG_START = 0;
    private final int MSG_STOP = 1;
    private final int MSG_ERROR = 2;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START:
                    start();
                    /*
                     * keep the service running when the caller (SettingsActivity) disconnect (onDestroy)
                     */
                    startService(new Intent(MPDService.this, MPDService.class));
                    break;
                case MSG_STOP:
                    stopSelf();
                    stop(false);
                    break;
                case MSG_ERROR:
                    stopSelf();
                    stop(true);
                    break;
            }
        }
    };

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    @Override
    public void onError(int ret) {
        Log.d(TAG, "LibMPD returned an error: " + ret);
        postError();
    }

    private void postError() {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_ERROR));
    }

    private void postStop() {
        if (!mHandler.hasMessages(MSG_ERROR)) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP));
        }
    }

    private void postStart() {
        if (!mHandler.hasMessages(MSG_ERROR)) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_START));
        }
    }

    private void signalStart() {
        int i = mCallbacks.beginBroadcast();
        while (i > 0) {
            i--;
            try {
                mCallbacks.getBroadcastItem(i).onStart();
            } catch (RemoteException e) {
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void signalStop(boolean error) {
        int i = mCallbacks.beginBroadcast();
        while (i > 0) {
            i--;
            try {
                mCallbacks.getBroadcastItem(i).onStop(error);
            } catch (RemoteException e) {
            }
        }
        mCallbacks.finishBroadcast();
    }

    private synchronized boolean start() {
        if (!mIsRunning && !LibMPD.isRunning()) {
            SharedPreferences sp = MPDConf.getSharedPreferences(this);

            MPDConf.reload(this);

            mIsRunning = LibMPD.start(this, this);
            if (!mIsRunning) {
                signalStop(true);
                return false;
            }
            Log.d(TAG, "MPD started: " + this);

            if (sp.getBoolean("wakelock", false)) {
                PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
                mWakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
                mWakelock.acquire();
                Log.d(TAG, "Wakelock acquired");
            }

            Intent mainIntent = new Intent(this, SettingsActivity.class);
            mainIntent.setAction("android.intent.action.MAIN");
            mainIntent.addCategory("android.intent.category.LAUNCHER");
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    mainIntent, PendingIntent.FLAG_CANCEL_CURRENT);

            Notification notification = new Notification.Builder(this)
                    .setContentTitle(getText(R.string.notification_title_mpd_running))
                    .setContentText(getText(R.string.notification_text_mpd_running))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentIntent(contentIntent)
                    .getNotification();

            startForeground(R.string.notification_title_mpd_running, notification);

            signalStart();

            Log.d(TAG, "start: " + mIsRunning + " / this: " + this);
        }
        return mIsRunning;
    }

    private synchronized void stop(boolean error) {
        if (mIsRunning) {
            Log.d(TAG, "stop");
            LibMPD.stop();
            mIsRunning = false;
            Log.d(TAG, "MPD stopped");

            if (mWakelock != null) {
                mWakelock.release();
                mWakelock = null;
            }
            stopForeground(true);

            signalStop(true);
        }
    }

    private synchronized boolean isRunning() {
        Log.d(TAG, "isRunning: " + mIsRunning);
        return mIsRunning;
    }

    private synchronized void registerCallback(IMPDServiceCallback cb) {
        if (cb != null) {
            mCallbacks.register(cb);
        }
    }

    private synchronized void unregisterCallback(IMPDServiceCallback cb) {
        if (cb != null) {
            mCallbacks.unregister(cb);
        }
    }

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();
        stopForeground(true);

        Log.d(TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: flags: " +flags+ " intent: " + intent);
        start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        stop(false);
        /*
         * mpd can't be turned off and on again:
         * Kill current process (used only by this current Service)
         * so that next service start will be on a new clean process
         */
        System.exit(0);
    }

    public static void start(Context context) {
        context.startService(new Intent(context, MPDService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, MPDService.class));
    }
}
