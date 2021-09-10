//
// Created by lilinnan on 2021/11/22.
//

#include "android_view_InputEventReceiverMonitor.h"

namespace android {
    int android_view_InputEventReceiverMonitor::handleEvent(int receiveFd, int events, void *data) {
        //需要加锁，保证事件处理过程中，那边不能读取事件，防止数据不一致
        AutoMutex _l(mLock);
        if ((events & (ALOOPER_EVENT_ERROR | ALOOPER_EVENT_HANGUP)) || mNeedRemove) {
            mFrom = -1;
            mTime = 0;
            mNeedRemove = false;
            return 0;
        }
        if (mNeedSkip) {
            mNeedSkip = false;
            return 1;
        }
        if (events & ALOOPER_EVENT_INPUT) {
            nsecs_t now = systemTime(CLOCK_MONOTONIC);
            if (mFrom == -1) {
                //monitor先到
                mFrom = 0;
                mTime = now;
                return 1;
            }
            //又一次到达
            //不管是来自于monitor或者是receiver都需要看看时间
            nsecs_t diff = now - mTime;
            if (diff < INPUT_EVENT_RECEIVER_MONITOR_TIMEOUT_THRESHOLD) {
                return 1;
            }
            if (mTimerForLog == 0 || now >= mTimerForLog) {
                mTimerForLog = now + INPUT_EVENT_RECEIVER_MONITOR_LOG_DIFF_TIME;
                //当到这个地方的时候，证明发生了超时的情况，需要进行输出或者其他处理
                std::string message = "time: " + std::to_string(diff / 1000000) + "ms, from: " +
                                      std::to_string(mFrom) + ", channel name: " + mInputChannelName;
                android_log_event_list(LOGTAG_INPUT_MONITOR_APP_TIMEOUT) << message << LOG_ID_EVENTS;
                ALOGI("Input monitor app timeout, %s", message.c_str());
            }
            mLastTimeout = diff;
        }
        return 1;
    }

    void android_view_InputEventReceiverMonitor::setMessageQueue(sp <MessageQueue> messageQueue) {
        mMonitorMessageQueue = messageQueue;
    }

    sp <MessageQueue> android_view_InputEventReceiverMonitor::getMessageQueue() {
        return mMonitorMessageQueue;
    }

    void android_view_InputEventReceiverMonitor::removeMonitorAtNextHandle() {
        AutoMutex _l(mLock);
        mNeedRemove = true;
    }

    void android_view_InputEventReceiverMonitor::messageFromReceiver() {
        printAndClearTimeout();
        mFrom = 1;
        mTime = systemTime(CLOCK_MONOTONIC);
        mNeedSkip = true;
    }

    void android_view_InputEventReceiverMonitor::receiverProcessFinish() {
        AutoMutex _l(mLock);
        printAndClearTimeout();
        mFrom = -1;
    }

    void android_view_InputEventReceiverMonitor::resetMonitor() {
        AutoMutex _l(mLock);
        mFrom = -1;
        mNeedSkip = false;
        mNeedRemove = false;
        mLastTimeout = 0;
        mTimerForLog = 0;
    }

    void android_view_InputEventReceiverMonitor::setInputChannelName(std::string name) {
        mInputChannelName = name;
    }

    void android_view_InputEventReceiverMonitor::printAndClearTimeout() {
        if (mLastTimeout > 0) {
            std::string message =
                    "time: " + std::to_string(mLastTimeout / 1000000) + "ms, from: " +
                    std::to_string(mFrom) + ", channel name: " + mInputChannelName + " was restore";
            android_log_event_list(LOGTAG_INPUT_MONITOR_APP_TIMEOUT) << message << LOG_ID_EVENTS;
            ALOGI("Input monitor app timeout, %s", message.c_str());
        }
        mLastTimeout = -1;
    }
}