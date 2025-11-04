/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.tv.extension.scan;

import android.os.Bundle;

/**
 * @hide
 */
interface IScanSession {
    /**
     * Start a service scan.
     *
     * @param broadcastType  @ScanConstants.BroadcastType broadcast type, such as ATSC
     *        countryCode  countryCode based on ISO 3166-1 alpha-3
     *        operator  @ScanConstants.OperatorType satellite, cable and IP-based operator type
     *        frequency  the list of the frequency of the scan
     *        scanType  @ScanConstants.ScanType type of scan, such as MANUAL
     *        languageCode  language code based on ISO 639-2/T
     *        optionalScanParams  other optional scan parameters
     * @return OpResult.RESULT_SUCCESS if successfully starts else OpResult.RESULT_FAILED
     */
    int startScan(int broadcastType, String countryCode, String operator, in int[] frequency,
        String scanType, String languageCode, in Bundle optionalScanParams);
    /**
     * Reset the scan information held in TIS.
     *
     * @return OpResult.RESULT_SUCCESS if successfully resets else OpResult.RESULT_FAILED
     */
    int resetScan();
    /**
     * Cancel scan.
     *
     * @return OpResult.RESULT_SUCCESS if successfully cancels else OpResult.RESULT_FAILED
     */
    int cancelScan();
    /**
     * Get available interface for created ScanExtension interface.
     *
     * @return list of available extension interface names
     */
    String[] getAvailableExtensionInterfaceNames();
    /**
     * Get extension interface for Scan.
     *
     * @param name TvInputServoceExtensionManager.StandardizedExtensionName extension interface name
     * @return IBinder of the selected extension interface
     */
    IBinder getExtensionInterface(String name);
    /**
     * Clear the results of the service scan from the service database.
     *
     * @param optionalClearParams optional clear parameters; if not null, this bundle should contain
     *                            broadcast_type, operator_id, slot_
     * @return OpResult.RESULT_SUCCESS if successfully clears else OpResult.RESULT_FAILED
     */
    int clearServiceList(in Bundle optionalClearParams);
    /**
     * Store the results of the service scan from the service database.
     *
     * @return OpResult.RESULT_SUCCESS if successfully stores else OpResult.RESULT_FAILED
     */
    int storeServiceList();
    /**
     * Get a service information specified by the service information ID.
     *
     * @param serviceInfoId id obtained from getServiceInfoIdList()
     *        keys  specify the keys in serviceInfo Bundle to get
     * @return requested service information
     */
    Bundle getServiceInfo(String serviceInfoId, in String[] keys);
    /**
     * Get a service information ID list.
     *
     * @return requested service info id list
     */
    String[] getServiceInfoIdList();
    /**
     * Get a list of service info by the filter.
     *
     * @param filterInfo filter information bundle
     *        keys  specify keys in serviceInfo Bundle to get
     * @return requested service info list bundle given filter information bundle and keys
     */
    Bundle getServiceInfoList(in Bundle filterInfo, in String[] keys);
    /**
     * Update the service information.
     *
     * @return OpResult.RESULT_SUCCESS if successfully updates else OpResult.RESULT_FAILED
     */
    int updateServiceInfo(in Bundle serviceInfo);
    /**
     * Updates the service information for the specified service information ID in array list.
     *
     * @return OpResult.RESULT_SUCCESS if successfully updates else OpResult.RESULT_FAILED
     */
    int updateServiceInfoByList(in Bundle[] serviceInfo);
    /**
     * Get unique session token for the scan.
     *
     * @return session token
     */
    String getSessionToken();
    /**
     * Release scan resource, the register listener will be released.
     *
     * @return OpResult.RESULT_SUCCESS if successfully releases else OpResult.RESULT_FAILED
     */
    int release();
    /************************************ DVBI specific functions ********************************/
    /**
     * Get all of the serviceLists, parsed from Local TV storage, Broadcast, USB file discovery.
     *
     * @return Bundle with essential information, including: serviceListName, serviceListLogoUri,
     *         providerName, recordId, uri. Optional information is allowed.
     */
    Bundle getServiceLists();
    /**
     * Users choose one serviceList from the serviceLists, and install the services.
     *
     * @param recordId from serviceListBundle
     * @return OpResult.RESULT_SUCCESS if successfully sets else OpResult.RESULT_FAILED
     */
    int setServiceList(int serviceListRecId);
    /**
     * Get all of the packageData, parsed from the selected serviceList XML.
     *
     * return Bundle with essential information, including packageOrder, packageId, packageText.
     *         Optional information is allowed.
     */
    Bundle getPackageData();
    /**
     * Choose the package using package id and install the corresponding services.
     *
     * @param packageId from packageBundle
     * @return OpResult.RESULT_SUCCESS if successfully sets else OpResult.RESULT_FAILED
     */
    int setPackage(String packageId);
    /**
     * Get all of the countryRegionData, parsed from the selected serviceList XML.
     *
     * @return Bundle with essential information, including countryRegionId, countryRegiontText,
     *         countryRegionCountryCode. Optional information is allowed.
     */
    Bundle getCountryRegionData();
    /**
     * Choose the countryRegion using countryRegion id, and install the corresponding services.
     *
     * @param countryRegionId from CountryRegionData
     * @return OpResult.RESULT_SUCCESS if successfully sets else OpResult.RESULT_FAILED
     */
    int setCountryRegion(String countryRegionId);
    /**
     * Get all of the regionData, parsed from the selected serviceList XML.
     *
     * @return Bundle with essential information, including regionId, regionOrder, regionText,
     *         regionParaentId, subRegionnCount. Optional information is allowed.
     */
    Bundle getRegionData();
    /**
     * Choose the region using the regionData id, and install the corresponding services.
     *
     * @param regionId from regionData
     * @return OpResult.RESULT_SUCCESS if successfully sets else OpResult.RESULT_FAILED
     */
    int setRegion(String regionId);
}
