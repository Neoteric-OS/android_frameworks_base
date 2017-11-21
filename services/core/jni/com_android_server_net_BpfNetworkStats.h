

namespace android {

#define DEFAULT_OVERFLOWUID 65534
#define BPF_PATH "/sys/fs/bpf"

static const char* BPF_UID_STATS_MAP = BPF_PATH "/traffic_uid_stats_map";
static const char* BPF_TAG_STATS_MAP = BPF_PATH "/traffic_tag_stats_map";

struct StatsKey {
    uint32_t uid;
    uint32_t tag;
    uint32_t counterSet;
    uint32_t ifaceIndex;
};

// TODO: verify if framework side still need the detail number about TCP and UDP
// traffic. If not, remove the related tx/rx bytes and packets field to save
// space and simplify the eBPF program.
struct StatsValue {
    uint64_t rxTcpPackets;
    uint64_t rxTcpBytes;
    uint64_t txTcpPackets;
    uint64_t txTcpBytes;
    uint64_t rxUdpPackets;
    uint64_t rxUdpBytes;
    uint64_t txUdpPackets;
    uint64_t txUdpBytes;
    uint64_t rxOtherPackets;
    uint64_t rxOtherBytes;
    uint64_t txOtherPackets;
    uint64_t txOtherBytes;
};

struct Stats {
    uint64_t rxBytes;
    uint64_t rxPackets;
    uint64_t txBytes;
    uint64_t txPackets;
    uint64_t tcpRxPackets;
    uint64_t tcpTxPackets;
};

bool hasBpfSupport();
int bpfGetUidStats(uid_t uid, struct Stats* stats);
int bpfGetIfaceStats(const char *iface, struct Stats* stats);
}
