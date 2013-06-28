LOCAL_PATH := $(call my-dir)

LIBAV_PATH := $(LOCAL_PATH)/libav
ICONV_PATH := $(LOCAL_PATH)/libiconv
GLIB_PATH := $(LOCAL_PATH)/glib
CURL_PATH := $(LOCAL_PATH)/curl
YAJL_PATH := $(LOCAL_PATH)/yajl
MPD_PATH := $(LOCAL_PATH)/mpd
MPDJNI_PATH := $(LOCAL_PATH)/mpd_jni
CPUFEATURESJNI_PATH := $(LOCAL_PATH)/cpufeatures_jni
ANDROID_LIBS_PATH := $(LOCAL_PATH)/android-libs

include $(LIBAV_PATH)/Android.mk
include $(ICONV_PATH)/Android.mk
include $(GLIB_PATH)/Android.mk
include $(CURL_PATH)/Android.mk
include $(YAJL_PATH)/Android.mk
include $(MPD_PATH)/Android.mk
include $(CPUFEATURESJNI_PATH)/Android.mk
include $(MPDJNI_PATH)/Android.mk
