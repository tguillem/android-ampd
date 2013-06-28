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

#include <stdlib.h>

#include <jni.h>
#include <android/log.h>

#define LOG_NDEBUG 0
#define LOG_TAG "mpd_jni"

#define LOGD(format, args...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, format, ##args);
#define LOGE(format, args...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, format, ##args);

void libmpd_init();
void libmpd_deinit();
int libmpd_run(int argc, char *argv[]);
void libmpd_quit(void);

#define MPD_ARGC 2

void
Java_be_deadba_ampd_LibMPD_init(JNIEnv *env)
{
	libmpd_init();
}

void
Java_be_deadba_ampd_LibMPD_deinit(JNIEnv *env)
{
	libmpd_deinit();
}

int
Java_be_deadba_ampd_LibMPD_run(JNIEnv *env, jobject thiz, jstring mpd_conf)
{
	int ret;
	const char *c_mpd_conf = (*env)->GetStringUTFChars(env, mpd_conf, NULL);
	const char *mpd_argv[MPD_ARGC];

	mpd_argv[0] = "libmpd";
	mpd_argv[1] = c_mpd_conf;

	mpd_argv[1] = c_mpd_conf;
	return libmpd_run(MPD_ARGC, mpd_argv);
}

void
Java_be_deadba_ampd_LibMPD_quit(JNIEnv *env)
{
	libmpd_quit();
}

jint
JNI_OnLoad(JavaVM* vm, void* reserved)
{
	JNIEnv* env = NULL;
	jint result = -1;
	jclass clazz;

	if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
		LOGE("GetEnv failed");
		goto bail;
	}
	if (!env) {
		LOGE("!env");
		goto bail;
	}

	result = JNI_VERSION_1_4;
bail:
	return result;
}

void
JNI_OnUnload(JavaVM* vm, void* reserved)
{
}
