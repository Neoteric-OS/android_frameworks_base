package android.media.tv.scan;

import android.media.tv.scan.ITkgsInfoListener;

import android.os.Bundle;


/**
 * @hide
 */
interface ITkgsInfo {
     int setPrefServiceList(in String prefServiceList);
     int setTkgsInfoListener(in ITkgsInfoListener listener);
}