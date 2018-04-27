/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <algorithm>
#include <fstream>
#include <iterator>
#include <memory>
#include <ostream>
#include <string>
#include <utility>
#include <vector>

#include "android-base/macros.h"
#include "android-base/stringprintf.h"
#include "androidfw/ApkAssets.h"
#include "androidfw/AssetManager2.h"
#include "androidfw/ConfigDescription.h"

#include "idmap2/CommandLineOptions.h"
#include "idmap2/Idmap.h"

using android::ApkAssets;
using android::ApkAssetsCookie;
using android::AssetManager2;
using android::ConfigDescription;
using android::kInvalidCookie;
using android::Res_value;
using android::ResStringPool;
using android::ResTable_config;
using android::base::StringPrintf;
using android::idmap2::CommandLineOptions;
using android::idmap2::IdmapHeader;
using android::idmap2::ResourceId;

namespace {
std::pair<bool, ResourceId> WARN_UNUSED ParseResId(const AssetManager2& am,
                                                   const std::string& raw) {
  // try to parse as a hex number
  char* endptr;
  ResourceId resid;
  resid = strtol(raw.c_str(), &endptr, 16);
  if (*endptr == '\0') {
    return std::make_pair(true, resid);
  }

  // next, try to parse as a package:type/name string
  resid = am.GetResourceId(raw);
  if (resid != 0) {
    return std::make_pair(true, resid);
  }

  // end of the road: raw could not be parsed
  return std::make_pair(false, 0);
}

std::pair<bool, std::string> WARN_UNUSED GetValue(const AssetManager2& am, ResourceId resid) {
  Res_value value;
  ResTable_config config;
  uint32_t flags;
  ApkAssetsCookie cookie = am.GetResource(resid, false, 0, &value, &config, &flags);
  if (cookie == kInvalidCookie) {
    return std::make_pair(false, "");
  }

  std::string out;

  // TODO(martenkongstad): use optional parameter GetResource(..., std::string*
  // stacktrace = NULL) instead
  out.append(StringPrintf("cookie=%d ", cookie));

  out.append("config='");
  out.append(config.toString().c_str());
  out.append("' value=");

  switch (value.dataType) {
    case Res_value::TYPE_INT_DEC:
      out.append(StringPrintf("%d", value.data));
      break;
    case Res_value::TYPE_INT_HEX:
      out.append(StringPrintf("0x%08x", value.data));
      break;
    case Res_value::TYPE_INT_BOOLEAN:
      out.append(value.data != 0 ? "true" : "false");
      break;
    case Res_value::TYPE_STRING: {
      const ResStringPool* pool = am.GetStringPoolForCookie(cookie);
      size_t len;
      const char* str = pool->string8At(value.data, &len);
      if (str != nullptr) {
        out.append(str, len);
      } else {
        // TODO(martenkongstad): implement this
        out.append("UTF16 strings not supported yet");
      }
    } break;
    default:
      out.append("dataType=");
      out.append(std::to_string(value.dataType));
      break;
  }
  return std::make_pair(true, out);
}
}  // namespace

bool Lookup(const std::vector<std::string>& args, std::ostream& out_error) {
  std::vector<std::string> idmap_paths;
  std::string config_str, resid_str;
  const CommandLineOptions opts =
      CommandLineOptions("idmap2 lookup")
          .MandatoryOption("--idmap-path", "input: path to idmap file to load", &idmap_paths)
          .MandatoryOption("--config", "configuration to use", &config_str)
          .MandatoryOption(
              "--resid",
              "Resource ID (in the target package; '0xpptteeee' or 'package:type/name') to look up",
              &resid_str);

  if (!opts.Parse(args, out_error)) {
    return false;
  }

  ConfigDescription config;
  if (!ConfigDescription::Parse(config_str, &config)) {
    out_error << "error: failed to parse config" << std::endl;
    return false;
  }

  std::vector<std::unique_ptr<const ApkAssets>> apk_assets;
  std::string target_path;
  for (const auto& idmap_path : idmap_paths) {
    std::fstream fin(idmap_path);
    auto idmap_header = IdmapHeader::FromBinaryStream(fin);
    fin.close();
    if (!idmap_header) {
      out_error << "error: failed to read idmap from " << idmap_path << std::endl;
      return false;
    }

    if (target_path.empty()) {
      target_path = idmap_header->GetTargetPath();
      auto target_apk = ApkAssets::Load(target_path);
      if (!target_apk) {
        out_error << "error: failed to read target apk from " << idmap_header->GetTargetPath()
                  << std::endl;
        return false;
      }
      apk_assets.push_back(std::move(target_apk));
    } else {
      if (target_path != idmap_header->GetTargetPath()) {
        out_error << "error: different target APKs (expected target APK " << target_path << " but "
                  << idmap_path << " has target APK " << idmap_header->GetTargetPath() << ")"
                  << std::endl;
        return false;
      }
    }

    auto overlay_apk = ApkAssets::LoadOverlay(idmap_path);
    if (!overlay_apk) {
      out_error << "error: failed to read overlay apk from " << idmap_header->GetOverlayPath()
                << std::endl;
      return false;
    }
    apk_assets.push_back(std::move(overlay_apk));
  }

  // AssetManager2::SetApkAssets requires raw ApkAssets pointers, not unique_ptrs
  std::vector<const ApkAssets*> raw_pointer_apk_assets;
  std::transform(apk_assets.cbegin(), apk_assets.cend(), std::back_inserter(raw_pointer_apk_assets),
                 [](const auto& p) -> const ApkAssets* { return p.get(); });
  AssetManager2 am;
  am.SetApkAssets(raw_pointer_apk_assets);
  am.SetConfiguration(config);

  ResourceId resid;
  bool lookup_ok;
  std::tie(lookup_ok, resid) = ParseResId(am, resid_str);
  if (!lookup_ok) {
    out_error << "error: failed to parse resource ID" << std::endl;
    return false;
  }

  std::string value;
  std::tie(lookup_ok, value) = GetValue(am, resid);
  if (!lookup_ok) {
    out_error << StringPrintf("error: resource 0x%08x not found", resid) << std::endl;
    return false;
  }
  std::cout << value << std::endl;

  return true;
}
