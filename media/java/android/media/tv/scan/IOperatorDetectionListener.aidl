package android.media.tv.scan;

import android.os.Bundle;


/**
 * @hide
 */
oneway interface IOperatorDetectionListener {
    void onDetectOperatorDetectionList(in Bundle detectOperatorDetectionList);
}