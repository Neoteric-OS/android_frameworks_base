#include <memory>
#include <cstdint>
#include <cstddef>
#include <fuzzer/FuzzedDataProvider.h>
#include "androidfw/ResourceTypes.h"

void populateDynamicRefTableWithFuzzedData(
    android::DynamicRefTable& table,
    FuzzedDataProvider& fuzzedDataProvider) {

    const size_t numMappings = fuzzedDataProvider.ConsumeIntegralInRange<size_t>(1, 5);
    for (size_t i = 0; i < numMappings; ++i) {
        const uint8_t packageId = fuzzedDataProvider.ConsumeIntegralInRange<uint8_t>(0x02, 0x7F);

        // Generate a package name using only ASCII characters
        std::string packageName;
        size_t packageNameLength = fuzzedDataProvider.ConsumeIntegralInRange<size_t>(1, 10);
        for (size_t j = 0; j < packageNameLength; ++j) {
            // Consume characters only in the ASCII range (0x20 to 0x7E) to ensure valid UTF-8 and readability
            char ch = fuzzedDataProvider.ConsumeIntegralInRange<char>(0x20, 0x7E);
            packageName.push_back(ch);
        }

        // Convert std::string to String16 for compatibility
        android::String16 androidPackageName(packageName.c_str(), packageName.length());

        // Add the mapping to the table
        table.addMapping(androidPackageName, packageId);
    }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    FuzzedDataProvider fuzzedDataProvider(data, size);

    auto dynamic_ref_table = std::make_shared<android::DynamicRefTable>();

    // Populate the DynamicRefTable with fuzzed data
    populateDynamicRefTableWithFuzzedData(*dynamic_ref_table, fuzzedDataProvider);

    // android::ResXMLTree tree;
    auto tree = android::ResXMLTree(std::move(dynamic_ref_table));

    tree.restart(); 

    size_t len = 0;
    auto code = tree.next();
    if (code == android::ResXMLParser::START_TAG) {
        // Access element name
        auto name = tree.getElementName(&len);

        // Access attributes of the current element
        for (size_t i = 0; i < tree.getAttributeCount(); i++) {
            // Access attribute name
            auto attrName = tree.getAttributeName(i, &len);
        }
    } else if (code == android::ResXMLParser::TEXT) {
        const auto text = tree.getText(&len);
    }
    return 0; // Non-zero return values are reserved for future use.
}
