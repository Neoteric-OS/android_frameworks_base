#ifndef OMAPI_AIDL_VTS_FUNCTIONAL_COMMON_OMAPI_OMAPIHELPER_H_
#define OMAPI_AIDL_VTS_FUNCTIONAL_COMMON_OMAPI_OMAPIHELPER_H_

#include <aidl/android/se/omapi/BnSecureElementListener.h>
#include <aidl/android/se/omapi/ISecureElementChannel.h>
#include <aidl/android/se/omapi/ISecureElementListener.h>
#include <aidl/android/se/omapi/ISecureElementReader.h>
#include <aidl/android/se/omapi/ISecureElementService.h>
#include <aidl/android/se/omapi/ISecureElementSession.h>

#include <VtsCoreUtil.h>
#include <android-base/logging.h>
#include <cutils/properties.h>
#include <gtest/gtest.h>

namespace android {
namespace se {
namespace omapi {
namespace common {
namespace test {

void loadVendorStableReaders(
        std::shared_ptr<aidl::android::se::omapi::ISecureElementService> omapiSecureService,
        std::map<std::string,
            std::shared_ptr<aidl::android::se::omapi::ISecureElementReader>>& seReaders);

void testSelectableAid(
        std::vector<std::vector<uint8_t>> authorizedAids,
        std::map<std::string,
            std::shared_ptr<aidl::android::se::omapi::ISecureElementReader>> seReaders);

void testUnauthorisedAid(
        std::vector<std::vector<uint8_t>> unAuthorizedAids,
        std::map<std::string,
            std::shared_ptr<aidl::android::se::omapi::ISecureElementReader>> seReaders);

void testTransmitAPDU(
        std::vector<uint8_t> aid,
        std::vector<std::vector<uint8_t>> apdus,
        std::map<std::string,
            std::shared_ptr<aidl::android::se::omapi::ISecureElementReader>> seReaders);

void testUnauthorisedAPDU(
        std::vector<uint8_t> aid,
        std::vector<std::vector<uint8_t>> apdus,
        std::map<std::string,
            std::shared_ptr<aidl::android::se::omapi::ISecureElementReader>> seReaders);

bool supportOMAPIReaders();

void getFirstApiLevel(int32_t* outApiLevel);

bool supportsHardware();
}
}
}
}
}

#endif  // OMAPI_AIDL_VTS_FUNCTIONAL_COMMON_OMAPI_OMAPIHELPER_H_
