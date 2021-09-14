/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "omapi/OmapiHelper.h"

#include <aidl/Vintf.h>
#include <android/binder_manager.h>
#include <hidl/GtestPrinter.h>

using namespace std;
using namespace ::testing;
using namespace android;
using namespace android::se::omapi::common::test;

int main(int argc, char** argv) {
    InitGoogleTest(&argc, argv);
    int status = RUN_ALL_TESTS();
    return status;
}

namespace {

class OMAPISEAccessControlTest2 : public TestWithParam<std::string> {
   protected:
    void SetUp() override {
        ASSERT_TRUE(supportsHardware());
        int32_t apiLevel;
        getFirstApiLevel(&apiLevel);
        ASSERT_TRUE(apiLevel > 27);
        ASSERT_TRUE(supportOMAPIReaders());
        ::ndk::SpAIBinder ks2Binder(AServiceManager_getService(omapiServiceName));
        mOmapiSeService = aidl::android::se::omapi::ISecureElementService::fromBinder(ks2Binder);
        ASSERT_TRUE(mOmapiSeService);

        loadVendorStableReaders(mOmapiSeService, mVSReaders);
    }

    void TearDown() override {
        if (mOmapiSeService != nullptr) {
            if (mVSReaders.size() > 0) {
                for (const auto& [name, reader] : mVSReaders) {
                    reader->closeSessions();
                }
            }
        }
    }

    std::shared_ptr<aidl::android::se::omapi::ISecureElementService> omapiSecureService() {
        return mOmapiSeService;
    }

    constexpr static const char omapiServiceName[] =
        "android.se.omapi.ISecureElementService/default";

    std::vector<uint8_t> AID_40 = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x40};
    std::vector<uint8_t> AID_41 = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x41};
    std::vector<uint8_t> AID_42 = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x42};
    std::vector<uint8_t> AID_43 = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x43};
    std::vector<uint8_t> AID_44 = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x44};
    std::vector<uint8_t> AID_45 = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x45};
    std::vector<uint8_t> AID_46 = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x46};
    std::vector<uint8_t> AID_47 = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x47};
    std::vector<uint8_t> AID_48 = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x48};
    std::vector<uint8_t> AID_49 = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x49};
    std::vector<uint8_t> AID_4A = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x4A};
    std::vector<uint8_t> AID_4B = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x4B};
    std::vector<uint8_t> AID_4C = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x4C};
    std::vector<uint8_t> AID_4D = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x4D};
    std::vector<uint8_t> AID_4E = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x4E};
    std::vector<uint8_t> AID_4F = {0xA0, 0x00, 0x00, 0x04, 0x76, 0x41, 0x6E, 0x64,
                                   0x72, 0x6F, 0x69, 0x64, 0x43, 0x54, 0x53, 0x4F};

    std::vector<std::vector<uint8_t>> AUTHORIZED_AID = {AID_40, AID_41, AID_43, AID_45, AID_46};
    std::vector<std::vector<uint8_t>> UNAUTHORIZED_AID = {
        AID_42, AID_44, AID_47, AID_48, AID_49, AID_4A, AID_4B, AID_4C, AID_4D, AID_4E, AID_4F};

    /* Authorized APDU for AID_40 */
    std::vector<std::vector<uint8_t>> AUTHORIZED_APDU_AID_40 = {
        {0x00, 0x06, 0x00, 0x00},
        {0xA0, 0x06, 0x00, 0x00},
    };
    /* Unauthorized APDU for AID_40 */
    std::vector<std::vector<uint8_t>> UNAUTHORIZED_APDU_AID_40 = {
        {0x00, 0x08, 0x00, 0x00, 0x00},
        {0x80, 0x06, 0x00, 0x00},
        {0xA0, 0x08, 0x00, 0x00, 0x00},
        {0x94, 0x06, 0x00, 0x00, 0x00},
    };

    /* Authorized APDU for AID_41 */
    std::vector<std::vector<uint8_t>> AUTHORIZED_APDU_AID_41 = {
        {0x94, 0x06, 0x00, 0x00},
        {0x94, 0x08, 0x00, 0x00, 0x00},
        {0x94, 0x0C, 0x00, 0x00, 0x01, 0xAA, 0x00},
        {0x94, 0x0A, 0x00, 0x00, 0x01, 0xAA}};
    /* Unauthorized APDU for AID_41 */
    std::vector<std::vector<uint8_t>> UNAUTHORIZED_APDU_AID_41 = {
        {0x00, 0x06, 0x00, 0x00},
        {0x80, 0x06, 0x00, 0x00},
        {0xA0, 0x06, 0x00, 0x00},
        {0x00, 0x08, 0x00, 0x00, 0x00},
        {0x00, 0x0A, 0x00, 0x00, 0x01, 0xAA},
        {0x80, 0x0A, 0x00, 0x00, 0x01, 0xAA},
        {0xA0, 0x0A, 0x00, 0x00, 0x01, 0xAA},
        {0x80, 0x08, 0x00, 0x00, 0x00},
        {0xA0, 0x08, 0x00, 0x00, 0x00},
        {0x00, 0x0C, 0x00, 0x00, 0x01, 0xAA, 0x00},
        {0x80, 0x0C, 0x00, 0x00, 0x01, 0xAA, 0x00},
        {0xA0, 0x0C, 0x00, 0x00, 0x01, 0xAA, 0x00},
    };

    std::shared_ptr<aidl::android::se::omapi::ISecureElementService> mOmapiSeService;

    std::map<std::string, std::shared_ptr<aidl::android::se::omapi::ISecureElementReader>>
        mVSReaders = {};
};

TEST_P(OMAPISEAccessControlTest2, TestAuthorizedAID) {
    testSelectableAid(AUTHORIZED_AID, mVSReaders);
}

TEST_P(OMAPISEAccessControlTest2, TestUnauthorizedAID) {
    testUnauthorisedAid(UNAUTHORIZED_AID, mVSReaders);
}

TEST_P(OMAPISEAccessControlTest2, TestAuthorizedAPDUAID40) {
    testTransmitAPDU(AID_40, AUTHORIZED_APDU_AID_40, mVSReaders);
}

TEST_P(OMAPISEAccessControlTest2, TestUnauthorisedAPDUAID40) {
    testUnauthorisedAPDU(AID_40, UNAUTHORIZED_APDU_AID_40, mVSReaders);
}

TEST_P(OMAPISEAccessControlTest2, TestAuthorizedAPDUAID41) {
    testTransmitAPDU(AID_41, AUTHORIZED_APDU_AID_41, mVSReaders);
}

TEST_P(OMAPISEAccessControlTest2, TestUnauthorisedAPDUAID41) {
    testUnauthorisedAPDU(AID_41, UNAUTHORIZED_APDU_AID_41, mVSReaders);
}

INSTANTIATE_TEST_SUITE_P(PerInstance, OMAPISEAccessControlTest2,
                         testing::ValuesIn(::android::getAidlHalInstanceNames(
                             aidl::android::se::omapi::ISecureElementService::descriptor)),
                         android::hardware::PrintInstanceNameToString);
GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(OMAPISEAccessControlTest2);

}  // namespace
