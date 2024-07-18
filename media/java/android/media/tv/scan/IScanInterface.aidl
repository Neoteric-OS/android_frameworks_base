package android.media.tv.scan;

import android.media.tv.scan.IScanListener;

import android.os.Bundle;


/**
 * @hide
 */
interface IScanInterface {
    IBinder createSession(in int brodcastType, in String countryCode, in String operator,
            in Bundle settings, in IScanListener listener);
    Bundle getParameters(in int brodcastType, in String countryCode, in String operator,
            in Bundle params);
}