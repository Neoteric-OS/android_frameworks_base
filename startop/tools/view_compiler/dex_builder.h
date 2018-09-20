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
#ifndef DEX_BUILDER_H_
#define DEX_BUILDER_H_

#include <map>
#include <string>
#include <vector>

#include "slicer/dex_ir.h"
#include "slicer/writer.h"

namespace startop {
namespace dex {

void WriteTestDexFile(const std::string& filename);

// Our custom allocator for dex::Writer
//
// This keeps track of all allocations and ensures they are freed then
// TrackingAllocator is destroyed. Pointers to memory allocated by this
// allocator must not outlive the allocator.
class TrackingAllocator : public ::dex::Writer::Allocator {
 public:
  virtual void* Allocate(size_t size);
  virtual void Free(void* ptr);

 private:
  std::map<void*, std::unique_ptr<uint8_t[]>> allocations_;
};

class DexBuilder : public ir::DexFile {
 public:
  DexBuilder();

  ir::String* GetOrAddString(const std::string& string);
  ir::Class* MakeClass(const std::string& name);

  ir::Type* GetOrAddType(const std::string& descriptor);

 private:
  // We'll need to allocate buffers for all of the encoded strings we create. This is where we store
  // all of them.
  std::vector<std::unique_ptr<uint8_t[]>> string_data_;

  // Keep track of what types we've defined so we can look them up later.
  std::map<std::string, ir::Type*> types_by_descriptor_;

  // Keep track of what strings we've defined so we can look them up later.
  std::map<std::string, ir::String*> strings_;
};

class TypeDescriptor {
 public:
  static const TypeDescriptor INT;
  static const TypeDescriptor VOID;

  const std::string& descriptor() const { return descriptor_; }
  std::string short_descriptor() const { return descriptor().substr(0, 1); }

 private:
  TypeDescriptor(std::string descriptor) : descriptor_{descriptor} {}

  const std::string descriptor_;
};

class Prototype {
 public:
  template <typename... TypeDescriptors>
  Prototype(TypeDescriptor return_type, TypeDescriptors... param_types)
      : return_type_{return_type}, param_types_{param_types...} {}

  ir::Proto* Encode(std::shared_ptr<DexBuilder> dex) const;

  std::string Shorty() const;

 private:
  const TypeDescriptor return_type_;
  const std::vector<TypeDescriptor> param_types_;
};

class MethodBuilder {
 public:
  MethodBuilder(std::shared_ptr<DexBuilder> dex, ir::MethodDecl* decl);

  ir::EncodedMethod* Encode() const;

 private:
  std::shared_ptr<DexBuilder> dex_;
  ir::MethodDecl* decl_;
};

class ClassBuilder {
 public:
  ClassBuilder(std::shared_ptr<DexBuilder> parent, ir::Class* class_def);

  MethodBuilder CreateMethod(const std::string& name, Prototype prototype);

 private:
  std::shared_ptr<DexBuilder> parent_;
  ir::Class* class_;
};

}  // namespace dex
}  // namespace startop

#endif  // DEX_BUILDER_H_
