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

#include "dex_builder.h"

#include "dex/descriptors_names.h"

#include <fstream>
#include <memory>

namespace startop {
namespace dex {

using std::shared_ptr;
using std::string;

using ::dex::kAccPublic;

const TypeDescriptor TypeDescriptor::INT{"I"};
const TypeDescriptor TypeDescriptor::VOID{"V"};

namespace {
// From https://source.android.com/devices/tech/dalvik/dex-format#dex-file-magic
constexpr uint8_t kDexFileMagic[]{0x64, 0x65, 0x78, 0x0a, 0x30, 0x33, 0x38, 0x00};

// Strings lengths can be 32 bits long, but encoded as LEB128 this can take up to five bytes.
constexpr size_t kMaxEncodedStringLength{5};

}  // namespace

void* TrackingAllocator::Allocate(size_t size) {
  std::unique_ptr<uint8_t[]> buffer = std::make_unique<uint8_t[]>(size);
  void* raw_buffer = buffer.get();
  allocations_[raw_buffer] = std::move(buffer);
  return raw_buffer;
}

void TrackingAllocator::Free(void* ptr) { allocations_.erase(allocations_.find(ptr)); }

// Learn how to write DEX files by writing something that is essentially:
/*
package dextest;

public class DexTest {
    public static int foo() { return 5; }
}
*/
void WriteTestDexFile(const string& filename) {
  shared_ptr<DexBuilder> dex_file{std::make_shared<DexBuilder>()};

  auto* class_def = dex_file->MakeClass("dextest.DexTest");
  class_def->source_file = dex_file->GetOrAddString("dextest.xml");

  ClassBuilder cbuilder{dex_file, class_def};
  auto method{cbuilder.CreateMethod("foo", Prototype{TypeDescriptor::VOID})};
  method.Encode();

  ::dex::Writer writer(dex_file);
  TrackingAllocator allocator;
  size_t image_size{0};
  ::dex::u1* image = writer.CreateImage(&allocator, &image_size);

  std::ofstream out_file(filename);
  out_file.write(reinterpret_cast<const char*>(image), image_size);
}

DexBuilder::DexBuilder() { magic = slicer::MemView{kDexFileMagic, sizeof(kDexFileMagic)}; }

ir::String* DexBuilder::GetOrAddString(const std::string& string) {
  ir::String*& entry = strings_[string];

  if (entry == nullptr) {
    // Need to encode the length and then write out the bytes, including 1 byte for null terminator
    auto buffer = std::make_unique<uint8_t[]>(string.size() + kMaxEncodedStringLength + 1);
    uint8_t* string_data_start = ::dex::WriteULeb128(buffer.get(), string.size());

    size_t header_length =
        reinterpret_cast<uintptr_t>(string_data_start) - reinterpret_cast<uintptr_t>(buffer.get());

    auto end = std::copy(string.begin(), string.end(), string_data_start);
    *end = '\0';

    entry = Alloc<ir::String>();
    // +1 for null terminator
    entry->data = slicer::MemView{buffer.get(), header_length + string.size() + 1};
    string_data_.push_back(std::move(buffer));
  }
  return entry;
}

ir::Class* DexBuilder::MakeClass(const std::string& name) {
  auto* class_def = Alloc<ir::Class>();
  auto* type_def = GetOrAddType(art::DotToDescriptor(name.c_str()));
  type_def->class_def = class_def;

  class_def->type = type_def;
  class_def->super_class = GetOrAddType(art::DotToDescriptor("java.lang.Object"));
  class_def->access_flags = kAccPublic;
  return class_def;
}

// TODO(eholk): we probably want GetOrAddString() also
ir::Type* DexBuilder::GetOrAddType(const std::string& descriptor) {
  if (types_by_descriptor_.find(descriptor) != types_by_descriptor_.end()) {
    return types_by_descriptor_[descriptor];
  }

  ir::Type* type = Alloc<ir::Type>();
  type->descriptor = GetOrAddString(descriptor);
  types_by_descriptor_[descriptor] = type;
  return type;
}

ir::Proto* Prototype::Encode(std::shared_ptr<DexBuilder> dex) const {
  auto* proto = dex->Alloc<ir::Proto>();
  proto->shorty = dex->GetOrAddString(Shorty());
  proto->return_type = dex->GetOrAddType(return_type_.descriptor());
  if (param_types_.size() > 0) {
    proto->param_types = dex->Alloc<ir::TypeList>();
    for (auto param_type : param_types_) {
      proto->param_types->types.push_back(dex->GetOrAddType(param_type.descriptor()));
    }
  } else {
    proto->param_types = nullptr;
  }
  return proto;
}

std::string Prototype::Shorty() const {
  std::string shorty;
  shorty.append(return_type_.short_descriptor());
  for (auto type_descriptor : param_types_) {
    shorty.append(type_descriptor.short_descriptor());
  }
  return shorty;
}

ClassBuilder::ClassBuilder(std::shared_ptr<DexBuilder> parent, ir::Class* class_def)
    : parent_(parent), class_(class_def) {}

MethodBuilder ClassBuilder::CreateMethod(const std::string& name, Prototype prototype) {
  auto* dex_name{parent_->GetOrAddString(name)};

  auto* decl = parent_->Alloc<ir::MethodDecl>();
  decl->name = dex_name;
  decl->parent = class_->type;
  decl->prototype = prototype.Encode(parent_);

  return MethodBuilder{parent_, decl};
}

MethodBuilder::MethodBuilder(std::shared_ptr<DexBuilder> dex, ir::MethodDecl* decl)
    : dex_{dex}, decl_{decl} {}

ir::EncodedMethod* MethodBuilder::Encode() const {
  auto* method = dex_->Alloc<ir::EncodedMethod>();
  method->decl = decl_;

  // TODO: encode the code, set the access flags.

  return method;
}

}  // namespace dex
}  // namespace startop
