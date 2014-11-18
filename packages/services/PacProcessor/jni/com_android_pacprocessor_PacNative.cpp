/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "PacProcessor"

#include <map>

#include <utils/Log.h>
#include <utils/Mutex.h>
#include "android_runtime/AndroidRuntime.h"
#include "resolv_netid.h"

#include "jni.h"
#include "JNIHelp.h"

#include "proxy_resolver_v8.h"

namespace android {

namespace {

class ProxyErrorLogger : public net::ProxyErrorListener {
public:
    virtual ~ProxyErrorLogger() {}
    void AlertMessage(String16 message) {
        String8 str(message);
        ALOGD("Alert: %s", str.string());
    }
    void ErrorMessage(String16 message) {
        String8 str(message);
        ALOGE("Error: %s", str.string());
    }
    static ProxyErrorLogger* getDefault() {
        if (sDefault == NULL) {
            sDefault = new ProxyErrorLogger();
        }
        return sDefault;
    }
    static void freeDefault() {
        if (sDefault != NULL) {
            delete sDefault;
            sDefault = NULL;
        }
    }
private:
    static ProxyErrorLogger* sDefault;
};
ProxyErrorLogger* ProxyErrorLogger::sDefault;

struct ProxyResolver {
    ProxyResolver() : proxyResolver(net::ProxyResolverJSBindings::CreateDefault(),
            ProxyErrorLogger::getDefault()), pacSet(false) {}
    virtual ~ProxyResolver() {}

    net::ProxyResolverV8 proxyResolver;
    bool pacSet;
};

#define GLOBAL_PROXY_NETID (NETID_UNSET)
typedef std::map<int, ProxyResolver*> ProxyResolverMap;
ProxyResolverMap proxyResolvers;
int defaultNetId = NETID_UNSET;
bool networkProxyDisable = false;

String16 jstringToString16(JNIEnv* env, jstring jstr) {
    const jchar* str = env->GetStringCritical(jstr, 0);
    String16 str16(reinterpret_cast<const char16_t*>(str),
                   env->GetStringLength(jstr));
    env->ReleaseStringCritical(jstr, str);
    return str16;
}

jstring string16ToJstring(JNIEnv* env, String16 string) {
    const char16_t* str = string.string();
    size_t len = string.size();

    return env->NewString(reinterpret_cast<const jchar*>(str), len);
}

void setNetworkProxyDisableNativeLocked(JNIEnv*, jobject, jboolean newNetworkProxyDisable) {
    networkProxyDisable = newNetworkProxyDisable;
}

void setDefaultNetIdNativeLocked(JNIEnv*, jobject, jint newDefaultNetId) {
    defaultNetId = newDefaultNetId;
}

jboolean setProxyScriptNativeLocked(JNIEnv* env, jobject, jstring script, jint netId) {
    ProxyResolverMap::iterator proxyResolverIter = proxyResolvers.find(netId);
    if (script == NULL) {
        if (proxyResolverIter != proxyResolvers.end()) {
            proxyResolvers.erase(proxyResolverIter);
            delete proxyResolverIter->second;
            if (proxyResolvers.empty()) {
                ProxyErrorLogger::freeDefault();
            }
        }
    } else {
        String16 script16 = jstringToString16(env, script);

        if (proxyResolverIter == proxyResolvers.end()) {
            proxyResolvers[netId] = new ProxyResolver();
        }

        if (proxyResolvers[netId]->proxyResolver.SetPacScript(script16) != OK) {
            ALOGE("Unable to set PAC script");
            return JNI_TRUE;
        }
        proxyResolvers[netId]->pacSet = true;
    }

    return JNI_FALSE;
}

void shutdownNativeLocked(JNIEnv*, jobject) {
    for(ProxyResolverMap::iterator i = proxyResolvers.begin(); i != proxyResolvers.end(); ++i) {
        delete i->second;
    }
    proxyResolvers.clear();
    ProxyErrorLogger::freeDefault();
}

jstring makeProxyRequestNativeLocked(JNIEnv* env, jobject, jstring url, jstring host, jint netId) {
    String16 url16 = jstringToString16(env, url);
    String16 host16 = jstringToString16(env, host);
    String16 ret;

    // Querying for default PAC?
    if (netId == NETID_UNSET) {
        // Query for default NetID.
        netId = defaultNetId;
    // Querying for a specific NetID and a global proxy is in place?
    } else if (defaultNetId == GLOBAL_PROXY_NETID) {
        // Query for global proxy.
        netId = GLOBAL_PROXY_NETID;
    }

    ProxyResolverMap::iterator proxyResolverIter = proxyResolvers.find(netId);

    if (proxyResolverIter == proxyResolvers.end()) {
        return NULL;
    }

    if (!proxyResolverIter->second->pacSet) {
        return NULL;
    }

    if (proxyResolverIter->second->proxyResolver.GetProxyForURL(url16, host16, &ret) != OK) {
        String8 ret8(ret);
        ALOGE("Error Running PAC: %s", ret8.string());
        return NULL;
    }

    jstring jret = string16ToJstring(env, ret);

    return jret;
}

JNINativeMethod gMethods[] = {
    { "setDefaultNetIdNativeLocked", "(I)V", (void*)setDefaultNetIdNativeLocked},
    { "setNetworkProxyDisableNativeLocked", "(Z)V", (void*)setNetworkProxyDisableNativeLocked},
    { "setProxyScriptNativeLocked", "(Ljava/lang/String;I)Z", (void*)setProxyScriptNativeLocked},
    { "shutdownNativeLocked", "()V", (void*)shutdownNativeLocked},
    { "makeProxyRequestNativeLocked", "(Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/String;",
        (void*)makeProxyRequestNativeLocked},
};

} /* namespace */

int register_com_android_pacprocessor_PacNative(JNIEnv* env) {
    return jniRegisterNativeMethods(env, "com/android/pacprocessor/PacNative",
            gMethods, NELEM(gMethods));
}

} /* namespace android */
