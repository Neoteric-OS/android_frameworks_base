package android.media.tv.scan;


import android.os.Bundle;


/**
 * @hide
 */
interface IScanSession {
    // Start a service scan.
    int startScan(in int broadcastType, in String countryCode, in String operator,
            in int[] frequency, in String scanType, in String languageCode);
    // Reset the scan information held in TIS.
    int resetScan();
    // Cancel scan.
    int cancelScan();

    // Get available interface for created ScanExtension interface.
    String[] getAvailableExtensionInterfaceNames();
    // Get extension interface for Scan.
    IBinder getExtensionInterface(in String name);

    // Clear the results of the service scan from the service database.
    int clearServiceList(in Bundle optionalClearParams);
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

    /* DVBI specific functions */
    // Get all of the serviceLists, parsed from Local TV storage, Broadcast, USB file discovery.
    Bundle getServiceLists();
    // Users choose one serviceList from the serviceLists, and install the services.
    int setServiceList(in int serviceListRecId);
    // Get all of the packageData, parsed from the selected serviceList XML.
    Bundle getPackageData();
    // Choose the package using package id and install the corresponding services.
    int setPackage(in String packageId);
    // Get all of the countryRegionData, parsed from the selected serviceList XML.
    Bundle getCountryRegionData();
    // Choose the countryRegion using countryRegion id, and install the corresponding services.
    int setCountryRegion(in String regionId);
    // Get all of the regionData, parsed from the selected serviceList XML.
    Bundle getRegionData();
    // Choose the region using the regionData id, and install the corresponding services.
    int setRegion(in String regionId);

    // Get unique session token for the scan.
    String getSessionToken();
    // Release scan resource, the register listener will be released.
    int release();
}