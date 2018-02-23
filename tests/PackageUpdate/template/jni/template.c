#include <stdio.h>
#include <string.h>
#include <jni.h>

jstring
FUNC (JNIEnv* env, jobject thiz __unused) {
    return (*env)->NewStringUTF(env, VERSION);
}