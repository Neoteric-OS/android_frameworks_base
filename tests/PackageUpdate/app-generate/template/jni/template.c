#include <stdio.h>
#include <string.h>
#include <jni.h>

jstring
%(methodname)s (JNIEnv* env, jobject thiz __unused) {
    return (*env)->NewStringUTF(env, "%(version)s");
}
