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
#include "com_android_internal_net_BpfNetworkStats.h"

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

int parseBpfNetworkStatsDetail(Vector<stats_line>* lines, const Vector<String8>& limitIfaces,
                                      int limitTag, int limitUid) {


    struct StatsKey curKey, nextKey;
    memset(&curKey, 0, sizeof(curKey));
    curKey.uid = DEFAULT_OVERFLOWUID;
    if (limitTag == -1 || limitTag > 0) {
        base::unique_fd uidStatsMap(bpf::mapRetrieve(BPF_UID_STATS_MAP, BPF_F_RDONLY));
        if (uidStatsMap < 0) {
            ALOGE("get map fd failed: %s", strerror(errno));
            return -1;
        }
        while (bpf::getNextMapKey(uidStatsMap, &curKey, &nextKey) > -1) {
            curKey = nextKey;
            char ifname[32];
            char *if_ptr = if_indextoname(curKey.ifaceIndex, ifname);
            if (if_ptr == nullptr)
                return -1;
            if (limitIfaces.size() > 0) {
                // Is this an iface the caller is interested in?
                int i = 0;
                while (i < (int)limitIfaces.size()) {
                    if (limitIfaces[i] == ifname) {
                        break;
                    }
                    i++;
                }
                if (i >= (int)limitIfaces.size()) {
                    // Nothing matched; skip this line.
                    //ALOGI("skipping due to iface: %s", buffer);
                    continue;
                }
            }
            if ((limitTag != -1 && limitTag != int(curKey.tag)) ||
                (limitUid != -1 && limitUid != int(curKey.uid)))
                continue;
            StatsValue statsEntry;
            if (bpf::findMapEntry(uidStatsMap, &curKey, &statsEntry) < 0) {
                ALOGE("get map statsEntry failed: %s", strerror(errno));
                return -1;
            }
            stats_line newLine;

            strcpy(newLine.iface, ifname);
            newLine.uid = curKey.uid;
            newLine.set = curKey.counterSet;
            newLine.tag = curKey.tag;
            newLine.rxPackets = statsEntry.rxTcpPackets + statsEntry.rxUdpPackets +
                statsEntry.rxOtherPackets;
            newLine.txPackets = statsEntry.txTcpPackets + statsEntry.txUdpPackets +
                statsEntry.txOtherPackets;
            newLine.rxBytes = statsEntry.rxTcpBytes + statsEntry.rxUdpBytes +
                statsEntry.rxOtherBytes;
            newLine.txBytes = statsEntry.txTcpBytes + statsEntry.txUdpBytes +
                statsEntry.txOtherBytes;
            lines->push_back(newLine);
        }
    }

    if (limitTag > 0)
        return 0;

    base::unique_fd tagStatsMap(bpf::mapRetrieve(BPF_TAG_STATS_MAP, BPF_F_RDONLY));
    if (tagStatsMap < 0) {
        ALOGE("get tagStats map fd failed: %s", strerror(errno));
        return -1;
    }

    memset(&curKey, 0, sizeof(curKey));
    curKey.uid = DEFAULT_OVERFLOWUID;
    while (bpf::getNextMapKey(tagStatsMap, &curKey, &nextKey) > -1) {
        curKey = nextKey;
        char ifname[32];
        char *if_ptr = if_indextoname(curKey.ifaceIndex, ifname);
        if (if_ptr == nullptr)
            return -1;
        if (limitIfaces.size() > 0) {
            // Is this an iface the caller is interested in?
            int i = 0;
            while (i < (int)limitIfaces.size()) {
                if (limitIfaces[i] == ifname) {
                    break;
                }
                i++;
            }
            if (i >= (int)limitIfaces.size()) {
                // Nothing matched; skip this line.
                //ALOGI("skipping due to iface: %s", buffer);
                continue;
            }
        }
        if ((limitTag != -1 && limitTag != int(curKey.tag)) ||
            (limitUid != -1 && limitUid != int(curKey.uid)))
            continue;
        StatsValue statsEntry;
        if (bpf::findMapEntry(tagStatsMap, &curKey, &statsEntry) < 0)
            return -1;
        stats_line newLine;
        strcpy(newLine.iface, ifname);
        newLine.uid = curKey.uid;
        newLine.set = curKey.counterSet;
        newLine.tag = curKey.tag;
        newLine.rxPackets = statsEntry.rxTcpPackets + statsEntry.rxUdpPackets +
            statsEntry.rxOtherPackets;
        newLine.txPackets = statsEntry.txTcpPackets + statsEntry.txUdpPackets +
            statsEntry.txOtherPackets;
        newLine.rxBytes = statsEntry.rxTcpBytes + statsEntry.rxUdpBytes +
            statsEntry.rxOtherBytes;
        newLine.txBytes = statsEntry.txTcpBytes + statsEntry.txUdpBytes +
            statsEntry.txOtherBytes;
        lines->push_back(newLine);
    }
    return 0;
}

}
