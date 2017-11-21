#include <net/if.h>
#include <string.h>
#include <sys/utsname.h>

#include <nativehelper/ScopedUtfChars.h>
#include <nativehelper/ScopedLocalRef.h>
#include <nativehelper/ScopedPrimitiveArray.h>

#include <utils/Log.h>
#include <utils/misc.h>
#include <utils/Vector.h>
#include <utils/String8.h>

#include "bpf/BpfUtils.h"
#include "android-base/unique_fd.h"
#include "com_android_server_net_BpfNetworkStats.h"

namespace android {

bool hasBpfSupport() {
    struct utsname buf;
    int kernel_version_major;
    int kernel_version_minor;

    int ret = uname(&buf);
    if (ret) {
        return false;
    }
    ret = sscanf(buf.release, "%d.%d", &kernel_version_major, &kernel_version_minor);
    if (ret >= 2 &&
        ((kernel_version_major == 4 && kernel_version_minor >= 9) || (kernel_version_major > 4)))
        // Turn off the eBPF feature temporarily since the selinux rules and kernel changes are not
        // landed yet.
        // TODO: turn back on when all the other dependencies are ready.
        return true;
    return false;
}

int bpfGetUidStats(uid_t uid, struct Stats* stats) {

    base::unique_fd mUidStatsMap(bpf::mapRetrieve(BPF_UID_STATS_MAP, 0));
    if (mUidStatsMap < 0) {
        ALOGE("get map fd failed: %s", strerror(errno));
        return -1;
    }

    struct StatsKey curKey, nextKey;
    memset(&curKey, 0, sizeof(curKey));
    curKey.uid = DEFAULT_OVERFLOWUID;
    while (bpf::getNextMapKey(mUidStatsMap, &curKey, &nextKey) > -1) {
        curKey = nextKey;
        if (curKey.uid == uid) {
            StatsValue statsEntry;
            if (bpf::findMapEntry(mUidStatsMap, &curKey, &statsEntry) < 0) {
                return -1;
            }
            stats->rxPackets += statsEntry.rxTcpPackets + statsEntry.rxUdpPackets +
                statsEntry.rxOtherPackets;
            stats->txPackets += statsEntry.txTcpPackets + statsEntry.txUdpPackets +
                statsEntry.txOtherPackets;
            stats->rxBytes += statsEntry.rxTcpBytes + statsEntry.rxUdpBytes +
                statsEntry.rxOtherBytes;
            stats->txBytes += statsEntry.txTcpBytes + statsEntry.txUdpBytes +
                statsEntry.txOtherBytes;
        }
    }
    return 0;
}

int bpfGetIfaceStats(const char *iface, struct Stats* stats) {

    int res = 0;
    struct StatsKey curKey, nextKey;
    base::unique_fd mUidStatsMap(bpf::mapRetrieve(BPF_UID_STATS_MAP, 0));
    if (mUidStatsMap < 0) {
        ALOGE("get map fd failed: %s", strerror(errno));
        return -1;
    }

    memset(&curKey, 0, sizeof(curKey));
    curKey.uid = DEFAULT_OVERFLOWUID;
    while (bpf::getNextMapKey(mUidStatsMap, &curKey, &nextKey) > -1) {
        curKey = nextKey;
        char curIface[32];
        char *if_ptr = if_indextoname(curKey.ifaceIndex, curIface);
        if (if_ptr == nullptr) {
            return -1;
        }
        if (!iface || strcmp(iface, curIface) == 0) {
            StatsValue statsEntry;
            if (bpf::findMapEntry(mUidStatsMap, &curKey, &statsEntry) < 0) {
                return -1;
            }

            stats->rxPackets += statsEntry.rxTcpPackets + statsEntry.rxUdpPackets +
                statsEntry.rxOtherPackets;
            stats->txPackets += statsEntry.txTcpPackets + statsEntry.txUdpPackets +
                statsEntry.txOtherPackets;
            stats->rxBytes += statsEntry.rxTcpBytes + statsEntry.rxUdpBytes +
                statsEntry.rxOtherBytes;
            stats->txBytes += statsEntry.txTcpBytes + statsEntry.txUdpBytes +
                statsEntry.txOtherBytes;
            stats->tcpRxPackets += statsEntry.rxTcpPackets;
            stats->tcpTxPackets += statsEntry.txTcpPackets;
        }
    }
    return res;
}

}
