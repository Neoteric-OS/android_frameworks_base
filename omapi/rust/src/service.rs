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

use std::sync::{RwLock, RwLockReadGuard};

use crate::{reader::AidlReader, utils::create_exception_status};

use android_se_omapi::aidl::android::se::omapi::{
    ISecureElementReader::ISecureElementReader,
    ISecureElementService::{BnSecureElementService, ISecureElementService},
};
use binder::{ExceptionCode::ILLEGAL_ARGUMENT, Result, Strong};
use log::{debug, info};

const ESE_PREFIX: &str = "eSE";
const SIM_PREFIX: &str = "SIM";

/// SecureElementService implements ISecureElementService.
///
/// All of the actual implementation is in SeService, accessed through the RwLock for thread
///  safety.
pub struct SecureElementService {
    service: RwLock<SeService>,
}

impl SecureElementService {
    /// Write words
    pub fn new_native_binder() -> Result<Strong<dyn ISecureElementService>> {
        Ok(BnSecureElementService::new_binder(
            SecureElementService { service: SeService::new() },
            binder::BinderFeatures::default(),
        ))
    }

    fn immutable_service(&self) -> RwLockReadGuard<SeService> {
        self.service.read().unwrap()
    }
}

impl binder::Interface for SecureElementService {}

impl ISecureElementService for SecureElementService {
    fn getReaders(&self) -> Result<Vec<String>> {
        self.immutable_service().get_readers()
    }

    fn getReader(&self, reader: &str) -> Result<Strong<dyn ISecureElementReader>> {
        self.immutable_service().get_reader(reader)
    }

    fn isNfcEventAllowed(
        &self,
        reader: &str,
        aid: Option<&[u8]>,
        package_names: Option<&[Option<String>]>,

        user_id: i32,
    ) -> Result<Option<Vec<bool>>> {
        self.immutable_service().is_nfc_event_allowed(reader, aid, package_names, user_id)
    }
}

/// SeService is the actual secure element service implementation.  It manages the readers,
/// including enumerating the available readers on startup.
struct SeService {
    ese_readers: Vec<Strong<dyn ISecureElementReader>>,
    sim_readers: Vec<Strong<dyn ISecureElementReader>>,
}

impl SeService {
    fn new() -> RwLock<SeService> {
        RwLock::new(SeService {
            ese_readers: enumerate_readers(ESE_PREFIX, 1),
            sim_readers: enumerate_readers(SIM_PREFIX, 1),
        })
    }

    fn get_readers(&self) -> Result<Vec<String>> {
        let mut result = Vec::new();
        for i in 0..self.ese_readers.len() {
            result.push(build_reader_name(ESE_PREFIX, i + 1));
        }
        for i in 0..self.sim_readers.len() {
            result.push(build_reader_name(ESE_PREFIX, i + 1));
        }
        Ok(result)
    }

    fn get_reader(&self, reader: &str) -> Result<Strong<dyn ISecureElementReader>> {
        let readers = match &reader[0..3] {
            ESE_PREFIX => Ok(&self.ese_readers),
            SIM_PREFIX => Ok(&self.sim_readers),
            _ => Err(create_invalid_reader_exception(reader)),
        }?;
        let reader_number =
            reader[3..].parse::<usize>().or(Err(create_invalid_reader_exception(reader)))?;
        Ok(readers.get(reader_number).ok_or(create_invalid_reader_exception(reader))?.clone())
    }

    fn is_nfc_event_allowed(
        &self,
        _reader: &str,
        _aid: Option<&[u8]>,
        _package_names: Option<&[Option<String>]>,
        _user_id: i32,
    ) -> Result<Option<Vec<bool>>> {
        todo!()
    }
}

fn enumerate_readers(
    name_prefix: &str,
    enumerate_from: usize,
) -> Vec<Strong<dyn ISecureElementReader>> {
    let mut result = Vec::new();
    let mut index = enumerate_from;

    loop {
        let name = build_reader_name(name_prefix, index);
        info!("Checking if terminal {} is available.", name.as_str());
        let retry: bool = index == 1;

        let service_name = build_service_name(&name);
        debug!("Attempting to retrieve service {}", &service_name);
        let se_service = if retry {
            binder::wait_for_interface(service_name.as_str())
        } else {
            binder::check_interface(service_name.as_str())
        };

        match se_service {
            Err(e) => {
                // TODO: Distinguish between "doesn't exist" and "binder error".
                info!("Got error {} checking terminal {}", e, name);
                return result;
            }
            Ok(reader) => {
                result.push(AidlReader::new_native_binder(&name, reader).unwrap());
                index += 1;
            }
        }
    }
}

fn build_service_name(name: &str) -> String {
    "android.hardware.secure_element.ISecureElement/".to_string() + name
}

fn build_reader_name(name_prefix: &str, index: usize) -> String {
    name_prefix.to_string() + &index.to_string()
}

fn create_invalid_reader_exception(reader: &str) -> binder::Status {
    create_exception_status(ILLEGAL_ARGUMENT, &format!("Reader: {reader} not supported."))
}
