/*
 * Copyright (C) 2022 The Android Open Source Project
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

// QTI_BEGIN: 2023-04-01: Android_UI: SystemUI: Readapt the Volte HD icon
/*
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

// QTI_END: 2023-04-01: Android_UI: SystemUI: Readapt the Volte HD icon
package com.android.systemui.statusbar.pipeline.mobile.domain.interactor

import android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO
import android.telephony.TelephonyManager.NETWORK_TYPE_GSM
import android.telephony.TelephonyManager.NETWORK_TYPE_LTE
import android.telephony.TelephonyManager.NETWORK_TYPE_UMTS
import com.android.settingslib.SignalIcon.MobileIconGroup
import com.android.settingslib.mobile.TelephonyIcons
import com.android.systemui.log.table.TableLogBuffer
// QTI_BEGIN: 2023-04-01: Android_UI: SystemUI: Readapt network type icon customization
import com.android.systemui.statusbar.pipeline.mobile.data.model.MobileIconCustomizationMode
// QTI_END: 2023-04-01: Android_UI: SystemUI: Readapt network type icon customization
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.util.MobileMappingsProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeMobileIconsInteractor(
    mobileMappings: MobileMappingsProxy,
    val tableLogBuffer: TableLogBuffer,
) : MobileIconsInteractor {
    val THREE_G_KEY = mobileMappings.toIconKey(THREE_G)
    val LTE_KEY = mobileMappings.toIconKey(LTE)
    val FOUR_G_KEY = mobileMappings.toIconKey(FOUR_G)
    val FIVE_G_OVERRIDE_KEY = mobileMappings.toIconKeyOverride(FIVE_G_OVERRIDE)

    /**
     * To avoid a reliance on [MobileMappings], we'll build a simpler map from network type to
     * mobile icon. See TelephonyManager.NETWORK_TYPES for a list of types and [TelephonyIcons] for
     * the exhaustive set of icons
     */
    val TEST_MAPPING: Map<String, MobileIconGroup> =
        mapOf(
            THREE_G_KEY to TelephonyIcons.THREE_G,
            LTE_KEY to TelephonyIcons.LTE,
            FOUR_G_KEY to TelephonyIcons.FOUR_G,
            FIVE_G_OVERRIDE_KEY to TelephonyIcons.NR_5G,
        )

    private val interactorCache: MutableMap<Int, FakeMobileIconInteractor> = mutableMapOf()

    override val isDefaultConnectionFailed = MutableStateFlow(false)

    override val filteredSubscriptions = MutableStateFlow<List<SubscriptionModel>>(listOf())

    override val activeMobileDataSubscriptionId: MutableStateFlow<Int?> = MutableStateFlow(null)

    private val _activeDataConnectionHasDataEnabled = MutableStateFlow(false)
    override val activeDataConnectionHasDataEnabled = _activeDataConnectionHasDataEnabled

    override val activeDataIconInteractor: MutableStateFlow<MobileIconInteractor?> =
        MutableStateFlow(null)

    override val alwaysShowDataRatIcon = MutableStateFlow(false)

    override val alwaysUseCdmaLevel = MutableStateFlow(false)

    override val mobileIsDefault = MutableStateFlow(false)

    override val isSingleCarrier = MutableStateFlow(true)

    override val icons: MutableStateFlow<List<MobileIconInteractor>> = MutableStateFlow(emptyList())

    override val isStackable: StateFlow<Boolean> = MutableStateFlow(false)

    private val _defaultMobileIconMapping = MutableStateFlow(TEST_MAPPING)
    override val defaultMobileIconMapping = _defaultMobileIconMapping

    private val _defaultMobileIconGroup = MutableStateFlow(DEFAULT_ICON)
    override val defaultMobileIconGroup = _defaultMobileIconGroup

    private val _isUserSetUp = MutableStateFlow(true)
    override val isUserSetUp = _isUserSetUp

    override val isForceHidden = MutableStateFlow(false)

    override val isDeviceInEmergencyCallsOnlyMode = MutableStateFlow(false)

// QTI_BEGIN: 2023-03-02: Android_UI: SystemUI: Support customization signal strength icon
    private val _alwaysUseRsrpLevelForLte = MutableStateFlow(false)
    override val alwaysUseRsrpLevelForLte = _alwaysUseRsrpLevelForLte

    private val _hideNoInternetState = MutableStateFlow(false)
    override val hideNoInternetState = _hideNoInternetState

// QTI_END: 2023-03-02: Android_UI: SystemUI: Support customization signal strength icon
// QTI_BEGIN: 2023-04-01: Android_UI: SystemUI: Readapt network type icon customization
    private val _networkTypeIconCustomization = MutableStateFlow(MobileIconCustomizationMode())
    override val networkTypeIconCustomization = _networkTypeIconCustomization

// QTI_END: 2023-04-01: Android_UI: SystemUI: Readapt network type icon customization
// QTI_BEGIN: 2023-04-01: Android_UI: SystemUI: Readapt the Volte HD icon
    private val _showVolteIcon = MutableStateFlow(false)
    override val showVolteIcon = _showVolteIcon
// QTI_END: 2023-04-01: Android_UI: SystemUI: Readapt the Volte HD icon
// QTI_BEGIN: 2023-04-01: Android_UI: SystemUI: Readapt VoWifi icon

    private val _showVowifiIcon = MutableStateFlow(false)
    override val showVowifiIcon = _showVowifiIcon
// QTI_END: 2023-04-01: Android_UI: SystemUI: Readapt VoWifi icon
// QTI_BEGIN: 2024-01-30: Android_UI: SystemUI: Implementation for MSIM C_IWLAN feature

    private val _defaultDataSubId = MutableStateFlow(0)
// QTI_END: 2024-01-30: Android_UI: SystemUI: Implementation for MSIM C_IWLAN feature
    override val defaultDataSubId: MutableStateFlow<Int?> = MutableStateFlow(DEFAULT_DATA_SUB_ID)

    override fun getMobileConnectionInteractorForSubId(subId: Int): FakeMobileIconInteractor {
        return interactorCache
            .getOrElse(subId) { FakeMobileIconInteractor(tableLogBuffer) }
            .also {
                interactorCache[subId] = it
                // Also update the icons
                icons.value = interactorCache.values.toList()
            }
    }

    /**
     * Returns the most recently created interactor for the given subId, or null if an interactor
     * has never been created for that sub.
     */
    fun getInteractorForSubId(subId: Int): FakeMobileIconInteractor? {
        return interactorCache[subId]
    }

    companion object {
        val DEFAULT_ICON = TelephonyIcons.G

        const val DEFAULT_DATA_SUB_ID = 1

        // Use [MobileMappings] to define some simple definitions
        const val THREE_G = NETWORK_TYPE_GSM
        const val LTE = NETWORK_TYPE_LTE
        const val FOUR_G = NETWORK_TYPE_UMTS
        const val FIVE_G_OVERRIDE = OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO
    }
}
