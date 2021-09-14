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

using namespace std;
using namespace ::testing;
using namespace android;

namespace android {
namespace se {
namespace omapi {
namespace common {
namespace test {

static inline std::string const UICC_READER_PREFIX = "SIM";
static inline std::string const ESE_READER_PREFIX = "eSE";
static inline std::string const SD_READER_PREFIX = "SD";
static inline std::string const FEATURE_SE_OMAPI_UICC = "android.hardware.se.omapi.uicc";
static inline std::string const FEATURE_SE_OMAPI_ESE = "android.hardware.se.omapi.ese";
static inline std::string const FEATURE_SE_OMAPI_SD = "android.hardware.se.omapi.sd";
static inline std::string const FEATURE_SE_LOW_RAM = "ro.config.low_ram";
static inline std::string const FEATURE_SE_HARDWARE_WATCH = "android.hardware.type.watch";
static inline std::string const FEATURE_SE_OMAPI_SERVICE = "com.android.se";
static inline std::string const FEATURE_SE_SDK_VERSION = "ro.build.version.sdk";
static inline std::string const FEATURE_SE_API_LEVEL = "ro.product.first_api_level";

class SEListener : public ::aidl::android::se::omapi::BnSecureElementListener {};

/**
 * Verifies TLV data
 *
 * @return true if the data is tlv formatted, false otherwise
 */
bool verifyBerTlvData(std::vector<uint8_t> tlv) {
    if (tlv.size() == 0) {
        LOG(ERROR) << "Invalid tlv, null";
        return false;
    }
    int i = 0;
    if ((tlv[i++] & 0x1F) == 0x1F) {
        // extra byte for TAG field
        i++;
    }

    int len = tlv[i++] & 0xFF;
    if (len > 127) {
        // more than 1 byte for length
        int bytesLength = len - 128;
        len = 0;
        for (int j = bytesLength; j > 0; j--) {
            len += (len << 8) + (tlv[i++] & 0xFF);
        }
    }
    // Additional 2 bytes for the SW
    return (tlv.size() == (i + len + 2));
}

void loadVendorStableReaders(
        std::shared_ptr<aidl::android::se::omapi::ISecureElementService> omapiSecureService,
        std::map<std::string,
            std::shared_ptr<aidl::android::se::omapi::ISecureElementReader>>& seReaders) {
    std::vector<std::string> readers = {};

    if (omapiSecureService != NULL) {
        auto status = omapiSecureService->getReaders(&readers);
        ASSERT_TRUE(status.isOk()) << status.getMessage();

        for (auto readerName : readers) {
            std::shared_ptr<::aidl::android::se::omapi::ISecureElementReader> reader;
            status = omapiSecureService->getReader(readerName, &reader);
            ASSERT_TRUE(status.isOk()) << status.getMessage();

            seReaders[readerName] = reader;
        }
    }
}

void testSelectableAid(
        std::vector<std::vector<uint8_t>> authorizedAids,
        std::map<std::string,
            std::shared_ptr<aidl::android::se::omapi::ISecureElementReader>> seReaders) {
    for (auto aid : authorizedAids) {
        std::shared_ptr<aidl::android::se::omapi::ISecureElementSession> session;
        std::shared_ptr<aidl::android::se::omapi::ISecureElementChannel> channel;
        auto seListener = ndk::SharedRefBase::make<SEListener>();

        if (seReaders.size() > 0) {
            for (const auto& [name, reader] : seReaders) {
                std::vector<uint8_t> selectResponse = {};
                ASSERT_NE(reader, nullptr) << "reader is null";

                bool status = false;
                auto res = reader->isSecureElementPresent(&status);
                ASSERT_TRUE(res.isOk()) << res.getMessage();
                ASSERT_TRUE(status);

                res = reader->openSession(&session);
                ASSERT_TRUE(res.isOk()) << res.getMessage();
                ASSERT_NE(session, nullptr) << "Could not open session";

                res = session->openLogicalChannel(aid, 0x00, seListener, &channel);
                ASSERT_TRUE(res.isOk()) << res.getMessage();
                ASSERT_NE(channel, nullptr) << "Could not open channel";

                res = channel->getSelectResponse(&selectResponse);
                ASSERT_TRUE(res.isOk()) << "failed to get Select Response";
                ASSERT_GE(selectResponse.size(), 2);

                if (channel != nullptr) channel->close();
                if (session != nullptr) session->close();

                ASSERT_EQ((selectResponse[selectResponse.size() - 1] & 0xFF), (0x00));
                ASSERT_EQ((selectResponse[selectResponse.size() - 2] & 0xFF), (0x90));
                ASSERT_TRUE(verifyBerTlvData(selectResponse)) << "Select Response is not complete";
            }
        }
    }
}

void testUnauthorisedAid(
        std::vector<std::vector<uint8_t>> unAuthorizedAids,
        std::map<std::string,
            std::shared_ptr<aidl::android::se::omapi::ISecureElementReader>> seReaders) {
    for (auto aid : unAuthorizedAids) {
        std::shared_ptr<aidl::android::se::omapi::ISecureElementSession> session;
        std::shared_ptr<aidl::android::se::omapi::ISecureElementChannel> channel;
        auto seListener = ndk::SharedRefBase::make<SEListener>();

        if (seReaders.size() > 0) {
            for (const auto& [name, reader] : seReaders) {
                ASSERT_NE(reader, nullptr) << "reader is null";

                bool status = false;
                auto res = reader->isSecureElementPresent(&status);
                ASSERT_TRUE(res.isOk()) << res.getMessage();
                ASSERT_TRUE(status);

                res = reader->openSession(&session);
                ASSERT_TRUE(res.isOk()) << res.getMessage();
                ASSERT_NE(session, nullptr) << "Could not open session";

                res = session->openLogicalChannel(aid, 0x00, seListener, &channel);

                if (channel != nullptr) channel->close();
                if (session != nullptr) session->close();

                if (!res.isOk()) {
                    ASSERT_EQ(res.getExceptionCode(), EX_SECURITY);
                    ASSERT_FALSE(res.isOk()) << "expected failed status for this test";
                }
            }
        }
    }
}

void testTransmitAPDU(
        std::vector<uint8_t> aid,
        std::vector<std::vector<uint8_t>> apdus,
        std::map<std::string,
            std::shared_ptr<aidl::android::se::omapi::ISecureElementReader>> seReaders) {
    for (auto apdu : apdus) {
        std::shared_ptr<aidl::android::se::omapi::ISecureElementSession> session;
        std::shared_ptr<aidl::android::se::omapi::ISecureElementChannel> channel;
        auto seListener = ndk::SharedRefBase::make<SEListener>();

        if (seReaders.size() > 0) {
            for (const auto& [name, reader] : seReaders) {
                ASSERT_NE(reader, nullptr) << "reader is null";
                bool status = false;
                std::vector<uint8_t> selectResponse = {};
                std::vector<uint8_t> transmitResponse = {};
                auto res = reader->isSecureElementPresent(&status);
                ASSERT_TRUE(res.isOk()) << res.getMessage();
                ASSERT_TRUE(status);

                res = reader->openSession(&session);
                ASSERT_TRUE(res.isOk()) << res.getMessage();
                ASSERT_NE(session, nullptr) << "Could not open session";

                res = session->openLogicalChannel(aid, 0x00, seListener, &channel);
                ASSERT_TRUE(res.isOk()) << res.getMessage();
                ASSERT_NE(channel, nullptr) << "Could not open channel";

                res = channel->getSelectResponse(&selectResponse);
                ASSERT_TRUE(res.isOk()) << "failed to get Select Response";
                ASSERT_GE(selectResponse.size(), 2);
                ASSERT_EQ((selectResponse[selectResponse.size() - 1] & 0xFF), (0x00));
                ASSERT_EQ((selectResponse[selectResponse.size() - 2] & 0xFF), (0x90));
                ASSERT_TRUE(verifyBerTlvData(selectResponse)) << "Select Response is not complete";

                res = channel->transmit(apdu, &transmitResponse);
                LOG(INFO) << "STATUS OF TRNSMIT: " << res.getExceptionCode()
                          << " Message: " << res.getMessage();
                if (channel != nullptr) channel->close();
                if (session != nullptr) session->close();
                ASSERT_TRUE(res.isOk()) << "failed to transmit";
            }
        }
    }
}

void testUnauthorisedAPDU(
        std::vector<uint8_t> aid,
        std::vector<std::vector<uint8_t>> apdus,
        std::map<std::string,
            std::shared_ptr<aidl::android::se::omapi::ISecureElementReader>> seReaders) {
    for (auto apdu : apdus) {
        std::shared_ptr<aidl::android::se::omapi::ISecureElementSession> session;
        std::shared_ptr<aidl::android::se::omapi::ISecureElementChannel> channel;
        auto seListener = ndk::SharedRefBase::make<SEListener>();

        if (seReaders.size() > 0) {
            for (const auto& [name, reader] : seReaders) {
                ASSERT_NE(reader, nullptr) << "reader is null";
                bool status = false;
                std::vector<uint8_t> selectResponse = {};
                std::vector<uint8_t> transmitResponse = {};
                auto res = reader->isSecureElementPresent(&status);
                ASSERT_TRUE(res.isOk()) << res.getMessage();
                ASSERT_TRUE(status);

                res = reader->openSession(&session);
                ASSERT_TRUE(res.isOk()) << res.getMessage();
                ASSERT_NE(session, nullptr) << "Could not open session";

                res = session->openLogicalChannel(aid, 0x00, seListener, &channel);
                ASSERT_TRUE(res.isOk()) << res.getMessage();
                ASSERT_NE(channel, nullptr) << "Could not open channel";

                res = channel->getSelectResponse(&selectResponse);
                ASSERT_TRUE(res.isOk()) << "failed to get Select Response";
                ASSERT_GE(selectResponse.size(), 2);
                ASSERT_EQ((selectResponse[selectResponse.size() - 1] & 0xFF), (0x00));
                ASSERT_EQ((selectResponse[selectResponse.size() - 2] & 0xFF), (0x90));
                ASSERT_TRUE(verifyBerTlvData(selectResponse)) << "Select Response is not complete";

                res = channel->transmit(apdu, &transmitResponse);
                LOG(INFO) << "STATUS OF TRNSMIT: " << res.getExceptionCode()
                          << " Message: " << res.getMessage();

                if (channel != nullptr) channel->close();
                if (session != nullptr) session->close();
                if (!res.isOk()) {
                    ASSERT_EQ(res.getExceptionCode(), EX_SECURITY);
                    ASSERT_FALSE(res.isOk()) << "expected failed status for this test";
                }
            }
        }
    }
}

bool supportOMAPIReaders() {
    return (deviceSupportsFeature(FEATURE_SE_OMAPI_UICC.c_str()) ||
            deviceSupportsFeature(FEATURE_SE_OMAPI_ESE.c_str()) ||
            deviceSupportsFeature(FEATURE_SE_OMAPI_SD.c_str()));
}

void getFirstApiLevel(int32_t* outApiLevel) {
    int32_t firstApiLevel = property_get_int32(FEATURE_SE_API_LEVEL.c_str(), -1);
    if (firstApiLevel < 0) {
        firstApiLevel = property_get_int32(FEATURE_SE_SDK_VERSION.c_str(), -1);
    }
    ASSERT_GT(firstApiLevel, 0);  // first_api_level must exist
    *outApiLevel = firstApiLevel;
    return;
}

bool supportsHardware() {
    bool lowRamDevice = property_get_bool(FEATURE_SE_LOW_RAM.c_str(), true);
    return !lowRamDevice || deviceSupportsFeature(FEATURE_SE_HARDWARE_WATCH.c_str()) ||
            deviceSupportsFeature(FEATURE_SE_OMAPI_SERVICE.c_str());  // android.se.omapi
}

}
}
}
}
}
