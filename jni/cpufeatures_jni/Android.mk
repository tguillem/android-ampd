LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES :=

LOCAL_SRC_FILES := \
	cpufeatures_jni.c

LOCAL_MODULE := cpufeatures_jni

LOCAL_STATIC_LIBRARIES := cpufeatures

include $(BUILD_SHARED_LIBRARY)

$(call import-module,cpufeatures)
