#include <stdlib.h>

#include <jni.h>
#include <cpu-features.h>

jboolean
Java_be_deadba_ampd_CpuFeatures_isArm(JNIEnv *env)
{
	return android_getCpuFamily() == ANDROID_CPU_FAMILY_ARM;
}

jboolean
Java_be_deadba_ampd_CpuFeatures_isArmv7a(JNIEnv *env)
{
	return android_getCpuFeatures() & ANDROID_CPU_ARM_FEATURE_ARMv7;
}

jboolean
Java_be_deadba_ampd_CpuFeatures_hasNeon(JNIEnv *env)
{
	return android_getCpuFeatures() & ANDROID_CPU_ARM_FEATURE_NEON;
}

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
	JNIEnv* env = NULL;
	jint result = -1;
	jclass clazz;

	if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
		goto bail;
	}
	if (!env) {
		goto bail;
	}

	result = JNI_VERSION_1_4;
bail:
	return result;
}

void JNI_OnUnload(JavaVM* vm, void* reserved)
{
}
