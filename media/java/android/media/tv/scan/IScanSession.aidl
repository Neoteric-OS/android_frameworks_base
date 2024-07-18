package android.media.tv.scan;


import android.os.Bundle;


/**
 * @hide
 */
interface IScanSession {
    // Start a service scan.
    int startScan(in int brodcastType, in String countryCode, in String operator,
            in Bundle scanParams);
    // Reset the scan information held in TIS.
    int resetScan();
    // Cancel scan.
    int cancelScan();

    // Get available interface for created ScanExtension interface.
    String[] getAvailableExtensionInterfaceNames();
    // Get extension interface for Scan.
    IBinder getExtensionInterface(in String name);

    // Clear the results of the service scan from the service database.
    int clearServiceList(in Bundle params);
    // Store the results of the service scan from the service database.
    int storeServiceList();
    // Get a service information specified by the service information ID.
    Bundle getServiceInfo(in String serviceInfoId, in String[] keys);
    // Get a service information ID list.
    String[] getServiceInfoIdList();
    // Get a list of service info by the filter.
    Bundle getServiceInfoList(in Bundle filterInfo, in String[] keys);
    // Update the service information.
    int updateServiceInfo(in Bundle serviceInfo);
    // Updates the service information for the specified service information ID in array list.
    int updateServiceInfoByList(in Bundle[] serviceInfo);

    // DVBI specific functions
    Bundle getPackageData();
    int setPackage(in String packageId);
    Bundle getCountryRegionData();
    int setCountryRegion(in String regionId);
    Bundle getRegionData();
    int setRegion(in String regionId);

    // Get unique session token for the scan.
    String getSessionToken();
    // Release scan resource, the register listener will be released.
    int release();
}