package android.service.resumeonreboot;

import android.os.RemoteCallback;

/** @hide */
interface IResumeOnRebootService {
    oneway void wrapSecret(in byte[] secret, in long lifeTimeInMillis, in RemoteCallback resultCallback);
    oneway void unwrap(in byte[] cipherText, in RemoteCallback resultCallback);
}