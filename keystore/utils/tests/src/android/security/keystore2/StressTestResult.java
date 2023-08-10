/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.security.keystore2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class holds test results. All times are in nanoseconds. Doubles are used rather than
 * longs because arithmetic on nanoseconds in longs can overflow.
 */
public class StressTestResult {
    private static final long MS_PER_NS = 1000000L;
    private long mThreadId;
    private double mSetupTime;
    private ArrayList<Double> mSamples = new ArrayList<>();
    private double mTeardownTime;

    public void addSetupTime(double timeInNs) {
        mSetupTime += timeInNs / MS_PER_NS;
    }

    public void addSetupTimeInMs(double timeInMs) {
        mSetupTime += timeInMs;
    }

    public double getSetupTime() {
        return mSetupTime;
    }

    public void addTeardownTime(double timeInNs) {
        mTeardownTime += timeInNs / MS_PER_NS;
    }

    public void addTeardownTimeInMs(double timeInMs) {
        mTeardownTime += timeInMs;
    }

    public double getTearDownTime() {
        return mTeardownTime;
    }

    public void addMeasurement(double timeInNs) {
        mSamples.add(timeInNs / MS_PER_NS);
    }

    public void addMeasurement(ArrayList<Double> samples) {
        mSamples.addAll(samples);
    }

    public ArrayList<Double> getSamples() {
        return mSamples;
    }

    public int getSampleCount() {
        return mSamples.size();
    }

    public double getTotalTime() {
        double sum = 0;
        for (int i = 0; i < mSamples.size(); ++i) {
            sum += mSamples.get(i);
        }
        return sum;
    }

    public double getTotalTimeSq() {
        double sum = 0;
        for (int i = 0; i < mSamples.size(); ++i) {
            sum += (double) mSamples.get(i) * mSamples.get(i);
        }
        return sum;
    }

    public double getMedian() {
        return getPercentile(0.5);
    }

    public double getPercentile(double v) {
        Collections.sort(mSamples);
        return mSamples.get((int) Math.ceil(mSamples.size() * v) - 1);
    }

    public double getMean() {
        return getTotalTime() / mSamples.size();
    }

    public double getSampleStdDev() {
        double totalTime = getTotalTime();
        int sampleCount = mSamples.size();
        return Math.sqrt(sampleCount * getTotalTimeSq() - totalTime * totalTime)
                / (sampleCount * (sampleCount - 1));
    }

    public long getThreadId() {
        return mThreadId;
    }

    public void setThreadId(long threadId) {
        this.mThreadId = threadId;
    }

    @Override
    public String toString() {
        return mSamples.size()
                + ","
                + getMean()
                + ","
                + getSampleStdDev()
                + ","
                + getMedian()
                + ","
                + getPercentile(0.9);
    }

    public void calculateCumulativePerformance(List<StressTestResult> results) {
        for (StressTestResult result : results) {
            addSetupTimeInMs(result.getSetupTime());
            addMeasurement(result.getSamples());
            addTeardownTimeInMs(result.getTearDownTime());
        }
    }
}
