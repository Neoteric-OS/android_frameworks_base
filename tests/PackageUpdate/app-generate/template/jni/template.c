#include <stdio.h>
#include <string.h>
#include <jni.h>

jstring
%(methodname)s (JNIEnv* env, jobject thiz) {
    return (*env)->NewStringUTF(env, "%(version)s");
}
