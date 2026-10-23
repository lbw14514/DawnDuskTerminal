#ifndef _JAVASOFT_JNI_MD_H_
#define _JAVASOFT_JNI_MD_H_

/*
 * Windows JNI platform header for cross-compiling with MinGW.
 * The Linux JDK only ships include/linux/jni_md.h, so the Windows target
 * needs this fallback on the cross-compile runner. On a Windows host the
 * JDK's own include/win32/jni_md.h is found first.
 */

#define JNIEXPORT __declspec(dllexport)
#define JNIIMPORT __declspec(dllimport)
#define JNICALL __stdcall

typedef long jint;
typedef long long jlong;
typedef signed char jbyte;

#endif
