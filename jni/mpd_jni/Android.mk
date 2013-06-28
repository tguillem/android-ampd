LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES :=

LOCAL_SRC_FILES := \
	mpd_jni.c

LOCAL_MODULE := libmpd_jni

LOCAL_SHARED_LIBRARIES := libmpd

LOCAL_LDLIBS := -llog

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_ARM_NEON:= true
endif

include $(BUILD_SHARED_LIBRARY)
