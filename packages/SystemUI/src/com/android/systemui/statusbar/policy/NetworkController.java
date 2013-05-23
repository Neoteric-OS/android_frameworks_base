/*
 * Copyright (C) 2010 The Android Open Source Project
 * Portions Copyright (C) 2012-2013 Motorola Mobility LLC All Rights Reserved.
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

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wimax.WimaxManagerConstants;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Slog;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.app.IBatteryStats;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.EriInfo;
import com.android.internal.util.AsyncChannel;
import com.android.server.am.BatteryStatsService;
import com.android.systemui.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;



public class NetworkController extends BroadcastReceiver {

    // Logging & debugging
    static final String TAG = "SBar.NetworkController"; // Shortened name for isLoggable
    static final boolean CHATTY =  Log.isLoggable( TAG, Log.VERBOSE ); // additional diagnostics, but not logspew
    static final boolean DEBUG = ( Log.isLoggable( TAG, Log.DEBUG ) || CHATTY );
    static final boolean INFO = ( true || DEBUG );

    // INFO level has been added, some items previously marked DEBUG have been changed to INFO.
    //   All places in code below marked CHATTY will use Slog.v
    //   All places in code below marked DEBUG will use Slog.d
    //   All places in code below marked INFO will use Slog.i

    private long mLoggerMobileSignalIntervalTimestamp = 0;
    private long mLoggerMobileDataIntervalTimestamp = 0;
    private int  mLoggerMobileIconId = (-1);
    private int  mLoggerMobileRoamingIconId = (-1);
    private int  mLoggerMobileSimIconId = (-1);
    private int  mLoggerMobileDataTypeIconId = (-1);
    private int  mLoggerMobileActivityIconId = (-1);

    private long mLoggerWimaxSignalIntervalTimestamp = 0;
    private long mLoggerWimaxDataIntervalTimestamp = 0;
    private int  mLoggerWimaxIconId = (-1);
    private int  mLoggerWimaxRoamingIconId = (-1);
    private int  mLoggerWimaxSimIconId = (-1);
    private int  mLoggerWimaxDataTypeIconId = (-1);
    private int  mLoggerWimaxActivityIconId = (-1);

    private long mLoggerWifiIntervalTimestamp = 0;
    private int  mLoggerWifiIconId = (-1);
    private int  mLoggerWifiDataActivityIconId = (-1);

    private long mLoggerOnSignalStrengthIntervalTimestamp = 0;
    private int  mLoggerOnSignalStrengthLevel = (-1);

    private static final long LOGGER_REQUIRED_REPORTING_INTERVAL = 120000; // once per 2-minutes


    Context mContext;

    // This is used so you can search for places the COntentDescription strings are set to empty
    private static final String sEmptyString = "";


    // telephony
    boolean mConfigMobileDataTypeHspaDataDistinguishable;
    private boolean mConfigMobileDataTypeHspapDataDistinguishable;
    private boolean mConfigMobileDataTypeShowDataDisabledIcon;
    private boolean mConfigMobileDataTypeShowConnectingOrAttachedIcon;
    private String mConfigOperatorName_string;

    private TelephonyManager mPhone;
    boolean mMobileDataIsConnected;
    IccCardConstants.State mMobileSimState = IccCardConstants.State.READY;
    int mPhoneCallingState = TelephonyManager.CALL_STATE_IDLE;
    int mMobileDataNetType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    int mMobileDataConnectionState = TelephonyManager.DATA_DISCONNECTED;
    int mMobileDataActivityStatus = TelephonyManager.DATA_ACTIVITY_NONE;

    private ConnectivityManager mCm = null;
    boolean mMobileDataIsEnabled = false;
    boolean mConfigMobileDataTypeShowBothWifiAndMobileNetwork = false;

    ServiceState mServiceState;
    protected int mServiceStateDataState = ServiceState.STATE_OUT_OF_SERVICE;
    protected int mServiceStateVoiceState = ServiceState.STATE_OUT_OF_SERVICE;
    boolean mConfigShowSignalBarsWhenDataOnlyService = false;
    boolean mConfigShowNetworkTypeWhenVoiceOnlyService = false;
    boolean mConfigShowEmergencyCallsOnlyIcon = false;

    boolean mConfigMobileRoamingDoNotShowRoaming;
    boolean mConfigMobileRoamingShowIconWhenNoDataConnection;

    private boolean mConfigMobileSimDifferingIconForSimError;
    int mNumBarsInSignalIcon = -1; // QHNF37 Set to select active set of signal icons
    private static final int EVENT_SIG_STRENGTH = 8;

    SignalStrength mSignalStrength;        // Class to pass in Radio's raw signal level and active bar count

    int mLastSignalLevel;
    private long mSignalSmoothingLastIconLevelChangeTime = 0;
    private int mSignalSmoothingCurrentShowingIconLevel = -1;
    private boolean mConfigSignalSmoothingSupported;

    TelephonyIcons mTelephonyIcons;

    // Split network status messages ("No service", "Searching for service", "Emergency calls only",
    //       "No internet connection", etc) to a separate label field - overlaying CarrierLabel violates carrier
    //       agreements & certification.
    String mMobileDataNetworkName;           // CarrierLabel (PLMN + SPN)
    String mMobileDataNetworkNameShortForm = sEmptyString;  // CarrierLabel for display in ONSText field of Statusbar
    String mMobileDataNetworkNameDefault;    // "No service" message
    String mMobileDataNetworkNameSeparator;  // Divider (text character) between plmn & spn, when both are shown
    private boolean mMobileDataNetworkNameCachedIsShowSpn;
    private String mMobileDataNetworkNameCachedSpn;
    private boolean mMobileDataNetworkNameCachedIsShowPlmn;
    private String mMobileDataNetworkNameCachedPlmn;
    private String[] mMobileDataNetworkNameShortFormAbbreviationsTable;
    private boolean mConfigMobileNetworkNameShortFormSupported;
    private boolean mConfigMobileNetworkNameSpnHasPriority;
    private boolean mConfigMobileNetworkNameShowBothPlmnAndSpnInExpanded;
    private boolean mConfigMobileNetworkNameShowNoService;
    private boolean mIsMobileNetworkNameShortFormVisible;

    String mMobileDataNetworkInfo;           // ("No service", "Searching for service", "Emergency calls only",
    //                                           "No internet connection", etc)

    boolean mConfigMobileDataTypeShowAtLeastThreeGees = false;
    boolean mConfigMobileDataSignalShowPhoneRSSIForData = false;
    boolean mConfigMobileDataSignalAlwaysShowCdmaRssi = false;

    protected String mMobilePhoneSignalContentDescription;
    protected String mMobileSimContentDescription;
    protected String mMobileRoamingContentDescription;
    protected String mWifiContentDescription;
    protected String mWimaxContentDescription;
    protected String mCombinedSignalContentDescription;
    protected String mMobileDataTypeContentDescription;

    protected String mMobileDataTypeDisabledDescription;
    protected String mMobileDataTypeAttachedDescription;
    protected String mMobileDataTypeSuspendedDescription;

    private final Handler mStatusBarHandler = new StatusBarHandler();

    // wifi
    private WifiManager mWifiManager;
    AsyncChannel mWifiChannel;
    boolean mWifiIsEnabled;
    boolean mWifiIsConnected;
    int mWifiRssi;
    int mWifiLevel;
    String mWifiSsid;
    int mWifiSignalLevelIconId = 0;   // wifi signal strength & condition icon
    int mWifiSignalLevelQSIconId = 0; // wifi signal strength & condition icon
    int mWifiDataActivityIconId = 0;  // overlay arrows for wifi direction
    int mWifiDataActivityStatus = WifiManager.DATA_ACTIVITY_NONE;

    // bluetooth
    private boolean mBluetoothIsTethered = false;
    private int mBluetoothTetherIconId =
        com.android.internal.R.drawable.stat_sys_tether_bluetooth;

    //wimax
    private boolean mConfigWimaxSupported = false;
    private boolean mWimaxIsEnabled = false;
    private boolean mWimaxIsConnected = false;
    private boolean mWimaxIsIdle = false;
    private int mWimaxSignalLevelIconId = 0;
    private int mWimaxSignalLevel = 0;
    private int mWimaxState = 0;
    private int mWimaxExtraState = 0;

    // data connectivity (regardless of state, can we access the internet?)
    // state of inet connection - 0 not connected, 100 connected
    private boolean mIsConnectedToMobileOrWifiOrWimax = false;
    private int mConnectedMobileDataType = ConnectivityManager.TYPE_NONE;
    private String mConnectedMobileDataTypeName;

    // Separate InetCondition for mobile vs wifi vs wimax
    private int mMobileInetCondition = 0;
    private int mWifiInetCondition = 0;
    private int mWimaxInetCondition = 0;
    private static final int INET_CONDITION_THRESHOLD = 50;

    // our ui
    int mMobilePhoneSignalIconId;            // Signal strength component in statusbar SignalCluster
    int mMobilePhoneSignalQSIconId;          // Signal strength component in QuickSettings "rssi" tile
    int mMobileDataSignalIconId;             // used to set tablet combined signal-strength Icon (wifi,mobileData,Bluetooth)
    int mMobileSimIconId;                    // Sim status icon layer in statusbar SignalCluster
    int mMobileRoamingIconId;                // Roaming indicator  in statusbar SignalCluster
    int mMobileRoamingQSIconId;              // Roaming indicator in QuickSettings "rssi" tile
    int mMobileDataTypeIconId;               // MobileDataType (4GLTE, 4G, 3G, G, H, E, 1X, etc) in statusbar SignalCluster
    int mMobileDataTypeQSIconId;             // MobileDataType (4GLTE, 4G, 3G, G, H, E, 1X, etc)
    int mMobileDataTypeDisabledIconId;       // "Disabled-by-user" (used to set mMobileDataTypeIconId)
    int mMobileDataTypeSuspendedIconId;      // Dim Network type when wifi or 1X call in progress
                                             //   (used to set mMobileDataTypeIconId)
    int mMobileDataTypeAttachedIconId;       // Dim Network type when "gprs attached" but not yet connected
                                             //   (used to set mMobileDataTypeIconId)
    int mMobileDataActivityIconId;           // Arrows for data direction in statusbar SignalCluster
    int mMobileDataActivityQSIconId;         // Arrows for data direction in QuickSettings "rssi" tile
    int mMobileDataActivityDisabledIconId;   // "Disabled-by-user" (used to set mMobileDataTypeIconId)
    int mMobileDataActivitySuspendedIconId;  // Dim Network type when wifi or 1X call in progress
                                             //   (used to set mMobileDataTypeIconId)
    int mMobileDataActivityAttachedIconId;   // Dim Network type when "gprs attached" but not yet connected
                                             //   (used to set mMobileDataTypeIconId)

    int mAirplaneModeIconId;                 // AirplaneMode indicator in statusbar SignalCluster
    private boolean mAirplaneModeIsEnabled = false;
    private boolean mLastAirplaneModeIsEnabled = true;

    ArrayList<ImageView> mMobilePhoneSignalIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mMobileSimIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mMobileRoamingIconViews = new ArrayList<ImageView>();

    ArrayList<ImageView> mWifiDataActivityIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mWifiIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mWimaxIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mCombinedSignalIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mMobileDataTypeIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mMobileDataActivityIconViews = new ArrayList<ImageView>();
    ArrayList<TextView> mCombinedLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mMobileLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mMobileLabelShortFormViews = new ArrayList<TextView>();
    ArrayList<TextView> mWifiLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mNoticeTextViews = new ArrayList<TextView>();
    ArrayList<SignalCluster> mSignalClusters = new ArrayList<SignalCluster>();
    ArrayList<NetworkSignalChangedCallback> mSignalsChangedCallbacks =
        new ArrayList<NetworkSignalChangedCallback>();

    int mLastPhoneSignalIconId = -1;
    int mLastMobileSimIconId = -1;
    int mLastMobileRoamingIconId = -1;
    int mLastMobileDataTypeAndActivityIconId = -1;
    int mLastWifiDataActivityIconId = -1;
    int mLastWifiIconId = -1;
    int mLastWimaxIconId = -1;
    int mLastCombinedSignalIconId = -1;
    int mLastMobileDataTypeIconId = -1;
    int mLastMobileDataActivityIconId = -1;
    String mLastCombinedLabel = sEmptyString;
    String mLastMobileLabel = sEmptyString;
    String mLastMobileShortLabel = sEmptyString;

    private boolean mHasMobileDataFeature;

    boolean mDataAndWifiIsStacked = false;

    // yuck -- stop doing this here and put it in the framework
    IBatteryStats mBatteryStats;


    //================================================================================================================

    public interface SignalCluster {

        void setWifiIndicators(
            boolean isVisible,
            int strengthIcon,
            int activityIcon,
            String contentDescription
        );

        void setMobileDataIndicators(
            boolean isVisible,
            int strengthIcon,
            int roamingIcon,
            int simIcon,
            int activityIcon,
            int typeIcon,
            String contentDescription,
            String typeContentDescription,
            String roamingDescription,
            String simDescription
        );

        void setIsAirplaneMode(
            boolean is,
            int airplaneIcon
        );

    }


    //================================================================================================================

    public interface NetworkSignalChangedCallback {

        void onWifiSignalChanged(
            boolean  isEnabled,
            int      wifiSignalIconId,
            String   wifiSignalContentDescription,
            String   description
        );

        void onMobileDataSignalChanged(
            boolean  isEnabled,
            int      mobileSignalIconId,
            String   mobileSignalContentDescription,
            int      dataTypeIconId,
            String   dataTypeContentDescription,
            String   description,
            int      mobileRoamingIconId,
            int      dataActivityIconId
            // Note: QuickSettings Rssi tile does not show Sim Status.
        );

        void onAirplaneModeChanged(
            boolean  isEnabled
        );

    }

    //================================================================================================================

    /**
     * Construct this controller object and register for updates.
     */
    public NetworkController(Context context) {

        mContext = context;
        final Resources res = context.getResources();

        mTelephonyIcons = new TelephonyIcons(mContext);

        mCm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mHasMobileDataFeature = mCm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);
        mMobileDataIsEnabled = mCm.getMobileDataEnabled();

        loadConfigOptions();

        // set up the default wifi icon, used when no radios have ever appeared
        updateWifiIcons();

        if(mConfigWimaxSupported) {
            updateWimaxIcons();
        }

        // telephony
        registerPhoneStateListener(context);



        mMobileDataNetworkName = sEmptyString; // Do not set to "No service" here.
        mMobileDataNetworkNameShortForm = sEmptyString;
        mIsMobileNetworkNameShortFormVisible = false;

        mMobileDataNetworkNameCachedIsShowSpn = false;
        mMobileDataNetworkNameCachedSpn = null;
        mMobileDataNetworkNameCachedIsShowPlmn = false;
        mMobileDataNetworkNameCachedPlmn = null;

        updateMobileNetworkName(false, null, false, null);

        // wifi
        createWifiHandler();

        // broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(ConnectivityManager.INET_CONDITION_ACTION);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        if(mConfigWimaxSupported) {
            filter.addAction(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION);
            filter.addAction(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION);
            filter.addAction(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION);
        }
        context.registerReceiver(this, filter);

        // AIRPLANE_MODE_CHANGED is sent at boot; we've probably already missed it
        updateAirplaneMode();

        // yuck
        mBatteryStats = BatteryStatsService.getService();
    }

    //================================================================================================================

    protected void createWifiHandler() {
        // wifi
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        Handler handler = new WifiHandler();
        mWifiChannel = new AsyncChannel();
        Messenger wifiMessenger = mWifiManager.getWifiServiceMessenger();
        if (wifiMessenger != null) {
            mWifiChannel.connect(
                mContext,
                handler,
                wifiMessenger
            );
        }
    }

    //================================================================================================================

    public boolean hasMobileDataFeature() {
        return mHasMobileDataFeature;
    }

    //================================================================================================================

    private void loadConfigOptions() {

        final Resources res = mContext.getResources();

        mConfigMobileDataSignalShowPhoneRSSIForData = res.getBoolean(
            R.bool.config_showPhoneRSSIForData
        );
        mConfigMobileDataSignalAlwaysShowCdmaRssi = res.getBoolean(
            com.android.internal.R.bool.config_alwaysUseCdmaRssi
        );
        mConfigMobileDataTypeShowAtLeastThreeGees = res.getBoolean(
            R.bool.config_showMin3G
        );

        mConfigWimaxSupported = res.getBoolean(
            com.android.internal.R.bool.config_wimaxEnabled
        );

        mConfigMobileRoamingDoNotShowRoaming = res.getBoolean(
            com.android.internal.R.bool.config_no_roaming_icon
        );
        mConfigMobileRoamingShowIconWhenNoDataConnection = res.getBoolean(
            com.android.internal.R.bool.config_show_roaming_icon_when_roaming_but_no_data
        );
        mConfigMobileDataTypeHspaDataDistinguishable = res.getBoolean(
            R.bool.config_hspa_data_distinguishable
        );
        mConfigMobileDataTypeHspapDataDistinguishable = res.getBoolean(
            R.bool.config_hspap_data_distinguishable
        );

        // config flag to determine if to display NetworkType "attached" icon
        mConfigMobileDataTypeShowConnectingOrAttachedIcon = res.getBoolean(
            R.bool.config_show_gprs_attached_icon
            );

        // config flag to determine if to display NetworkType "disabled" icon
        mConfigMobileDataTypeShowDataDisabledIcon = res.getBoolean(
            R.bool.config_show_data_disabled_icon
            );

        // Support carrier-specific NetworkType icon sets
        mConfigOperatorName_string = res.getString(
            com.android.internal.R.string.config_operator_name
        );

        mConfigMobileDataTypeShowBothWifiAndMobileNetwork = res.getBoolean(
            R.bool.config_show_both_wifi_and_mobile_network
        );

        mConfigShowSignalBarsWhenDataOnlyService = res.getBoolean(
            R.bool.config_show_signal_bars_when_data_only_service
        );
        mConfigShowNetworkTypeWhenVoiceOnlyService = res.getBoolean(
            R.bool.config_show_network_type_when_voice_only_service
        );
        mConfigShowEmergencyCallsOnlyIcon = res.getBoolean(
            R.bool.config_show_emergency_calls_only_icon
        );
        mConfigSignalSmoothingSupported = res.getBoolean(
            R.bool.config_signal_strength_smoothing
        );
        mConfigMobileSimDifferingIconForSimError = res.getBoolean(
            R.bool.config_differing_icon_for_sim_error
        );

        // Carrier name (both in statusbar and on notification panel)
        mConfigMobileNetworkNameShortFormSupported = res.getBoolean(
            R.bool.config_show_carrier_name_in_statusbar
        );
        mConfigMobileNetworkNameSpnHasPriority = res.getBoolean(
            R.bool.config_spn_priority_over_plmn
        );
        mConfigMobileNetworkNameShowNoService = res.getBoolean(
            R.bool.config_display_no_service_text
        );
        mMobileDataNetworkNameShortFormAbbreviationsTable = res.getStringArray(
            R.array.config_carrier_label_abbreviated_names_table
        );
        mMobileDataNetworkNameSeparator = mContext.getString(
            R.string.status_bar_network_name_separator
        );
        mMobileDataNetworkNameDefault = mContext.getString(
            com.android.internal.R.string.lockscreen_carrier_default
        );

        if (DEBUG) {
            Slog.d(TAG,
                "loadConfigOptions: config_showPhoneRSSIForData="
                + mConfigMobileDataSignalShowPhoneRSSIForData
            );
            Slog.d(TAG,
                "loadConfigOptions: config_alwaysUseCdmaRssi="
                + mConfigMobileDataSignalAlwaysShowCdmaRssi
            );
            Slog.d(TAG,
                "loadConfigOptions: config_showMin3G="
                + mConfigMobileDataTypeShowAtLeastThreeGees
            );
            Slog.d(TAG,
                "loadConfigOptions: config_wimaxEnabled="
                + mConfigWimaxSupported
            );
            Slog.d(TAG,
                "loadConfigOptions: config_no_roaming_icon="
                + mConfigMobileRoamingDoNotShowRoaming
            );
            Slog.d(TAG,
                "loadConfigOptions: config_show_roaming_icon_when_roaming_but_no_data="
                + mConfigMobileRoamingShowIconWhenNoDataConnection
            );
            Slog.d(TAG,
                "loadConfigOptions: config_hspa_data_distinguishable="
                + mConfigMobileDataTypeHspaDataDistinguishable
            );
            Slog.d(TAG,
                "loadConfigOptions: config_hspap_data_distinguishable="
                + mConfigMobileDataTypeHspapDataDistinguishable
            );
            Slog.d(TAG,
                "loadConfigOptions: config_show_gprs_attached_icon="
                + mConfigMobileDataTypeShowConnectingOrAttachedIcon
            );
            Slog.d(TAG,
                "loadConfigOptions: config_show_data_disabled_icon="
                + mConfigMobileDataTypeShowDataDisabledIcon
            );
            Slog.d(TAG,
                "loadConfigOptions: mConfigOperatorName_string=\""
                + mConfigOperatorName_string +"\""
            );
            Slog.d(TAG,
                "loadConfigOptions: config_show_both_wifi_and_mobile_network="
                + mConfigMobileDataTypeShowBothWifiAndMobileNetwork
            );
            Slog.d(TAG,
                "loadConfigOptions: config_show_signal_bars_when_data_only_service="
                + mConfigShowSignalBarsWhenDataOnlyService
            );
            Slog.d(TAG,
                "loadConfigOptions: config_show_network_type_when_voice_only_service="
                + mConfigShowNetworkTypeWhenVoiceOnlyService
            );
            Slog.d(TAG,
                "loadConfigOptions: config_show_emergency_calls_only_icon="
                + mConfigShowEmergencyCallsOnlyIcon
            );
            Slog.d(TAG,
                "loadConfigOptions: config_signal_strength_smoothing="
                + mConfigSignalSmoothingSupported
            );
            Slog.d(TAG,
                "loadConfigOptions: mConfigMobileSimDifferingIconForSimError="
                + mConfigMobileSimDifferingIconForSimError
            );
            Slog.d(TAG,
                "loadConfigOptions: config_show_carrier_name_in_statusbar="
                + mConfigMobileNetworkNameShortFormSupported
            );
            Slog.d(TAG,
                "loadConfigOptions: config_spn_priority_over_plmn="
                + mConfigMobileNetworkNameSpnHasPriority
            );
            Slog.d(TAG,
                "loadConfigOptions: config_display_no_service_text="
                + mConfigMobileNetworkNameShowNoService
            );
            Slog.d(TAG,
                "loadConfigOptions: config_carrier_label_abbreviated_names_table:"
            );
            if( mMobileDataNetworkNameShortFormAbbreviationsTable.length != 0 ) {
                for (
                    int iDebugIndex = 0;
                    iDebugIndex < mMobileDataNetworkNameShortFormAbbreviationsTable.length;
                    iDebugIndex += 2
                ) {
                    Slog.d(TAG,
                        "   [" + (iDebugIndex/2) + "]: map \""
                        + mMobileDataNetworkNameShortFormAbbreviationsTable[iDebugIndex]
                        + "\" to \""
                        + mMobileDataNetworkNameShortFormAbbreviationsTable[iDebugIndex+1]
                    );
                }
            } else {
                    Slog.d(TAG, "   (array is empty)" );
            }
            Slog.d(TAG,
                "loadConfigOptions: status_bar_network_name_separator=\""
                + mMobileDataNetworkNameSeparator +"\""
            );
            Slog.d(TAG,
                "loadConfigOptions: lockscreen_carrier_default=\""
                + mMobileDataNetworkNameDefault +"\""
            );
        }
    }

    //================================================================================================================

    protected void registerPhoneStateListener(Context context) {
        // telephony
        mPhone = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        mPhone.listen(
            mPhoneStateListener,
            (
                PhoneStateListener.LISTEN_SERVICE_STATE
                | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                | PhoneStateListener.LISTEN_CALL_STATE
                | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                | PhoneStateListener.LISTEN_DATA_ACTIVITY
            )
        );
    }

    //================================================================================================================

    public void addPhoneSignalIconView(ImageView v) {
        mMobilePhoneSignalIconViews.add(v);
    }

    //================================================================================================================

    //COMPATIBILITY
    public void addRoamingIconView(ImageView v) {
        mMobileRoamingIconViews.add(v);
    }

    public void addMobileRoamingIconView(ImageView v) {
        mMobileRoamingIconViews.add(v);
    }

    //================================================================================================================

    public void addMobileSimIconView(ImageView v) {
        mMobileSimIconViews.add(v);
    }

    //================================================================================================================

    //COMPATIBILITY
    //public void addDataDirectionOverlayIconView(ImageView v) {
    //    mWifiDataActivityIconViews.add(v);  // <--- Remapped
    //}

    public void addWifiDataActivityIconView(ImageView v) {
        mWifiDataActivityIconViews.add(v);
    }

    //================================================================================================================

    public void addMobileDataActivityIconView(ImageView v) {
        mMobileDataActivityIconViews.add(v);
    }

    //================================================================================================================

    public void addWifiIconView(ImageView v) {
        mWifiIconViews.add(v);
    }

    //================================================================================================================

    public void addWimaxIconView(ImageView v) {
        mWimaxIconViews.add(v);
    }

    //================================================================================================================

    public void addCombinedSignalIconView(ImageView v) {
        mCombinedSignalIconViews.add(v);
    }

    //================================================================================================================

    //COMPATIBILITY
    public void addDataTypeIconView(ImageView v) {
        mMobileDataTypeIconViews.add(v);
    }

    public void addMobileDataTypeIconView(ImageView v) {
        mMobileDataTypeIconViews.add(v);
    }

    //================================================================================================================

    public void addCombinedLabelView(TextView v) {
        mCombinedLabelViews.add(v);
    }

    //================================================================================================================

    public void addMobileLabelView(TextView v) {
        mMobileLabelViews.add(v);
    }

    //================================================================================================================

    public void addMobileLabelShortFormView(TextView v) {
        mMobileLabelShortFormViews.add(v);
    }

    //================================================================================================================

    public void addWifiLabelView(TextView v) {
        mWifiLabelViews.add(v);
    }

    //================================================================================================================

    public void addEmergencyLabelView(TextView v) {
        mNoticeTextViews.add(v);
    }

    //================================================================================================================

    public void addSignalCluster(SignalCluster cluster) {
        mSignalClusters.add(cluster);
        refreshSignalCluster(cluster);
    }

    //================================================================================================================


    public void addNetworkSignalChangedCallback(NetworkSignalChangedCallback cb) {
        mSignalsChangedCallbacks.add(cb);
        notifySignalsChangedCallbacks(cb);
    }

    //================================================================================================================

    public void refreshSignalCluster(SignalCluster cluster) {
        long currTime = SystemClock.elapsedRealtime();

        cluster.setWifiIndicators(
            // only show wifi in the cluster if connected or if wifi-only
            (
                mWifiIsEnabled
                &&
                (
                    mWifiIsConnected
                    ||
                    ( ! mHasMobileDataFeature )
                )
            ),
            mWifiSignalLevelIconId,
            mWifiDataActivityIconId,
            mWifiContentDescription
        );

         if (INFO) {
            if(
                DEBUG
                ||
                mLoggerWifiIconId != mWifiSignalLevelIconId
                // Do not log each data activity (arrows) change
                // ||
                // mLoggerWifiDataActivityIconId != mWifiDataActivityIconId
                ||
                currTime > mLoggerWifiIntervalTimestamp
            ) {
                Slog.i(TAG,
                    "refreshSignalCluster: wifi: mWifiIsConnected="
                    + mWifiIsConnected
                    + " Wifi="
                    + ((DEBUG) ? Integer.toHexString(mWifiSignalLevelIconId) : sEmptyString)
                    + ((DEBUG) ? "/" : sEmptyString)
                    + getResourceName(mWifiSignalLevelIconId)
                    + " Activity="
                    + ((DEBUG) ? Integer.toHexString(mWifiDataActivityIconId) : sEmptyString)
                    + ((DEBUG) ? "/" : sEmptyString)
                    + getResourceName(mWifiDataActivityIconId)
                    + " Accessibility="
                    + "\"" + mWifiContentDescription + "\""
                );
                mLoggerWifiIntervalTimestamp = currTime + LOGGER_REQUIRED_REPORTING_INTERVAL;
                mLoggerWifiIconId = mWifiSignalLevelIconId;
                mLoggerWifiDataActivityIconId = mWifiDataActivityIconId;
            }
        }
        if (
            mConfigWimaxSupported
            &&
            mWimaxIsEnabled
            &&
            mWimaxIsConnected
        ) {
            // wimax is special
            cluster.setMobileDataIndicators(
                true, // mHasMobileDataFeature,
                (
                    mConfigMobileDataSignalAlwaysShowCdmaRssi
                    ? mMobilePhoneSignalIconId
                    : mWimaxSignalLevelIconId
                ),
                mMobileRoamingIconId,
                mMobileSimIconId,
                mMobileDataActivityIconId,
                mMobileDataTypeIconId,
                mWimaxContentDescription,
                mMobileDataTypeContentDescription,
                mMobileRoamingContentDescription,
                mMobileSimContentDescription
            );

            if (INFO) {
                if(
                    DEBUG
                    ||
                    mLoggerWimaxIconId != (
                        mConfigMobileDataSignalAlwaysShowCdmaRssi
                        ? mMobilePhoneSignalIconId
                        : mWimaxSignalLevelIconId
                    )
                    ||
                    mLoggerWimaxRoamingIconId != mMobileRoamingIconId
                    ||
                    mLoggerWimaxSimIconId != mMobileSimIconId
                    ||
                    currTime > mLoggerWimaxSignalIntervalTimestamp
                ) {
                    Slog.i(TAG,
                        "refreshSignalCluster: wimax: Signal="
                        + (
                            (DEBUG)
                            ? Integer.toHexString(
                                ( mConfigMobileDataSignalAlwaysShowCdmaRssi )
                                ? mMobilePhoneSignalIconId
                                : mWimaxSignalLevelIconId
                            )
                            : sEmptyString
                        )
                        + (
                            (DEBUG)
                            ? "/"
                            : sEmptyString
                        )
                        + getResourceName(
                            ( mConfigMobileDataSignalAlwaysShowCdmaRssi )
                            ? mMobilePhoneSignalIconId
                            : mWimaxSignalLevelIconId
                        )
                        + " Roaming="
                        + ((DEBUG) ? Integer.toHexString(mMobileRoamingIconId) : sEmptyString)
                        + ((DEBUG) ? "/" : sEmptyString)
                        + getResourceName(mMobileRoamingIconId)
                        + " Sim="
                        + ((DEBUG) ? Integer.toHexString(mMobileSimIconId) : sEmptyString)
                        + ((DEBUG) ? "/" : sEmptyString)
                        + getResourceName(mMobileSimIconId)
                        + " Accessibility="
                        + "\"" + mWimaxContentDescription + "\""
                        + ",\"" +  mMobileRoamingContentDescription  + "\""
                        + ",\"" + mMobileSimContentDescription + "\""
                    );
                    mLoggerWimaxSignalIntervalTimestamp = currTime + LOGGER_REQUIRED_REPORTING_INTERVAL;
                    mLoggerWimaxIconId = (
                        ( mConfigMobileDataSignalAlwaysShowCdmaRssi )
                        ? mMobilePhoneSignalIconId
                        : mWimaxSignalLevelIconId
                    );
                    mLoggerWimaxRoamingIconId = mMobileRoamingIconId;
                    mLoggerWimaxSimIconId = mMobileSimIconId;
                }
                if(
                    DEBUG
                    ||
                    mLoggerWimaxDataTypeIconId != mMobileDataTypeIconId
                    ||
                    mLoggerWimaxActivityIconId != mMobileDataActivityIconId
                    ||
                    currTime > mLoggerWimaxDataIntervalTimestamp
                ) {
                    Slog.i(TAG,
                        "refreshSignalCluster: wimax: mHasMobileDataFeature=true"
                        + " DataTypeShown="
                        + ((DEBUG) ? Integer.toHexString(mMobileDataTypeIconId) : sEmptyString)
                        + ((DEBUG) ? "/" : sEmptyString)
                        + getResourceName(mMobileDataTypeIconId)
                        + " Activity="
                        + ((DEBUG) ? Integer.toHexString(mMobileDataActivityIconId) : sEmptyString)
                        + ((DEBUG) ? "/" : sEmptyString)
                        + getResourceName(mMobileDataActivityIconId)
                        + " Accessibility="
                        + "\"" + mMobileDataTypeContentDescription + "\""
                    );
                    mLoggerWimaxDataIntervalTimestamp = currTime + LOGGER_REQUIRED_REPORTING_INTERVAL;
                    mLoggerWimaxDataTypeIconId = mMobileDataTypeIconId;
                    mLoggerWimaxActivityIconId = mMobileDataActivityIconId;
                }
            }
        } else {
            // normal mobile data
            cluster.setMobileDataIndicators(
                mHasMobileDataFeature,
                (
                    ( mConfigMobileDataSignalShowPhoneRSSIForData )
                    ? mMobilePhoneSignalIconId
                    : mMobileDataSignalIconId
                ),
                mMobileRoamingIconId,
                mMobileSimIconId,
                mMobileDataActivityIconId,
                mMobileDataTypeIconId,
                mMobilePhoneSignalContentDescription,
                mMobileDataTypeContentDescription,
                mMobileRoamingContentDescription,
                mMobileSimContentDescription
            );

            if (INFO) {
                if(
                    DEBUG
                    ||
                    mLoggerMobileIconId != (
                        ( mConfigMobileDataSignalAlwaysShowCdmaRssi )
                        ? mMobilePhoneSignalIconId
                        : mMobileDataSignalIconId
                    )
                    ||
                    mLoggerMobileRoamingIconId != mMobileRoamingIconId
                    ||
                    mLoggerMobileSimIconId != mMobileSimIconId
                    ||
                    currTime > mLoggerMobileSignalIntervalTimestamp
                ) {
                    Slog.i(TAG,
                        "refreshSignalCluster: mobile: Signal="
                        + (
                            (DEBUG)
                            ? Integer.toHexString(
                                ( mConfigMobileDataSignalShowPhoneRSSIForData )
                                ? mMobilePhoneSignalIconId
                                : mMobileDataSignalIconId
                            )
                            : sEmptyString
                        )
                        + ((DEBUG) ? "/" : sEmptyString)
                        + getResourceName(
                            ( mConfigMobileDataSignalShowPhoneRSSIForData )
                            ? mMobilePhoneSignalIconId
                            : mMobileDataSignalIconId
                        )
                        + " Roaming="
                        + ((DEBUG) ? Integer.toHexString(mMobileRoamingIconId) : sEmptyString)
                        + ((DEBUG) ? "/" : sEmptyString)
                        + getResourceName(mMobileRoamingIconId)
                        + " Sim="
                        + ((DEBUG) ? Integer.toHexString(mMobileSimIconId) : sEmptyString)
                        + ((DEBUG) ? "/" : sEmptyString)
                        + getResourceName(mMobileSimIconId)
                        + " Accessibility="
                        + "\"" + mMobilePhoneSignalContentDescription + "\""
                        + ",\"" + mMobileRoamingContentDescription + "\""
                        + ",\"" + mMobileSimContentDescription + "\""
                    );
                    mLoggerMobileSignalIntervalTimestamp = currTime + LOGGER_REQUIRED_REPORTING_INTERVAL;
                    mLoggerMobileIconId = (
                        ( mConfigMobileDataSignalAlwaysShowCdmaRssi )
                        ? mMobilePhoneSignalIconId
                        : mMobileDataSignalIconId
                    );
                    mLoggerMobileRoamingIconId = mMobileRoamingIconId;
                    mLoggerMobileSimIconId = mMobileSimIconId;
                }
                if(
                    DEBUG
                    ||
                    mLoggerMobileDataTypeIconId != mMobileDataTypeIconId
                    ||
                    mLoggerMobileActivityIconId != mMobileDataActivityIconId
                    ||
                    currTime > mLoggerMobileDataIntervalTimestamp
                ) {
                    Slog.i(TAG,
                        "refreshSignalCluster: mobile: mHasMobileDataFeature="
                        + mHasMobileDataFeature
                        + " DataTypeShown="
                        + ((DEBUG) ? Integer.toHexString(mMobileDataTypeIconId) : sEmptyString)
                        + ((DEBUG) ? "/" : sEmptyString)
                        + getResourceName(mMobileDataTypeIconId)
                        + " Activity="
                        + ((DEBUG) ? Integer.toHexString(mMobileDataActivityIconId) : sEmptyString)
                        + ((DEBUG) ? "/" : sEmptyString)
                        + getResourceName(mMobileDataActivityIconId)
                        + " Accessibility="
                        + "\"" + mMobileDataTypeContentDescription + "\""
                    );
                    mLoggerMobileDataIntervalTimestamp = currTime + LOGGER_REQUIRED_REPORTING_INTERVAL;
                    mLoggerMobileDataTypeIconId = mMobileDataTypeIconId;
                    mLoggerMobileActivityIconId = mMobileDataActivityIconId;
                }
            }
        }

        cluster.setIsAirplaneMode(
            mAirplaneModeIsEnabled,
            mAirplaneModeIconId
        );
    }

    //================================================================================================================

    void notifySignalsChangedCallbacks(NetworkSignalChangedCallback cb) {

       // only show wifi in the cluster if connected or if wifi-only
        boolean wifiEnabled = (
            mWifiIsEnabled
            &&
            (
                mWifiIsConnected
                ||
                ( ! mHasMobileDataFeature )
            )
        );

        String wifiDesc =
            ( wifiEnabled )
            ? mWifiSsid
            : null;

        cb.onWifiSignalChanged(
            wifiEnabled,
            mWifiSignalLevelQSIconId,
            mWifiContentDescription,
            wifiDesc
        );

        if (isEmergencyOnly()) {
            cb.onMobileDataSignalChanged(
                false,
                mMobilePhoneSignalQSIconId,
                mMobilePhoneSignalContentDescription,
                mMobileDataTypeQSIconId,
                mMobileDataTypeContentDescription,
                null,
                mMobileRoamingQSIconId,
                mMobileDataActivityQSIconId
            );
        } else {
            if (mWimaxIsEnabled && mWimaxIsConnected) {
                // Wimax is special
                cb.onMobileDataSignalChanged(
                    true,
                    mWimaxSignalLevelIconId,
                    mMobilePhoneSignalContentDescription,
                    mMobileDataTypeQSIconId,
                    mWimaxContentDescription,
                    mMobileDataNetworkName,
                    mMobileRoamingQSIconId,
                    mMobileDataActivityQSIconId
                );
            } else {
                // Normal mobile data
                cb.onMobileDataSignalChanged(
                    mHasMobileDataFeature,
                    mMobilePhoneSignalQSIconId,
                    mMobilePhoneSignalContentDescription,
                    mMobileDataTypeQSIconId,
                    mMobileDataTypeContentDescription,
                    mMobileDataNetworkName,
                    mMobileRoamingQSIconId,
                    mMobileDataActivityQSIconId
                );
            }
        }
        cb.onAirplaneModeChanged(mAirplaneModeIsEnabled);
    }

    //================================================================================================================

    public void setStackedMode(boolean stacked) {
        mDataAndWifiIsStacked = true;  // NOTE: input parameter "stacked" is IGNORED, forced to true
    }

    //================================================================================================================

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if ( action.equals(WifiManager.RSSI_CHANGED_ACTION) ) {

            if (INFO) Slog.i(TAG, "onReceive: WifiManager.RSSI_CHANGED_ACTION Received");

            updateWifiState(intent);
            refreshViews();

        } else if ( action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION) ) {

            if (INFO) Slog.i(TAG, "onReceive: WifiManager.WIFI_STATE_CHANGED_ACTION Received");

            updateWifiState(intent);
            refreshViews();

        } else if ( action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION) ) {

            if (INFO) Slog.i(TAG, "onReceive: WifiManager.NETWORK_STATE_CHANGED_ACTION Received" );

            updateWifiState(intent);
            refreshViews();


        } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {

            if (INFO) Slog.i(TAG, "onReceive: TelephonyIntents.ACTION_SIM_STATE_CHANGED) Received");

            updateMobileSimState(intent);
            refreshViews();

        } else if (action.equals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION)) {

            if (INFO) Slog.i(TAG, "onReceive: TelephonyIntents.SPN_STRINGS_UPDATED_ACTION Received");

            mMobileDataNetworkNameCachedIsShowSpn = intent.getBooleanExtra(
                TelephonyIntents.EXTRA_SHOW_SPN,
                false
            );
            mMobileDataNetworkNameCachedSpn = intent.getStringExtra(
                TelephonyIntents.EXTRA_SPN
            );
            mMobileDataNetworkNameCachedIsShowPlmn = intent.getBooleanExtra(
                TelephonyIntents.EXTRA_SHOW_PLMN,
                false
            );
            mMobileDataNetworkNameCachedPlmn = intent.getStringExtra(
                TelephonyIntents.EXTRA_PLMN
            );
            updateMobileNetworkName(
                mMobileDataNetworkNameCachedIsShowSpn,
                mMobileDataNetworkNameCachedSpn,
                mMobileDataNetworkNameCachedIsShowPlmn,
                mMobileDataNetworkNameCachedPlmn
            );
            refreshViews();

        } else if ( action.equals(ConnectivityManager.CONNECTIVITY_ACTION) ) {

            if (INFO) Slog.i(TAG, "onReceive: ConnectivityManager.CONNECTIVITY_ACTION Received");

            updateConnectivityStatus(intent);
            refreshViews();

        } else if ( action.equals(ConnectivityManager.INET_CONDITION_ACTION) ) {

            if (INFO) Slog.i(TAG, "onReceive: ConnectivityManager.INET_CONDITION_ACTION Received");

            updateConnectivityStatus(intent);
            refreshViews();

        } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {

            if (INFO) Slog.i(TAG, "onReceive: Intent.ACTION_CONFIGURATION_CHANGED Received");

            loadConfigOptions();

            refreshViews();

        } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {

            if (INFO) Slog.i(TAG, "onReceive: Intent.ACTION_AIRPLANE_MODE_CHANGED Received");

            updateAirplaneMode();
            refreshViews();

        } else if ( action.equals(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION) ) {

            if (INFO) Slog.i(TAG, "onReceive: WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION Received");

            updateWimaxState(intent);
            refreshViews();

        } else if ( action.equals(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION) ) {

            if (INFO) Slog.i(TAG, "onReceive: WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION Received");

            updateWimaxState(intent);
            refreshViews();

        } else if ( action.equals(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION) ) {

            if (INFO) Slog.i(TAG, "onReceive: WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION Received");

            updateWimaxState(intent);
            refreshViews();

        }
    }

    //================================================================================================================
    // ===== Telephony ===============================================================================================
    //================================================================================================================

    PhoneStateListener mPhoneStateListener = new PhoneStateListener() {

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {

            long currTime = SystemClock.elapsedRealtime();

            if (INFO) {
                int level = (
                    (signalStrength == null)
                    ? (-2) // differs from -1
                    : signalStrength.getLevel()
                );

                if(
                    DEBUG
                    ||
                    mLoggerOnSignalStrengthLevel != level
                    ||
                    currTime > mLoggerOnSignalStrengthIntervalTimestamp
                ) {
                    Slog.i(TAG,
                        "onSignalStrengthsChanged " + signalStrength
                        + (
                            (signalStrength == null)
                            ? "signalStrength=(null), level unavailable"
                            : (" level=" + signalStrength.getLevel() )
                        )
                    );
                    mLoggerOnSignalStrengthLevel = level;
                    mLoggerOnSignalStrengthIntervalTimestamp = currTime + LOGGER_REQUIRED_REPORTING_INTERVAL;
                }
            }

            boolean newDataEnabledState = mCm.getMobileDataEnabled();

            if(newDataEnabledState != mMobileDataIsEnabled) {
                mMobileDataIsEnabled = newDataEnabledState;
                updateMobileDataTypeInfo();
            }

            mSignalStrength = signalStrength;

            refreshViews();
        }

        //================================================================================================================

        @Override
        public void onServiceStateChanged(ServiceState state) {
            if (INFO) {
                Slog.i(TAG,
                "onServiceStateChanged state=" + state
                + " VoiceState=" + state.getState() + "=" +
                    ( (state.getState()==ServiceState.STATE_IN_SERVICE) ? "STATE_IN_SERVICE" :
                    ( (state.getState()==ServiceState.STATE_OUT_OF_SERVICE) ? "STATE_OUT_OF_SERVICE" :
                    ( (state.getState()==ServiceState.STATE_EMERGENCY_ONLY) ? "STATE_EMERGENCY_ONLY" :
                    ( (state.getState()==ServiceState.STATE_POWER_OFF) ? "STATE_POWER_OFF" : "unknown"
                    ) ) ) )
                + " DataState=" + state.getDataServiceState() + "=" +
                    ( (state.getDataServiceState()==ServiceState.STATE_IN_SERVICE) ? "STATE_IN_SERVICE" :
                    ( (state.getDataServiceState()==ServiceState.STATE_OUT_OF_SERVICE) ? "STATE_OUT_OF_SERVICE" :
                    ( (state.getDataServiceState()==ServiceState.STATE_EMERGENCY_ONLY) ? "STATE_EMERGENCY_ONLY" :
                    ( (state.getDataServiceState()==ServiceState.STATE_POWER_OFF) ? "STATE_POWER_OFF" : "unknown"
                    ) ) ) )
                );
            }

            mServiceState = state;

            // For Europe & tablets, use data state to set data icons

            int serviceStateMobileDataType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            if ( mServiceState != null ) {
                mServiceStateVoiceState = mServiceState.getState();
                mServiceStateDataState = mServiceState.getDataServiceState();
                serviceStateMobileDataType = mServiceState.getNetworkType();
            } else {
                mServiceStateVoiceState = ServiceState.STATE_OUT_OF_SERVICE;
                mServiceStateDataState = ServiceState.STATE_OUT_OF_SERVICE;
                serviceStateMobileDataType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            }

            if( DEBUG ) {
                Slog.v(TAG,
                    "onServiceStateChanged: mServiceState=" + mServiceState
                );
                Slog.d(TAG, "onServiceStateChanged: serviceStateMobileDataType="
                    + serviceStateMobileDataType + "="
                    + "\"" + TelephonyManager.getNetworkTypeName(serviceStateMobileDataType) + "\""
                );
            }

            mMobileDataNetType = serviceStateMobileDataType;

            updateMobileNetworkName(
                mMobileDataNetworkNameCachedIsShowSpn,
                mMobileDataNetworkNameCachedSpn,
                mMobileDataNetworkNameCachedIsShowPlmn,
                mMobileDataNetworkNameCachedPlmn
            );

            refreshViews();
        }

        //================================================================================================================

        @Override
        public void onCallStateChanged(
            int state,
            String incomingNumber
        ) {
            if (INFO) {
                Slog.i(TAG,
                    "onCallStateChanged state=" + state
                    + " incomingNumber=\"" + incomingNumber + "\""
                );
            }

            mPhoneCallingState = state;

            refreshViews();
        }

        //================================================================================================================

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            if (INFO) {
                Slog.i(TAG,
                    "onDataConnectionStateChanged: state=" + state
                    + " networkType=" + networkType
                );
            }
            mMobileDataConnectionState = state;
            mMobileDataNetType = networkType;

            if (DEBUG) {
                Slog.d(TAG, "onDataConnectionStateChanged: mMobileDataConnectionState="
                    + mMobileDataConnectionState + "=" +
                    ( (mMobileDataConnectionState==TelephonyManager.DATA_UNKNOWN) ? "DATA_UNKNOWN" :
                    ( (mMobileDataConnectionState==TelephonyManager.DATA_DISCONNECTED) ? "DATA_DISCONNECTED" :
                    ( (mMobileDataConnectionState==TelephonyManager.DATA_CONNECTING) ? "DATA_CONNECTING" :
                    ( (mMobileDataConnectionState==TelephonyManager.DATA_CONNECTED) ? "DATA_CONNECTED" :
                    ( (mMobileDataConnectionState==TelephonyManager.DATA_SUSPENDED) ? "DATA_SUSPENDED" : "unknown"
                    ) ) ) ) )
                );
                Slog.d(TAG, "onDataConnectionStateChanged: mMobileDataNetType="
                    + mMobileDataNetType + "="
                    + "\"" + TelephonyManager.getNetworkTypeName(networkType) + "\""
                );
                int serviceStateMobileDataType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
                if ( mServiceState != null ) {
                    serviceStateMobileDataType = mServiceState.getNetworkType();
                }
                Slog.d(TAG, "onDataConnectionStateChanged: serviceStateMobileDataType="
                    + serviceStateMobileDataType + "="
                    + "\"" + TelephonyManager.getNetworkTypeName(serviceStateMobileDataType) + "\""
                );
                if ( serviceStateMobileDataType != mMobileDataNetType ) {
                    Slog.i(TAG,
                        "onDataConnectionStateChanged: WARNING -----> mMobileDataNetType != serviceStateMobileDataType"
                    );
                }

            }

            refreshViews();
        }


        //================================================================================================================

        @Override
        public void onDataActivity(
            int direction
        ) {
            if (INFO) {
                Slog.i(TAG, "onDataActivity: direction=" + direction);
            }
            mMobileDataActivityStatus = direction;

            refreshViews();
        }
    };


    //================================================================================================================
    //================================================================================================================


    private final void updateMobileSimState(
        Intent intent
    ) {
        String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
            mMobileSimState = IccCardConstants.State.ABSENT;
        } else if (IccCardConstants.INTENT_VALUE_ICC_IO_ERROR.equals(stateExtra)) {
            mMobileSimState = IccCardConstants.State.IO_ERROR;
        } else if (
            IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)
            ||
            IccCardConstants.INTENT_VALUE_ICC_IMSI.equals(stateExtra)
            ||
            IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(stateExtra)
        ) {
            mMobileSimState = IccCardConstants.State.READY;
        } else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
            final String lockedReason = intent.getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
            if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                mMobileSimState = IccCardConstants.State.PIN_REQUIRED;
            } else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                mMobileSimState = IccCardConstants.State.PUK_REQUIRED;
            } else {
                mMobileSimState = IccCardConstants.State.NETWORK_LOCKED;
            }
        } else if (IccCardConstants.INTENT_VALUE_ICC_IO_ERROR.equals(stateExtra)) {
           mMobileSimState = IccCardConstants.State.IO_ERROR;
        } else {
            mMobileSimState = IccCardConstants.State.UNKNOWN;
        }

        if (INFO) {
            Slog.i(TAG,
                "updateMobileSimState: mMobileSimState=" + mMobileSimState + "=" +
                ( (mMobileSimState == IccCardConstants.State.ABSENT) ? "IccCardConstants.State.ABSENT" :
                ( (mMobileSimState == IccCardConstants.State.READY) ? "IccCardConstants.State.READY" :
                ( (mMobileSimState == IccCardConstants.State.PIN_REQUIRED) ? "IccCardConstants.State.PIN_REQUIRED" :
                ( (mMobileSimState == IccCardConstants.State.PUK_REQUIRED) ? "IccCardConstants.State.PUK_REQUIRED" :
                ( (mMobileSimState == IccCardConstants.State.NETWORK_LOCKED) ? "IccCardConstants.State.NETWORK_LOCKED" :
                ( (mMobileSimState == IccCardConstants.State.IO_ERROR) ? "IccCardConstants.State.IO_ERROR" :
                ( (mMobileSimState == IccCardConstants.State.UNKNOWN) ? "IccCardConstants.State.UNKNOWN" :
                "-unknown-"
                ) ) ) ) ) ) )
            );
        }
    }


    //================================================================================================================
    //================================================================================================================

    private boolean hasVoiceService() {
        if ( mServiceState != null ) {
            switch (mServiceState.getState()) {
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_POWER_OFF:
                    return false;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

    //================================================================================================================

    private boolean hasDataService() {
        if ( mServiceState != null ) {
            switch (mServiceState.getDataServiceState()) {
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY: // Data service not allowed in emergency-only mode
                case ServiceState.STATE_POWER_OFF:
                    return false;
                case ServiceState.STATE_IN_SERVICE:
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

    //================================================================================================================

    private boolean isAirplaneModeOn() {
        // was:
        //  return Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        mAirplaneModeIsEnabled = (
            Settings.Global.getInt(
                mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON,
                0
            )
            ==
            1
        );
        return mAirplaneModeIsEnabled; // SDeach: Use saved mode (eliminates strange side effects on transition)
    }

    //================================================================================================================

    // SignalStrength.isGsm is not accurate, when service state changed,
    // it is not updated in time. So check service state first, if could not get
    // service state, or service state is unknown, check SignalStrength.isGsm

    private boolean isCdma() {
        return (
            (mSignalStrength != null)
            &&
            (
                ( ! mSignalStrength.isGsm() )
                ||
                mSignalStrength.maybeLteBasedOnCdma()
            )
        );
    }

    //================================================================================================================

    boolean isCdmaEri() {
        if ( mServiceState != null ) {
            final int iconIndex = mServiceState.getCdmaEriIconIndex();
            if (iconIndex != EriInfo.ROAMING_INDICATOR_OFF) {
                final int iconMode = mServiceState.getCdmaEriIconMode();
                if (
                    ( iconMode == EriInfo.ROAMING_ICON_MODE_NORMAL )
                    ||
                    ( iconMode == EriInfo.ROAMING_ICON_MODE_FLASH )
                ) {
                    return true;
                }
            }
        }
        return false;
    }

    //================================================================================================================

    // Check whether display "attached" data icon when PS attached/connecting

    private boolean isAttachedMobileDataTypeIconShown() {
        if (
            ( mMobileDataConnectionState != TelephonyManager.DATA_CONNECTED )
            &&
            ( mMobileDataConnectionState != TelephonyManager.DATA_CONNECTING )
            &&
            mConfigMobileDataTypeShowConnectingOrAttachedIcon
            &&
            ( mServiceState != null )
        ) {
            return mServiceState.getRadioTechnology() != ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
        } else {
            return false;
        }
    }

    private boolean isConnectedOrConnectingOrAttachedMobileDataTypeIconShown() {
        if (
            ( mMobileDataConnectionState == TelephonyManager.DATA_CONNECTED )
            ||
            ( mMobileDataConnectionState == TelephonyManager.DATA_CONNECTING )
            ||
            isAttachedMobileDataTypeIconShown()
        ) {
            return true;
        } else {
            return false;
        }
    }

    //================================================================================================================

    public boolean isEmergencyOnly() {
        if (mServiceState != null){
            if (CHATTY) {
                Slog.v(TAG,
                    "isEmergencyOnly: mServiceState is valid. _.isEmergencyOnly()="
                    + mServiceState.isEmergencyOnly()
                    + " _.getState()=" + mServiceState.getState()
                    + " _.getDataServiceState()=" + mServiceState.getDataServiceState()
                );
            }
            if (mServiceState.isEmergencyOnly()) {
                if (CHATTY) {
                    Slog.v(TAG,
                        "isEmergencyOnly: returns true (based on isEmergencyOnly)"
                    );
                }
                return true;
            } else {
                switch (mServiceState.getState()) {
                    case ServiceState.STATE_EMERGENCY_ONLY:
                        if (CHATTY) {
                            Slog.v(TAG,
                                "isEmergencyOnly: returns true (based on getState)"
                            );
                        }
                        return true;
                    default:
                        // fall thru
                }
                switch (mServiceState.getDataServiceState()) {
                    case ServiceState.STATE_EMERGENCY_ONLY:
                        if (CHATTY) {
                            Slog.v(TAG,
                                "isEmergencyOnly: returns true (based on getDataState)"
                            );
                        }
                        return true;
                    default:
                        // fall thru
                }
            }
        } else {
            if (CHATTY) {
                Slog.v(TAG,
                    "isEmergencyOnly: mServiceState is null=" + mServiceState
                );
            }
        }
        if (CHATTY) {
            Slog.v(TAG,
                "isEmergencyOnly: returns false"
            );
        }
        return false;
    }


    //================================================================================================================

    private boolean isEvdo() {
        if (mServiceState == null) return false;
        int radioTech = mServiceState.getRadioTechnology();
        return (
            (radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0)
            ||
            (radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A)
            ||
            (radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B)
            ||
            (radioTech  == ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD)
        );
    }

    //================================================================================================================

    private boolean isForced1xMode() {
        if (CHATTY) {
            Slog.v(TAG,
                "isForced1xMode: ( mPhoneCallingState != TelephonyManager.CALL_STATE_IDLE )="
                + ( mPhoneCallingState != TelephonyManager.CALL_STATE_IDLE )
            );
            Slog.v(TAG,
                "isForced1xMode: isEvdo=" + isEvdo()
            );
            Slog.v(TAG,
                "isForced1xMode: EVDO concurrent mode allowed:"
                + ( ( ( mServiceState != null) && ( mServiceState.getCssIndicator() == 1 ) ) ? "true" : "false" )
            );
            Slog.v(TAG,
                "isForced1xMode: isLte=" + isLte()
            );
            Slog.v(TAG,
                "isForced1xMode: LTE concurrent mode allowed:"
                + ( ( ( mServiceState != null) && ( mServiceState.getCssIndicator() == 1 ) ) ? "true" : "false" )
            );
        }

        //  1. data camped on LTE,
        //     a.) if phone support SVLTE, show LTE information(data icon/signal strength),
        //     b.) if phone does not support SVLTE, show 1X information
        //  2. data camped on EVDO,
        //     a.) if phone support SVDO, show EVDO information,
        //     b.) if phone does not support SVDO, show 1x information.
        //
        // if (
        //     ( mPhoneCallingState != TelephonyManager.CALL_STATE_IDLE )  // Call in progress (or ringing)
        //     &&
        //     (
        //         ( isEvdo() && ! SVDO_supported )
        //         ||
        //         ( isLTE() && ! SVDATA_supported )
        //     )
        // ) {
        //     use 1x (base CDMA) signal_level & 1x_icon
        // } else {
        //     use "Active" radioTech's level & icon
        // }

        if (
            ( mPhoneCallingState != TelephonyManager.CALL_STATE_IDLE )  // Call in progress (or ringing)
            &&
            (
                (
                    mMobileDataNetType == TelephonyManager.NETWORK_TYPE_1xRTT
                    ||
                    mMobileDataNetType == TelephonyManager.NETWORK_TYPE_CDMA
                ) // IS95A or IS95B
                ||
                (
                    (
                        mMobileDataNetType != TelephonyManager.NETWORK_TYPE_1xRTT
                        &&
                        mMobileDataNetType != TelephonyManager.NETWORK_TYPE_CDMA
                    )   // Not IS95A or IS95B
                    &&
                    (
                        (mServiceState == null)
                        ||
                        ( mServiceState.getCssIndicator() != 1 ) // 1 = Simultaneous Voice/Data allowed
                    )
                )
            )
        ) {
            if (DEBUG) {
                Slog.d(TAG, "isForced1xMode: returns true" );
            }
            return true;
        }

        if (DEBUG) {
            Slog.d(TAG, "isForced1xMode: returns false" );
        }
        return false;
    }

    //================================================================================================================

    private boolean isLte() {
        return (
            (mServiceState != null)
            &&
            (
                mServiceState.getRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_LTE
            )
        );
    }

    //================================================================================================================

    private void updateAirplaneMode() {
        mAirplaneModeIsEnabled = (
            Settings.Global.getInt(
                mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON,
                0
            )
            ==
            1
        );
    }

    //================================================================================================================

    private final void updateTelephonySignalStrength() {
        if (CHATTY) {
            Slog.v(TAG, "updateTelephonySignalStrength mSignalStrength=" + mSignalStrength);
        }

        if (mSignalStrength != null) {
            int newNumBarsInSignalIcon = mSignalStrength.getMaxLevel();
            if (newNumBarsInSignalIcon > 0) {
                mNumBarsInSignalIcon = newNumBarsInSignalIcon;
            }
            if (DEBUG) {
                Slog.d(TAG, "updateTelephonySignalStrength mNumBarsInSignalIcon=" + mNumBarsInSignalIcon);
            }
        }

        if( mNumBarsInSignalIcon < TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_MIN_BARS ) {
            mNumBarsInSignalIcon = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_MIN_BARS;
        }
        if( mNumBarsInSignalIcon > TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_MAX_BARS ) {
            mNumBarsInSignalIcon = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_MAX_BARS;
        }


        if (isEmergencyOnly() && mConfigShowEmergencyCallsOnlyIcon) {
            if (DEBUG) Slog.d(TAG, "updateTelephonySignalStrength: EmergencyOnly mode");

            mMobilePhoneSignalIconId
                = mMobileDataSignalIconId
                = mTelephonyIcons.getEmergencyModeIconId(
                    mNumBarsInSignalIcon
                );

            int descriptionId = mTelephonyIcons.getEmergencyModeDescriptionId(
                mNumBarsInSignalIcon
            );
            mMobilePhoneSignalContentDescription = getDescriptionStringFromId(descriptionId);

            mMobileRoamingContentDescription = sEmptyString;

        } else if (
            ! (
                hasVoiceService()
                ||
                ( mConfigShowSignalBarsWhenDataOnlyService && hasDataService() )
            )
        ) {
            if (INFO) Slog.i(TAG, "updateTelephonySignalStrength:  No service");

            mMobilePhoneSignalIconId
                = mMobileDataSignalIconId
                = mTelephonyIcons.getSignalStrengthNullIconId(
                    mNumBarsInSignalIcon
                );

            int descriptionId = mTelephonyIcons.getSignalStrengthNullDescriptionId(
                mNumBarsInSignalIcon
            );
            mMobilePhoneSignalContentDescription = getDescriptionStringFromId(descriptionId);

            mMobileRoamingContentDescription =
                mContext.getString(R.string.accessibility_roaming_no_service);

            if (DEBUG) {
                Slog.d(TAG,
                    "updateTelephonySignalStrength: !hasVoiceService(): No service icon="
                    + mMobilePhoneSignalIconId + "/" + getResourceName(mMobilePhoneSignalIconId)
                    +  " NumBars=" + mNumBarsInSignalIcon
                );
                Slog.d(TAG,
                    "updateTelephonySignalStrength: "
                    + "(mServiceStateDataState != ServiceState.STATE_IN_SERVICE): No data service icon="
                    + mMobileDataSignalIconId + "/" + getResourceName(mMobileDataSignalIconId)
                    + " NumBars=" + mNumBarsInSignalIcon
                );
            }

        } else if (mSignalStrength == null) {
            if (DEBUG) Slog.d(TAG, "updateTelephonySignalStrength: mSignalStrength == null");

            mMobilePhoneSignalIconId
            = mMobileDataSignalIconId
            = mTelephonyIcons.getSignalStrengthNullIconId(
                mNumBarsInSignalIcon
            );

            int descriptionId = mTelephonyIcons.getSignalStrengthNullDescriptionId(
                mNumBarsInSignalIcon
            );
            mMobilePhoneSignalContentDescription = getDescriptionStringFromId(descriptionId);

            mMobileRoamingContentDescription =
                mContext.getString(R.string.accessibility_roaming_no_service);

            if (DEBUG) {
                Slog.d(TAG,
                    "updateTelephonySignalStrength hasVoiceService={noSignalLevel}: No service icon="
                    + mMobilePhoneSignalIconId + "/" + getResourceName(mMobilePhoneSignalIconId)
                    + " NumBars=" + mNumBarsInSignalIcon
                );
            }

        } else {

            int iconLevel;

            mLastSignalLevel
                = iconLevel
                = getSignalLevelInternal(mSignalStrength);
            if (DEBUG) Slog.d(TAG,
                "updateTelephonySignalStrength: getSignalLevelInternal(mSignalStrength) = " + mLastSignalLevel
            );

            // Bounds check unsmoothed level
            if( iconLevel < 0 ) {
                iconLevel=0;
            }

            if(iconLevel > mNumBarsInSignalIcon) {
                iconLevel = mNumBarsInSignalIcon;
            }

            if (mConfigSignalSmoothingSupported == true) {
                long currTime = SystemClock.elapsedRealtime();
                if (currTime < mSignalSmoothingLastIconLevelChangeTime) {
                    Slog.d(TAG, "Clock Skew reset mSignalSmoothingLastIconLevelChangeTime");
                    mSignalSmoothingLastIconLevelChangeTime = currTime;
                }
                mStatusBarHandler.removeMessages(EVENT_SIG_STRENGTH);
                if (iconLevel < mSignalSmoothingCurrentShowingIconLevel) {
                    if ((currTime - mSignalSmoothingLastIconLevelChangeTime) > 2000) {
                        // allow the update by only 1 bar down per 2 seconds
                        if (mSignalSmoothingCurrentShowingIconLevel > 0) {
                            mSignalSmoothingCurrentShowingIconLevel--;
                            mSignalSmoothingLastIconLevelChangeTime = currTime;
                        }
                    }
                } else if (iconLevel > mSignalSmoothingCurrentShowingIconLevel) {
                    if ((currTime - mSignalSmoothingLastIconLevelChangeTime) > 1000) {
                        // allow the update by only 1 bar up per 1 second
                        if (mSignalSmoothingCurrentShowingIconLevel <= mNumBarsInSignalIcon) {
                            mSignalSmoothingCurrentShowingIconLevel++;
                            mSignalSmoothingLastIconLevelChangeTime = currTime;
                        }
                    }
                }
                if (iconLevel != mSignalSmoothingCurrentShowingIconLevel) {
                    mStatusBarHandler.sendMessageDelayed(
                        mStatusBarHandler.obtainMessage(EVENT_SIG_STRENGTH),
                        3000
                    );
                }
                iconLevel = mSignalSmoothingCurrentShowingIconLevel;

                // Bounds check smoothed level
                if( iconLevel < 0 ) {
                    iconLevel=0;
                }

                if(iconLevel > mNumBarsInSignalIcon) {
                    iconLevel = mNumBarsInSignalIcon;
                }

                if (DEBUG) Slog.d(TAG, "updateTelephonySignalStrength: smoothed level = " + iconLevel);
            }

            mMobilePhoneSignalIconId
                = mMobileDataSignalIconId
                = mTelephonyIcons.getSignalStrengthIconId(
                    mNumBarsInSignalIcon,
                    iconLevel,
                    mMobileInetCondition
                );

            int descriptionId = mTelephonyIcons.getSignalStrengthDescriptionId(
                mNumBarsInSignalIcon,
                iconLevel,
                mMobileInetCondition
            );
            mMobilePhoneSignalContentDescription = getDescriptionStringFromId(descriptionId);

            if (CHATTY) {
                Slog.v(TAG,
                    "updateTelephonySignalStrength (has service): iconLevel=" + iconLevel
                    + " NumBars=" + mNumBarsInSignalIcon
                    + " IconId=" + mMobilePhoneSignalIconId + "/" + getResourceName(mMobilePhoneSignalIconId)
                    + " Description=\"" + mMobilePhoneSignalContentDescription + "\""
                    );
            }

            //  -- New code always separates roaming icon into a separate layer for both CDMA & GSM.
            //       see updateMobileRoamingIcon()
            //  -- For CDMA: this was handled as a separate signal_level icon
            //       & a separate roaming_indicator icon.
            //  -- For GSM: this was handled as a combined signal_level + roaming_indicator icon.


        }
    }

    private String getDescriptionStringFromId( int descriptionId ) {
        if(descriptionId == 0) {
            return sEmptyString;
        } else {
            String returnValue = mContext.getString( descriptionId );
            if ( returnValue == null ) returnValue = sEmptyString;
            return returnValue;
        }
    }


    //================================================================================================================

    private int getSignalLevelInternal(SignalStrength signalStrength) {
        if ( isForced1xMode() ) {
            if (CHATTY) {
                Slog.v(TAG, "getSignalLevelInternal: using CDMA level: level=" + signalStrength.getCdmaLevel());
            }
            return signalStrength.getCdmaLevel();
        } else {
            if (CHATTY) {
                Slog.v(TAG, "getSignalLevelInternal: using active-radio level: level=" + signalStrength.getLevel());
            }
            return signalStrength.getLevel();
        }
    }

    //================================================================================================================


    private final void updateMobileDataTypeInfo() {
        mMobileDataIsEnabled = mCm.getMobileDataEnabled();

        if (
            mConfigWimaxSupported
            &&
            mWimaxIsEnabled
            &&
            mWimaxIsConnected
        ) {
            if (DEBUG) Slog.d(TAG, "updateMobileDataTypeInfo: (mWimaxIsEnabled && mWimaxIsConnected) == true");
            // wimax is a special 4g network not handled by telephony
            mMobileDataTypeIconId = (
                ( mWimaxInetCondition == 0 )
                ? R.drawable.stat_sys_data_connected_4g       // Wimax
                : R.drawable.stat_sys_data_fully_connected_4g // Wimax
            );

            mMobileDataTypeDisabledIconId = R.drawable.stat_sys_data_4g_null; // Wimax
            mMobileDataActivityDisabledIconId = 0; // Wimax

            mMobileDataTypeContentDescription = mContext.getString(
                R.string.accessibility_data_connection_4g);
        } else {

            mTelephonyIcons.chooseActiveIconSet(
                mConfigOperatorName_string,
                mMobileDataNetType,
                mPhone.isNetworkRoaming(),
                mConfigMobileDataTypeHspaDataDistinguishable,
                mConfigMobileDataTypeHspapDataDistinguishable,
                mConfigMobileDataTypeShowAtLeastThreeGees
            );

        }
        // Roaming icon now is set in updateRoamingIcon()
    }

    //================================================================================================================

    private final void updateMobileRoamingIcon() {
        if (
            ( ! hasVoiceService() )
            ||
            isEmergencyOnly() // Disable roaming icon when EmergencyOnly
        ) {
            mMobileRoamingIconId = 0;
            if( isEmergencyOnly() ) {
                mMobileRoamingContentDescription = sEmptyString;
            } else {
                mMobileRoamingContentDescription = mContext.getString(
                    R.string.accessibility_roaming_no_service); // May wish to set this to "silence".
            }
            if (DEBUG) {
                Slog.d(TAG,
                    "updateMobileRoamingIcon: no Service:"
                );
                Slog.d(TAG,
                    "updateRoamingIcon: mMobileRoamingIconId = "
                    + mMobileRoamingIconId
                    + "/" + getResourceName(mMobileRoamingIconId)
                    + " mMobileRoamingContentDescription = "
                    + mMobileRoamingContentDescription
                    + " (#1)"
                );
            }
        } else {
            if (CHATTY) {
                Slog.v(TAG, "updateMobileRoamingIcon: has Service:");
            }
            if (
                ( mConfigMobileRoamingDoNotShowRoaming )
                &&
                mPhone.isNetworkRoaming()
            ) {
                mMobileRoamingIconId = 0;
                mMobileRoamingContentDescription = mContext.getString(R.string.accessibility_roaming_off);
                if (CHATTY) {
                    Slog.v(TAG,
                        "updateRoamingIcon: mMobileRoamingIconId = "
                        + mMobileRoamingIconId
                        + "/" + getResourceName(mMobileRoamingIconId)
                        + " mMobileRoamingContentDescription = "
                        + mMobileRoamingContentDescription
                        + " (#2)"
                    );
                }
            } else if( ! isCdma() ) {

                if (CHATTY) {
                    Slog.v(TAG, "updateMobileRoamingIcon: not CDMA");
                }

                if( mPhone.isNetworkRoaming() ) {
                    mMobileRoamingIconId = R.drawable.stat_sys_data_connected_roam;
                    mMobileRoamingContentDescription = mContext.getString(R.string.accessibility_roaming);
                    if (CHATTY) {
                        Slog.v(TAG,
                            "updateRoamingIcon: mMobileRoamingIconId = "
                            + mMobileRoamingIconId
                            + "/" + getResourceName(mMobileRoamingIconId)
                            + " mMobileRoamingContentDescription = "
                            + mMobileRoamingContentDescription
                            + " (#3)"
                        );
                    }
                } else {
                    mMobileRoamingIconId = 0;
                    mMobileRoamingContentDescription = mContext.getString(R.string.accessibility_roaming_off);
                    if (CHATTY) {
                        Slog.v(TAG,
                            "updateRoamingIcon: mMobileRoamingIconId = "
                            + mMobileRoamingIconId
                            + "/" + getResourceName(mMobileRoamingIconId)
                            + " mMobileRoamingContentDescription = "
                            + mMobileRoamingContentDescription
                            + " (#4)"
                        );
                    }
                }

            } else {

                if (CHATTY) {
                    Slog.v(TAG, "updateMobileRoamingIcon: CDMA");
                }

                // Determine if normal Roaming
                if( ! isCdmaEri() ) {
                    if (CHATTY) {
                        Slog.v(TAG, "updateMobileRoamingIcon: isCdmaEri is false");
                    }
                    if( mPhone.isNetworkRoaming() ) {
                        mMobileRoamingIconId = R.drawable.stat_sys_data_connected_roam;
                        mMobileRoamingContentDescription = mContext.getString(R.string.accessibility_roaming);
                        if (CHATTY) {
                            Slog.v(TAG,
                                "updateRoamingIcon: mMobileRoamingIconId = "
                                + mMobileRoamingIconId
                                + "/" + getResourceName(mMobileRoamingIconId)
                                + " mMobileRoamingContentDescription = "
                                + mMobileRoamingContentDescription
                                + " (#5)"
                            );
                        }
                    } else {
                        mMobileRoamingIconId = 0;
                        mMobileRoamingContentDescription = mContext.getString(R.string.accessibility_roaming_off);
                        if (CHATTY) {
                            Slog.v(TAG,
                                "updateRoamingIcon: mMobileRoamingIconId = "
                                + mMobileRoamingIconId
                                + "/" + getResourceName(mMobileRoamingIconId)
                                + " mMobileRoamingContentDescription = "
                                + mMobileRoamingContentDescription
                                + " (#6)"
                            );
                        }
                    }
                } else {
                    // Is CdmaERI
                    if (CHATTY) {
                        Slog.v(TAG, "updateMobileRoamingIcon: isCdmaEri is true");
                    }
                    int[] iconList = TelephonyIcons.TELEPHONY_ROAMING_INDICATOR_CDMA;
                    int iconIndex =
                        (mServiceState != null)
                        ? mServiceState.getCdmaEriIconIndex()
                        : -1;
                    int iconMode =
                        (mServiceState != null)
                        ? mServiceState.getCdmaEriIconMode()
                        : -1;

                    if (iconIndex == -1) {
                        Slog.e(TAG,
                            "getCdmaEriIconIndex returned -1 or null, skipping CDMA-Roaming icon update");
                        // Do nothing: Leave existing roaming icon state.
                    } else {
                        if (iconMode == -1) {
                            Slog.e(TAG,
                                "getCdmeEriIconMode returned -1 or null, skipping CDMA-Roaming icon update");
                            // Do nothing: Leave existing roaming icon state.
                        } else {
                            if (iconIndex == EriInfo.ROAMING_INDICATOR_OFF) {
                                if (CHATTY) Slog.v(TAG, "Cdma ROAMING_INDICATOR_OFF, removing ERI icon");
                                mMobileRoamingIconId = 0;
                                mMobileRoamingContentDescription =
                                    mContext.getString(R.string.accessibility_roaming_off);
                                if (CHATTY) {
                                    Slog.v(TAG,
                                        "updateRoamingIcon: mMobileRoamingIconId = "
                                        + mMobileRoamingIconId
                                        + "/" + getResourceName(mMobileRoamingIconId)
                                        + " mMobileRoamingContentDescription = "
                                        + mMobileRoamingContentDescription
                                        + " (#7)"
                                    );
                                }
                            } else {
                                // Standard CDMA "triangle" icon - solid or flashing
                                switch (iconMode) {
                                case EriInfo.ROAMING_ICON_MODE_NORMAL:
                                    if (CHATTY) {
                                        Slog.v(TAG, "updateRoamingIcon: CDMA normal (non-flashing) mode");
                                    }
                                    iconList = TelephonyIcons.TELEPHONY_ROAMING_INDICATOR_CDMA;
                                    if( iconIndex >= iconList.length ) {
                                        iconIndex = 0; // Standard static icon.
                                    }
                                    mMobileRoamingIconId = iconList[iconIndex];
                                    mMobileRoamingContentDescription = mContext.getString(
                                        R.string.accessibility_roaming);
                                    if (CHATTY) {
                                        Slog.v(TAG,
                                            "updateRoamingIcon: mMobileRoamingIconId = "
                                            + mMobileRoamingIconId
                                            + "/" + getResourceName(mMobileRoamingIconId)
                                            + " mMobileRoamingContentDescription = "
                                            + mMobileRoamingContentDescription
                                            + " (#8)"
                                        );
                                    }
                                    break;
                                case EriInfo.ROAMING_ICON_MODE_FLASH:
                                    if (CHATTY) {
                                        Slog.v(TAG, "updateRoamingIcon: CDMA flashing mode");
                                    }
                                    iconList = TelephonyIcons.TELEPHONY_ROAMING_INDICATOR_CDMA_FLASH;
                                    if( iconIndex >= iconList.length ) {
                                        iconIndex = 2; // Standard flashing icon.
                                    }
                                    mMobileRoamingIconId =  iconList[iconIndex];
                                    mMobileRoamingContentDescription = mContext.getString(
                                        R.string.accessibility_roaming);
                                    if (CHATTY) {
                                        Slog.v(TAG,
                                            "updateRoamingIcon: mMobileRoamingIconId = "
                                            + mMobileRoamingIconId
                                            + "/" + getResourceName(mMobileRoamingIconId)
                                            + " mMobileRoamingContentDescription = "
                                            + mMobileRoamingContentDescription
                                            + " (#9)"
                                        );
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (DEBUG) Slog.d(TAG,
            "updateMobileRoamingIcon:"
            + " mMobileRoamingIconId=" + Integer.toHexString(mMobileRoamingIconId)
            + "/" + getResourceName(mMobileRoamingIconId)
            + " mMobileRoamingContentDescription=\"" + mMobileRoamingContentDescription + "\""
        );

    }

    //================================================================================================================

    // Returns true if sim is not needed (hasVoiceService, hasDataService,
    //   or sim is showing status other than ABSENT/ERROR).
    private boolean isSimOK() {
        if( hasVoiceService() ) return true;
        if( hasDataService() ) return true;
        if(
            mMobileSimState == IccCardConstants.State.ABSENT
            ||
            mMobileSimState == IccCardConstants.State.IO_ERROR
            ||
            mMobileSimState == IccCardConstants.State.UNKNOWN
        ) {
            return false;
        }
        return true;
    }

    private final void updateMobileSimIcon() {
        if (DEBUG) Slog.d(TAG,
            "updateMobileSimIcon: MobileSimState= " + mMobileSimState
        );
        if (CHATTY) Slog.v(TAG,
            "updateMobileSimIcon: hasVoiceService=" + hasVoiceService()
            + " mMobileSimState == IccCardConstants.State.ABSENT="
            + ((mMobileSimState == IccCardConstants.State.ABSENT) ? "true" : "false")
            + " or __.IO_ERROR="
            + ((mMobileSimState == IccCardConstants.State.IO_ERROR) ? "true" : "false")
            + " or __.UNKNOWN="
            + ((mMobileSimState == IccCardConstants.State.UNKNOWN) ? "true" : "false")
        );

        if (
            (
                ( ! hasVoiceService() )
                ||
                isEmergencyOnly()
            )
            &&
            (
                ! isSimOK()
            )
        ) {

            if (CHATTY) Slog.v(TAG, "updateMobileSimIcon: set mMobileSimIconId to NO_SIM");
            mMobileSimIconId = R.drawable.stat_sys_no_sim;
            mMobileSimContentDescription = mContext.getString(
                R.string.accessibility_no_sim);

            if (
                mConfigMobileSimDifferingIconForSimError
                ||
                "vzw".equals(mConfigOperatorName_string)
            ) {
                if (
                    mMobileSimState != IccCardConstants.State.ABSENT
                ) {
                    mMobileSimIconId = R.drawable.stat_sys_corrupt_sim;
                    mMobileSimContentDescription = mContext.getString(
                        R.string.accessibility_corrupt_sim);
                    if (CHATTY) Slog.v(TAG, "updateMobileSimIcon:  override mMobileSimIconId to SIM_CORRUPT");
                }
            }
        } else {
            mMobileSimIconId = 0;
            mMobileSimContentDescription = sEmptyString;
            if (CHATTY) Slog.v(TAG, "updateMobileSimIcon: has Service: mMobileSimIconId=0");
        }

        if (DEBUG) {
            Slog.d(TAG,
                "updateMobileSimIcon: exiting: mMobileSimIconId=" + Integer.toHexString(mMobileSimIconId)
                + "/" + getResourceName(mMobileSimIconId)
                + ", mMobileSimContentDescription=\"" + mMobileSimContentDescription + "\""
            );
        }
    }

    //================================================================================================================

    private void updateMobileDataTypeAndActivityIcons() {
        boolean isVisible = true;

        if (DEBUG) Slog.d(TAG, "updateMobileDataTypeAndActivityIcons: mServiceStateDataState="
            + mServiceStateDataState + "=" +
            ( (mServiceStateDataState==ServiceState.STATE_IN_SERVICE) ? "STATE_IN_SERVICE" :
            ( (mServiceStateDataState==ServiceState.STATE_OUT_OF_SERVICE) ? "STATE_OUT_OF_SERVICE" :
            ( (mServiceStateDataState==ServiceState.STATE_EMERGENCY_ONLY) ? "STATE_EMERGENCY_ONLY" :
            ( (mServiceStateDataState==ServiceState.STATE_POWER_OFF) ? "STATE_POWER_OFF" : "unknown"
            ) ) ) )
            + " hasDataService=" + hasDataService()
            + " hasVoiceService=" + hasVoiceService()
        );

        if (DEBUG) Slog.d(TAG, "updateMobileDataTypeAndActivityIcons: mMobileDataConnectionState="
            + mMobileDataConnectionState + "=" +
            ( (mMobileDataConnectionState==TelephonyManager.DATA_UNKNOWN) ? "DATA_UNKNOWN" :
            ( (mMobileDataConnectionState==TelephonyManager.DATA_DISCONNECTED) ? "DATA_DISCONNECTED" :
            ( (mMobileDataConnectionState==TelephonyManager.DATA_CONNECTING) ? "DATA_CONNECTING" :
            ( (mMobileDataConnectionState==TelephonyManager.DATA_CONNECTED) ? "DATA_CONNECTED" :
            ( (mMobileDataConnectionState==TelephonyManager.DATA_SUSPENDED) ? "DATA_SUSPENDED" : "unknown"
            ) ) ) ) )
        );

        if (DEBUG) Slog.d(TAG, "updateMobileDataTypeAndActivityIcons: mMobileDataActivityStatus="
            + mMobileDataActivityStatus + "=" +
            ( (mMobileDataActivityStatus==TelephonyManager.DATA_ACTIVITY_NONE) ? "DATA_ACTIVITY_NONE" :
            ( (mMobileDataActivityStatus==TelephonyManager.DATA_ACTIVITY_IN) ? "DATA_ACTIVITY_IN" :
            ( (mMobileDataActivityStatus==TelephonyManager.DATA_ACTIVITY_OUT) ? "DATA_ACTIVITY_OUT" :
            ( (mMobileDataActivityStatus==TelephonyManager.DATA_ACTIVITY_INOUT) ? "DATA_ACTIVITY_INOUT" :
            ( (mMobileDataActivityStatus==TelephonyManager.DATA_ACTIVITY_DORMANT) ? "DATA_ACTIVITY_DORMANT" : "unknown"
            ) ) ) ) )
        );


        boolean suspended = false;
        if(
            ( mMobileDataConnectionState == TelephonyManager.DATA_SUSPENDED )
            &&
            mMobileDataIsEnabled
        ) {
            suspended = true;
        }

        if( isCdma() ) {
            // Applies only to CDMA & CDMA-LTE phones
            mMobileDataIsEnabled = mCm.getMobileDataEnabled();

            if (DEBUG) Slog.d(TAG,
                "updateMobileDataTypeAndActivityIcons: CDMA/CDMA-LTE: isCdma=" + isCdma()
                + ", isLte=" + isLte()
                + ", mPhone.getPhoneType()=" + mPhone.getPhoneType()
            );

            if( mMobileDataConnectionState != TelephonyManager.DATA_CONNECTED ) {
                if (
                    ( mServiceState != null )
                    &&
                    ( mServiceState.getRadioTechnology() != ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN )
                    &&
                    ( mConfigMobileDataTypeShowBothWifiAndMobileNetwork && mWifiIsConnected )
                    &&
                    mMobileDataIsEnabled
                    &&
                    ( ! isEmergencyOnly() )
                ) {
                    suspended = true;
                }
            }
            if (DEBUG) Slog.d(TAG,
                "updateMobileDataTypeAndActivityIcons: CDMA/CDMA-LTE:"
                +" mMobileDataConnectionState=" + mMobileDataConnectionState
                + ", ( mMobileDataConnectionState != TelephonyManager.DATA_CONNECTED )="
                + ( mMobileDataConnectionState != TelephonyManager.DATA_CONNECTED )
                + ", ( mServiceState != null )=" + ( mServiceState != null )
                + ", ( mServiceState.getRadioTechnology() != ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN )="
                + (
                    ( mServiceState != null )
                    ?( mServiceState.getRadioTechnology() != ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN )
                    : "(not testable)"
                )
                + ", mConfigMobileDataTypeShowBothWifiAndMobileNetwork="
                + mConfigMobileDataTypeShowBothWifiAndMobileNetwork
                + ", mWifiIsConnected=" + mWifiIsConnected
                + ", mMobileDataIsEnabled=" + mMobileDataIsEnabled
                + " = suspended=" + suspended
            );
        } else if( ! isCdma() ) {
            // Applies only to GSM & GSM-LTE phones
            mMobileDataIsEnabled = mCm.getMobileDataEnabled();
            if (DEBUG) Slog.d(TAG,
                "updateMobileDataTypeAndActivityIcons: GSM/GSM-LTE: isCdma=" + isCdma()
                + ", isLte=" + isLte()
                + ", mPhone.getPhoneType()=" + mPhone.getPhoneType()
            );
            if(
                isConnectedOrConnectingOrAttachedMobileDataTypeIconShown()
                &&
                ( ! isEmergencyOnly() )
                &&
                mMobileDataIsEnabled
                &&
                (
                    (
                        mConfigMobileDataTypeShowBothWifiAndMobileNetwork
                        &&
                        mWifiIsConnected
                    )
                    ||
                    ( ! mWifiIsConnected )
                )
            ) {
                if( mMobileDataConnectionState != TelephonyManager.DATA_CONNECTED ) {
                    suspended = true;
                }
            }
            if (DEBUG) Slog.d(TAG,
                "updateMobileDataTypeAndActivityIcons: GSM/GSM-LTE:"
                +" isConnectedOrConnectingOrAttachedMobileDataTypeIconShown="
                + isConnectedOrConnectingOrAttachedMobileDataTypeIconShown()
                + ", isEmergencyOnly=" + isEmergencyOnly()
                + ", ( ! isEmergencyOnly )=" + ( ! isEmergencyOnly() )
                + ", mMobileDataIsEnabled=" + mMobileDataIsEnabled
                + ", mConfigMobileDataTypeShowBothWifiAndMobileNetwork="
                + mConfigMobileDataTypeShowBothWifiAndMobileNetwork
                + ", mWifiIsConnected=" + mWifiIsConnected
                + ", mMobileDataConnectionState=" + mMobileDataConnectionState
                + ", ( mMobileDataConnectionState != TelephonyManager.DATA_CONNECTED )="
                + ( mMobileDataConnectionState != TelephonyManager.DATA_CONNECTED )
                + " = suspended=" + suspended
            );
        }

        mMobileDataTypeIconId = mTelephonyIcons.getMobileDataTypeIconId(
            mMobileDataConnectionState,
            mMobileInetCondition,
            isDisabledMobileDataTypeIconShown(),
            suspended
        );

        mMobileDataTypeSuspendedIconId = mTelephonyIcons.getMobileDataTypeSuspendedIconId();

        mMobileDataTypeAttachedIconId = mTelephonyIcons.getMobileDataTypeAttachedIconId();

        mMobileDataTypeDisabledIconId = mTelephonyIcons.getMobileDataTypeDisabledIconId();

        mMobileDataActivitySuspendedIconId = mTelephonyIcons.getMobileDataActivitySuspendedIconId();

        mMobileDataActivityAttachedIconId = mTelephonyIcons.getMobileDataActivityAttachedIconId();

        mMobileDataActivityDisabledIconId = mTelephonyIcons.getMobileDataActivityDisabledIconId();

        if( ! isSimOK () ) {
            mMobileDataTypeSuspendedIconId = 0;
            mMobileDataTypeAttachedIconId = 0;
            mMobileDataTypeDisabledIconId = 0;
            mMobileDataActivitySuspendedIconId = 0;
            mMobileDataActivityAttachedIconId = 0;
            mMobileDataActivityDisabledIconId = 0;
        }

        int descriptionId = mTelephonyIcons.getMobileDataTypeDescriptionId(
            mMobileDataConnectionState,
            mMobileInetCondition,
            isDisabledMobileDataTypeIconShown(),
            suspended
        );
        mMobileDataTypeContentDescription = getDescriptionStringFromId(
            (isSimOK()) ? descriptionId : 0
        );

        descriptionId =  mTelephonyIcons.getMobileDataTypeSuspendedDescriptionId();
        mMobileDataTypeSuspendedDescription = getDescriptionStringFromId(
            (isSimOK()) ? descriptionId : 0
        );

        descriptionId =  mTelephonyIcons.getMobileDataTypeAttachedDescriptionId();
        mMobileDataTypeAttachedDescription = getDescriptionStringFromId(
            (isSimOK()) ? descriptionId : 0
        );

        descriptionId =  mTelephonyIcons.getMobileDataTypeDisabledDescriptionId();
        mMobileDataTypeDisabledDescription = getDescriptionStringFromId(
            (isSimOK()) ? descriptionId : 0
        );

        mMobileDataActivityIconId = mTelephonyIcons.getMobileDataActivityIconId(
            mMobileDataConnectionState,
            mMobileInetCondition,
            isDisabledMobileDataTypeIconShown(),
            suspended,
            mMobileDataActivityStatus
        );

        if (mMobileDataNetType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
            isVisible = false;
            mMobileDataActivityIconId = 0;
        } else if (isLte()) {
            // LTE case: Covers LTE on both CDMA & GSM phones
            if (
                hasDataService()
                ||
                ( mConfigShowNetworkTypeWhenVoiceOnlyService && hasVoiceService() )
            ) {
                switch (mMobileDataConnectionState) {
                    default:
                    case TelephonyManager.DATA_UNKNOWN:
                    case TelephonyManager.DATA_DISCONNECTED:
                        isVisible = false;
                        mMobileDataActivityIconId = 0;
                        break;

                    case TelephonyManager.DATA_CONNECTING:
                    case TelephonyManager.DATA_SUSPENDED:
                        break;

                    case TelephonyManager.DATA_CONNECTED:
                        break;

                }

            }


            if ( isForced1xMode() ) {
                isVisible = false;
                mMobileDataActivityIconId = 0;
            }

            if (DEBUG) Slog.d(TAG,
                "updateMobileDataTypeAndActivityIcons: LTE: mMobileDataConnectionState="
                + mMobileDataConnectionState
                + " mMobileDataTypeIconId=" + Integer.toHexString(mMobileDataTypeIconId) + "/"
                + getResourceName(mMobileDataTypeIconId)
                + " mMobileDataActivityIconId=" + Integer.toHexString(mMobileDataActivityIconId) + "/"
                + getResourceName(mMobileDataActivityIconId)
                + " isVisible=" + isVisible
                );

        } else if( ! isCdma() ) {
            // GSM case: we have to check also the sim state
            if (
                ( mMobileSimState == IccCardConstants.State.READY )
                ||
                ( mMobileSimState == IccCardConstants.State.UNKNOWN )
            ) {

                if (
                    (
                        hasDataService()
                        ||
                        ( mConfigShowNetworkTypeWhenVoiceOnlyService && hasVoiceService() )
                    )
                    &&
                    isConnectedOrConnectingOrAttachedMobileDataTypeIconShown()
                ) {
                    switch (mMobileDataConnectionState) {
                        default:
                        case TelephonyManager.DATA_UNKNOWN:
                        case TelephonyManager.DATA_DISCONNECTED:
                            isVisible = false;
                            mMobileDataActivityIconId = 0;
                            break;

                        case TelephonyManager.DATA_CONNECTING:
                        case TelephonyManager.DATA_SUSPENDED:
                        case TelephonyManager.DATA_CONNECTED:
                            break;

                    }

                }

            }

            if (DEBUG) Slog.d(TAG,
                "updateMobileDataTypeAndActivityIcons: GSM: mMobileDataConnectionState="
                + mMobileDataConnectionState
                + " mMobileDataTypeIconId=" + Integer.toHexString(mMobileDataTypeIconId) + "/"
                + getResourceName(mMobileDataTypeIconId)
                + " mMobileDataActivityIconId=" + Integer.toHexString(mMobileDataActivityIconId) + "/"
                + getResourceName(mMobileDataActivityIconId)
                + " isVisible=" + isVisible
                );

        } else {
            // CDMA case: mMobileDataActivityStatus can be also DATA_ACTIVITY_DORMANT
            switch (mMobileDataConnectionState) {
                default:
                case TelephonyManager.DATA_UNKNOWN:
                case TelephonyManager.DATA_DISCONNECTED:
                case TelephonyManager.DATA_CONNECTING:
                case TelephonyManager.DATA_SUSPENDED:
                    isVisible = false;
                    mMobileDataActivityIconId = 0;
                    break;

                case TelephonyManager.DATA_CONNECTED:
                    break;

            } // switch (mMobileDataConnectionState)

            if ( isForced1xMode() ) {
                isVisible = false;
                mMobileDataActivityIconId = 0;
            }

            if (DEBUG) Slog.d(TAG,
                "updateMobileDataTypeAndActivityIcons: CDMA: mMobileDataConnectionState="
                + mMobileDataConnectionState
                + " mMobileDataTypeIconId=" + Integer.toHexString(mMobileDataTypeIconId) + "/"
                + getResourceName(mMobileDataTypeIconId)
                + " mMobileDataActivityIconId=" + Integer.toHexString(mMobileDataActivityIconId) + "/"
                + getResourceName(mMobileDataActivityIconId)
                + " isVisible=" + isVisible
                );
        }

        if(
            isAirplaneModeOn()
            ||
            ( ! isSimOK() )
        ) {
            mMobileDataIsConnected = false;
            mMobileDataActivityIconId = 0;
        } else {
            mMobileDataIsConnected = isVisible;
        }

        if( ! mMobileDataIsConnected ) {
            mMobileDataActivityIconId = 0;
        }

        if (DEBUG) Slog.d(TAG,
            "updateMobileDataTypeAndActivityIcons: mMobileDataIsConnected="
            + mMobileDataIsConnected);
        if (DEBUG) Slog.d(TAG,
            "updateMobileDataTypeAndActivityIcons: mMobileDataActivityIconId="
            + Integer.toHexString(mMobileDataActivityIconId)
            + "/" + getResourceName(mMobileDataActivityIconId)
        );

        // I moved the following to the end of the function, it seems to be dependent on isVisible.
        // yuck - this should NOT be done by the status bar
        long ident = Binder.clearCallingIdentity();
        try {
            mBatteryStats.notePhoneDataConnectionState(
                mPhone.getNetworkType(),
                isVisible
            );
        } catch (RemoteException e) {
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
        // end of "yuck - this should NOT be done by the status bar"

    }

    //================================================================================================================

    void updateMobileNetworkName(
        boolean showSpn,
        String spn,
        boolean showPlmn,
        String plmn
    ) {

        // Always log this: DO NOT wrap in "if(INFO)"
        Slog.i(TAG,
            "updateMobileNetworkName: "
            + "showSpn=" + showSpn + " spn=\"" + spn + "\" "
            + "showPlmn=" + showPlmn + " plmn=\"" + plmn + "\" "
            + "mConfigMobileNetworkNameShortFormSupported=" + mConfigMobileNetworkNameShortFormSupported);

        if( DEBUG && false ) {
                spn="TestSpn";
                showSpn=true;
        }


        showPlmn =
            (
                showPlmn
                &&
                ( plmn != null )
                &&
                ( ! plmn.isEmpty() )
            )
            ? true
            : false;

        showSpn =
            (
                showSpn
                &&
                ( spn != null )
                &&
                ( ! spn.isEmpty() )
            )
            ? true
            : false;

        String short_spn = spn;
        String short_plmn = plmn;

        if (
            ( plmn != null )
            &&
            plmn.equals(mMobileDataNetworkNameDefault)
        ) {
            short_plmn = null; // Suppress "No Service." in statusbar OnsText field
            mMobileDataNetworkNameShortForm = sEmptyString;
        }

        if (
            ( plmn != null )
            &&
            plmn.equals(
                Resources.getSystem().getText( com.android.internal.R.string.emergency_calls_only ).toString()
            )
        ) {
            short_plmn = null; // Suppress "Emergency calls only" in statusbar OnsText field
            mMobileDataNetworkNameShortForm = sEmptyString;
        }

        if (
            ( short_spn != null )
            &&
            ( mMobileDataNetworkNameShortFormAbbreviationsTable.length != 0 )
        ) {
            for(
                int i=0;
                i < mMobileDataNetworkNameShortFormAbbreviationsTable.length;
                i += 2
            ) {
                if( short_spn.equalsIgnoreCase(mMobileDataNetworkNameShortFormAbbreviationsTable[i]) ) {
                    short_spn = mMobileDataNetworkNameShortFormAbbreviationsTable[i+1];
                    break;
                }
            }
        }

        if (
            ( short_plmn != null )
            &&
            ( mMobileDataNetworkNameShortFormAbbreviationsTable.length != 0 )
        ) {
            for(
                int i=0;
                i < mMobileDataNetworkNameShortFormAbbreviationsTable.length;
                i+= 2
            ) {
                if( short_plmn.equalsIgnoreCase( mMobileDataNetworkNameShortFormAbbreviationsTable[i] ) ) {
                    short_plmn = mMobileDataNetworkNameShortFormAbbreviationsTable[i+1];
                    break;
                }
            }
        }

        if( DEBUG ) {
            Slog.d(TAG,
                "updateMobileNetworkName:   mConfigMobileNetworkNameShortFormSupported is "
                + mConfigMobileNetworkNameShortFormSupported);
            Slog.d(TAG,
                "updateMobileNetworkName:   mConfigMobileNetworkNameShowBothPlmnAndSpnInExpanded is "
                + mConfigMobileNetworkNameShowBothPlmnAndSpnInExpanded);
            Slog.d(TAG,
                "updateMobileNetworkName:   mConfigMobileNetworkNameShowNoService is "
                + mConfigMobileNetworkNameShowNoService);

            Slog.d(TAG,
                "updateMobileNetworkName:   updateMobileNetworkName after fixups: "
                + "showSpn=" + showSpn + " spn=\"" + spn + "\" " + " short_spn=\"" + short_spn + "\" \n"
                + "showPlmn=" + showPlmn + " plmn=\"" + plmn + "\" " + " short_plmn=\"" + short_plmn + "\" "
                );
        }

        StringBuilder str = new StringBuilder();
        boolean isSomethingEmitted = false;
        boolean isShortFormDone = false;

        if (mConfigMobileNetworkNameShortFormSupported) {
            if (
                mConfigMobileNetworkNameSpnHasPriority
                &&
                (
                    mServiceState != null
                    &&
                    ( ! mServiceState.getRoaming() )
                )
                &&
                showSpn
                &&
                ( ! plmn.equals(
                        Resources.getSystem().getText( com.android.internal.R.string.emergency_calls_only ).toString()
                    )
                )
                &&
                ( short_spn != null )
            ) {
                mIsMobileNetworkNameShortFormVisible = true;
                mMobileDataNetworkNameShortForm = short_spn;
                isShortFormDone = true;
            }

            if(
                ( ! isShortFormDone )
                &&
                showPlmn
                &&
                ( ! plmn.equals(
                        Resources.getSystem().getText(com.android.internal.R.string.emergency_calls_only).toString()
                    )
                )
                &&
                ( short_plmn != null )
            ) {
                mIsMobileNetworkNameShortFormVisible = true;
                mMobileDataNetworkNameShortForm = short_plmn;
                isShortFormDone = true;
            }
            if (
                ( ! isShortFormDone )
                &&
                showSpn
                &&
                ( short_spn != null )
            ) {
                mIsMobileNetworkNameShortFormVisible = true;
                mMobileDataNetworkNameShortForm = short_spn;
                isShortFormDone = true;
            }

            if ( ! isShortFormDone ) {
                mMobileDataNetworkNameShortForm = sEmptyString;
                mIsMobileNetworkNameShortFormVisible = false;
            }

        } else {
            mMobileDataNetworkNameShortForm = sEmptyString;
            mIsMobileNetworkNameShortFormVisible = false;
        }

        if( DEBUG ) {
            Slog.d(TAG, "updateMobileNetworkName:   isShortFormDone is "+ isShortFormDone);
            Slog.d(TAG, "updateMobileNetworkName:   false is "+ false);
            Slog.d(TAG, "updateMobileNetworkName:   false is "+ false);
        }

        if (
            showPlmn // plmn is supplied
        ) {
            str.append(plmn);
            isSomethingEmitted = true;
        }

        if (
            showSpn // spn is supplied
        ) {
            if(
                ( ! isSomethingEmitted )
                ||
                (
                    isSomethingEmitted
                    &&
                    (
                        plmn == null
                        ||
                        ( ! plmn.equals(spn) )
                    )
                )
            ) {
                if ( isSomethingEmitted ) {
                    str.append(mMobileDataNetworkNameSeparator);
                }
                str.append(spn);
                isSomethingEmitted = true;
            }
        }

        mMobileDataNetworkName = str.toString(); // Set text or erase last content

        if ( ! isSomethingEmitted ) {
            if (
                mConfigMobileNetworkNameShowNoService
                &&
                (
                    ( mServiceState == null )
                    ||
                    ( mServiceState.getState() != ServiceState.STATE_IN_SERVICE )
                )
            ) {
                if( DEBUG ) {
                    Slog.d(TAG,"updateMobileNetworkName:  Display \"No service\"");
                }
                mMobileDataNetworkName = mMobileDataNetworkNameDefault;
            } else {
                Slog.d(TAG,"updateMobileNetworkName:  Display nothing - Do not display \"No service\"");
                mMobileDataNetworkName = sEmptyString;
            }
        }

        if( DEBUG ) {
            if( mIsMobileNetworkNameShortFormVisible ) {
                Slog.i(TAG,
                    "updateMobileNetworkName:   mMobileDataNetworkNameShortForm = \""
                    + mMobileDataNetworkNameShortForm + "\"" );
            }
            Slog.i(TAG,
                "updateMobileNetworkName:   mMobileDataNetworkName = \""
                + mMobileDataNetworkName + "\"");
        }

    }

    //================================================================================================================
    // ===== Wifi ====================================================================================================
    //================================================================================================================

    class WifiHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                    if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                        mWifiChannel.sendMessage(
                            Message.obtain(
                                this,
                                AsyncChannel.CMD_CHANNEL_FULL_CONNECTION
                            )
                        );
                    } else {
                        Slog.e(TAG, "Failed to connect to wifi");
                    }
                    break;
                case WifiManager.DATA_ACTIVITY_NOTIFICATION:
                    if (msg.arg1 != mWifiDataActivityStatus) {
                        mWifiDataActivityStatus = msg.arg1;
                        refreshViews();
                    }
                    break;
                default:
                    //Ignore
                    break;
            }
        }
    }

    //================================================================================================================

    private void updateWifiState(Intent intent) {
        final String action = intent.getAction();
        if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            mWifiIsEnabled = (
                intent.getIntExtra(
                    WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN
                )
                ==
                WifiManager.WIFI_STATE_ENABLED
            );

        } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            final NetworkInfo networkInfo =
                (NetworkInfo)intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            boolean wasConnected = mWifiIsConnected;
            mWifiIsConnected = (
                ( networkInfo != null )
                &&
                networkInfo.isConnected()
            );

            // If we just connected, grab the inintial signal strength and ssid
            if (
                mWifiIsConnected
                &&
                ( ! wasConnected )
            ) {
                // try getting it out of the intent first
                WifiInfo info = (WifiInfo) intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                if (info == null) {
                    info = mWifiManager.getConnectionInfo();
                }
                if (info != null) {
                    mWifiSsid = huntForSsid(info);
                } else {
                    mWifiSsid = null;
                }
            } else if ( ! mWifiIsConnected ) {
                mWifiSsid = null;
            }

            // Apparently the wifi level is not stable at this point even if we've just connected to
            // the network; we need to wait for an RSSI_CHANGED_ACTION for that. So let's just set
            // it to 0 for now
            mWifiLevel = 0;
            mWifiRssi = -200;

        } else if ( action.equals( WifiManager.RSSI_CHANGED_ACTION ) ) {
            if (mWifiIsConnected) {
                mWifiRssi = intent.getIntExtra(
                    WifiManager.EXTRA_NEW_RSSI,
                    -200
                );
                mWifiLevel = WifiManager.calculateSignalLevel(
                    mWifiRssi,
                    WifiIcons.WIFI_LEVEL_COUNT
                );
            }
        }

        updateWifiIcons();
    }

    //================================================================================================================

    private void updateWifiIcons() {
        if (mWifiIsConnected) {
            mWifiSignalLevelIconId = WifiIcons.WIFI_SIGNAL_STRENGTH[mWifiInetCondition][mWifiLevel];
            mWifiContentDescription = getDescriptionStringFromId(
                AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH[mWifiLevel]
            );
        } else {
            if (mDataAndWifiIsStacked) {
                mWifiSignalLevelIconId = 0;
            } else {
                mWifiSignalLevelIconId =
                    ( mWifiIsEnabled )
                    ? R.drawable.stat_sys_wifi_signal_null
                    : 0;
            }
            mWifiContentDescription = getDescriptionStringFromId(R.string.accessibility_no_wifi);
        }
    }

    //================================================================================================================

    private String huntForSsid(WifiInfo info) {
        String ssid = info.getSSID();
        if (ssid != null) {
            return ssid;
        }

        // OK, it's not in the connectionInfo; we have to go hunting for it

        List<WifiConfiguration> networks = mWifiManager.getConfiguredNetworks();
        for ( WifiConfiguration net : networks ) {
            if (net.networkId == info.getNetworkId()) {
                return net.SSID;
            }
        }
        return null;
    }

    //================================================================================================================

    private void updateWifiActivityIcon() {
        if (mWifiIsConnected) {
            if (mWifiSsid == null) {
                mWifiDataActivityIconId = 0; // no wifis, no bits
            } else {
                switch (mWifiDataActivityStatus) {
                    case WifiManager.DATA_ACTIVITY_IN:
                        mWifiDataActivityIconId = R.drawable.stat_sys_wifi_in;
                        break;
                    case WifiManager.DATA_ACTIVITY_OUT:
                        mWifiDataActivityIconId = R.drawable.stat_sys_wifi_out;
                        break;
                    case WifiManager.DATA_ACTIVITY_INOUT:
                        mWifiDataActivityIconId = R.drawable.stat_sys_wifi_inout;
                        break;
                    case WifiManager.DATA_ACTIVITY_NONE:
                        mWifiDataActivityIconId = 0;
                        break;
                }
            }
        } else {
            mWifiDataActivityIconId = 0; // no wifis, no activity indicators
        }
    }

    //================================================================================================================
    // ===== Wimax ===================================================================================================
    //================================================================================================================

    private final void updateWimaxState(Intent intent) {
        final String action = intent.getAction();
        boolean wasConnected = mWimaxIsConnected;
        if (action.equals(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION)) {

            int wimaxStatus = intent.getIntExtra(
                WimaxManagerConstants.EXTRA_4G_STATE,
                WimaxManagerConstants.NET_4G_STATE_UNKNOWN
            );
            mWimaxIsEnabled = ( wimaxStatus == WimaxManagerConstants.NET_4G_STATE_ENABLED );

        } else if (action.equals(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION)) {

            mWimaxSignalLevel = intent.getIntExtra(
                WimaxManagerConstants.EXTRA_NEW_SIGNAL_LEVEL,
                0
            );

        } else if (action.equals(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION)) {

            mWimaxState = intent.getIntExtra(
                WimaxManagerConstants.EXTRA_WIMAX_STATE,
                WimaxManagerConstants.NET_4G_STATE_UNKNOWN
            );
            mWimaxExtraState = intent.getIntExtra(
                WimaxManagerConstants.EXTRA_WIMAX_STATE_DETAIL,
                WimaxManagerConstants.NET_4G_STATE_UNKNOWN
            );
            mWimaxIsConnected = ( mWimaxState == WimaxManagerConstants.WIMAX_STATE_CONNECTED );
            mWimaxIsIdle = ( mWimaxExtraState == WimaxManagerConstants.WIMAX_IDLE );

        }

        updateMobileDataTypeInfo();
        updateWimaxIcons();
    }

    //================================================================================================================

    private void updateWimaxIcons() {
        if (mWimaxIsEnabled) {
            if (mWimaxIsConnected) {

                if (mWimaxIsIdle) {
                    mWimaxSignalLevelIconId = WimaxIcons.WIMAX_IDLE;
                } else {
                    mWimaxSignalLevelIconId = WimaxIcons.WIMAX_SIGNAL_STRENGTH[mWimaxInetCondition][mWimaxSignalLevel];
                }

                mWimaxContentDescription = getDescriptionStringFromId(
                    AccessibilityContentDescriptions.WIMAX_CONNECTION_STRENGTH[mWimaxSignalLevel]
                );

            } else {
                mWimaxSignalLevelIconId = WimaxIcons.WIMAX_DISCONNECTED;
                mWimaxContentDescription = getDescriptionStringFromId(R.string.accessibility_no_wimax);
            }
        } else {
            mWimaxSignalLevelIconId = 0;
            mWimaxContentDescription = sEmptyString;
        }
    }

    //================================================================================================================
    // ===== Full or limited Internet connectivity ===================================================================
    //================================================================================================================


    private void updateConnectivityStatus(Intent intent) {
        if (DEBUG) {
            Slog.d(TAG, "updateConnectivityStatus: intent=" + intent);
        }

        final ConnectivityManager connManager =
            (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo info = connManager.getActiveNetworkInfo();

        // Are we connected at all, by any interface?
        mIsConnectedToMobileOrWifiOrWimax = (
            ( info != null )
            &&
            info.isConnected()
        );

        if (mIsConnectedToMobileOrWifiOrWimax) {
            mConnectedMobileDataType = info.getType();
            mConnectedMobileDataTypeName = info.getTypeName();
        } else {
            mConnectedMobileDataType = ConnectivityManager.TYPE_NONE;
            mConnectedMobileDataTypeName = null;
        }

        int connectionStatus = intent.getIntExtra(
            ConnectivityManager.EXTRA_INET_CONDITION,
            0
        );

        if (CHATTY) {
            Slog.v(TAG, "updateConnectivityStatus: networkInfo=" + info);
            Slog.v(TAG, "updateConnectivityStatus: connectionStatus=" + connectionStatus);
        }

        int inetCondition = (
            ( connectionStatus > INET_CONDITION_THRESHOLD )
            ? 1
            : 0
        );

if( DEBUG ) {
    inetCondition = 1;
}

        if (INFO) {
            logConnectivityStatus(
                intent.getAction(),
                mConnectedMobileDataType,
                inetCondition
            );
        }

        Slog.i (TAG,
            "updateConnectivityStatus: NetworkInfo: " + info
            + ", inetCondition= " + inetCondition
        );

        switch (mConnectedMobileDataType) {
            case ConnectivityManager.TYPE_MOBILE:
                if (info.isConnected()) {
                    if (CHATTY) {
                        if(mMobileInetCondition != inetCondition)
                            Slog.v (TAG, "updateConnectivityStatus:  Change mMobileInetCondition= " +
                                    inetCondition);
                    }
                    mMobileInetCondition = inetCondition;
                } else {
                    mMobileInetCondition = 0;
                }
                break;
            case ConnectivityManager.TYPE_WIFI:
                if (info.isConnected()) {
                    if (CHATTY) {
                        if(mWifiInetCondition != inetCondition)
                            Slog.v (TAG, "updateConnectivityStatus:  Change mWifiInetCondition= " +
                                    inetCondition);
                    }
                    mWifiInetCondition = inetCondition;
                } else {
                    mWifiInetCondition = 0;
                }
                break;
            case ConnectivityManager.TYPE_WIMAX:
                if (info.isConnected()) {
                    if (CHATTY) {
                        if(mWimaxInetCondition != inetCondition)
                            Slog.v (TAG, "updateConnectivityStatus:  Change mWimaxInetCondition= " +
                                    inetCondition);
                    }
                    mWimaxInetCondition = inetCondition;
                } else {
                    mWimaxInetCondition = 0;
                }
                break;
            case ConnectivityManager.TYPE_NONE:
                if (CHATTY) {
                    Slog.v(TAG, "there is no connection, reset iNetCondition");
                }
                mMobileInetCondition = 0;
                mWifiInetCondition = 0;
                mWimaxInetCondition = 0;
                break;
        }

        if (
            info != null
            &&
            info.getType() == ConnectivityManager.TYPE_BLUETOOTH
        ) {
            mBluetoothIsTethered = info.isConnected();
        } else {
            mBluetoothIsTethered = false;
        }
    }

    //================================================================================================================

    private void logConnectivityStatus(
        String source,
        int netType,
        int netCondition
    ) {
        boolean printLog = false;
        switch(netType) {
        case ConnectivityManager.TYPE_MOBILE:
            printLog = netCondition != mMobileInetCondition;
            break;
        case ConnectivityManager.TYPE_WIFI:
            printLog = netCondition != mWifiInetCondition;
            break;
        case ConnectivityManager.TYPE_WIMAX:
            printLog = netCondition != mWimaxInetCondition;
            break;
        default:
            // do nothing;
            break;
        }
        if (printLog) {
            Slog.i(TAG,
                "the netConditon of netType " + netType +
                " is updated as " + netCondition + " by " + source +
                ",icon color should be " + (netCondition == 1 ? "blue." : "white."));
        }
    }

    //================================================================================================================
    // ===== Update the views ========================================================================================
    //================================================================================================================

    private boolean isConfigMobileDataTypeShowDataDisabledIcon() {

        if(DEBUG)
            Slog.d(TAG,
                "isConfigMobileDataTypeShowDataDisabledIcon: returns mConfigMobileDataTypeShowDataDisabledIcon="
                + mConfigMobileDataTypeShowDataDisabledIcon);

        return mConfigMobileDataTypeShowDataDisabledIcon;
    }

    private boolean isMobileDataEnabled() {
        if ( mCm == null ) {
            mMobileDataIsEnabled = false;
        } else {
            mMobileDataIsEnabled = mCm.getMobileDataEnabled();
        }

        if(DEBUG)
            Slog.d(TAG,
                "getIsDataDisabled: mMobileDataIsEnabled="
                + mMobileDataIsEnabled
            );

        return mMobileDataIsEnabled;
    }

    private boolean isDisabledMobileDataTypeIconShown() {

        boolean displayDataDisabledIcon = isConfigMobileDataTypeShowDataDisabledIcon();

        boolean hasVoiceAndDataService = (
            ( mServiceState != null )
            &&
            ( mServiceState.getState() == ServiceState.STATE_IN_SERVICE )
            //&&
            //( mServiceState.getDataServiceState() == ServiceState.STATE_IN_SERVICE )
        );

        boolean isDataEnabled = isMobileDataEnabled();

        boolean isAllowedByWifi = (
            (! mWifiIsConnected )
            ||
            ( // wifi connected, but mConfigMobileDataTypeShowBothWifiAndMobileNetwork enabled
                mConfigMobileDataTypeShowBothWifiAndMobileNetwork && mWifiIsConnected
            )
        );

        boolean returnValue = (
            displayDataDisabledIcon
            &&
            hasVoiceAndDataService
            &&
            ( ! isDataEnabled )
            &&
            isAllowedByWifi
            &&
            ( ! isEmergencyOnly() )
        );

        if(DEBUG)
            Slog.d(TAG,
                "isDisabledMobileDataTypeIconShown: displayDataDisabledIcon="
                + displayDataDisabledIcon
                + ", hasVoiceAndDataService="
                + hasVoiceAndDataService
                + ", (! isDataEnabled )="
                + ( ! isDataEnabled )
                + ", isAllowedByWifi="
                + isAllowedByWifi
                + ", ( ! isEmergencyOnly() )="
                + ( ! isEmergencyOnly() )
                + ", returnValue="
                + returnValue);

        return returnValue;
    }

    private void adjustDataIconForDisabledOption() {

        if( isDisabledMobileDataTypeIconShown() ) {
            mMobileDataTypeIconId = mMobileDataTypeDisabledIconId;
            mMobileDataActivityIconId = mMobileDataActivityDisabledIconId;

            if(DEBUG) {
                Slog.d(TAG,
                    "getDataDisabledIconShowing: Show Data Disabled icon mPhone.getNetworkType()="
                    + mPhone.getNetworkType()
                    + " mMobileDataTypeIconId(MobileDataType)=" + mMobileDataTypeIconId + "/"
                    + getResourceName(mMobileDataTypeIconId)
                    + " mMobileDataActivityIconId(MobileDataActivity)=" + mMobileDataActivityIconId + "/"
                    + getResourceName(mMobileDataActivityIconId)
                );
            }
        }

    }

    //================================================================================================================

    void refreshViews() {
        Context context = mContext;
        int combinedSignalIconId = 0;
        int mobileRoamingIconId = 0;
        int combinedActivityIconId = 0;
        String combinedLabel = sEmptyString;
        String wifiLabel = sEmptyString;
        String mobileLabel = sEmptyString;
        String noticeText = "";
        boolean isDisplayNoticeTextVisible = false;
        int N;
        final boolean emergencyOnly = isEmergencyOnly();

        // Reset all icons & descriptions to empty.
        mMobilePhoneSignalIconId = 0;
        mMobilePhoneSignalContentDescription = sEmptyString;
        mMobileDataSignalIconId = 0;

        mMobileDataTypeDisabledIconId = 0;
        mMobileDataTypeSuspendedIconId = 0;
        mMobileDataTypeAttachedIconId = 0;
        mMobileDataActivityDisabledIconId = 0;
        mMobileDataActivitySuspendedIconId = 0;
        mMobileDataActivityAttachedIconId = 0;
        mMobileDataTypeIconId = 0;
        mMobileDataActivityIconId = 0;
        mCombinedSignalContentDescription = sEmptyString;
        mMobileDataTypeContentDescription = sEmptyString;
        mMobileDataTypeDisabledDescription = sEmptyString;
        mMobileDataTypeAttachedDescription = sEmptyString;
        mMobileDataTypeSuspendedDescription = sEmptyString;
        mWimaxContentDescription = sEmptyString;

        mMobileRoamingIconId = 0;
        mMobileRoamingContentDescription = sEmptyString;

        mMobileSimIconId = 0;
        mMobileSimContentDescription = sEmptyString;

        mWifiSignalLevelIconId = 0;
        mWifiDataActivityIconId = 0;
        mWifiContentDescription = sEmptyString;

        //--------------------------------------------------------------------------------------------------------
        // We want to update all the views at once to reflect any/all status changes
        //--------------------------------------------------------------------------------------------------------
        // This eliminates latency in the original design.
        // (There are data-coupled dependencies between these update___() calls, do not re-order.)
        //--------------------------------------------------------------------------------------------------------
        // Voice and Base service icons & accessibility descriptions
        updateTelephonySignalStrength();
        updateMobileRoamingIcon();
        updateMobileSimIcon();

        // Mobile data service icons & accessibility descriptions
        updateMobileDataTypeInfo();
        if(
            mConfigWimaxSupported
            &&
            mWimaxIsEnabled
            &&
            mWimaxIsConnected
        ) {
            updateWimaxIcons();
        } else {
            updateMobileDataTypeAndActivityIcons();
        }

        // Wifi data service icons & accessibility descriptions
        updateWifiIcons();
        updateWifiActivityIcon();
        //--------------------------------------------------------------------------------------------------------

        if (DEBUG) {
            Slog.d(TAG, "refreshViews: mMobileDataIsConnected=" + mMobileDataIsConnected);
        }



        if ( ! mHasMobileDataFeature ) {
            mMobileDataSignalIconId = mMobilePhoneSignalIconId = 0;
            mobileLabel = sEmptyString;
            mMobileDataIsConnected = false;
        } else {
            // We want to show the carrier name if in service and either:
            //   - We are connected to mobile data, or
            //   - We are not connected to mobile data, as long as the *reason* packets are not
            //     being routed over that link is that we have better connectivity via wifi.
            // If data is disconnected for some other reason but wifi (or ethernet/bluetooth)
            // is connected, we show nothing.
            // Otherwise (nothing connected) we show "No internet connection".

            if (mMobileDataIsConnected) {
                mobileLabel = mMobileDataNetworkName;
            } else if (
                mIsConnectedToMobileOrWifiOrWimax
                ||
                emergencyOnly
            ) {
                if (
                    hasVoiceService()
                    ||
                    emergencyOnly
                ) {
                    // The isEmergencyOnly test covers the case of a phone with no SIM
                    mobileLabel = mMobileDataNetworkName;
                    if( emergencyOnly ) {
                        noticeText = mMobileDataNetworkName;
                        isDisplayNoticeTextVisible = true;
                    }
                } else {
                    // Tablets, basically
                    mobileLabel = sEmptyString;
                }
            } else {
                mobileLabel = mMobileDataNetworkName;
                noticeText = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
                isDisplayNoticeTextVisible = true;
            }

            // Now for things that should only be shown when actually using mobile data.

            if (mMobileDataIsConnected) {
                combinedSignalIconId = mMobileDataSignalIconId;
                combinedLabel = mobileLabel;
                combinedActivityIconId = mMobileDataActivityIconId;
                combinedSignalIconId = mMobileDataSignalIconId;
                mCombinedSignalContentDescription = mMobileDataTypeContentDescription;
                mobileRoamingIconId = mMobileRoamingIconId;

                if (CHATTY) {
                    Slog.v(TAG,
                        "refreshViews: mobile active:");
                    Slog.v(TAG,
                        "refreshViews:   combinedLabel=\"" + combinedLabel + "\"");
                    Slog.v(TAG,
                        "refreshViews:   combinedActivityIconId=" + combinedActivityIconId
                        + " = mMobileDataActivityIconId=" + mMobileDataActivityIconId
                        + "/" + getResourceName(mMobileDataActivityIconId));
                    Slog.v(TAG,
                        "refreshViews:   combinedSignalIconId=" + combinedSignalIconId
                        + " = mMobileDataSignalIconId=" + mMobileDataSignalIconId
                        + "/" + getResourceName(mMobileDataSignalIconId));
                    Slog.v(TAG,
                        "refreshViews:   mobileRoamingIconId=" + mobileRoamingIconId + "/"
                        + getResourceName(mobileRoamingIconId));
                    Slog.v(TAG,
                        "refreshViews:   mCombinedSignalContentDescription=\""
                        + mCombinedSignalContentDescription + "\"");
                }

            } else if (mWifiIsConnected) {
                mMobileDataActivityIconId = 0;
            }
            else {
                mMobileDataActivityIconId = 0; // no icon (flightmode, or not attached/connecting nor connected)
                combinedActivityIconId = 0; // no icon (flightmode, or not attached/connecting nor connected)
                mCombinedSignalContentDescription = mMobileDataTypeContentDescription;
                mobileRoamingIconId = mMobileRoamingIconId;
            }
        }
        if (CHATTY) {
            Slog.v(TAG, "refreshViews: mWifiIsConnected=" + mWifiIsConnected);
        }

        if (mWifiIsConnected) {
            if (mWifiSsid == null) {
                wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_wifi_nossid);
            } else {
                wifiLabel = mWifiSsid;
                if (CHATTY) {
                    wifiLabel += "xxxxXXXXxxxxXXXX";
                }
            }

            combinedActivityIconId = mWifiDataActivityIconId;
            combinedLabel = wifiLabel;
            combinedSignalIconId = mWifiSignalLevelIconId; // set by updateWifiIcons()
            mCombinedSignalContentDescription = mWifiContentDescription;

            if (CHATTY) {
                Slog.v(TAG,
                    "refreshViews: wifi active:");
                Slog.v(TAG,
                    "refreshViews:   wifiLabel=\"" + wifiLabel + "\"");
                Slog.v(TAG,
                    "refreshViews:   combinedActivityIconId="
                    + combinedActivityIconId + "/" + getResourceName(combinedActivityIconId)
                    + " = mWifiDataActivityIconId="
                    + mWifiDataActivityIconId + "/" + getResourceName(mWifiDataActivityIconId));
                Slog.v(TAG,
                    "refreshViews:   combinedSignalIconId="
                    + combinedSignalIconId + "/" + getResourceName(combinedSignalIconId)
                    + " = mWifiSignalLevelIconId=" + mWifiSignalLevelIconId + "/" + getResourceName(mWifiSignalLevelIconId));
                Slog.v(TAG,
                    "refreshViews:   mCombinedSignalContentDescription=\""
                    + mCombinedSignalContentDescription + "\"");
                Slog.v(TAG,
                    "refreshViews:   combinedLabel=\"" + combinedLabel + "\"");
            }
        } else {
            if (mHasMobileDataFeature) {
                wifiLabel = sEmptyString;
            } else {
                wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            }
            mWifiDataActivityIconId = 0;
           if (CHATTY) {
                Slog.v(TAG,
                    "refreshViews: wifi disabled:");
                Slog.v(TAG,
                    "refreshViews:   wifiLabel=\"" + wifiLabel + "\"");
                Slog.v(TAG,
                    "refreshViews:   mWifiDataActivityIconId="
                    + mWifiDataActivityIconId + "/" + getResourceName(mWifiDataActivityIconId));
            }
        }

        if (CHATTY) {
            Slog.v(TAG, "refreshViews: mBluetoothIsTethered=" + mBluetoothIsTethered);
        }

        if (mBluetoothIsTethered) {
            combinedLabel = mContext.getString(R.string.bluetooth_tethered);
            combinedSignalIconId = mBluetoothTetherIconId;
            mCombinedSignalContentDescription = mContext.getString(
                    R.string.accessibility_bluetooth_tether);
            mobileRoamingIconId = mMobileRoamingIconId;

            if (CHATTY) {
                Slog.v(TAG,
                    "refreshViews: bluetooth tethering active:");
                Slog.v(TAG,
                    "refreshViews:   combinedActivityIconId="
                    + combinedActivityIconId + "/" + getResourceName(combinedActivityIconId)
                    + " = mBluetoothTetherIconId="
                    + mBluetoothTetherIconId + "/" + getResourceName(mBluetoothTetherIconId));
                Slog.v(TAG,
                    "refreshViews:   mCombinedSignalContentDescription=\""
                    + mCombinedSignalContentDescription + "\"");
                Slog.v(TAG,
                    "refreshViews:   combinedLabel=\"" + combinedLabel + "\"");
            }
        }

        if (CHATTY) {
            Slog.v(TAG, "refreshViews: mAirplaneModeIsEnabled=" + mAirplaneModeIsEnabled);
            Slog.v(TAG, "refreshViews: or no service:");
            Slog.v(TAG, "refreshViews:   mServiceState=" + mServiceState + "==null");
            Slog.v(TAG, "refreshViews:   || ( hasVoiceService()=" + hasVoiceService() + "==false && ");
            Slog.v(TAG, "refreshViews:   mServiceState.isEmergencyOnly()="
                + ( (mServiceState != null) ? mServiceState.isEmergencyOnly() : "null" ) + "==false )");
        }

        final boolean ethernetConnected = (mConnectedMobileDataType == ConnectivityManager.TYPE_ETHERNET);
        if (ethernetConnected) {
            combinedLabel = context.getString(R.string.ethernet_label);
        }

        if (
            mAirplaneModeIsEnabled
            &&
            (
                mServiceState == null
                ||
                (
                    ( ! hasVoiceService() )
                    &&
                    ( ! mServiceState.isEmergencyOnly() )
                )
            )
        ) {
            // Only display the airplane-mode icon if not in "emergency calls only" mode.

            mMobilePhoneSignalIconId = 0;
            mMobileDataSignalIconId = 0;
            mMobileDataTypeIconId = 0;
            mMobileSimIconId = 0;
            mMobileDataTypeIconId = 0;
            mMobileDataActivityIconId = 0;

            mAirplaneModeIconId = mTelephonyIcons.getAirplaneModeIconId(
                mNumBarsInSignalIcon
            );

            int descriptionId = mTelephonyIcons.getAirplaneModeDescriptionId(
                mNumBarsInSignalIcon
            );

            if( descriptionId != 0 ) {
                mMobilePhoneSignalContentDescription = getDescriptionStringFromId( descriptionId );
            } else {
                mMobilePhoneSignalContentDescription = "";
            }

            // combined values from connected wifi take precedence over airplane mode
            if (mWifiIsConnected) {
                // Suppress "No internet connection." from mobile if wifi connected.
                mobileLabel = sEmptyString;
            } else {
                if (mHasMobileDataFeature) {
                    // let the mobile icon show "No internet connection."
                    wifiLabel = sEmptyString;
                } else {
                    // let the mobile icon show "No internet connection."
                    wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
                    combinedLabel = wifiLabel;
                }
                mCombinedSignalContentDescription = mMobilePhoneSignalContentDescription;
                combinedSignalIconId = mMobileDataSignalIconId;
            }

            if (CHATTY) {
                Slog.v(TAG,
                    "refreshViews: airplane mode and no service:");
                Slog.v(TAG,
                    "refreshViews:   mMobilePhoneSignalIconId=" + mMobilePhoneSignalIconId
                    + "/" + getResourceName(mMobilePhoneSignalIconId));
                Slog.v(TAG,
                    "refreshViews:   mMobileDataSignalIconId=" + mMobileDataSignalIconId
                    + "/" + getResourceName(mMobileDataSignalIconId));
                Slog.v(TAG,
                    "refreshViews:   mAirplaneModeIconId=" + mAirplaneModeIconId + "/" + getResourceName(mAirplaneModeIconId));
                Slog.v(TAG,
                    "refreshViews:   mMobileDataTypeIconId(MobileDataType)=" + mMobileDataTypeIconId
                    + "/" + getResourceName(mMobileDataTypeIconId));
                Slog.v(TAG,
                    "refreshViews:   mMobileDataTypeIconId(MobileDataType)="
                    + mMobileDataTypeIconId + "/" + getResourceName(mMobileDataTypeIconId));
                Slog.v(TAG,
                    "refreshViews:   wifiLabel=\"" + wifiLabel + "\"");
                Slog.v(TAG,
                    "refreshViews:   mCombinedSignalContentDescription=\"" + mCombinedSignalContentDescription + "\"");
            }
        }
        else if (
            ( ! mMobileDataIsConnected )
            &&
            ( ! mWifiIsConnected )
            &&
            ( ! mBluetoothIsTethered )
            &&
            ( ! mWimaxIsConnected )
            &&
            ( ! ethernetConnected )
        ) {
            // pretty much totally disconnected
            if (CHATTY) {
                Slog.v(TAG, "refreshViews: pretty much totally disconnected:");
                Slog.v(TAG, "refreshViews:   mMobileDataIsConnected=" + mMobileDataIsConnected);
                Slog.v(TAG, "refreshViews:   mWifiIsConnected=" + mWifiIsConnected);
                Slog.v(TAG, "refreshViews:   mBluetoothIsTethered=" + mBluetoothIsTethered);
                Slog.v(TAG, "refreshViews:   mWimaxIsConnected=" + mWimaxIsConnected);
            }

            combinedLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);

            // On devices without mobile radios, we want to show the wifi icon
            combinedSignalIconId = (
                ( mHasMobileDataFeature )
                ? mMobileDataSignalIconId
                : mWifiSignalLevelIconId
            );
            mCombinedSignalContentDescription = (
                ( mHasMobileDataFeature )
                ? mMobileDataTypeContentDescription
                : mWifiContentDescription
            );

            mMobileDataTypeIconId = 0;
            mMobileDataActivityIconId = 0;

            if (
                ( ! mConfigMobileRoamingDoNotShowRoaming )
                &&
                mConfigMobileRoamingShowIconWhenNoDataConnection
                &&
                mPhone.isNetworkRoaming()
            ) {
                mobileRoamingIconId = R.drawable.stat_sys_data_connected_roam;
                if( CHATTY) {
                    Slog.v(TAG,
                    "refreshViews: mobileRoamingIconId=R.drawable.stat_sys_data_connected_roam = "
                    + mobileRoamingIconId + "/" + getResourceName(mobileRoamingIconId)
                    + " (#A)");
                }
            } else {
                mobileRoamingIconId = mMobileRoamingIconId; // SHOULD THIS BE 0? Should this be 2-layer test
                if( CHATTY )
                    Slog.v(TAG,
                        "refreshViews: mobileRoamingIconId var = "
                        + mobileRoamingIconId + "/" + getResourceName(mobileRoamingIconId)
                        + " (#B)");
            }

            if (CHATTY) {
                Slog.v(TAG,
                    "refreshViews:   mMobilePhoneSignalIconId="
                    + mMobilePhoneSignalIconId + "/" + getResourceName(mMobilePhoneSignalIconId));
                Slog.v(TAG,
                    "refreshViews:   mCombinedSignalContentDescription=\""
                    + mCombinedSignalContentDescription + "\"");
                Slog.v(TAG,
                    "refreshViews:   combinedLabel=\"" + combinedLabel + "\"");
                Slog.v(TAG,
                    "refreshViews:   mobileRoamingIconId=" + mobileRoamingIconId + "/"
                    + getResourceName(mobileRoamingIconId));
                Slog.v(TAG,
                    "refreshViews:   mMobileDataTypeIconId(MobileDataType)=" + mMobileDataTypeIconId + "/"
                    + getResourceName(mMobileDataTypeIconId)
                );
            }
        }

        // Adjustment to networkType icon for:
        //   1.) "gprs-attached"
        //   2.) "data disabled by user" in settings->DataUsage
        //   3.) "suspended for wifi"
        if (DEBUG) {
            Slog.d(TAG, "refreshViews: PRE Adjustment to networkType icon");
            Slog.d(TAG, "refreshViews:   mMobileDataTypeIconId=" + mMobileDataTypeIconId
                + " mMobileDataActivityIconId=" + mMobileDataActivityIconId
                + " mMobileInetCondition=" + mMobileInetCondition
                );
        }

        if( isMobileDataEnabled() ) {
            if (DEBUG) {
                Slog.d(TAG, "Data is enabled");
            }
            if( mWifiIsConnected ) {
                if (DEBUG) {
                    Slog.d(TAG, "Data is enabled, wifi is active" );
                }
                if ( mConfigMobileDataTypeShowBothWifiAndMobileNetwork ) {
                    if (DEBUG) {
                        Slog.d(TAG, "Data is enabled, wifi is active, show both is true: show \"suspended-for-wifi icon\"" );
                    }
                    mMobileDataTypeIconId = mMobileDataTypeSuspendedIconId;
                    mMobileDataActivityIconId = mMobileDataActivitySuspendedIconId; // Do not display data activity icon when suspended or disabled
                    mMobileDataTypeContentDescription = mMobileDataTypeSuspendedDescription;
                    if(
                        ( mMobileDataConnectionState == TelephonyManager.DATA_UNKNOWN )
                    ) {
                        // Normal data icon is off, so not-connected should also not be shown.
                        mMobileDataTypeIconId = 0;
                        mMobileDataTypeContentDescription = sEmptyString;
                    }
                } else {
                    if (DEBUG) {
                        Slog.d(TAG, "Data is enabled, wifi is active, show both is false: show \"no icon\"" );
                    }
                    mMobileDataTypeIconId = 0;
                    mMobileDataActivityIconId = 0; // Do not display data activity icon when suspended or disabled
                    mMobileDataTypeContentDescription = sEmptyString;
                }
                if( ! isEmergencyOnly() ) {
                    mMobileInetCondition = 0; // Not using Mobile for primary connection
                }
            } else {
                if (DEBUG) {
                    Slog.d(TAG, "Data is enabled, Wifi is inactive: check for attached mode" );
                }
                if( ! isCdma() ) {
                    if (DEBUG) {
                        Slog.d(TAG, "is GSM" );
                    }
                    if( isAttachedMobileDataTypeIconShown() ) {
                        if (DEBUG) {
                            Slog.d(TAG,
                                "Data is enabled, wifi is inactive, is GSM, attached icon enabled:"
                                + " show \"attached icon\""
                            );
                        }
                        mMobileDataTypeIconId = mMobileDataTypeAttachedIconId;
                        mMobileDataActivityIconId = mMobileDataActivityAttachedIconId; // Do not display data activity icon when suspended or disabled
                        if( ! isEmergencyOnly() ) {
                            mMobileInetCondition = 0; // Not using Mobile for primary connection
                        }
                        mMobileDataTypeContentDescription = mMobileDataTypeAttachedDescription;
                    } else {
                        if (DEBUG) {
                            Slog.d(TAG, "Show normal data and activity icons" );
                        }
                        mMobileDataTypeIconId = mMobileDataTypeIconId; // Redundant
                        mMobileDataActivityIconId = mMobileDataActivityIconId; // Redundant
                        mMobileInetCondition = mMobileInetCondition; // Redundant
                        mMobileDataTypeContentDescription = mMobileDataTypeContentDescription; // Redundant
                    }
                } else {
                    if (DEBUG) {
                        Slog.d(TAG, "is CDMA" );
                    }
                    if (DEBUG) {
                        Slog.d(TAG, "Show normal data and activity icons" );
                    }
                    mMobileDataTypeIconId = mMobileDataTypeIconId; // Redundant
                    mMobileDataActivityIconId = mMobileDataActivityIconId; // Redundant
                    mMobileInetCondition = mMobileInetCondition; // Redundant
                    mMobileDataTypeContentDescription = mMobileDataTypeContentDescription; // Redundant
                }
            }
        } else {
            if( isDisabledMobileDataTypeIconShown() ) {
                if (DEBUG) {
                    Slog.d(TAG, "Data is disabled, show \"disabled icon\"" );
                }
                mMobileDataTypeIconId = mMobileDataTypeDisabledIconId;
                mMobileDataActivityIconId = mMobileDataActivityDisabledIconId; // Do not display data activity icon when suspended or disabled
                if( ! isEmergencyOnly() ) {
                    mMobileInetCondition = 0; // Not using Mobile for primary connection
                }
                mMobileDataTypeContentDescription = mMobileDataTypeDisabledDescription;
            } else {
                if (DEBUG) {
                    Slog.d(TAG, "Data is disabled, show \"no icon\"" );
                }
                mMobileDataTypeIconId = 0;
                mMobileDataActivityIconId = 0; // Do not display data activity icon when suspended or disabled
                if( ! isEmergencyOnly() ) {
                    mMobileInetCondition = 0; // Not using Mobile for primary connection
                }
                mMobileDataTypeContentDescription = sEmptyString;
            }
        }
        if (DEBUG) {
            Slog.d(TAG, "refreshViews: POST Adjustment to networkType icon");
            Slog.d(TAG, "refreshViews:   mMobileDataTypeIconId=" + mMobileDataTypeIconId
                + " mMobileDataActivityIconId=" + mMobileDataActivityIconId
                + " mMobileInetCondition=" + mMobileInetCondition
                );
        }

        if (DEBUG) {
            Slog.d(TAG, "refreshViews: PRE adjustDataIconForDisabledOption:");
            Slog.d(TAG, "refreshViews:   mMobileDataTypeIconId=" + mMobileDataTypeIconId
                + " mMobileDataActivityIconId=" + mMobileDataActivityIconId
                + " mMobileInetCondition=" + mMobileInetCondition
                );
        }
        adjustDataIconForDisabledOption();
        if (DEBUG) {
            Slog.d(TAG, "refreshViews: POST adjustDataIconForDisabledOption");
            Slog.d(TAG, "refreshViews:   mMobileDataTypeIconId=" + mMobileDataTypeIconId
                + " mMobileDataActivityIconId=" + mMobileDataActivityIconId
                + " mMobileInetCondition=" + mMobileInetCondition
                );
        }

        // if data icon is not shown, do not show data activity icon
        if (mMobileDataTypeIconId == 0) mMobileDataActivityIconId = 0;

        if (DEBUG) {
            Slog.d(TAG,
                "refreshViews:   adjusted mMobileDataActivityIconId="
                + mMobileDataActivityIconId + "/"
                + getResourceName(mMobileDataActivityIconId)
            );
        }

        // Use statusbar icons as the icons in QuickSettings panel
        mMobilePhoneSignalQSIconId = mMobilePhoneSignalIconId;
        mMobileRoamingQSIconId = mMobileRoamingIconId;
        mMobileDataTypeQSIconId = mMobileDataTypeIconId;
        mMobileDataActivityQSIconId = mMobileDataActivityIconId;
        mWifiSignalLevelQSIconId = mWifiSignalLevelIconId;


        if (CHATTY) {
            //   Modified format & added getResourceName to many items
            Slog.v(TAG,
                "refreshViews: SUMMARY:\n connected to={"
                + ( mWifiIsConnected ? " wifi" : sEmptyString )
                + ( mMobileDataIsConnected ? " data" : sEmptyString )
                + " }\n level="
                + ( (mSignalStrength == null) ? "null" : Integer.toString(mSignalStrength.getLevel()) )
                + " 1X-corrected level="
                + ( (mSignalStrength == null) ? "null" : Integer.toString(getSignalLevelInternal(mSignalStrength)) )

                + "\n combinedSignalIconId=0x"
                + Integer.toHexString(combinedSignalIconId)
                + "/" + getResourceName(combinedSignalIconId)

                + "\n  combinedActivityIconId=0x"
                + Integer.toHexString(combinedActivityIconId)
                + "/" + getResourceName(combinedActivityIconId)

                + "\n  mAirplaneModeIsEnabled="
                + mAirplaneModeIsEnabled

                + "\n  emergencyOnly="
                + emergencyOnly

                + "\n  mMobileDataActivityStatus="
                + mMobileDataActivityStatus

                + "\n  mMobilePhoneSignalIconId=0x"
                + Integer.toHexString(mMobilePhoneSignalIconId)
                + "/" + getResourceName(mMobilePhoneSignalIconId)

                + "\n  mMobilePhoneSignalQSIconId=0x"
                + Integer.toHexString(mMobilePhoneSignalQSIconId)
                + "/" + getResourceName(mMobilePhoneSignalQSIconId)

                + "\n  mMobileDataSignalIconId=0x"
                + Integer.toHexString(mMobileDataSignalIconId)
                + "/" + getResourceName(mMobileDataSignalIconId)

                + "\n  mMobileRoamingIconId=0x"
                + Integer.toHexString(mMobileRoamingIconId)
                + "/" + getResourceName(mMobileRoamingIconId)

                + "\n  mMobileRoamingQSIconId=0x"
                + Integer.toHexString(mMobileRoamingQSIconId)
                + "/" + getResourceName(mMobileRoamingQSIconId)

                + "\n  mMobileDataTypeIconId(MobileDataType)=0x"
                + Integer.toHexString(mMobileDataTypeIconId)
                + "/" + getResourceName(mMobileDataTypeIconId)

                + "\n  mMobileDataTypeQSIconId=0x"
                + Integer.toHexString(mMobileDataTypeQSIconId)
                + "/" + getResourceName(mMobileDataTypeQSIconId)

                + "\n  mMobileDataActivityIconId=0x"
                + Integer.toHexString(mMobileDataActivityIconId)
                + "/" + getResourceName(mMobileDataActivityIconId)


                + "\n  mMobileDataActivityQSIconId=0x"
                + Integer.toHexString(mMobileDataActivityQSIconId)
                + "/" + getResourceName(mMobileDataActivityQSIconId)

                + "\n  mMobileSimIconId=0x"
                + Integer.toHexString(mMobileSimIconId)
                + "/" + getResourceName(mMobileSimIconId)

                + "\n  mWimaxSignalLevelIconId=0x"
                + Integer.toHexString(mWimaxSignalLevelIconId)
                + "/" + getResourceName(mWimaxSignalLevelIconId)

                + "\n  mWifiSignalLevelIconId=0x"
                + Integer.toHexString(mWifiSignalLevelIconId)
                + "/" + getResourceName(mWifiSignalLevelIconId)

                + "\n  mWifiSignalLevelQSIconId=0x"
                + Integer.toHexString(mWifiSignalLevelQSIconId)
                + "/" + getResourceName(mWifiSignalLevelQSIconId)

                + "\n  mBluetoothTetherIconId=0x"
                + Integer.toHexString(mBluetoothTetherIconId)
                + "/" + getResourceName(mBluetoothTetherIconId)
                + "\n  mobileLabel=\"" + mobileLabel + "\""
                + "\n  wifiLabel=\"" + wifiLabel + "\""
                + "\n  combinedLabel=\"" + combinedLabel + "\""
                + "\n  isDisplayNoticeTextVisible=" + isDisplayNoticeTextVisible + " noticeText=\"" + noticeText + "\""
            );
        }

        if (
            mLastPhoneSignalIconId          != mMobilePhoneSignalIconId
            ||
            mLastWifiDataActivityIconId     != mWifiDataActivityIconId
            ||
            mLastMobileDataActivityIconId   != mMobileDataActivityIconId
            ||
            mLastMobileRoamingIconId        != mMobileRoamingIconId
            ||
            mLastWifiIconId                 != mWifiSignalLevelIconId
            ||
            mLastWimaxIconId                != mWimaxSignalLevelIconId
            ||
            mLastMobileDataTypeIconId       != mMobileDataTypeIconId
            ||
            mLastAirplaneModeIsEnabled      != mAirplaneModeIsEnabled
            ||
            mLastMobileSimIconId            != mMobileSimIconId
            ||
            ( ! mLastMobileLabel.equals(mobileLabel) )
        ) {
            for (SignalCluster cluster : mSignalClusters) {
                refreshSignalCluster(cluster);
            }
            for (NetworkSignalChangedCallback cb : mSignalsChangedCallbacks) {
                notifySignalsChangedCallbacks(cb);
            }
        }

        if (mLastAirplaneModeIsEnabled != mAirplaneModeIsEnabled) {
            mLastAirplaneModeIsEnabled = mAirplaneModeIsEnabled;
        }

        // the phone signal strength icon on phones
        if (mLastPhoneSignalIconId != mMobilePhoneSignalIconId) {
            mLastPhoneSignalIconId = mMobilePhoneSignalIconId;
            N = mMobilePhoneSignalIconViews.size();
            for (
                int i=0;
                i<N;
                i++
            ) {
                final ImageView v = mMobilePhoneSignalIconViews.get(i);
                if( v != null ) {
                    if (mMobilePhoneSignalIconId == 0) {
                        v.setVisibility(View.GONE);
                    } else {
                        v.setVisibility(View.VISIBLE);
                        v.setImageResource(mMobilePhoneSignalIconId);
                        v.setContentDescription(mMobilePhoneSignalContentDescription);
                    }
                }
            }
        }

        if (mLastMobileSimIconId != mMobileSimIconId) {
            if (CHATTY) {
                Slog.v(TAG,
                    "changing MobileSimIconViews icon id to "
                    + mMobileSimIconId + "/" + getResourceName(mMobileSimIconId));
            }
            mLastMobileSimIconId = mMobileSimIconId;
            N = mMobileSimIconViews.size();
            for (
                int i=0;
                i<N;
                i++
            ) {
                final ImageView v = mMobileSimIconViews.get(i);
                if( v != null ) {
                    if (mMobileSimIconId == 0) {
                        v.setVisibility(View.INVISIBLE);
                    } else {
                        v.setVisibility(View.VISIBLE);
                        v.setImageResource(mMobileSimIconId);
                        v.setContentDescription(mMobileSimContentDescription);
                    }
                }
            }
        }

        // the wifi icon on phones
        if (mLastWifiIconId != mWifiSignalLevelIconId) {
            mLastWifiIconId = mWifiSignalLevelIconId;
            N = mWifiIconViews.size();
            for (
                int i=0;
                i<N;
                i++
            ) {
                final ImageView v = mWifiIconViews.get(i);
                if( v != null ) {
                    if (mWifiSignalLevelIconId == 0) {
                        v.setVisibility(View.GONE);
                    } else {
                        v.setVisibility(View.VISIBLE);
                        v.setImageResource(mWifiSignalLevelIconId);
                        v.setContentDescription(mWifiContentDescription);
                    }
                }
            }
        }

        // the wimax icon on phones
        if (mLastWimaxIconId != mWimaxSignalLevelIconId) {
            mLastWimaxIconId = mWimaxSignalLevelIconId;
            N = mWimaxIconViews.size();
            for (
                int i=0;
                i<N;
                i++
            ) {
                final ImageView v = mWimaxIconViews.get(i);
                if( v != null ) {
                    if (mWimaxSignalLevelIconId == 0) {
                        v.setVisibility(View.GONE);
                    } else {
                        v.setVisibility(View.VISIBLE);
                        v.setImageResource(mWimaxSignalLevelIconId);
                        v.setContentDescription(mWimaxContentDescription);
                    }
                }
            }
        }

        // the combined data signal icon (on tablets)
        if (mLastCombinedSignalIconId != combinedSignalIconId) {
            mLastCombinedSignalIconId = combinedSignalIconId;
            N = mCombinedSignalIconViews.size();
            for (
                int i=0;
                i<N;
                i++
            ) {
                final ImageView v = mCombinedSignalIconViews.get(i);
                if( v != null ) {
                    v.setImageResource(combinedSignalIconId);
                    v.setContentDescription(mCombinedSignalContentDescription);
                }
            }
        }

        // the wifi data direction layer
        if (mLastWifiDataActivityIconId != mWifiDataActivityIconId) {
            if (CHATTY) {
                Slog.v(TAG,
                    "changing WifiDataActivityIconViews icon id to "
                    + mWifiDataActivityIconId + "/" + getResourceName(mWifiDataActivityIconId));
            }
            mLastWifiDataActivityIconId = mWifiDataActivityIconId;
            N = mWifiDataActivityIconViews.size();
            for (
                int i=0;
                i<N;
                i++
            ) {
                final ImageView v = mWifiDataActivityIconViews.get(i);
                if( v != null ) {
                    if (mWifiDataActivityIconId == 0) {
                        v.setVisibility(View.GONE);
                    } else {
                        v.setVisibility(View.VISIBLE);
                        v.setImageResource(mWifiDataActivityIconId);
                        v.setContentDescription(mMobileDataTypeContentDescription);
                    }
                }
            }
        }

        // the mobile data network type layer
        if (mLastMobileDataTypeIconId != mMobileDataTypeIconId) {
            mLastMobileDataTypeIconId = mMobileDataTypeIconId;
            N = mMobileDataTypeIconViews.size();
            for (
                int i=0;
                i<N;
                i++
            ) {
                final ImageView v = mMobileDataTypeIconViews.get(i);
                if( v != null ) {
                    if (mMobileDataTypeIconId == 0) {
                        v.setVisibility(View.GONE);
                    } else {
                        v.setVisibility(View.VISIBLE);
                        v.setImageResource(mMobileDataTypeIconId);
                        v.setContentDescription(mMobileDataTypeContentDescription);
                    }
                }
            }
        }

        // the mobile data direction layer
        if (mLastMobileDataActivityIconId != mMobileDataActivityIconId) {
            if (CHATTY) {
                Slog.v(TAG,
                    "changing MobileDataActivityIconViews icon id to "
                    + mMobileDataActivityIconId + "/" + getResourceName(mMobileDataActivityIconId));
            }
            mLastMobileDataActivityIconId = mMobileDataActivityIconId;
            N = mMobileDataActivityIconViews.size();
            for (
                int i=0;
                i<N;
                i++
            ) {
                final ImageView v = mMobileDataActivityIconViews.get(i);
                if( v != null ) {
                    if (mMobileDataActivityIconId == 0) {
                        v.setVisibility(View.GONE);
                    } else {
                        v.setVisibility(View.VISIBLE);
                        v.setImageResource(mMobileDataActivityIconId);
                        v.setContentDescription(mMobileDataTypeContentDescription);
                    }
                }
            }
        }

        // the mobile roaming layer
        if (mLastMobileRoamingIconId != mobileRoamingIconId) {
            if (CHATTY) {
                Slog.v(TAG,
                    "changing RoamingIconViews icon id to "
                    + mobileRoamingIconId + "/" + getResourceName(mobileRoamingIconId));
            }
            mLastMobileRoamingIconId = mobileRoamingIconId;
            N = mMobileRoamingIconViews.size();
            for (
                int i=0;
                i<N;
                i++
            ) {
                final ImageView v = mMobileRoamingIconViews.get(i);
                if( v != null ) {
                    if (mobileRoamingIconId == 0) {
                        v.setVisibility(View.INVISIBLE);
                    } else {
                        v.setVisibility(View.VISIBLE);
                        v.setImageResource(mobileRoamingIconId);
                        v.setContentDescription(mMobileRoamingContentDescription);
                    }
                }
            }
        }

        // the combinedLabel in the notification panel
        if ( ! mLastCombinedLabel.equals(combinedLabel) ) {
            mLastCombinedLabel = combinedLabel;
            N = mCombinedLabelViews.size();
            for (
                int i=0;
                i<N;
                i++
            ) {
                TextView v = mCombinedLabelViews.get(i);
                if( v != null ) {
                    v.setText(combinedLabel);
                }
            }
        }

        // wifi label
        N = mWifiLabelViews.size();
        for (
                int i=0;
                i<N;
                i++
        ) {
            TextView v = mWifiLabelViews.get(i);
            if( v != null ) {
                v.setText(wifiLabel);
                if (sEmptyString.equals(wifiLabel)) {
                    v.setVisibility(View.GONE);
                } else {
                    v.setVisibility(View.VISIBLE);
                }
            }
        }

        // mobile label
        if ( ! mLastMobileLabel.equals(mobileLabel) ) {
            mLastMobileLabel = mobileLabel;
            N = mMobileLabelViews.size();
            for (
                    int i=0;
                    i<N;
                    i++
            ) {
                TextView v = mMobileLabelViews.get(i);
                if( v != null ) {
                    v.setText(mobileLabel);
                    if (sEmptyString.equals(mobileLabel)) {
                        v.setVisibility(View.GONE);
                    } else {
                        v.setVisibility(View.VISIBLE);
                    }
                }
            }
        }

        // mobile label short form
        if ( ! mLastMobileShortLabel.equals(mMobileDataNetworkNameShortForm) ) {

            Slog.d( TAG, "mLastMobileShortLabel=" + mLastMobileShortLabel
                + ", mMobileDataNetworkNameShortForm="+  mMobileDataNetworkNameShortForm
            );

            mLastMobileShortLabel = mMobileDataNetworkNameShortForm;
            N = mMobileLabelShortFormViews.size();
            for (
                    int i=0;
                    i<N;
                    i++
            ) {
                TextView v = mMobileLabelShortFormViews.get(i);
                if (v != null) {
                    if ( ! mIsMobileNetworkNameShortFormVisible ) {
                        v.setVisibility(View.GONE);
                    } else {
                        v.setText(mMobileDataNetworkNameShortForm);
                        v.setVisibility(View.VISIBLE);
                    }
                }
            }
        }

        // e-call label
        N = mNoticeTextViews.size();
        for (
                int i=0;
                i<N;
                i++
        ) {
            TextView v = mNoticeTextViews.get(i);
            if (v != null) {
                if ( ! isDisplayNoticeTextVisible ) {
                    v.setVisibility(View.GONE);
                } else {
                    v.setText(noticeText);
                    v.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    //================================================================================================================

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NetworkController state:");
        pw.println(
            String.format(
                "  %s network type %d (%s)",
                (
                    mIsConnectedToMobileOrWifiOrWimax
                    ? "CONNECTED"
                    : "DISCONNECTED"
                ),
                mConnectedMobileDataType,
                mConnectedMobileDataTypeName
            )
        );
        pw.println("  - telephony ------");
        pw.print("  hasVoiceService()=");
        pw.println(hasVoiceService());
        pw.print("  hasDataService()=");
        pw.println(hasDataService());
        pw.print("  mConfigMobileDataTypeHspaDataDistinguishable=");
        pw.println(mConfigMobileDataTypeHspaDataDistinguishable);
        pw.print("  mMobileDataIsConnected=");
        pw.println(mMobileDataIsConnected);
        pw.print("  mMobileSimState=");
        pw.println(mMobileSimState);
        pw.print("  mPhoneCallingState=");
        pw.println(mPhoneCallingState);
        pw.print("  mMobileDataConnectionState=");
        pw.println(mMobileDataConnectionState);
        pw.print("  mMobileDataActivityStatus=");
        pw.println(mMobileDataActivityStatus);
        pw.print("  mMobileDataNetType=");
        pw.print(mMobileDataNetType);
        pw.print("/");
        pw.println(TelephonyManager.getNetworkTypeName(mMobileDataNetType));
        pw.print("  mServiceState=");
        pw.println(mServiceState);
        pw.print("  mSignalStrength=");
        pw.println(mSignalStrength);
        pw.print("  mLastSignalLevel=");
        pw.println(mLastSignalLevel);
        pw.print("  mMobileDataNetworkName=");
        pw.println(mMobileDataNetworkName);
        pw.print("  mMobileDataNetworkNameDefault=");
        pw.println(mMobileDataNetworkNameDefault);
        pw.print("  mMobileDataNetworkNameSeparator=");
        pw.println(mMobileDataNetworkNameSeparator.replace("\n","\\n"));
        pw.print("  mMobilePhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mMobilePhoneSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mMobilePhoneSignalIconId));
        pw.print("  mMobileRoamingIconId=");
        pw.print(Integer.toHexString(mMobileRoamingIconId));
        pw.print("/");
        pw.println(getResourceName(mMobileRoamingIconId));
        pw.print("  mMobileSimIconId=");
        pw.print(Integer.toHexString(mMobileSimIconId));
        pw.print("/");
        pw.println(getResourceName(mMobileSimIconId));
        pw.print("  mMobileDataSignalIconId=");
        pw.print(Integer.toHexString(mMobileDataSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mMobileDataSignalIconId));
        pw.print("  mMobileDataTypeIconId(MobileDataType)=");
        pw.print(Integer.toHexString(mMobileDataTypeIconId));
        pw.print("/");
        pw.println(getResourceName(mMobileDataTypeIconId));

        pw.println("  - wifi ------");
        pw.print("  mWifiIsEnabled=");
        pw.println(mWifiIsEnabled);
        pw.print("  mWifiIsConnected=");
        pw.println(mWifiIsConnected);
        pw.print("  mWifiRssi=");
        pw.println(mWifiRssi);
        pw.print("  mWifiLevel=");
        pw.println(mWifiLevel);
        pw.print("  mWifiSsid=");
        pw.println(mWifiSsid);
        pw.println(String.format("  mWifiSignalLevelIconId=0x%08x/%s",
                    mWifiSignalLevelIconId,
                    getResourceName(mWifiSignalLevelIconId)));
        pw.print("  mWifiDataActivityStatus=");
        pw.println(mWifiDataActivityStatus);

        if (mConfigWimaxSupported) {
            pw.println("  - wimax ------");
            pw.print("  mWimaxIsEnabled="); pw.println(mWimaxIsEnabled);
            pw.print("  mWimaxState="); pw.println(mWimaxState);
            pw.print("  mWimaxIsConnected="); pw.println(mWimaxIsConnected);
            pw.print("  mWimaxIsIdle="); pw.println(mWimaxIsIdle);
            pw.println(String.format("  mWimaxSignalLevelIconId=0x%08x/%s", mWimaxSignalLevelIconId, getResourceName(mWimaxSignalLevelIconId)));
            pw.println(String.format("  mWimaxSignalLevel=%d", mWimaxSignalLevel));
            pw.println(String.format("  mWimaxState=%d", mWimaxState));
            pw.println(String.format("  mWimaxExtraState=%d", mWimaxExtraState));
        }

        pw.println("  - Bluetooth ----");
        pw.print("  mBtReverseTethered=");
        pw.println(mBluetoothIsTethered);

        pw.println("  - connectivity ------");
        pw.print("  mMobileInetCondition=");
        pw.print(mMobileInetCondition);
        pw.print("  mWifiInetCondition=");
        pw.print(mWifiInetCondition);
        pw.print("  mWimaxInetCondition=");
        pw.println(mWimaxInetCondition);

        pw.println("  - icons ------");
        pw.print("  mLastPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mLastPhoneSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mLastPhoneSignalIconId));
        pw.print("  mLastWifiDataActivityIconId=0x");
        pw.print(Integer.toHexString(mLastWifiDataActivityIconId));
        pw.print("/");
        pw.println(getResourceName(mLastWifiDataActivityIconId));
        pw.print("  mLastWifiIconId=0x");
        pw.print(Integer.toHexString(mLastWifiIconId));
        pw.print("/");
        pw.println(getResourceName(mLastWifiIconId));
        pw.print("  mLastCombinedSignalIconId=0x");
        pw.print(Integer.toHexString(mLastCombinedSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mLastCombinedSignalIconId));
        pw.print("  mLastMobileDataTypeIconId=0x");
        pw.print(Integer.toHexString(mLastMobileDataTypeIconId));
        pw.print("/");
        pw.println(getResourceName(mLastMobileDataTypeIconId));
        pw.print("  mLastCombinedLabel=");
        pw.print(mLastCombinedLabel);
        pw.println(sEmptyString);
    }

    //================================================================================================================

    protected String getResourceName(int resId) {
        if (resId != 0) {
            final Resources res = mContext.getResources();
            try {
                // retain only the id portion (remove "com.android.systemui:drawable/")
                String var = res.getResourceName(resId);
                int delimIndex = var.indexOf('/');
                if(
                    delimIndex > 0
                    &&
                    delimIndex < var.length()
                ) {
                    var = var.substring(
                        delimIndex + 1,
                        var.length()
                    );
                }
                return var;
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(none)";
        }
    }

    //================================================================================================================

    private class StatusBarHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SIG_STRENGTH:
                    updateTelephonySignalStrength();
                    break;

            }
        }
    }

    //================================================================================================================

}
