/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.server.net;

import android.app.IAlarmManager;
import android.content.Context;
import android.os.DeadSystemException;
import android.os.ParcelableException;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SimpleClock;
import android.util.Log;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;

/**
 * Set of networing clock utilities
 *
 * @hide
 */
public final class NetworkingClocks {

    /**
     * Create and return a {@link Clock} that is the best among {@link Clock#systemUTC()}, {@link
     * ZoneOffset#UTC} and {@link SystemClock#currentNetworkTimeClock()}
     */
    public static Clock newNetworkingClock() {
        return new BestClock(ZoneOffset.UTC, new NetworkingClock(), Clock.systemUTC());
    }

    private static final class NetworkingClock extends SimpleClock {
        private final IAlarmManager mAlarmManager;

        NetworkingClock() {
            super(ZoneOffset.UTC);
            mAlarmManager =
                    IAlarmManager.Stub.asInterface(
                            ServiceManager.getService(Context.ALARM_SERVICE));
        }

        public long millis() {
            if (mAlarmManager == null) {
                throw new RuntimeException(new DeadSystemException());
            }
            try {
                return mAlarmManager.currentNetworkTimeMillis();
            } catch (ParcelableException e) {
                e.maybeRethrow(DateTimeException.class);
                throw new RuntimeException(e);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    private static final class BestClock extends SimpleClock {
        private static final String TAG = "BestClock";

        private final Clock[] mClock;

        BestClock(ZoneId zone, Clock... clocks) {
            super(zone);
            mClock = clocks;
        }

        @Override
        public long millis() {
            for (Clock clock : mClock) {
                try {
                    return clock.millis();
                } catch (DateTimeException e) {
                    // Ignore and attempt the next clock
                    Log.w(TAG, e.toString());
                }
            }
            throw new DateTimeException(
                    "No clocks in " + Arrays.toString(mClock) + " were able to provide time");
        }
    }
}
