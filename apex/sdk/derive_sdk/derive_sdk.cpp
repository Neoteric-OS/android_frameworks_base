#include <algorithm>
#include <glob.h>
#include <iostream>
#include <vector>

#include <android-base/file.h>
#include <android-base/properties.h>
#include <android-base/strings.h>

#include "frameworks/base/apex/sdk/derive_sdk/sdk.pb.h"

using com::android::sdk::proto::SdkVersion;

int main(int, char**) {
    static constexpr char glob_pattern[] = "/apex/*/etc/sdkinfo.binarypb";
    glob_t glob_result;
    const int ret = glob(glob_pattern, GLOB_MARK, nullptr, &glob_result);
    if (ret != 0 && ret != GLOB_NOMATCH) {
        globfree(&glob_result);
        std::cerr << "glob failed" << std::endl;
        return 1;
    }
    std::vector<std::string> paths;
    for (size_t i = 0; i < glob_result.gl_pathc; i++) {
        std::string path = glob_result.gl_pathv[i];
        // Filter-out /apex/<name>@<ver> paths. The paths are bind-mounted to
        // /apex/<name> paths, so unless we filter them out, we will parse the
        // same file twice.
        std::vector<std::string> segments = android::base::Split(path, "/");
        if (segments.size() >= 3 && segments[2].find('@') != std::string::npos) {
            continue;
        }
        paths.push_back(path);
    }
    globfree(&glob_result);

    std::vector<int> versions;
    for (const auto& path : paths) {
        std::string contents;
        if (!android::base::ReadFileToString(path, &contents, true)) {
            std::cerr << "failed to read " << path << std::endl;
            continue;
        }
        SdkVersion sdk_version;
        if (!sdk_version.ParseFromString(contents)) {
            std::cerr << "failed to parse " << path << std::endl;
            continue;
        }
        versions.push_back(sdk_version.version());
    }
    auto itr = std::min_element(versions.begin(), versions.end());
    std::string prop_value = itr == versions.end() ? "0" : std::to_string(*itr);

    if (!android::base::SetProperty("persist.com.android.sdk.sdk_info", prop_value)) {
        std::cerr << "failed to set sdk_info prop" << std::endl;
        return 1;
    }

    return 0;
}
