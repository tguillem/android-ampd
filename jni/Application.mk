#APP_CFLAGS := 
ifeq (1,$(NDK_ARCH_ALL))
APP_ABI := armeabi-v7a armeabi x86 mips
else
APP_ABI := armeabi-v7a
endif
APP_PLATFORM := android-14
