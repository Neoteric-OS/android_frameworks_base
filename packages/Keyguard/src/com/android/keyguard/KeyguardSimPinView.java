/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.keyguard;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.content.DialogInterface;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.view.View;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView.OnEditorActionListener;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;

/**
 * Displays a PIN pad for unlocking.
 */
public class KeyguardSimPinView extends KeyguardAbsKeyInputView
        implements KeyguardSecurityView, OnEditorActionListener, TextWatcher {
    public static final String LOG_TAG = "KeyguardSimPinView";
    private static final boolean DEBUG = KeyguardViewMediator.DEBUG;
    public static final String TAG = "KeyguardSimPinView";

    private static int sCancelledCount = 0;
    private int mSubscription = -1;

    protected ProgressDialog mSimUnlockProgressDialog = null;
    protected CheckSimPin mCheckSimPinThread;

    protected AlertDialog mRemainingAttemptsDialog;

    public KeyguardSimPinView(Context context) {
        this(context, null);
    }

    public KeyguardSimPinView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void showCancelButton() {
        final View cancel = findViewById(R.id.key_cancel);
        if (cancel != null) {
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doHapticKeyClick();
                    closeKeyGuard(false);
                }
            });
        }
    }

    public void resetState() {
        mSecurityMessageDisplay.setMessage(R.string.kg_sim_pin_instructions, true);
        mPasswordEntry.setEnabled(true);
    }


    protected String getPinPasswordErrorMessage(int attemptsRemaining) {
        String displayMessage;

        if (attemptsRemaining == 0) {
            displayMessage = getContext().getString(R.string.kg_password_wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            displayMessage = getContext().getResources()
                    .getQuantityString(R.plurals.kg_password_wrong_pin_code, attemptsRemaining,
                            attemptsRemaining);
        } else {
            displayMessage = getContext().getString(R.string.kg_password_pin_failed);
        }
        if (DEBUG) Log.d(LOG_TAG, "getPinPasswordErrorMessage:"
                + " attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    @Override
    protected boolean shouldLockout(long deadline) {
        // SIM PIN doesn't have a timed lockout
        return false;
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.pinEntry;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        final View ok = findViewById(R.id.key_enter);
        if (ok != null) {
            ok.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doHapticKeyClick();
                    verifyPasswordAndUnlock();
                }
            });
        }
        showCancelButton();

        // The delete button is of the PIN keyboard itself in some (e.g. tablet) layouts,
        // not a separate view
        View pinDelete = findViewById(R.id.delete_button);
        if (pinDelete != null) {
            pinDelete.setVisibility(View.VISIBLE);
            pinDelete.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    CharSequence str = mPasswordEntry.getText();
                    if (str.length() > 0) {
                        mPasswordEntry.setText(str.subSequence(0, str.length()-1));
                    }
                    doHapticKeyClick();
                }
            });
            pinDelete.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    mPasswordEntry.setText("");
                    doHapticKeyClick();
                    return true;
                }
            });
        }

        mPasswordEntry.setKeyListener(DigitsKeyListener.getInstance());
        mPasswordEntry.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        mPasswordEntry.requestFocus();

        mSecurityMessageDisplay.setTimeout(0); // don't show ownerinfo/charging status by default
    }

    @Override
    public void showUsabilityHint() {
    }

    @Override
    public void onPause() {
        // dismiss the dialog.
        if (mSimUnlockProgressDialog != null) {
            mSimUnlockProgressDialog.dismiss();
            mSimUnlockProgressDialog = null;
        }
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPin extends Thread {
        private final String mPin;

        protected CheckSimPin(String pin) {
            mPin = pin;
        }

        protected CheckSimPin(String pin,int sub) {
            mPin = pin;
            mSubscription = sub;
        }

        abstract void onSimCheckResponse(final int result, final int attemptsRemaining);

        @Override
        public void run() {
            try {
                Log.v(TAG, "call supplyPinReportResult()");
                final int[] result = ITelephony.Stub.asInterface(ServiceManager
                        .checkService("phone")).supplyPinReportResultUsingSub(mSubscription, mPin);
                Log.v(TAG, "supplyPinReportResult returned: " + result[0] + " " + result[1]);
                post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(result[0], result[1]);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException for supplyPinReportResult:", e);
                post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(PhoneConstants.PIN_GENERAL_FAILURE, -1);
                    }
                });
            }
        }
    }

    protected Dialog getSimUnlockProgressDialog() {
        if (mSimUnlockProgressDialog == null) {
            mSimUnlockProgressDialog = new ProgressDialog(mContext);
            mSimUnlockProgressDialog.setMessage(
                    mContext.getString(R.string.kg_sim_unlock_progress_dialog_message));
            mSimUnlockProgressDialog.setIndeterminate(true);
            mSimUnlockProgressDialog.setCancelable(false);
            mSimUnlockProgressDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        }
        return mSimUnlockProgressDialog;
    }

    protected Dialog getSimRemainingAttemptsDialog(int remaining) {
        String msg = getPinPasswordErrorMessage(remaining);
        if (mRemainingAttemptsDialog == null) {
            Builder builder = new AlertDialog.Builder(mContext);
            builder.setMessage(getSecurityMessageDisplay(msg));
            builder.setCancelable(false);
            builder.setNeutralButton(R.string.ok, null);
            mRemainingAttemptsDialog = builder.create();
            mRemainingAttemptsDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        } else {
            mRemainingAttemptsDialog.setMessage(getSecurityMessageDisplay(msg));
        }
        return mRemainingAttemptsDialog;
    }

    @Override
    protected void verifyPasswordAndUnlock() {
        String entry = mPasswordEntry.getText().toString();

        if (entry.length() < 4) {
            // otherwise, display a message to the user, and don't submit.
            mSecurityMessageDisplay.setMessage(
                    getSecurityMessageDisplay(R.string.kg_invalid_sim_pin_hint), true);
            mPasswordEntry.setText("");
            mCallback.userActivity(0);
            return;
        }

        getSimUnlockProgressDialog().show();

        if (mCheckSimPinThread == null) {

            if (DEBUG) Log.d(TAG, "startCheckSimPin(), Multi SIM enabled");
            mCheckSimPinThread = new CheckSimPin(mPasswordEntry.getText().toString(),
                    KeyguardUpdateMonitor.getInstance(mContext).getPinLockedSubscription()) {
                void onSimCheckResponse(final int result, final int attemptsRemaining) {
                    post(new Runnable() {
                        public void run() {
                            if (mSimUnlockProgressDialog != null) {
                                mSimUnlockProgressDialog.hide();
                            }
                            if (result == PhoneConstants.PIN_RESULT_SUCCESS) {
                                // before closing the keyguard, report back that the sim is unlocked
                                // so it knows right away.
                                closeKeyGuard(true);
                            } else {
                                if (result == PhoneConstants.PIN_PASSWORD_INCORRECT) {
                                    if (attemptsRemaining <= 2) {
                                        // this is getting critical - show dialog
                                        getSimRemainingAttemptsDialog(attemptsRemaining).show();
                                    } else {
                                        // show message
                                        mSecurityMessageDisplay.setMessage(
                                                getSecurityMessageDisplay(
                                                getPinPasswordErrorMessage(
                                                attemptsRemaining)), true);
                                    }
                                } else {
                                    // "PIN operation failed!" - no idea what this was and no way to
                                    // find out. :/
                                    mSecurityMessageDisplay.setMessage(getSecurityMessageDisplay
                                        (R.string.kg_password_pin_failed), true);
                                }
                                if (DEBUG) Log.d(LOG_TAG, "verifyPasswordAndUnlock "
                                        + " CheckSimPin.onSimCheckResponse: " + result
                                        + " attemptsRemaining=" + attemptsRemaining);
                                mPasswordEntry.setText("");
                            }
                            mCallback.userActivity(0);
                            mCheckSimPinThread = null;
                        }
                    });
                }
            };
            mCheckSimPinThread.start();
        }
    }

    /*
    * CloseKeyGuard: Method used to Close the keyguard.
    * @param bAuthenticated:
    *               true:  user entered correct PIN and pressed OK.
    *               false: user pressed cancel.
    *
    * Description: used to close keyguard when user presses cancel or
    * user enters correct PIN and presses OK
    *
    * Logic:
    * 1. Calculate number of SIMs configured i.e. selected from MultiSIM settings
    * 2. Calculate number of SIMs which are PIN Locked
    * 3. sCancelledCount will tell us number of keyguards got cancelled. If user tries to cancel
    *    all Keyguard screens, do not allow him to close Keyguard as atleast one SIM has to
    *    be authenticated before allowing user to access HomeScreen.
    * 4. If present keyguard screen is last one we are about to show HomeScreen after closing this
    *    keyguard so reset the sCancelledCount.
    * 5. Report SIM unlocked to KGUpdateMonitor so that keyguard is closed right away without
    *    waiting for indication from framework.
    */
    private void closeKeyGuard(boolean bAuthenticated) {
        KeyguardUpdateMonitor updateMonitor = KeyguardUpdateMonitor.getInstance(getContext());
        int numCardsConfigured = 0;
        int numPinLocked = 0;
        IccCardConstants.State simState = IccCardConstants.State.UNKNOWN;

        int numPhones = TelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < numPhones; i++) {
            simState = updateMonitor.getSimState(i);
            if (simState == IccCardConstants.State.PIN_REQUIRED) {
                numPinLocked++;
            }

            //Get number of cards that are present and configured.
            //Exclude cards with DETECTED state as they are not configured and are not used.
            if (simState == IccCardConstants.State.READY ||
                simState == IccCardConstants.State.PIN_REQUIRED ||
                simState == IccCardConstants.State.PUK_REQUIRED ||
                simState == IccCardConstants.State.NETWORK_LOCKED) {
                numCardsConfigured++;
            }
        }

        //If Cancel is pressed for last configured sub return without incrementing sCancelledCount
        // else increment sCancelledCount.
        if (!bAuthenticated) {
            if (sCancelledCount >= (numCardsConfigured - 1)) {
                return;
            } else {
                sCancelledCount++;
            }
        }

        //If this is last PIN lock screen, we will show HomeScreen now.
        //Hence reset the static sCancelledCount variable.
        if (numPinLocked <= 1) {
            sCancelledCount = 0;
        }

        //If Cancel is pressed get current locked sub,
        //In case user presses OK we anyways have locked sub, no need to get again.
        if (!bAuthenticated) mSubscription = updateMonitor.getPinLockedSubscription();

        //Before closing the keyguard, report back that the sim is unlocked
        //so it knows right away.
        if (mSubscription >= 0) updateMonitor.reportSimUnlocked(mSubscription);
        mCallback.dismiss(true);
    }

    protected CharSequence getSecurityMessageDisplay(int resId) {
        // Returns the String in the format
        // "SUB:%d : %s", sub, msg
        return getContext().getString(R.string.msim_kg_sim_pin_msg_format,
                KeyguardUpdateMonitor.getInstance(mContext).getPinLockedSubscription()+1,
                getContext().getResources().getText(resId));
    }

    protected CharSequence getSecurityMessageDisplay(String msg) {
        // Returns the String in the format
        // "SUB:%d : %s", sub, msg
        return getContext().getString(R.string.msim_kg_sim_pin_msg_format,
                KeyguardUpdateMonitor.getInstance(mContext).getPinLockedSubscription()+1,msg);
    }

}

