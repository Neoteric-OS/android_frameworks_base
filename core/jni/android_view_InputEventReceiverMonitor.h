
#ifndef FRAMEWORKS_ANDROID_VIEW_INPUTEVENTRECEIVERMONITOR_H
#define FRAMEWORKS_ANDROID_VIEW_INPUTEVENTRECEIVERMONITOR_H
#include <inttypes.h>

#include <nativehelper/JNIHelp.h>

#include <android_runtime/AndroidRuntime.h>
#include <log/log.h>
#include <utils/Looper.h>
#include <utils/Vector.h>
#include <input/InputTransport.h>
#include "android_os_MessageQueue.h"
#include "android_view_InputChannel.h"
#include "android_view_KeyEvent.h"
#include "android_view_MotionEvent.h"

#include <nativehelper/ScopedLocalRef.h>

#include "core_jni_helpers.h"

#include <log/log_event_list.h>

//卡顿的阈值(ns)
constexpr nsecs_t INPUT_EVENT_RECEIVER_MONITOR_TIMEOUT_THRESHOLD = 100000000;
//log输出间隔(ns)
constexpr nsecs_t INPUT_EVENT_RECEIVER_MONITOR_LOG_DIFF_TIME = 1000000000;
//log tag
constexpr int LOGTAG_INPUT_MONITOR_APP_TIMEOUT = 1081000;

namespace android {
    class android_view_InputEventReceiverMonitor : public LooperCallback {
    public:
        explicit android_view_InputEventReceiverMonitor() {}

        ~android_view_InputEventReceiverMonitor() {}

        /**
         * 获取MessageQueue
         * @return
         */
        sp <MessageQueue> getMessageQueue();

        /**
         * 设置message queue
         * @param messageQueue
         */
        void setMessageQueue(sp <MessageQueue> messageQueue);

        /**
         * 设置ChannelName，每个receiver都有一个channel，我们需要把channel name
         * 设置到监控对象中，便于在查看输出日志时，定位问题
         * @param name 设置ChannelName
         */
        void setInputChannelName(std::string name);

        /**
         * 这里需要锁来保持一致性
         */
        Mutex mLock;

        /**
         * 信息来自receiver
         */
        void messageFromReceiver();

        /**
         * receiver信息处理完成
         */
        void receiverProcessFinish();

        /**
         * 调用此方法可以在monitor下次被调用时将自身移除
         */
        void removeMonitorAtNextHandle();

        /**
         * 重置monitor
         */
        void resetMonitor();

        void printAndClearTimeout();

    private:

        /**
         * MessageQueue，用来监听fd的message queue
         */
        sp <MessageQueue> mMonitorMessageQueue;

        /**
         * 信息来自于哪里，monitor or receiver
         */
        int mFrom = -1;

        /**
         * 来信息的时间
         */
        nsecs_t mTime = 0;

        /**
         * 定时器
         */
        nsecs_t mTimerForLog = 0;

        /**
         * 已经超时的记录
         */
        nsecs_t mLastTimeout = -1;

        /**
         * 是否需要在下次被唤醒时移除
         */
        bool mNeedRemove = false;

        /**
         * 是否需要在下次被唤醒时候跳过，因为在receiver处理
         * 完成后，可能还会有一条消息在monitor中因为调度问
         * 题没有被调用，如果再次被调用可能会导致监控错误
         */
        bool mNeedSkip = false;

        /**
         * channel name
         */
        std::string mInputChannelName;

        /**
         * 我们必须实现的方法，用来监控fd节点的回调
         * @param receiveFd
         * @param events
         * @param data
         * @return
         */
        virtual int handleEvent(int receiveFd, int events, void *data) override;
    };
}

#endif //FRAMEWORKS_ANDROID_VIEW_INPUTEVENTRECEIVERMONITOR_H