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

#include <fstream>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"

#include "android-base/macros.h"
#include "androidfw/ApkAssets.h"

#include "idmap2/CommandLineOptions.h"
#include "idmap2/Idmap.h"

#include "TestHelpers.h"

using ::testing::IsNull;
using ::testing::NotNull;

namespace android {
namespace idmap2 {

TEST(IdmapTests, TestCanonicalIdmapPathFor) {
  ASSERT_EQ(Idmap::CanonicalIdmapPathFor("/foo", "/vendor/overlay/bar.apk"),
            "/foo/vendor@overlay@bar.apk@idmap");
}

TEST(IdmapTests, CreateIdmapHeaderFromBinaryStream) {
  std::string raw(reinterpret_cast<const char*>(idmap_raw_data), idmap_raw_data_len);
  std::istringstream stream(raw);
  std::unique_ptr<const IdmapHeader> header = IdmapHeader::FromBinaryStream(stream);
  ASSERT_THAT(header, NotNull());
  ASSERT_EQ(header->GetMagic(), 0x504d4449u);
  ASSERT_EQ(header->GetVersion(), 0x01u);
  ASSERT_EQ(header->GetTargetCrc(), 0x1234u);
  ASSERT_EQ(header->GetOverlayCrc(), 0x5678u);
  ASSERT_EQ(header->GetTargetPath(), "target.apk");
  ASSERT_EQ(header->GetOverlayPath(), "overlay.apk");
}

TEST(IdmapTests, CreateIdmapDataHeaderFromBinaryStream) {
  const size_t offset = 0x210;
  std::string raw(reinterpret_cast<const char*>(idmap_raw_data + offset),
                  idmap_raw_data_len - offset);
  std::istringstream stream(raw);

  std::unique_ptr<const IdmapData::Header> header = IdmapData::Header::FromBinaryStream(stream);
  ASSERT_THAT(header, NotNull());
  ASSERT_EQ(header->GetTargetPackageId(), 0x7fu);
  ASSERT_EQ(header->GetTypeCount(), 2u);
}

TEST(IdmapTests, CreateIdmapDataResourceTypeFromBinaryStream) {
  const size_t offset = 0x214;
  std::string raw(reinterpret_cast<const char*>(idmap_raw_data + offset),
                  idmap_raw_data_len - offset);
  std::istringstream stream(raw);

  std::unique_ptr<const IdmapData::ResourceType> data =
      IdmapData::ResourceType::FromBinaryStream(stream);
  ASSERT_THAT(data, NotNull());
  ASSERT_EQ(data->GetTargetType(), 0x02u);
  ASSERT_EQ(data->GetOverlayType(), 0x02u);
  ASSERT_EQ(data->GetEntryCount(), 1u);
  ASSERT_EQ(data->GetEntryOffset(), 0u);
  ASSERT_EQ(data->GetEntry(0), 0u);
}

TEST(IdmapTests, CreateIdmapDataFromBinaryStream) {
  const size_t offset = 0x210;
  std::string raw(reinterpret_cast<const char*>(idmap_raw_data + offset),
                  idmap_raw_data_len - offset);
  std::istringstream stream(raw);

  std::unique_ptr<const IdmapData> data = IdmapData::FromBinaryStream(stream);
  ASSERT_THAT(data, NotNull());
  ASSERT_EQ(data->GetHeader()->GetTargetPackageId(), 0x7fu);
  ASSERT_EQ(data->GetHeader()->GetTypeCount(), 2u);
  const std::vector<std::unique_ptr<const IdmapData::ResourceType>>& types =
      data->GetResourceTypes();
  ASSERT_EQ(types.size(), 2u);

  ASSERT_EQ(types[0]->GetTargetType(), 0x02u);
  ASSERT_EQ(types[0]->GetOverlayType(), 0x02u);
  ASSERT_EQ(types[0]->GetEntryCount(), 1u);
  ASSERT_EQ(types[0]->GetEntryOffset(), 0u);
  ASSERT_EQ(types[0]->GetEntry(0), 0x0000u);

  ASSERT_EQ(types[1]->GetTargetType(), 0x03u);
  ASSERT_EQ(types[1]->GetOverlayType(), 0x03u);
  ASSERT_EQ(types[1]->GetEntryCount(), 3u);
  ASSERT_EQ(types[1]->GetEntryOffset(), 3u);
  ASSERT_EQ(types[1]->GetEntry(0), 0x0000u);
  ASSERT_EQ(types[1]->GetEntry(1), kNoEntry);
  ASSERT_EQ(types[1]->GetEntry(2), 0x0001u);
}

TEST(IdmapTests, CreateIdmapFromBinaryStream) {
  std::string raw(reinterpret_cast<const char*>(idmap_raw_data), idmap_raw_data_len);
  std::istringstream stream(raw);

  std::stringstream error;
  std::unique_ptr<const Idmap> idmap = Idmap::FromBinaryStream(stream, error);
  ASSERT_THAT(idmap, NotNull());

  ASSERT_THAT(idmap->GetHeader(), NotNull());
  ASSERT_EQ(idmap->GetHeader()->GetMagic(), 0x504d4449u);
  ASSERT_EQ(idmap->GetHeader()->GetVersion(), 0x01u);
  ASSERT_EQ(idmap->GetHeader()->GetTargetCrc(), 0x1234u);
  ASSERT_EQ(idmap->GetHeader()->GetOverlayCrc(), 0x5678u);
  ASSERT_EQ(idmap->GetHeader()->GetTargetPath(), "target.apk");
  ASSERT_EQ(idmap->GetHeader()->GetOverlayPath(), "overlay.apk");

  const std::vector<std::unique_ptr<const IdmapData>>& dataBlocks = idmap->GetData();
  ASSERT_EQ(dataBlocks.size(), 1u);

  const std::unique_ptr<const IdmapData>& data = dataBlocks[0];
  ASSERT_EQ(data->GetHeader()->GetTargetPackageId(), 0x7fu);
  ASSERT_EQ(data->GetHeader()->GetTypeCount(), 2u);
  const std::vector<std::unique_ptr<const IdmapData::ResourceType>>& types =
      data->GetResourceTypes();
  ASSERT_EQ(types.size(), 2u);

  ASSERT_EQ(types[0]->GetTargetType(), 0x02u);
  ASSERT_EQ(types[0]->GetOverlayType(), 0x02u);
  ASSERT_EQ(types[0]->GetEntryCount(), 1u);
  ASSERT_EQ(types[0]->GetEntryOffset(), 0u);
  ASSERT_EQ(types[0]->GetEntry(0), 0x0000u);

  ASSERT_EQ(types[1]->GetTargetType(), 0x03u);
  ASSERT_EQ(types[1]->GetOverlayType(), 0x03u);
  ASSERT_EQ(types[1]->GetEntryCount(), 3u);
  ASSERT_EQ(types[1]->GetEntryOffset(), 3u);
  ASSERT_EQ(types[1]->GetEntry(0), 0x0000u);
  ASSERT_EQ(types[1]->GetEntry(1), kNoEntry);
  ASSERT_EQ(types[1]->GetEntry(2), 0x0001u);
}

TEST(IdmapTests, GracefullyFailToCreateIdmapFromCorruptBinaryStream) {
  std::string raw(reinterpret_cast<const char*>(idmap_raw_data),
                  10);  // data too small
  std::istringstream stream(raw);

  std::stringstream error;
  std::unique_ptr<const Idmap> idmap = Idmap::FromBinaryStream(stream, error);
  ASSERT_THAT(idmap, IsNull());
}

TEST(IdmapTests, CreateIdmapFromApkAssets) {
  const std::string target_apk_path(GetTestDataPath() + "/target/target.apk");
  std::unique_ptr<const ApkAssets> target_apk = ApkAssets::Load(target_apk_path);
  ASSERT_THAT(target_apk, NotNull());

  const std::string overlay_apk_path(GetTestDataPath() + "/overlay/overlay.apk");
  std::unique_ptr<const ApkAssets> overlay_apk = ApkAssets::Load(overlay_apk_path);
  ASSERT_THAT(overlay_apk, NotNull());

  std::stringstream error;
  std::unique_ptr<const Idmap> idmap =
      Idmap::FromApkAssets(target_apk_path, *target_apk, overlay_apk_path, *overlay_apk, error);
  ASSERT_THAT(idmap, NotNull());

  ASSERT_THAT(idmap->GetHeader(), NotNull());
  ASSERT_EQ(idmap->GetHeader()->GetMagic(), 0x504d4449u);
  ASSERT_EQ(idmap->GetHeader()->GetVersion(), 0x01u);
  ASSERT_EQ(idmap->GetHeader()->GetTargetCrc(), 0xf5ad1d1d);
  ASSERT_EQ(idmap->GetHeader()->GetOverlayCrc(), 0xd470336b);
  ASSERT_EQ(idmap->GetHeader()->GetTargetPath(), target_apk_path);
  ASSERT_EQ(idmap->GetHeader()->GetOverlayPath(), overlay_apk_path);

  const std::vector<std::unique_ptr<const IdmapData>>& dataBlocks = idmap->GetData();
  ASSERT_EQ(dataBlocks.size(), 1u);

  const std::unique_ptr<const IdmapData>& data = dataBlocks[0];

  ASSERT_EQ(data->GetHeader()->GetTargetPackageId(), 0x7fu);
  ASSERT_EQ(data->GetHeader()->GetTypeCount(), 2u);

  const std::vector<std::unique_ptr<const IdmapData::ResourceType>>& types =
      data->GetResourceTypes();
  ASSERT_EQ(types.size(), 2u);

  ASSERT_EQ(types[0]->GetTargetType(), 0x01u);
  ASSERT_EQ(types[0]->GetOverlayType(), 0x01u);
  ASSERT_EQ(types[0]->GetEntryCount(), 1u);
  ASSERT_EQ(types[0]->GetEntryOffset(), 0u);
  ASSERT_EQ(types[0]->GetEntry(0), 0x0000u);

  ASSERT_EQ(types[1]->GetTargetType(), 0x02u);
  ASSERT_EQ(types[1]->GetOverlayType(), 0x02u);
  ASSERT_EQ(types[1]->GetEntryCount(), 4u);
  ASSERT_EQ(types[1]->GetEntryOffset(), 3u);
  ASSERT_EQ(types[1]->GetEntry(0), 0x0000u);
  ASSERT_EQ(types[1]->GetEntry(1), kNoEntry);
  ASSERT_EQ(types[1]->GetEntry(2), 0x0001u);
  ASSERT_EQ(types[1]->GetEntry(3), 0x0002u);
}

class TestVisitor : public Visitor {
 public:
  explicit TestVisitor(std::ostream& stream) : stream_(stream) {
  }

  void visit(const Idmap& idmap ATTRIBUTE_UNUSED) {
    stream_ << "TestVisitor::visit(Idmap)" << std::endl;
  }

  void visit(const IdmapHeader& idmap ATTRIBUTE_UNUSED) {
    stream_ << "TestVisitor::visit(IdmapHeader)" << std::endl;
  }

  void visit(const IdmapData& idmap ATTRIBUTE_UNUSED) {
    stream_ << "TestVisitor::visit(IdmapData)" << std::endl;
  }

  void visit(const IdmapData::Header& idmap ATTRIBUTE_UNUSED) {
    stream_ << "TestVisitor::visit(IdmapData::Header)" << std::endl;
  }

  void visit(const IdmapData::ResourceType& idmap ATTRIBUTE_UNUSED) {
    stream_ << "TestVisitor::visit(IdmapData::ResourceType)" << std::endl;
  }

 private:
  std::ostream& stream_;
};

TEST(IdmapTests, TestVisitor) {
  std::string raw(reinterpret_cast<const char*>(idmap_raw_data), idmap_raw_data_len);
  std::istringstream stream(raw);

  std::stringstream error;
  std::unique_ptr<const Idmap> idmap = Idmap::FromBinaryStream(stream, error);
  ASSERT_THAT(idmap, NotNull());

  std::stringstream test_stream;
  TestVisitor visitor(test_stream);
  idmap->accept(&visitor);

  ASSERT_EQ(test_stream.str(),
            "TestVisitor::visit(Idmap)\n"
            "TestVisitor::visit(IdmapHeader)\n"
            "TestVisitor::visit(IdmapData)\n"
            "TestVisitor::visit(IdmapData::Header)\n"
            "TestVisitor::visit(IdmapData::ResourceType)\n"
            "TestVisitor::visit(IdmapData::ResourceType)\n");
}

}  // namespace idmap2
}  // namespace android
