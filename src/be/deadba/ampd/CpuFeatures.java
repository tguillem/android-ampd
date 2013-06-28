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

import android.util.Log;

public class CpuFeatures {
    private static final String TAG = "CpuFeatures";

    static {
        try {
            System.loadLibrary("cpufeatures_jni");
        } catch (UnsatisfiedLinkError ule) {
            Log.e(TAG, "Can't load library: " + ule);
        } catch (SecurityException se) {
            Log.e(TAG, "Encountered a security issue when loading library: " + se);
        }
    }
    public native static boolean isArm();
    public native static boolean isArmv7a();
    public native static boolean hasNeon();
}
