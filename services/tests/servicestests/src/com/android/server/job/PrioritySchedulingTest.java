/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.job;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.test.AndroidTestCase;
import com.android.server.job.MockPriorityJobService.TestEnvironment;
import com.android.server.job.MockPriorityJobService.TestEnvironment.Event;

import java.util.ArrayList;

@TargetApi(24)
public class PrioritySchedulingTest extends AndroidTestCase {
    /** Environment that notifies of JobScheduler callbacks. */
    static TestEnvironment kTestEnvironment = TestEnvironment.getTestEnvironment();
    /** Handle for the service which receives the execution callbacks from the JobScheduler. */
    static ComponentName kJobServiceComponent;
    JobScheduler mJobScheduler;
    /** The maximum number of concurrent jobs that JobSchedulerService runs at one time. */
    int mMaxActiveJobs = 1;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        kTestEnvironment.setUp();
        kJobServiceComponent = new ComponentName(getContext(), MockPriorityJobService.class);
        mJobScheduler = (JobScheduler) getContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);
        mJobScheduler.cancelAll();
        mMaxActiveJobs = mJobScheduler.getMaxActiveJobs();
    }

    @Override
    public void tearDown() throws Exception {
        mJobScheduler.cancelAll();
        super.tearDown();
    }

    public void testLowerPriorityJobPreempted() throws Exception {
        JobInfo jobsArray[] = new JobInfo[mMaxActiveJobs];
        for (int i = 0 ; i < mMaxActiveJobs ; i++) {
            jobsArray[i] = new JobInfo.Builder(i, kJobServiceComponent)
                    .setPriority(JobInfo.PRIORITY_FOREGROUND_APP + 1)
                    .setOverrideDeadline(7000L)
                    .build();
            mJobScheduler.schedule(jobsArray[i]);
        }
        JobInfo highPrioJob = new JobInfo.Builder(mMaxActiveJobs, kJobServiceComponent)
                .setPriority(JobInfo.PRIORITY_FOREGROUND_APP + 2)
                .setMinimumLatency(2000L)
                .setOverrideDeadline(7000L)
                .build();
        mJobScheduler.schedule(highPrioJob);
        Thread.sleep(10000);  // Wait for high prio job to preempt one of the lower priority jobs

        Event highPrioJobExecution = new Event(TestEnvironment.EVENT_START_JOB, mMaxActiveJobs);
        ArrayList<Event> executedEvents = kTestEnvironment.getExecutedEvents();
        boolean wasHighPrioJobExecuted = executedEvents.contains(highPrioJobExecution);
        boolean wasSomeJobPreempted = false;
        for (Event event: executedEvents) {
            if (event.event == TestEnvironment.EVENT_PREEMPT_JOB) {
                wasSomeJobPreempted = true;
                break;
            }
        }
        assertTrue("No job was preempted.", wasSomeJobPreempted);
        assertTrue("Lower priority jobs were not preempted.",  wasHighPrioJobExecuted);
    }

    public void testHigherPriorityJobNotPreempted() throws Exception {
        JobInfo jobsArray[] = new JobInfo[mMaxActiveJobs];
        for (int i = 0 ; i < mMaxActiveJobs ; i++) {
            jobsArray[i] = new JobInfo.Builder(i, kJobServiceComponent)
                    .setPriority(JobInfo.PRIORITY_FOREGROUND_APP + 2)
                    .setOverrideDeadline(7000L)
                    .build();
            mJobScheduler.schedule(jobsArray[i]);
        }
        JobInfo lowPrioJob = new JobInfo.Builder(mMaxActiveJobs, kJobServiceComponent)
                .setPriority(JobInfo.PRIORITY_FOREGROUND_APP + 1)
                .setMinimumLatency(2000L)
                .setOverrideDeadline(7000L)
                .build();
        mJobScheduler.schedule(lowPrioJob);
        Thread.sleep(10000);  // Wait for low prio job to preempt one of the higher priority jobs

        Event lowPrioJobExecution = new Event(TestEnvironment.EVENT_START_JOB, mMaxActiveJobs);
        boolean wasLowPrioJobExecuted = kTestEnvironment.getExecutedEvents().contains(lowPrioJobExecution);
        assertFalse("Higher priority job was preempted.", wasLowPrioJobExecuted);
    }
}
