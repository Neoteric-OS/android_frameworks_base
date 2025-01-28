/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.locksettings;

import android.os.Handler;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.widget.LockscreenCredential;
import com.android.server.utils.Slogf;

/**
 * Keeps track of the most recent wrong LSKF guesses for each user, so that they can be rejected
 * before they reach hardware and count as a guess for throttling purposes.
 * <p>
 * This is helpful for legitimate users who may mis-enter their LSKF in the same way multiple times.
 * It does not help capable attackers, for whom duplicate wrong guesses provide no additional
 * information.  Overall, this makes it possible for the hardware to implement a stricter throttling
 * policy for (unique) wrong guesses, which increases security.
 * <p>
 * Wrong LSKFs can be somewhat sensitive information; they may be similar to the correct LSKF, or
 * they may be the correct LSKF for another user on the device or for another device that belongs to
 * the same person.  Therefore, the list of wrong LSKFs for each user is kept only in system_server
 * memory and is cleared when the correct LSKF is entered or when no wrong LSKF has been entered
 * recently.  No way to retrieve LSKFs from the list is provided, other than compromising
 * system_server memory.  A higher level of protection than system_server memory is not necessary,
 * considering the regular clearing and the fact that all LSKFs go through system_server anyway.
 */
class WrongGuessTracker {

    private static final String TAG = "WrongGuessTracker";

    @VisibleForTesting
    /** The maximum number of unique wrong guesses remembered per user. */
    static final int MAX_LENGTH = 5;

    /**
     * The number of milliseconds from a user's most recent wrong guess to when that user's
     * remembered wrong guesses are forgotten.
     */
    @VisibleForTesting
    static final int TIMEOUT_MILLIS = 300_000;

    private final Injector mInjector;

    /**
     * The most recent unique wrong LSKF guesses for each user.
     * <p>
     * If nothing is remembered for a user, then that user's map value is null.  Otherwise it is an
     * array of length MAX_LENGTH that contains the user's most recent unique wrong LSKF guesses.
     * The guesses are ordered from newest to oldest and are followed by nulls in any unused space.
     */
    private final SparseArray<LockscreenCredential[]> mMap = new SparseArray<>();

    WrongGuessTracker() {
        this(new Injector());
    }

    WrongGuessTracker(Injector injector) {
        mInjector = injector;
    }

    /**
     * Checks if the given LSKF was already checked recently and determined to be incorrect.
     *
     * @param userId Android user ID whose LSKF is being checked
     * @param guess the LSKF being checked
     * @return true if @param guess is a duplicate wrong guess, false if it's not
     */
    synchronized boolean checkIfSeen(int userId, LockscreenCredential guess) {
        LockscreenCredential[] wrongGuesses = mMap.get(userId);
        if (wrongGuesses != null) {
            for (int i = 0; i < MAX_LENGTH && wrongGuesses[i] != null; i++) {
                LockscreenCredential wrongGuess = wrongGuesses[i];
                if (wrongGuess.equals(guess)) {
                    // The wrong guess was already seen in the last MAX_LENGTH unique wrong guesses.
                    // It is now the most recently seen one, so move it to the front of the list.
                    for (int j = i; j >= 1; j--) {
                        wrongGuesses[j] = wrongGuesses[j - 1];
                    }
                    wrongGuesses[0] = wrongGuess;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Inserts a new wrong guess into the tracker.
     * <p>
     * This should be called only if {@link #checkIfSeen(int, LockscreenCredential)} returned false
     * and then the guess also failed the real credential check.
     *
     * @param userId Android user ID whose credential was checked
     * @param newWrongGuess a new wrong guess for the user's credential
     */
    synchronized void insert(int userId, LockscreenCredential newWrongGuess) {
        // Insert new wrong guess into front of list.
        LockscreenCredential[] wrongGuesses = mMap.get(userId);
        if (wrongGuesses == null) {
            wrongGuesses = new LockscreenCredential[MAX_LENGTH];
            mMap.put(userId, wrongGuesses);
        } else {
            if (wrongGuesses[MAX_LENGTH - 1] != null) {
                wrongGuesses[MAX_LENGTH - 1].zeroize();
            }
            for (int i = MAX_LENGTH - 1; i >= 1; i--) {
                wrongGuesses[i] = wrongGuesses[i - 1];
            }
        }
        wrongGuesses[0] = newWrongGuess.duplicate();

        // Schedule the list to be cleared after a period of time with no new wrong guesses.
        scheduleClearWork(userId, wrongGuesses);
    }

    @GuardedBy("this")
    private void scheduleClearWork(int userId, LockscreenCredential[] wrongGuesses) {
        mInjector.cancelClearWork(/* token= */ wrongGuesses);
        mInjector.scheduleClearWork(() -> {
            Slogf.i(TAG, "Forgetting wrong LSKF guesses for user %d", userId);
            clear(userId);
        }, /* token= */ wrongGuesses, TIMEOUT_MILLIS);
    }

    /**
     * Clears all wrong guess information for a user.
     * @param userId Android user ID whose information to clear
     */
    synchronized void clear(int userId) {
        LockscreenCredential[] wrongGuesses = mMap.get(userId);
        if (wrongGuesses != null) {
            for (int i = 0; i < MAX_LENGTH; i++) {
                if (wrongGuesses[i] != null) {
                    wrongGuesses[i].zeroize();
                    wrongGuesses[i] = null;
                }
            }
            mInjector.cancelClearWork(/* token= */ wrongGuesses);
            mMap.remove(userId);
        }
    }

    @VisibleForTesting
    static class Injector {

        void cancelClearWork(Object token) {
            Handler.getMain().removeCallbacksAndMessages(token);
        }

        void scheduleClearWork(Runnable runnable, Object token, long delayMillis) {
            Handler.getMain().postDelayed(runnable, token, delayMillis);
        }
    }
}
