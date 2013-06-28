#!/bin/bash

if [ -z "${ANDROID_NDK}" ];then
	echo "ANDROID_NDK not defined"
	exit 1
fi

dump_symbols()
{
	nm -D $1 | awk '{print $3}' | grep -v '^__aeabi\|__FINI_ARRAY__\|__INIT_ARRAY__\|__dso_handle'
}


TARGET_CC_ARM="${ANDROID_NDK}/toolchains/arm-linux-androideabi-4.6/prebuilt/linux-x86/bin/arm-linux-androideabi-gcc"
TARGET_CC_X86="${ANDROID_NDK}/toolchains/x86-4.6/prebuilt/linux-x86/bin/i686-linux-android-gcc"
TARGET_CC_MIPS="${ANDROID_NDK}/toolchains/mipsel-linux-android-4.6/prebuilt/linux-x86/bin/mipsel-linux-android-gcc"

for i in *.so; do
	lib=`basename $i .so`
	echo "// generated C file; do not modify; see dump-libs.sh" > $lib.c
	for s in `dump_symbols $lib.so`; do
		echo "void $s() {}" >> $lib.c;
	done
	for arch in arm x86 mips; do
		cc=
		if [ "${arch}" = arm ];then
			cc=${TARGET_CC_ARM}
		elif [ "${arch}" = x86 ];then
			cc=${TARGET_CC_X86}
		else
			cc=${TARGET_CC_MIPS}
		fi
		mkdir -p ${arch}
		$cc $lib.c -shared -o ${arch}/$lib.so --sysroot=${ANDROID_NDK}/platforms/android-9/arch-${arch}
	done
done
