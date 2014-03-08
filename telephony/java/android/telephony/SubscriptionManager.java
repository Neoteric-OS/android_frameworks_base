package android.telephony;

import static android.Manifest.permission.READ_PHONE_STATE;
import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.UserHandle;
import android.net.Uri;
import android.provider.BaseColumns;
import android.telephony.Rlog;
import android.os.ServiceManager;
import android.os.RemoteException;

import com.android.internal.telephony.ISub;
import com.android.internal.telephony.TelephonyIntents;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 *@hide
 */
public class SubscriptionManager implements BaseColumns {
    private static final String LOG_TAG = "SUB";

    public static final Uri CONTENT_URI =
            Uri.parse("content://telephony/siminfo");

    public static final int DEFAULT_INT_VALUE = -100;

    public static final String DEFAULT_STRING_VALUE = "N/A";

    public static final int EXTRA_VALUE_NEW_SIM = 1;
    public static final int EXTRA_VALUE_REMOVE_SIM = 2;
    public static final int EXTRA_VALUE_REPOSITION_SIM = 3;
    public static final int EXTRA_VALUE_NOCHANGE = 4;

    public static final String INTENT_KEY_DETECT_STATUS = "simDetectStatus";
    public static final String INTENT_KEY_SIM_COUNT = "simCount";
    public static final String INTENT_KEY_NEW_SIM_SLOT = "newSIMSlot";
    public static final String INTENT_KEY_NEW_SIM_STATUS = "newSIMStatus";

    /**
     * The ICC ID of a SIM.
     * <P>Type: TEXT (String)</P>
     */
    public static final String ICC_ID = "icc_id";

    /**
     * <P>Type: INTEGER (int)</P>
     */
    public static final String SIM_ID = "sim_id";

    public static final int SIM_NOT_INSERTED = -1;

    /**
     * The display name of a SIM.
     * <P>Type: TEXT (String)</P>
     */
    public static final String DISPLAY_NAME = "display_name";

    public static final int DEFAULT_NAME_RES = com.android.internal.R.string.unknownName;

    /**
     * The display name source of a SIM.
     * <P>Type: INT (int)</P>
     */
    public static final String NAME_SOURCE = "name_source";

    public static final int DEFAULT_SOURCE = 0;

    public static final int SIM_SOURCE = 1;

    public static final int USER_INPUT = 2;

    /**
     * The color of a SIM.
     * <P>Type: INTEGER (int)</P>
     */
    public static final String COLOR = "color";

    public static final int COLOR_1 = 0;

    public static final int COLOR_2 = 1;

    public static final int COLOR_3 = 2;

    public static final int COLOR_4 = 3;

    public static final int COLOR_DEFAULT = COLOR_1;

    /**
     * The phone number of a SIM.
     * <P>Type: TEXT (String)</P>
     */
    public static final String NUMBER = "number";

    /**
     * The number display format of a SIM.
     * <P>Type: INTEGER (int)</P>
     */
    public static final String DISPLAY_NUMBER_FORMAT = "display_number_format";

    public static final int DISPALY_NUMBER_NONE = 0;

    public static final int DISPLAY_NUMBER_FIRST = 1;

    public static final int DISPLAY_NUMBER_LAST = 2;

    public static final int DISLPAY_NUMBER_DEFAULT = DISPLAY_NUMBER_FIRST;

    /**
     * Permission for data roaming of a SIM.
     * <P>Type: INTEGER (int)</P>
     */
    public static final String DATA_ROAMING = "data_roaming";

    public static final int DATA_ROAMING_ENABLE = 1;

    public static final int DATA_ROAMING_DISABLE = 0;

    public static final int DATA_ROAMING_DEFAULT = DATA_ROAMING_DISABLE;

    private static final int RES_TYPE_BACKGROUND_DARK = 0;

    private static final int RES_TYPE_BACKGROUND_LIGHT = 1;

    private static final int[] sSimBackgroundDarkRes = setSimResource(RES_TYPE_BACKGROUND_DARK);

    private static final int[] sSimBackgroundLightRes = setSimResource(RES_TYPE_BACKGROUND_LIGHT);

    private static HashMap<Integer, Long> mSimInfo = new HashMap<Integer, Long>();

    public SubscriptionManager() {
        logd("SubscriptionManager created");    
    }

    /**
     * Get the SubInfoRecord according to an index
     * @param context Context provided by caller
     * @param subId The unique SubInfoRecord index in database
     * @return SubInfoRecord, maybe null
     */
    public static SubInfoRecord getSubInfoUsingSubId(Context context, long subId) {
        logd("[getSubInfoUsingSubIdx]+ subId:" + subId);
        if (subId <= 0) {
            logd("[getSubInfoUsingSubIdx]- subId <= 0");
            return null;
        }

        SubInfoRecord subInfo = null;

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                subInfo = iSub.getSubInfoUsingSubId(subId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        
        return subInfo;
        
    }

    /**
     * Get the SubInfoRecord according to an IccId
     * @param context Context provided by caller
     * @param iccId the IccId of SIM card
     * @return SubInfoRecord, maybe null
     */
    public static List<SubInfoRecord> getSubInfoUsingIccId(Context context, String iccId) {
        logd("[getSubInfoUsingIccId]+ iccId:" + iccId);
        if (iccId == null) {
            logd("[getSubInfoUsingIccId]- null iccid");
            return null;
        }

        List<SubInfoRecord> result = null;

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                result = iSub.getSubInfoUsingIccId(iccId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return result;
    }

    /**
     * Get the SubInfoRecord according to simId
     * @param context Context provided by caller
     * @param simId the slot which the SIM is inserted
     * @return SubInfoRecord, maybe null
     */
    public static List<SubInfoRecord> getSubInfoUsingSimId(Context context, int simId) {
        logd("[getSubInfoUsingSimId]+ simId:" + simId);
        if (simId < 0) {
            logd("[getSubInfoUsingSimId]- simId < 0");
            return null;
        }

        List<SubInfoRecord> result = null;

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                result = iSub.getSubInfoUsingSimId(simId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return result;
    }

    /**
     * Get all the SubInfoRecord(s) in subinfo database
     * @param context Context provided by caller
     * @return Array list of all SubInfoRecords in database, include thsoe that were inserted before
     */
    public static List<SubInfoRecord> getAllSubInfoList(Context context) {
        logd("[getAllSubInfoList]+");

        List<SubInfoRecord> result = null;        

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                result = iSub.getAllSubInfoList();
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return result;
    }

    /**
     * Get the SubInfoRecord(s) of the currently inserted SIM(s)
     * @param context Context provided by caller
     * @return Array list of currently inserted SubInfoRecord(s)
     */
    public static List<SubInfoRecord> getActivatedSubInfoList(Context context) {
        logd("[getActivatedSubInfoList]+");

        List<SubInfoRecord> result = null;

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                result = iSub.getActivatedSubInfoList();
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return result;
    }

    /**
     * Get the SUB count of all SUB(s) in subinfo database
     * @param context Context provided by caller
     * @return all SIM count in database, include what was inserted before
     */
    public static int getAllSubInfoCount(Context context) {
        logd("[getAllSubInfoCount]+");
        
        int result = 0;

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                result = iSub.getAllSubInfoCount();
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        
        return result;
    }

    /**
     * Add a new SubInfoRecord to subinfo database if needed
     * @param context Context provided by caller
     * @param iccId the IccId of the SIM card
     * @param simId the slot which the SIM is inserted
     * @return the URL of the newly created row or the updated row
     */
    public static Uri addSubInfoRecord(Context context, String iccId, int simId) {
        logd("[addSubInfoRecord]+ iccId:" + iccId + " simId:" + simId);
        if (iccId == null) {
            logd("[addSubInfoRecord]- null iccId");
        }

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                iSub.addSubInfoRecord(iccId, simId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        
        return null;
        
    }

    /**
     * Set SIM color by simInfo index
     * @param context Context provided by caller
     * @param color the color of the SIM
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    public static int setColor(Context context, int color, long subId) {
        logd("[setColor]+ color:" + color + " subId:" + subId);
        int size = sSimBackgroundDarkRes.length;
        if (subId <= 0 || color < 0 || color >= size) {
            logd("[setColor]- fail");
            return -1;
        }

        int result = 0;

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                result = iSub.setColor(color, subId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        
        return result;
        
    }

    /**
     * Set display name by simInfo index
     * @param context Context provided by caller
     * @param displayName the display name of SIM card
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    public static int setDisplayName(Context context, String displayName, long subId) {
        return setDisplayName(context, displayName, subId, -1);
    }

    /**
     * Set display name by simInfo index with name source
     * @param context Context provided by caller
     * @param displayName the display name of SIM card
     * @param subId the unique SubInfoRecord index in database
     * @param nameSource, 0: DEFAULT_SOURCE, 1: SIM_SOURCE, 2: USER_INPUT
     * @return the number of records updated
     */
    public static int setDisplayName(Context context, String displayName, long subId, long nameSource) {
        logd("[setDisplayName]+  displayName:" + displayName + " subId:" + subId + " nameSource:" + nameSource);
        if (subId <= 0) {
            logd("[setDisplayName]- fail");
            return -1;
        }

        int result = 0;

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                result = iSub.setDisplayNameUsingSrc(displayName, subId, nameSource);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        
        return result;

    }

    /**
     * Set phone number by subId
     * @param context Context provided by caller
     * @param number the phone number of the SIM
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    public static int setDispalyNumber(Context context, String number, long subId) {
        logd("[setDispalyNumber]+ number:" + number + " subId:" + subId);
        if (number == null || subId <= 0) {
            logd("[setDispalyNumber]- fail");
            return -1;
        }

        int result = 0;

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                result = iSub.setDispalyNumber(number, subId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        
        return result;

    }

    /**
     * Set number display format. 0: none, 1: the first four digits, 2: the last four digits
     * @param context Context provided by caller
     * @param format the display format of phone number
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    public static int setDispalyNumberFormat(Context context, int format, long subId) {
        logd("[setDispalyNumberFormat]+ format:" + format + " subId:" + subId);
        if (format < 0 || subId <= 0) {
            logd("[setDispalyNumberFormat]- fail, return -1");
            return -1;
        }
        
        int result = 0;

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                result = iSub.setDispalyNumberFormat(format, subId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        
        return result;

    }

    /**
     * Set data roaming by simInfo index
     * @param context Context provided by caller
     * @param roaming 0:Don't allow data when roaming, 1:Allow data when roaming
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    public static int setDataRoaming(Context context, int roaming, long subId) {
        logd("[setDataRoaming]+ roaming:" + roaming + " subId:" + subId);
        if (roaming < 0 || subId <= 0) {
            logd("[setDataRoaming]- fail");
            return -1;
        }
        
        int result = 0;

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                result = iSub.setDataRoaming(roaming, subId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        
        return result;
    }

    public static int getSimId(long subId) {
        logd("[getSimId]+ subId:" + subId);
        if (subId <= 0) {
            logd("[getSimId]- subId <= 0");
            return SIM_NOT_INSERTED;
        }

        int result = 0;

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                result = iSub.getSimId(subId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        
        return result;

    }

    public static long[] getSubId(int simId) {
        logd("[getSubId]+ simId:" + simId);
        
        if (simId < 0) {
            logd("[getSubId]- simId < 0");
            return null;
        }

        long[] subId = null;

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                subId = iSub.getSubId(simId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        
        return subId;
    }

    public static int getPhoneId(long subId) {
        logd("[getPhoneId]+ subId:" + subId);
        if (subId <= 0) {
            logd("[getPhoneId]- subId <= 0");
            return SIM_NOT_INSERTED;
        }

        int result = 0;

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                result = iSub.getPhoneId(subId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        
        return result;

    }

    private static int[] setSimResource(int type) {
        int[] simResource = null;

        switch (type) {
            case RES_TYPE_BACKGROUND_DARK:
                simResource = new int[] {
                    com.android.internal.R.drawable.sim_dark_blue,
                    com.android.internal.R.drawable.sim_dark_orange,
                    com.android.internal.R.drawable.sim_dark_green,
                    com.android.internal.R.drawable.sim_dark_purple
                };
                break;
            case RES_TYPE_BACKGROUND_LIGHT:
                simResource = new int[] {
                    com.android.internal.R.drawable.sim_light_blue,
                    com.android.internal.R.drawable.sim_light_orange,
                    com.android.internal.R.drawable.sim_light_green,
                    com.android.internal.R.drawable.sim_light_purple
                };
                break;
        }

        return simResource;
    }

    private static void logd(String msg) {
        Rlog.d(LOG_TAG, "[SubManager]" + msg);
    }

    public static long getDefaultSubId()
    {
        logd("[getDefaultSubId] return 1");
        return 1;
    }

    public static void clearSubInfo()
    {
        logd("[clearSubInfo]+");

        try {
            ISub iSub = ISub.Stub.asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                iSub.clearSubInfo();
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        
        return;
    }
}

