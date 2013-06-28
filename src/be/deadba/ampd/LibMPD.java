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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.content.Context;
import android.util.Log;

public class LibMPD {
    private static final String TAG = "LibMPD";

    public interface OnErrorListener {
        public void onError(int ret);
    }

    private static LibMPD sLibMPD = null;

    private MPDThread mMPDThread;
    private final Context mContext;
    private final OnErrorListener mOnErrorListener;
    private boolean mInit = false;

    private static final String sLibList[] = new String[] {
        "avutil",
        "avcodec",
        "avformat",
        "curl",
        "yajl",
        "iconv",
        "glib",
        "gthread",
        "mpd",
        "mpd_jni"
    };

    private void initLib() {
        String libPath = null;

        if (CpuFeatures.isArm() && CpuFeatures.isArmv7a() && !CpuFeatures.hasNeon()) {
            /*
             * Rase case: cpu is armeabi-v7a but without Neon (like tegra 2).
             * Don't try to link with armeabi-v7a libs that are built with NEON.
             * Instead, copy armeabi libs from apk (zip) into files folder and link with them.
             */
            libPath = mContext.getFilesDir() + "/armeabi";

            File apk = new File(mContext.getPackageCodePath());
            File outDir = new File(libPath);

            if (!outDir.exists() || apk.lastModified() > outDir.lastModified()) {
                Log.d(TAG, "Armv7a without neon: copying armeabi libs from apk");

                ZipFile zf = null;
                FileOutputStream fos = null;
                InputStream is = null;

                try {
                    zf = new ZipFile(apk);
                    byte[] buffer = new byte[16*1024];

                    if (!outDir.exists())
                        outDir.mkdir();
                    else
                        outDir.setLastModified(System.currentTimeMillis());

                    for (String lib : sLibList) {
                        ZipEntry ze = zf.getEntry("lib/armeabi/lib"+lib+".so");
                        fos = new FileOutputStream(outDir.getPath() +"/lib"+lib+".so");
                        is = zf.getInputStream(ze);

                        int read;
                        while ((read = is.read(buffer)) != -1)
                            fos.write(buffer, 0, read);
                        fos.close();
                        fos = null;
                        is.close();
                        is = null;
                    }
                    zf.close();
                    zf = null;
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    if (fos != null)
                        fos.close();
                    if (is != null)
                        is.close();
                    if (zf != null)
                        zf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            if (libPath == null) {
                // links with libs from lib/
                for (String lib : sLibList)
                    System.loadLibrary(lib);
            } else {
                // links with libs from files/armebi/
                for (String lib : sLibList)
                    System.load(libPath + "/lib"  + lib + ".so");
            }
            init();
            mInit = true;
        } catch (UnsatisfiedLinkError ule) {
            Log.e(TAG, "Can't load library: " + ule);
        } catch (SecurityException se) {
            Log.e(TAG, "Encountered a security issue when loading library: " + se);
        }
    }

    final private class MPDThread extends Thread {
        @Override
        public void run() {
            int ret = LibMPD.run(MPDConf.getPath(mContext));
            Log.d(TAG, "mpd terminated: " + ret);
            if (ret != 0 && mOnErrorListener != null)
                mOnErrorListener.onError(ret);
        }

        public void finish() {
            LibMPD.quit();
            try {
                join();
            } catch (InterruptedException e) {
            }
        }
    }

    private LibMPD(Context ctx, OnErrorListener listener) {
        mContext = ctx;
        mOnErrorListener = listener;
        initLib();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        deinit();
    }

    private boolean start() {
        if (mInit && mMPDThread == null) {
            mMPDThread = new MPDThread();
            mMPDThread.start();
            return true;
        } else {
            return false;
        }
    }

    private void finish() {
        if (mInit && mMPDThread != null) {
            mMPDThread.finish();
            mMPDThread = null;
        }
    }

    private boolean isThreadRunning() {
        return mMPDThread != null && mMPDThread.isAlive();
    }

    public static synchronized boolean start(Context ctx, OnErrorListener listener) {
        if (sLibMPD != null) {
            /*
             * mpd can be run only one time
             */
            return false;
        }
        sLibMPD = new LibMPD(ctx, listener);
        return sLibMPD.start();
    }

    public static synchronized boolean stop() {
        if (sLibMPD == null)
            return false;
        sLibMPD.finish();
        return true;
    }

    public static synchronized boolean isRunning() {
        return sLibMPD != null && sLibMPD.isThreadRunning();
    }

    private native static void init();
    private native static void deinit();
    private native static int run(String mpdConf);
    private native static void quit();
}
