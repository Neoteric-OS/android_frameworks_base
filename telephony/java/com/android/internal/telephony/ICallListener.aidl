package com.android.internal.telephony;

oneway interface ICallListener {
	void onAccepted(String number);
	void onCancelled(String number);
	void onHeld(String number);
	void onDtmf(String number, char dtmf);
}

