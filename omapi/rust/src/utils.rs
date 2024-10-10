// Copyright 2024, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! TODO

use binder::{self, ExceptionCode, Result};

pub enum ServiceSpecificException {
    IoError,
    NoSuchElement,
}

pub fn service_specific_exception<T>(error: ServiceSpecificException, message: &str) -> Result<T> {
    Err(binder::Status::new_service_specific_error_str(
        match error {
            ServiceSpecificException::IoError => 1,
            ServiceSpecificException::NoSuchElement => 2,
        },
        Some(message),
    ))
}

pub fn binder_exception<T>(exception_code: ExceptionCode, message: &str) -> Result<T> {
    Err(create_exception_status(exception_code, message))
}

pub fn create_exception_status(exception_code: ExceptionCode, message: &str) -> binder::Status {
    binder::Status::new_exception_str(exception_code, Some(message))
}
