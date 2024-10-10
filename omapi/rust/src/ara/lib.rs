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

//! This crate implements the [Global Platform Secure Element Access
//! Control](https://globalplatform.org/wp-content/uploads/2024/08/GPD_SE_Access_Control_v1.1.0.10_PublicRvw.pdf)
//! specification.  To be precise, it implements the rule engine portion of the Access Control
//! Enforcer (ACE) in the "Rules Cached" mode (not the deprecated instant query mode).  It can
//! parse the Response-ALL-REF-DO response data structure provided by the Access Rule Application
//! Master (ARA-M) applet, de-conflict the rules, and then provide an interface to query for
//! whether specific APDU access should be allowed or NFC events provided to a caller.
//!
//! This implementation is compliant with version 1.2.0, but there's no need to give it the result
//! of a GET DATA (Config) command (though that command should be sent, to inform the ARA-M that
//! the ACE is v1.2) because on the ACE side the behavioral changes needed to accommodate a
//! pre-1.2 ARA-M are not relevant to the "Rules Cached" mode.
//!
//! Unimplemented features include:
//!
//! *  Support for Access Rule Files (ARFs).
//! *  Support for instant query mode, which is deprecated and wouldn't work well for Android.
//!
//! The entry point to this library is [`build_rules`], which takes a buffer containing TLV
//! BER-encoded Response-ALL-REF-AR-DO returned by a compliant ARA-M for the GET DATA (All)
//! command.  [`build_rules`] parses the data and returns a [`Result<RuleCache>`].  The
//! [`RuleCache`] is the processed and cached ARA rule set, ready to be queried.  If anything goes
//! wrong with parsing or validation of the input data, an [`Err`] with an informative message is
//! returned, with context and backtrace for logging.
//!
//! To evaluate whether access should be permitted, call one of [`RuleCache::check_apdu_access`]
//! or [`RuleCache::check_nfc_access`], to compute APDU or NFC event access, respectively.  Both
//! functions take two arguments, a [`DeviceAppId`], which specifies the requesting app by
//! certificate hash(es), and a [`AppletId`], which specifies which secure element applet the app
//! wishes to use.  [`AppletId`] is an enum which either specifies an applet by AID, with
//! [`AppletId::Aid`], or specifies the SE channel's default applet, with
//! [`AppletId::DefaultApplet`].  The library doesn't know which applet is default, it just
//! determines whether the rules allow whichever applet is the default to be accessed by the
//! specified app.
//!
//! The result of a call to [RuleCache::check_apdu_access] is an [`ApduAccessRule`] enum, which
//! has three variants:
//!
//! *  [`ApduAccessRule::Always`] means that all APDUs should be allowed;
//! *  [`ApduAccessRule::Never`] means that no APDUs should be allowed; and
//! *  [`ApduAccessRule::PartialAllow`] means that some APDUs should be allowed.
//!
//! In the PartialAllow case, To determine if a specific APDU should be allowed, use the contained
//! [`ApduFilterSet`] by calling [`ApduFilterSet::allow`] and passing the four-byte header (CLA,
//! INS, P1, P2 bytes) of the APDU to check.  The function returns [`bool`].
//!
//! The result of a call to [`RuleCache::check_nfc_access`] is a [`bool`].
//!
//! Example:
//!
//! ```rust
//! # use ara::{build_rules, RuleCache, DeviceAppId, AppletId, ApduAccessRule};
//! # fn main() -> anyhow::Result<()> {
//! // The TLV data must be read from the SE, and whenever something changes must be re-retrieved
//! // and re-parsed into a new RuleCache.  This example uses a snippet snippet of static data that
//! // contains a single rule that matches any AID and App, and gives ALWAYS APDU access.  It does
//! // not include any NFC rules, so per Annex G, NFC access defaults to ALWAYS.
//! let tlv_data = hex::decode("ff400de20be1044f00c100e303d00101").unwrap();
//!
//! // Parse TLV and create RuleSet
//! let rules = build_rules(&tlv_data)?;
//!
//! // We need app certificates to create an app ID.  Make a Vec containing two (fake) cert hashes.
//! let cert_vec = vec![[0;32], [1;32]];
//! let device_app_id = DeviceAppId::new(cert_vec, vec![]);
//!
//! // Now query for APDU access to the default applet.
//! match rules.check_apdu_access(&device_app_id, &AppletId::DefaultApplet) {
//!     ApduAccessRule::Always => {
//!         // All APDUs are allowed.  The TLV data is very permissive.
//!         assert!(true);
//!     },
//!     ApduAccessRule::Never => {
//!         // This indicates the communication should not be allowed.
//!     },
//!     ApduAccessRule::PartialAllow(filter) => {
//!         // In this case we need to check individual APDUs against the filter
//!         if filter.allow(&[0x80, 0xA0, 0x00, 0x00]) {
//!             // This APDU is allowed.
//!         }
//!     },
//! }
//!
//! // NFC access is returned as a bool.
//! assert!(rules.check_nfc_access(&device_app_id, &AppletId::DefaultApplet) == true);
//!
//! # Ok(())
//! # }
//! ```
//!
mod rules;
mod tlv;

use anyhow::{ensure, Result};
pub use rules::{ApduAccessRule, ApduFilterSet, AppletId, DeviceAppId, RuleCache};

/// Parse TLV data and return a [`RuleCache`] object that can be used to determine
/// whether SE access should be permitted.
pub fn build_rules(data: &[u8]) -> Result<RuleCache> {
    let (tlv_obj, unused_buf) = tlv::parse(data)?;
    ensure!(unused_buf.is_empty(), "Input buffer contained extraneous data");
    RuleCache::from_tlv(&tlv_obj)
}

#[test]
fn parse_test() -> anyhow::Result<()> {
    build_rules(&hex::decode("ff400de20be1044f00c100e303d00101").unwrap())?;
    Ok(())
}
