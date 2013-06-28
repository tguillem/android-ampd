
ifeq ($(ANDROID_NDK),)
$(error ANDROID_NDK undefined)
endif
ifeq ($(ANDROID_SDK),)
$(error ANDROID_SDK undefined)
endif

ndk_v:=
ndk_debug:=
ndk_arch_all:=
build_type:=

ifeq (1,$(VERBOSE))
	ndk_v := V=1
endif
ifneq (,$(NDK_DEBUG))
	ndk_debug := NDK_DEBUG=$(NDK_DEBUG)
endif
ifneq (,$(NDK_ARCH_ALL))
	ndk_arch_all := NDK_ARCH_ALL=$(NDK_ARCH_ALL)
endif

ifeq (,$(BUILD))
	build_type := debug
endif
ifeq (DEBUG,$(BUILD))
	build_type :=debug
endif
ifeq (RELEASE,$(BUILD))
	build_type := release
	ndk_arch_all := NDK_ARCH_ALL=1
endif


all: aMPD

local.properties:
	$(ANDROID_SDK)/tools/android update project --path .

jni/mpd/Android.mk:
	git submodule init
	git submodule update

aMPD: jni/mpd/Android.mk local.properties
	+ $(ANDROID_NDK)/ndk-build $(ndk_debug) $(ndk_v) $(ndk_arch_all) -C .
	ant $(build_type)
clean:
	ant clean
	rm -rf obj libs/armeabi libs/armeabi-v7a libs/mips libs/x86

gdb:
	$(ANDROID_NDK)/ndk-gdb
