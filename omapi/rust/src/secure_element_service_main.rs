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

//! This crate implements ISecureElementService, which provides GP OMAPI.

use log::{error, info};
use std::panic;

use anyhow::Result;

static DEBUG_BUILD_PROPERTY: &str = "ro.debuggable";

/// Start the service.
fn main() -> Result<()> {
    initialize_logging();

    // Saying hi.
    info!("SecureElementService is starting.");

    let se_service = se_service::SecureElementService::new_native_binder()?;
    binder::add_service("testservice", se_service.as_binder())?;
    Ok(())
}

/// Set up logging.  By default, we log "info" and higher on user builds, and "debug" or higher
/// on userdebug and eng builds.
///
/// Debug logs should not expose sensitive data, i.e. don't log possibly-sensitive APDU
/// contents with "debug!".  It's fine to log known non-sensitive APDU contents, such as SELECT
/// APDUs.
///
/// Sensitive data can be logged with "trace!", and that logging can be enabled by temporarily
/// and locally modifying this function.
fn initialize_logging() {
    let log_level =
        if is_debuggable_build() { log::LevelFilter::Debug } else { log::LevelFilter::Info };
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("secure_element_service")
            .with_max_level(log_level)
            .with_log_buffer(android_logger::LogId::System)
            .format(|buf, record| {
                writeln!(
                    buf,
                    "{}:{} - {}",
                    record.file().unwrap_or("unknown"),
                    record.line().unwrap_or(0),
                    record.args()
                )
            }),
    );

    // Redirect panic messages to logcat.
    panic::set_hook(Box::new(|panic_info| {
        error!("{}", panic_info);
    }));
}

/// Returns true if we're running on a "debuggable" build (userdebug or eng).
fn is_debuggable_build() -> bool {
    rustutils::system_properties::read_bool(DEBUG_BUILD_PROPERTY, false).unwrap_or(false)
}
