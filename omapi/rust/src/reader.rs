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

//#[cfg(test)]
pub mod tests;

use std::{
    collections::HashMap,
    mem,
    sync::{Arc, RwLock, RwLockReadGuard, RwLockWriteGuard, Weak},
};

use crate::utils::{
    binder_exception, service_specific_exception,
    ServiceSpecificException::{IoError, NoSuchElement},
};

use android_hardware_secure_element::aidl::android::hardware::secure_element::ISecureElement as HwSe;
use android_hardware_secure_element::aidl::android::hardware::secure_element::ISecureElementCallback as HwSeCb;

use android_se_omapi::aidl::android::se::omapi::{
    ISecureElementChannel::{BnSecureElementChannel, ISecureElementChannel},
    ISecureElementListener::ISecureElementListener,
    ISecureElementReader::{BnSecureElementReader, ISecureElementReader},
    ISecureElementSession::{BnSecureElementSession, ISecureElementSession},
};

use binder::{
    BinderFeatures, DeathRecipient,
    ExceptionCode::{ILLEGAL_ARGUMENT, ILLEGAL_STATE, SERVICE_SPECIFIC, UNSUPPORTED_OPERATION},
    IBinder, Result, Status, Strong,
};
use log::{debug, error, info, trace};

const BASIC_CHANNEL: i8 = 0;

#[derive(Debug)]
pub struct Reader {
    /// Name of this reader.  It's critical that these names be globally-unique.  In Android, this
    /// is not a problem as long as only one SeReader is created for each SE HAL service.  For unit
    /// tests (which run in parallel) we have to make the names unique for each test thread.
    name: String,

    /// Handle of the underlying SE HAL instance, which is the binder service that communicates
    /// with the actual hardware.
    se_hal: Strong<dyn HwSe::ISecureElement>,

    /// True if the SE HAL is connected to actual SE hardware.  May be false if the SE is a SIM
    /// which has been removed from the device.
    is_connected: bool,

    /// True if the basic channel was opened without specifying an AID.  Not meaningful if the
    /// basic channel is not yet open.
    is_default_app_selected_on_basic_channel: bool,

    /// Open sessions & channels.
    sessions: HashMap<
        /* session_id */ usize,
        HashMap</* channel_id */ i8, /* select_response */ Vec<u8>>,
    >,

    /// Counter used to create session IDs.
    session_id_counter: usize,
}

impl Reader {
    fn new(se_hal: Strong<dyn HwSe::ISecureElement>, name: &str) -> Result<Reader> {
        Ok(Reader {
            name: name.to_owned(),
            se_hal,
            is_connected: false,
            is_default_app_selected_on_basic_channel: true,
            sessions: HashMap::new(),
            session_id_counter: 0,
        })
    }

    fn is_secure_element_present(&self) -> Result<bool> {
        self.se_hal.isCardPresent()
    }

    fn close_session(&mut self, session_id: usize) {
        trace!("Closing session {session_id}");
        if let Some(session) = self.sessions.remove(&session_id) {
            for id in session.into_keys() {
                let _ = self.close_channel_internal(id);
            }
        }
    }

    fn close_channels(&mut self) {
        let session_ids_to_close = self.sessions.keys().copied().collect::<Vec<_>>();
        for session_id in session_ids_to_close {
            self.close_channels_for_session(session_id);
        }
    }

    fn close_channels_for_session(&mut self, session_id: usize) {
        let mut tmp = HashMap::new();
        if let Some(channel_map) = self.sessions.get_mut(&session_id) {
            mem::swap(channel_map, &mut tmp);
        } else {
            error!("Attempted to close channels for closed session {session_id}");
            return;
        };

        for id in tmp.keys() {
            let _ = self.close_channel_internal(*id);
        }
    }

    fn close_channel_internal(&self, channel_id: i8) -> Result<()> {
        trace!("Closing channel {channel_id}.");

        if channel_id == BASIC_CHANNEL {
            info!("Closing basic channel.  SELECT without AID.");
            let _ = self.select(None);
        }

        if !self.is_connected {
            debug!("Can't actually close {channel_id}, reader isn't connected.");
        } else if let Err(e) = self.se_hal.closeChannel(channel_id) {
            // Don't report errors closing the basic channel; they're expected.
            if channel_id != 0 {
                error!("Error closing channel {channel_id}: {e}.");
            }
        }

        trace!("Closed channel {channel_id}");
        Ok(())
    }

    fn close_channel(&mut self, channel_id: i8) -> Result<()> {
        for channel_map in self.sessions.values_mut() {
            channel_map.remove(&channel_id);
        }
        self.close_channel_internal(channel_id)
    }

    fn select(&self, aid: Option<Vec<u8>>) -> Result<()> {
        let aid = aid.unwrap_or_default();
        let mut select_command = vec![0x00, 0xA4, 0x04, 0x00, aid.len() as u8];
        select_command.extend(aid);

        let response = self.se_hal.transmit(&select_command)?;
        if response != vec![0x90, 0x00] {
            service_specific_exception(
                NoSuchElement,
                &format!("Incorrect status word {:#?}", response),
            )?
        }
        Ok(())
    }

    fn reset(&self) -> Result<bool> {
        // TODO check SECURE_ELEMENT_PRIVILEGED_OPERATION permission.
        if let Err(e) = self.se_hal.reset() {
            error!("Got error {e} resetting SE {}.", self.name);
            // It's odd that we don't propagate the error, but the Java implementation doesn't.
            Ok(false)
        } else {
            Ok(true)
        }
    }

    fn get_atr(&self) -> Result<Option<Vec<u8>>> {
        if !self.is_connected {
            return Ok(None);
        }

        let atr = self.se_hal.getAtr()?;
        debug!("ATR: {}", hex::encode(&atr));

        if atr.is_empty() {
            Ok(None)
        } else {
            Ok(Some(atr))
        }
    }

    fn get_select_response(&self, channel_id: i8) -> Result<Option<Vec<u8>>> {
        for channel_map in self.sessions.values() {
            if let Some(select_response) = channel_map.get(&channel_id) {
                return Ok(Some(select_response.clone()));
            }
        }

        Ok(None)
    }

    fn open_basic_channel(
        &mut self,
        session: &AidlSession,
        aid: Option<&[u8]>,
        p2: i8,
        listener: &Strong<dyn ISecureElementListener>,
    ) -> Result<Option<Strong<dyn ISecureElementChannel>>> {
        trace!(
            "openBasicChannel() {} P2 = 0x{p2:02x}",
            aid.map_or("None".to_string(), hex::encode)
        );

        debug!("1.1");
        if !self.is_channel_closed(BASIC_CHANNEL) {
            info!("Attempted to re-open basic channel");
            return Ok(None);
        }

        debug!("1.2");
        let aid = aid.unwrap_or_default();
        if let Err(e) = self.validate_channel_open(session.id, p2, aid) {
            trace!("Open channel failed with {e}");
            Err(e)?
        }

        // TODO set up channel access control

        debug!("1.3");
        if self.sessions.values().any(|channel_map| channel_map.contains_key(&BASIC_CHANNEL)) {
            error!("Basic channel in use");
            return Ok(None);
        }

        debug!("1.4");
        if aid.is_empty() && !self.is_default_app_selected_on_basic_channel {
            error!("Default application is not selected");
            return Ok(None);
        }

        debug!("1.5");
        let select_response = match self.se_hal.openBasicChannel(aid, p2) {
            Ok(response) => response,
            Err(status) => return convert_hw_exception(status),
        };

        debug!("1.6");
        if !aid.is_empty() {
            self.is_default_app_selected_on_basic_channel = false
        };

        debug!("1.7");
        self.create_channel(
            session.reader.clone(),
            session.id,
            select_response,
            BASIC_CHANNEL,
            listener,
        )
    }

    fn open_logical_channel(
        &mut self,
        session: &AidlSession,
        aid: Option<&[u8]>,
        p2: i8,
        listener: &Strong<dyn ISecureElementListener>,
    ) -> Result<Option<Strong<dyn ISecureElementChannel>>> {
        trace!(
            "openLogicalChannel() {} P2 = 0x{p2:02x}",
            aid.map_or("None".to_string(), hex::encode)
        );

        let aid = aid.unwrap_or_default();
        self.validate_channel_open(session.id, p2, aid)?;
        let channel_closed = self.is_channel_closed(BASIC_CHANNEL);
        if channel_closed {
            binder_exception(ILLEGAL_STATE, "Can't open logical channel before basic channel")?
        }

        // TODO set up channel access control

        let channel_response = match self.se_hal.openLogicalChannel(aid, p2) {
            Ok(response) => response,
            Err(status) => return convert_hw_exception(status),
        };

        if channel_response.channelNumber <= 0 {
            return Ok(None);
        }

        self.create_channel(
            session.reader.clone(),
            session.id,
            channel_response.selectResponse,
            channel_response.channelNumber,
            listener,
        )
    }

    fn create_channel(
        &mut self,
        reader: Arc<RwLock<Reader>>,
        session_id: usize,
        select_response: Vec<u8>,
        channel_id: i8,
        listener: &Strong<dyn ISecureElementListener>,
    ) -> Result<Option<Strong<dyn ISecureElementChannel>>> {
        trace!("Opened channel {}, SR: {}", channel_id, hex::encode(&select_response));

        if self.sessions.values().any(|channel_map| channel_map.contains_key(&channel_id)) {
            // The Java implementation didn't check for this happening.  Maybe it's okay?
            error!("Re-opened un-closed channel {}?", channel_id);
        }

        self.sessions
            .get_mut(&session_id)
            .expect("Attempt to create channel in closed session")
            .insert(channel_id, select_response);

        let reader_clone = reader.clone();
        let mut recipient = DeathRecipient::new(move || {
            let _ = reader_clone.write().unwrap().close_channel(channel_id);
        });

        if let Err(err) = listener.as_binder().link_to_death(&mut recipient) {
            // If not testing, return all errors.
            #[cfg(not(test))]
            return Err(err.into());

            // If testing, return all errors except INVALID_OPERATION, which we expect because
            // the recipient is a local binder object.
            #[cfg(test)]
            if err != binder::StatusCode::INVALID_OPERATION {
                return Err(err.into());
            }
        }

        Ok(Some(AidlChannel::new_native_binder(reader, channel_id)))
    }

    fn validate_channel_open(&mut self, session_id: usize, p2: i8, aid: &[u8]) -> Result<()> {
        self.check_connected()?;
        self.check_session_open(session_id)?;
        validate_p2(p2)?;
        validate_aid(aid)?;
        Ok(())
    }

    fn check_connected(&mut self) -> Result<()> {
        if !self.is_connected {
            service_specific_exception(IoError, "Secure Element is not connected")?;
        };
        Ok(())
    }

    fn check_session_open(&mut self, session_id: usize) -> Result<()> {
        if self.is_session_closed(session_id) {
            return binder_exception(ILLEGAL_STATE, "Session is closed");
        }
        Ok(())
    }

    fn is_session_closed(&self, session_id: usize) -> bool {
        !self.sessions.contains_key(&session_id)
    }

    fn is_channel_closed(&self, channel_id: i8) -> bool {
        !self.sessions.values().any(|channel_map| channel_map.contains_key(&channel_id))
    }

    fn on_state_change(&mut self, connected: bool, debug_reason: &str) -> Result<()> {
        info!("Terminal::onStateChange: connected: {connected} reason: {debug_reason}");
        self.is_connected = connected;
        self.close_channels();

        // TODO: if connected, reinitialize access control

        Ok(())
    }
}

pub struct AidlReader {
    reader: Arc<RwLock<Reader>>,
}

impl AidlReader {
    pub fn new_native_binder(
        name: &str,
        se_service: Strong<dyn HwSe::ISecureElement>,
    ) -> Result<Strong<dyn ISecureElementReader>> {
        let reader = Arc::new(RwLock::new(Reader::new(se_service.clone(), name)?));
        se_service.init(&SeHalCallback::new_native_binder(Arc::downgrade(&reader)))?;

        Ok(BnSecureElementReader::new_binder(AidlReader { reader }, BinderFeatures::default()))
    }

    fn immutable_reader(&self) -> RwLockReadGuard<Reader> {
        self.reader.read().unwrap()
    }

    fn mutable_reader(&self) -> RwLockWriteGuard<Reader> {
        self.reader.write().unwrap()
    }
}

impl binder::Interface for AidlReader {}

impl ISecureElementReader for AidlReader {
    fn isSecureElementPresent(&self) -> Result<bool> {
        self.immutable_reader().is_secure_element_present()
    }

    fn openSession(&self) -> Result<Strong<dyn ISecureElementSession>> {
        let mut reader = self.mutable_reader();

        if !reader.is_secure_element_present()? {
            service_specific_exception(IoError, "Secure Element is not present.")?
        }

        let session_id = reader.session_id_counter;
        reader.sessions.insert(session_id, HashMap::new());
        reader.session_id_counter += 1;

        trace!("Opened session {session_id}");
        Ok(AidlSession::new_native_binder(self.reader.clone(), session_id))
    }

    fn closeSessions(&self) -> Result<()> {
        self.mutable_reader().close_channels();
        Ok(())
    }

    fn reset(&self) -> Result<bool> {
        self.immutable_reader().reset()
    }
}

#[derive(Debug)]
struct AidlSession {
    reader: Arc<RwLock<Reader>>,
    id: usize,
}

impl AidlSession {
    fn new_native_binder(
        reader: Arc<RwLock<Reader>>,
        session_id: usize,
    ) -> Strong<dyn ISecureElementSession> {
        BnSecureElementSession::new_binder(
            AidlSession { reader, id: session_id },
            BinderFeatures::default(),
        )
    }

    fn immutable_reader(&self) -> RwLockReadGuard<Reader> {
        self.reader.read().unwrap()
    }

    fn mutable_reader(&self) -> RwLockWriteGuard<Reader> {
        self.reader.write().unwrap()
    }
}

impl Drop for AidlSession {
    fn drop(&mut self) {
        trace!("SeSession {} dropped.", self.id)
    }
}

impl binder::Interface for AidlSession {}
impl ISecureElementSession for AidlSession {
    fn getAtr(&self) -> Result<Option<Vec<u8>>> {
        self.immutable_reader().get_atr()
    }

    fn close(&self) -> Result<()> {
        self.mutable_reader().close_session(self.id);
        Ok(())
    }

    fn closeChannels(&self) -> Result<()> {
        self.mutable_reader().close_channels_for_session(self.id);
        Ok(())
    }

    fn isClosed(&self) -> Result<bool> {
        Ok(self.immutable_reader().is_session_closed(self.id))
    }

    fn openBasicChannel(
        &self,
        aid: Option<&[u8]>,
        p2: i8,
        listener: &Strong<dyn ISecureElementListener>,
    ) -> Result<Option<Strong<dyn ISecureElementChannel>>> {
        self.mutable_reader().open_basic_channel(self, aid, p2, listener)
    }

    fn openLogicalChannel(
        &self,
        aid: Option<&[u8]>,
        p2: i8,
        listener: &Strong<dyn ISecureElementListener>,
    ) -> Result<Option<Strong<dyn ISecureElementChannel>>> {
        self.mutable_reader().open_logical_channel(self, aid, p2, listener)
    }
}

#[derive(Debug)]
struct AidlChannel {
    reader: Arc<RwLock<Reader>>,
    id: i8,
}

impl AidlChannel {
    fn new_native_binder(
        reader: Arc<RwLock<Reader>>,
        channel_id: i8,
    ) -> Strong<dyn ISecureElementChannel> {
        BnSecureElementChannel::new_binder(
            AidlChannel { reader, id: channel_id },
            BinderFeatures::default(),
        )
    }

    fn immutable_reader(&self) -> RwLockReadGuard<Reader> {
        self.reader.read().unwrap()
    }

    fn mutable_reader(&self) -> RwLockWriteGuard<Reader> {
        self.reader.write().unwrap()
    }
}

impl binder::Interface for AidlChannel {}
impl ISecureElementChannel for AidlChannel {
    fn close(&self) -> Result<()> {
        self.mutable_reader().close_channel(self.id)
    }

    fn isClosed(&self) -> Result<bool> {
        Ok(self.immutable_reader().is_channel_closed(self.id))
    }

    fn isBasicChannel(&self) -> Result<bool> {
        Ok(self.id == BASIC_CHANNEL)
    }

    fn getSelectResponse(&self) -> Result<Option<Vec<u8>>> {
        self.immutable_reader().get_select_response(self.id)
    }

    fn transmit(&self, apdu: &[u8]) -> Result<Vec<u8>> {
        self.immutable_reader().se_hal.transmit(apdu)
    }

    fn selectNext(&self) -> Result<bool> {
        todo!()
    }
}

#[derive(Debug)]
struct SeHalCallback {
    reader: Weak<RwLock<Reader>>,
}

impl SeHalCallback {
    fn new_native_binder(
        reader: Weak<RwLock<Reader>>,
    ) -> Strong<dyn HwSeCb::ISecureElementCallback> {
        HwSeCb::BnSecureElementCallback::new_binder(
            SeHalCallback { reader },
            BinderFeatures::default(),
        )
    }
}

impl binder::Interface for SeHalCallback {}
impl HwSeCb::ISecureElementCallback for SeHalCallback {
    fn onStateChange(&self, connected: bool, debug_reason: &str) -> Result<()> {
        let _ = self
            .reader
            .upgrade()
            .map(|r| r.write().unwrap().on_state_change(connected, debug_reason));
        Ok(())
    }
}

fn convert_hw_exception(status: Status) -> Result<Option<Strong<dyn ISecureElementChannel>>> {
    if status.exception_code() == SERVICE_SPECIFIC {
        match status.service_specific_error() {
            HwSe::CHANNEL_NOT_AVAILABLE => {
                // I would think that returning an error would be the right thing to do, but the
                // Java implementation just returns null, so we do that.
                Ok(None)
            }
            HwSe::UNSUPPORTED_OPERATION => binder_exception(
                UNSUPPORTED_OPERATION,
                "OpenBasicChannel() failed with unsupported operation",
            ),
            HwSe::IOERROR => {
                service_specific_exception(IoError, "OpenBasicChannel() failed with IO error")
            }
            HwSe::NO_SUCH_ELEMENT_ERROR => service_specific_exception(
                NoSuchElement,
                "OpenBasicChannel() failed with no such element",
            ),
            other => {
                error!("Unknown service-specific error {}", other);
                Err(status)
            }
        }
    } else {
        error!("Unexpected binder error {:?}", status.exception_code());
        Err(status)
    }
}

fn validate_aid(aid: &[u8]) -> Result<()> {
    match aid.len() {
        0 | 5..=16 => Ok(()),
        _ => binder_exception(ILLEGAL_ARGUMENT, "AID out of range"),
    }
}

fn validate_p2(p2: i8) -> Result<()> {
    match p2 {
        0x00 | 0x04 | 0x08 | 0x0C => Ok(()),
        other => {
            binder_exception(UNSUPPORTED_OPERATION, &format!("p2 not supported 0x{:02x}", other))
        }
    }
}
