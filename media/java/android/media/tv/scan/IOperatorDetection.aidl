package android.media.tv.scan;

import android.media.tv.scan.IOperatorDetectionListener;

import android.os.Bundle;


/**
 * Country: Any
 * Broadcast Type: BROADCAST_TYPE_DVB_S
 * (Operator: M7)
 *
 * @hide
 */
interface IOperatorDetection {
    // Set the operator selected info for scanning.
    int setOperatorDetection(in Bundle operatorSelected);
    // Set the listener to be invoked when one or more operator detection has been detected by operator detection searches.
    int setListener(in IOperatorDetectionListener listener);
}