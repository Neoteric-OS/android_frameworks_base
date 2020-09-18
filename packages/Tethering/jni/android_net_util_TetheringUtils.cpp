/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <errno.h>
#include <error.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/JNIHelpCompat.h>
#include <nativehelper/ScopedUtfChars.h>
#include <net/if.h>
#include <netinet/icmp6.h>
#include <sys/socket.h>

#define LOG_TAG "TetheringUtils"
#include <android/log.h>

#include "OffloadUtils.h"
#include "bpf/BpfMap.h"

using android::base::unique_fd;

namespace android {
static bpf::BpfMap<TetherIngressKey, TetherIngressValue> mBpfIngressMap;
static bpf::BpfMap<uint32_t, TetherStatsValue> mBpfStatsMap;
static bpf::BpfMap<uint32_t, uint64_t> mBpfLimitMap;

void startBpf(const char* extIface) {
    // TODO: perhaps ignore IPv4-only interface because IPv4 traffic downstream is not supported.
    int ifIndex = if_nametoindex(extIface);
    if (!ifIndex) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Fail to get index for interface %s",
                extIface);
        return;
    }

    auto isEthernet = net::isEthernet(extIface);
    if (!isEthernet.ok()) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "isEthernet(%s[%d]) failure: %s", extIface,
              ifIndex, isEthernet.error().message().c_str());
        return;
    }

    int rv = net::getTetherIngressProgFd(isEthernet.value());
    if (rv < 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "getTetherIngressProgFd(%d) failure: %s",
                isEthernet.value(), strerror(-rv));
        return;
    }
    unique_fd tetherProgFd(rv);

    rv = net::tcFilterAddDevIngressTether(ifIndex, tetherProgFd, isEthernet.value());
    if (rv) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
              "tcFilterAddDevIngressTether(%d[%s], %d) failure: %s",
              ifIndex, extIface, isEthernet.value(), strerror(-rv));
        return;
    }
}

void stopBpf(const char* extIface) {
    // TODO: perhaps ignore IPv4-only interface because IPv4 traffic downstream is not supported.
    int ifIndex = if_nametoindex(extIface);
    if (!ifIndex) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Fail to get index for interface %s",
                extIface);
        return;
    }

    int rv = net::tcFilterDelDevIngressTether(ifIndex);
    if (rv < 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                "tcFilterDelDevIngressTether(%d[%s]) failure: %s", ifIndex, extIface,
                strerror(-rv));
    }
}

static void android_net_util_enableBpf(JNIEnv *env, jobject, jboolean enable, jstring extIface) {
    const char* iface = env->GetStringUTFChars(extIface, NULL);
    if (enable) {
        startBpf(iface);
    } else {
        stopBpf(iface);
    }
}

static void android_net_util_initBpfMaps(JNIEnv *env, jobject clazz) {
    int fd = net::getTetherIngressMapFd();
    if (fd >= 0) {
        mBpfIngressMap.reset(fd);
        mBpfIngressMap.clear();
    }
    fd = net::getTetherStatsMapFd();
    if (fd >= 0) {
        mBpfStatsMap.reset(fd);
        mBpfStatsMap.clear();
    }
    fd = net::getTetherLimitMapFd();
    if (fd >= 0) {
        mBpfLimitMap.reset(fd);
        mBpfLimitMap.clear();
    }
}

static void android_net_util_setupRaSocket(JNIEnv *env, jobject clazz, jobject javaFd,
        jint ifIndex)
{
    static const int kLinkLocalHopLimit = 255;

    int fd = jniGetFDFromFileDescriptor(env, javaFd);

    // Set an ICMPv6 filter that only passes Router Solicitations.
    struct icmp6_filter rs_only;
    ICMP6_FILTER_SETBLOCKALL(&rs_only);
    ICMP6_FILTER_SETPASS(ND_ROUTER_SOLICIT, &rs_only);
    socklen_t len = sizeof(rs_only);
    if (setsockopt(fd, IPPROTO_ICMPV6, ICMP6_FILTER, &rs_only, len) != 0) {
        jniThrowExceptionFmt(env, "java/net/SocketException",
                "setsockopt(ICMP6_FILTER): %s", strerror(errno));
        return;
    }

    // Most/all of the rest of these options can be set via Java code, but
    // because we're here on account of setting an icmp6_filter go ahead
    // and do it all natively for now.

    // Set the multicast hoplimit to 255 (link-local only).
    int hops = kLinkLocalHopLimit;
    len = sizeof(hops);
    if (setsockopt(fd, IPPROTO_IPV6, IPV6_MULTICAST_HOPS, &hops, len) != 0) {
        jniThrowExceptionFmt(env, "java/net/SocketException",
                "setsockopt(IPV6_MULTICAST_HOPS): %s", strerror(errno));
        return;
    }

    // Set the unicast hoplimit to 255 (link-local only).
    hops = kLinkLocalHopLimit;
    len = sizeof(hops);
    if (setsockopt(fd, IPPROTO_IPV6, IPV6_UNICAST_HOPS, &hops, len) != 0) {
        jniThrowExceptionFmt(env, "java/net/SocketException",
                "setsockopt(IPV6_UNICAST_HOPS): %s", strerror(errno));
        return;
    }

    // Explicitly disable multicast loopback.
    int off = 0;
    len = sizeof(off);
    if (setsockopt(fd, IPPROTO_IPV6, IPV6_MULTICAST_LOOP, &off, len) != 0) {
        jniThrowExceptionFmt(env, "java/net/SocketException",
                "setsockopt(IPV6_MULTICAST_LOOP): %s", strerror(errno));
        return;
    }

    // Specify the IPv6 interface to use for outbound multicast.
    len = sizeof(ifIndex);
    if (setsockopt(fd, IPPROTO_IPV6, IPV6_MULTICAST_IF, &ifIndex, len) != 0) {
        jniThrowExceptionFmt(env, "java/net/SocketException",
                "setsockopt(IPV6_MULTICAST_IF): %s", strerror(errno));
        return;
    }

    // Additional options to be considered:
    //     - IPV6_TCLASS
    //     - IPV6_RECVPKTINFO
    //     - IPV6_RECVHOPLIMIT

    // Bind to [::].
    const struct sockaddr_in6 sin6 = {
            .sin6_family = AF_INET6,
            .sin6_port = 0,
            .sin6_flowinfo = 0,
            .sin6_addr = IN6ADDR_ANY_INIT,
            .sin6_scope_id = 0,
    };
    auto sa = reinterpret_cast<const struct sockaddr *>(&sin6);
    len = sizeof(sin6);
    if (bind(fd, sa, len) != 0) {
        jniThrowExceptionFmt(env, "java/net/SocketException",
                "bind(IN6ADDR_ANY): %s", strerror(errno));
        return;
    }

    // Join the all-routers multicast group, ff02::2%index.
    struct ipv6_mreq all_rtrs = {
        .ipv6mr_multiaddr = {{{0xff,2,0,0,0,0,0,0,0,0,0,0,0,0,0,2}}},
        .ipv6mr_interface = ifIndex,
    };
    len = sizeof(all_rtrs);
    if (setsockopt(fd, IPPROTO_IPV6, IPV6_JOIN_GROUP, &all_rtrs, len) != 0) {
        jniThrowExceptionFmt(env, "java/net/SocketException",
                "setsockopt(IPV6_JOIN_GROUP): %s", strerror(errno));
        return;
    }
}

/*
 * JNI registration.
 */
static const JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "setupRaSocket", "(Ljava/io/FileDescriptor;I)V", (void*) android_net_util_setupRaSocket },
    { "initBpfMaps", "()", (void*) android_net_util_initBpfMaps },
    { "enableBpf", "(ZLjava/lang/String)", (void*) android_net_util_enableBpf },
};

int register_android_net_util_TetheringUtils(JNIEnv* env) {
    return jniRegisterNativeMethods(env,
            "android/net/util/TetheringUtils",
            gMethods, NELEM(gMethods));
}

extern "C" jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "ERROR: GetEnv failed");
        return JNI_ERR;
    }

    if (register_android_net_util_TetheringUtils(env) < 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}

}; // namespace android
