/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef UTIL_H_
#define UTIL_H_

#include <cstdlib>
#include <memory>
#include <optional>
#include <sstream>
#include <vector>

#include "android-base/result.h"
#include "android-base/macros.h"

#include "androidfw/StringPiece.h"

#ifdef __ANDROID__
#define ANDROID_LOG(x) LOG(x)
#else
#define ANDROID_LOG(x) std::stringstream()
#endif

namespace android {
namespace util {

/**
 * Makes a std::unique_ptr<> with the template parameter inferred by the
 * compiler.
 * This will be present in C++14 and can be removed then.
 */
template <typename T, class... Args>
std::unique_ptr<T> make_unique(Args&&... args) {
  return std::unique_ptr<T>(new T{std::forward<Args>(args)...});
}

// Based on std::unique_ptr, but uses free() to release malloc'ed memory
// without incurring the size increase of holding on to a custom deleter.
template <typename T>
class unique_cptr {
 public:
  using pointer = typename std::add_pointer<T>::type;

  constexpr unique_cptr() : ptr_(nullptr) {}
  constexpr explicit unique_cptr(std::nullptr_t) : ptr_(nullptr) {}
  explicit unique_cptr(pointer ptr) : ptr_(ptr) {}
  unique_cptr(unique_cptr&& o) noexcept : ptr_(o.ptr_) { o.ptr_ = nullptr; }

  ~unique_cptr() { std::free(reinterpret_cast<void*>(ptr_)); }

  inline unique_cptr& operator=(unique_cptr&& o) noexcept {
    if (&o == this) {
      return *this;
    }

    std::free(reinterpret_cast<void*>(ptr_));
    ptr_ = o.ptr_;
    o.ptr_ = nullptr;
    return *this;
  }

  inline unique_cptr& operator=(std::nullptr_t) {
    std::free(reinterpret_cast<void*>(ptr_));
    ptr_ = nullptr;
    return *this;
  }

  pointer release() {
    pointer result = ptr_;
    ptr_ = nullptr;
    return result;
  }

  inline pointer get() const { return ptr_; }

  void reset(pointer ptr = pointer()) {
    if (ptr == ptr_) {
      return;
    }

    pointer old_ptr = ptr_;
    ptr_ = ptr;
    std::free(reinterpret_cast<void*>(old_ptr));
  }

  inline void swap(unique_cptr& o) { std::swap(ptr_, o.ptr_); }

  inline explicit operator bool() const { return ptr_ != nullptr; }

  inline typename std::add_lvalue_reference<T>::type operator*() const { return *ptr_; }

  inline pointer operator->() const { return ptr_; }

  inline bool operator==(const unique_cptr& o) const { return ptr_ == o.ptr_; }

  inline bool operator!=(const unique_cptr& o) const { return ptr_ != o.ptr_; }

  inline bool operator==(std::nullptr_t) const { return ptr_ == nullptr; }

  inline bool operator!=(std::nullptr_t) const { return ptr_ != nullptr; }

 private:
  DISALLOW_COPY_AND_ASSIGN(unique_cptr);

  pointer ptr_;
};

template <typename T>
using Result = base::expected<T, const char*>;

// A tri-state result class that can represent a value, null, or an error.
template <typename T>
class OptionalResult {
 public:
  constexpr OptionalResult() = default;
  constexpr OptionalResult(const T& value) : result(value) {};
  constexpr OptionalResult(T&& value) : result(std::forward<T>(value)) {};
  template <typename U> constexpr OptionalResult(base::unexpected<U>&& error) :
      result(std::forward<base::unexpected<U>>(error)) {};

  // Retrieves the value. This function should only be used after checking that the result
  // represents an error using `has_value`.
  constexpr T* operator->() { return result.operator->(); }
  constexpr const T* operator->() const { return result.operator->(); }
  constexpr const T& operator*() const& { return result.value(); }
  constexpr T& operator*() & { return result.value(); }
  constexpr const T&& operator*() const&& { return result.value(); }
  constexpr T&& operator*() && { return result.value(); }

  constexpr const T& value() const& { return result.value(); }
  constexpr T& value() & { return result.value(); }
  constexpr const T&& value() const&& { return result.value(); }
  constexpr T&& value() && { return result.value(); }

  // Retrieves the value if the result represents a value; otherwise, returns the value of `other`.
  template <typename U>
  constexpr T value_or(U&& other) const { return result.value_or(std::forward<U>(other)); }

  // Retrieves the error message. This function should only be used after checking that the result
  // represents an error using `has_error`.
  constexpr const char* error() const { return *result.error(); }

  // Retrieves an object that can represent a null value or an error.
  constexpr std::optional<const char*> null_or_error() const { return result.error(); }

  // Checks whether the result represents a value.
  constexpr bool has_value() const { return result.has_value(); }

  // Checks whether the result represents a null value.
  constexpr bool has_nothing() const {
    return !result.has_value() && !result.error().has_value();
  }

  // Checks whether the result represents an error.
  constexpr bool has_error() const {
    return !result.has_value() && result.error().has_value();
  }

 private:
  base::expected<T, std::optional<const char*>> result;
};

void ReadUtf16StringFromDevice(const uint16_t* src, size_t len, std::string* out);

// Converts a UTF-8 string to a UTF-16 string.
std::u16string Utf8ToUtf16(const StringPiece& utf8);

// Converts a UTF-16 string to a UTF-8 string.
std::string Utf16ToUtf8(const StringPiece16& utf16);

std::vector<std::string> SplitAndLowercase(const android::StringPiece& str, char sep);

}  // namespace util
}  // namespace android

#endif /* UTIL_H_ */
