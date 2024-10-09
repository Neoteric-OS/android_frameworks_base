package android.media.tv.scan;


/**
 * For satellite search function.
 * @hide
 */
interface IScanSatSearch {
    // Set currecnt LNB as customized LNB, default LNB is universal LNB
    int setCustomizedLnb(in String customizedLnb);
}