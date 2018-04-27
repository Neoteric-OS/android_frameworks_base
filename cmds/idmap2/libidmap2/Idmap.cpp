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
#include <iostream>
#include <iterator>
#include <limits>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "android-base/macros.h"
#include "androidfw/AssetManager2.h"
#include "utils/String16.h"
#include "utils/String8.h"

#include "idmap2/Idmap.h"
#include "idmap2/ResourceUtils.h"
#include "idmap2/ZipFile.h"

namespace android {
namespace idmap2 {

#define EXTRACT_TYPE(resid) ((0x00ff0000 & (resid)) >> 16)

#define EXTRACT_ENTRY(resid) (0x0000ffff & (resid))

struct MatchingResources {
  void Add(ResourceId target_resid, ResourceId overlay_resid) {
    TypeId target_typeid = EXTRACT_TYPE(target_resid);
    if (map.find(target_typeid) == map.end()) {
      map.emplace(target_typeid, std::set<std::pair<ResourceId, ResourceId>>());
    }
    map[target_typeid].insert(std::make_pair(target_resid, overlay_resid));
  }

  // target type id -> set { pair { overlay entry id, overlay entry id } }
  std::map<TypeId, std::set<std::pair<ResourceId, ResourceId>>> map;
};

static bool WARN_UNUSED Read16(std::istream& stream, uint16_t* out) {
  uint16_t value;
  if (stream.read(reinterpret_cast<char*>(&value), sizeof(uint16_t))) {
    *out = value;  // TODO(martenkongstad): endianess
    return true;
  }
  return false;
}

static bool WARN_UNUSED Read32(std::istream& stream, uint32_t* out) {
  uint32_t value;
  if (stream.read(reinterpret_cast<char*>(&value), sizeof(uint32_t))) {
    *out = value;
    return true;
  }
  return false;
}

// a string is encoded as a 256 char array; the array is always null-terminated
static bool WARN_UNUSED ReadString(std::istream& stream, std::string* out) {
  char buf[256];
  memset(buf, 0, sizeof(buf));
  if (!stream.read(buf, sizeof(buf))) {
    return false;
  }
  if (buf[sizeof(buf) - 1] != '\0') {
    return false;
  }
  out->assign(buf);
  return true;
}

static ResourceId NameToResid(const AssetManager2& am, const std::string& name) {
  return am.GetResourceId(name);
}

#if 0
static const LoadedPackage* GetPackageByName(const LoadedArsc& loaded_arsc,
                                             const std::string& package_name ATTRIBUTE_UNUSED) {
  const std::vector<std::unique_ptr<const LoadedPackage>>& packages = loaded_arsc.GetPackages();
  if (packages.empty()) {
    return nullptr;
  }

  // idmap version 0x01 doesn't match by package name: the first package is
  // always used
  int id = packages[0]->GetPackageId();
  return loaded_arsc.GetPackageById(id);
}
#else
static const LoadedPackage* GetPackageAtIndex0(const LoadedArsc& loaded_arsc) {
  const std::vector<std::unique_ptr<const LoadedPackage>>& packages = loaded_arsc.GetPackages();
  if (packages.empty()) {
    return nullptr;
  }
  int id = packages[0]->GetPackageId();
  return loaded_arsc.GetPackageById(id);
}
#endif

std::unique_ptr<const IdmapHeader> IdmapHeader::FromBinaryStream(std::istream& stream) {
  std::unique_ptr<IdmapHeader> idmap_header(new IdmapHeader());

  if (!Read32(stream, &idmap_header->magic_) || !Read32(stream, &idmap_header->version_) ||
      !Read32(stream, &idmap_header->target_crc_) || !Read32(stream, &idmap_header->overlay_crc_) ||
      !ReadString(stream, &idmap_header->target_path_) ||
      !ReadString(stream, &idmap_header->overlay_path_)) {
    return nullptr;
  }

  return std::move(idmap_header);
}

std::unique_ptr<const IdmapData::Header> IdmapData::Header::FromBinaryStream(std::istream& stream) {
  std::unique_ptr<IdmapData::Header> idmap_data_header(new IdmapData::Header());

  uint16_t target_package_id16;
  if (!Read16(stream, &target_package_id16) || !Read16(stream, &idmap_data_header->type_count_)) {
    return nullptr;
  }
  idmap_data_header->target_package_id_ = target_package_id16;

  return std::move(idmap_data_header);
}

std::unique_ptr<const IdmapData::ResourceType> IdmapData::ResourceType::FromBinaryStream(
    std::istream& stream) {
  std::unique_ptr<IdmapData::ResourceType> data(new IdmapData::ResourceType());

  uint16_t target_type16, overlay_type16, entry_count;
  if (!Read16(stream, &target_type16) || !Read16(stream, &overlay_type16) ||
      !Read16(stream, &entry_count) || !Read16(stream, &data->entry_offset_)) {
    return nullptr;
  }
  data->target_type_ = target_type16;
  data->overlay_type_ = overlay_type16;
  for (uint16_t i = 0; i < entry_count; i++) {
    ResourceId resid;
    if (!Read32(stream, &resid)) {
      return nullptr;
    }
    data->entries_.push_back(resid);
  }

  return std::move(data);
}

std::unique_ptr<const IdmapData> IdmapData::FromBinaryStream(std::istream& stream) {
  std::unique_ptr<IdmapData> data(new IdmapData());
  data->header_ = IdmapData::Header::FromBinaryStream(stream);
  if (!data->header_) {
    return nullptr;
  }
  for (size_t type_count = 0; type_count < data->header_->GetTypeCount(); type_count++) {
    std::unique_ptr<const ResourceType> type = IdmapData::ResourceType::FromBinaryStream(stream);
    if (!type) {
      return nullptr;
    }
    data->resource_types_.push_back(std::move(type));
  }
  return std::move(data);
}

// TODO(martenkongstad): this function assumes parameters are absolute paths,
// and no trailing / in dir
std::string Idmap::CanonicalIdmapPathFor(const std::string& dir, const std::string& apk_path) {
  std::string copy(++apk_path.cbegin(), apk_path.cend());
  replace(copy.begin(), copy.end(), '/', '@');
  return dir + "/" + copy + "@idmap";
}

std::unique_ptr<const Idmap> Idmap::FromBinaryStream(std::istream& stream,
                                                     std::ostream& out_error) {
  std::unique_ptr<Idmap> idmap(new Idmap());

  idmap->header_ = IdmapHeader::FromBinaryStream(stream);
  if (!idmap->header_) {
    out_error << "error: failed to parse idmap header" << std::endl;
    return nullptr;
  }

  // idmap version 0x01 does not specify the number of data blocks that follow
  // the idmap header; assume exactly one data block
  for (int i = 0; i < 1; i++) {
    std::unique_ptr<const IdmapData> data = IdmapData::FromBinaryStream(stream);
    if (!data) {
      out_error << "error: failed to parse data block " << i << std::endl;
      return nullptr;
    }
    idmap->data_.push_back(std::move(data));
  }

  return std::move(idmap);
}

std::unique_ptr<const Idmap> Idmap::FromApkAssets(const std::string& target_apk_path,
                                                  const ApkAssets& target_apk_assets,
                                                  const std::string& overlay_apk_path,
                                                  const ApkAssets& overlay_apk_assets,
                                                  std::ostream& out_error) {
  AssetManager2 target_asset_manager;
  if (!target_asset_manager.SetApkAssets({&target_apk_assets}, true, false)) {
    out_error << "error: failed to create target asset manager" << std::endl;
    return nullptr;
  }

  AssetManager2 overlay_asset_manager;
  if (!overlay_asset_manager.SetApkAssets({&overlay_apk_assets}, true, false)) {
    out_error << "error: failed to create overlay asset manager" << std::endl;
    return nullptr;
  }

  const LoadedArsc* target_arsc = target_apk_assets.GetLoadedArsc();
  if (!target_arsc) {
    out_error << "error: failed to load target resources.arsc" << std::endl;
    return nullptr;
  }

  const LoadedArsc* overlay_arsc = overlay_apk_assets.GetLoadedArsc();
  if (!overlay_arsc) {
    out_error << "error: failed to load overlay resources.arsc" << std::endl;
    return nullptr;
  }

  const LoadedPackage* target_pkg = GetPackageAtIndex0(*target_arsc);
  if (!target_pkg) {
    out_error << "error: failed to load target package from resources.arsc" << std::endl;
    return nullptr;
  }

  const LoadedPackage* overlay_pkg = GetPackageAtIndex0(*overlay_arsc);
  if (!overlay_pkg) {
    out_error << "error: failed to load overlay package from resources.arsc" << std::endl;
    return nullptr;
  }

  const std::unique_ptr<const ZipFile> target_zip = ZipFile::Open(target_apk_path);
  if (!target_zip) {
    out_error << "error: failed to open target as zip" << std::endl;
    return nullptr;
  }

  const std::unique_ptr<const ZipFile> overlay_zip = ZipFile::Open(overlay_apk_path);
  if (!overlay_zip) {
    out_error << "error: failed to open overlay as zip" << std::endl;
    return nullptr;
  }

  std::unique_ptr<IdmapHeader> header(new IdmapHeader());
  header->magic_ = Idmap::magic;
  header->version_ = Idmap::version;
  bool crc_status;
  std::tie(crc_status, header->target_crc_) = target_zip->Crc("resources.arsc");
  if (!crc_status) {
    out_error << "error: failed to get zip crc for target" << std::endl;
    return nullptr;
  }
  std::tie(crc_status, header->overlay_crc_) = overlay_zip->Crc("resources.arsc");
  if (!crc_status) {
    out_error << "error: failed to get zip crc for overlay" << std::endl;
    return nullptr;
  }
  header->target_path_ = target_apk_path;
  header->overlay_path_ = overlay_apk_path;
  std::unique_ptr<Idmap> idmap(new Idmap());
  idmap->header_ = std::move(header);

  // find the resources that exist in both packages
  MatchingResources matching_resources;
  const auto end = overlay_pkg->end();
  for (auto iter = overlay_pkg->begin(); iter != end; ++iter) {
    const ResourceId overlay_resid = *iter;
    bool lookup_ok;
    std::string name;
    std::tie(lookup_ok, name) = utils::ResidToQualifiedName(overlay_asset_manager, overlay_resid);
    if (!lookup_ok) {
      continue;
    }
    // prepend "<package>:" to turn name into "<package>:<type>/<name>"
    name.insert(0, ":");
    name.insert(0, target_pkg->GetPackageName());
    const ResourceId target_resid = NameToResid(target_asset_manager, name);
    if (target_resid == 0) {
      continue;
    }
    matching_resources.Add(target_resid, overlay_resid);
  }

  // encode idmap data
  std::unique_ptr<IdmapData> data(new IdmapData());
  const auto types_end = matching_resources.map.cend();
  for (auto ti = matching_resources.map.cbegin(); ti != types_end; ++ti) {
    auto ei = ti->second.cbegin();
    std::unique_ptr<IdmapData::ResourceType> type(new IdmapData::ResourceType());
    type->target_type_ = EXTRACT_TYPE(ei->first);
    type->overlay_type_ = EXTRACT_TYPE(ei->second);
    type->entry_offset_ = EXTRACT_ENTRY(ei->first);
    EntryId last_target_entry = kNoEntry;
    for (; ei != ti->second.cend(); ++ei) {
      if (last_target_entry != kNoEntry) {
        int count = EXTRACT_ENTRY(ei->first) - last_target_entry - 1;
        type->entries_.insert(type->entries_.end(), count, kNoEntry);
      }
      type->entries_.push_back(EXTRACT_ENTRY(ei->second));
      last_target_entry = EXTRACT_ENTRY(ei->first);
    }
    data->resource_types_.push_back(std::move(type));
  }

  std::unique_ptr<IdmapData::Header> data_header(new IdmapData::Header());
  data_header->target_package_id_ = target_pkg->GetPackageId();
  data_header->type_count_ = data->resource_types_.size();
  data->header_ = std::move(data_header);

  idmap->data_.push_back(std::move(data));

  return std::move(idmap);
}

void IdmapHeader::accept(Visitor* v) const {
  assert(v != nullptr);
  v->visit(*this);
}

void IdmapData::Header::accept(Visitor* v) const {
  assert(v != nullptr);
  v->visit(*this);
}

void IdmapData::ResourceType::accept(Visitor* v) const {
  assert(v != nullptr);
  v->visit(*this);
}

void IdmapData::accept(Visitor* v) const {
  assert(v != nullptr);
  v->visit(*this);
  header_->accept(v);
  auto end = resource_types_.cend();
  for (auto iter = resource_types_.cbegin(); iter != end; ++iter) {
    (*iter)->accept(v);
  }
}

void Idmap::accept(Visitor* v) const {
  assert(v != nullptr);
  v->visit(*this);
  header_->accept(v);
  auto end = data_.cend();
  for (auto iter = data_.cbegin(); iter != end; ++iter) {
    (*iter)->accept(v);
  }
}

}  // namespace idmap2
}  // namespace android
