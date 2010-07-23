package com.android.internal.telephony;

import com.android.internal.telephony.ICallControl;
import com.android.internal.telephony.ICallListener;

interface IPhoneControl {
	ICallControl newIncomingCall(String number, ICallListener listener);
	ICallControl newOutgoingCall(String number, ICallListener listener);
}

