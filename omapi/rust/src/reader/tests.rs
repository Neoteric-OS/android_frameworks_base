use super::*;

use std::{
    sync::{Arc, Mutex},
    thread,
};

use android_hardware_secure_element::aidl::android::hardware::secure_element::{
    ISecureElement::{BnSecureElement, ISecureElement},
    ISecureElementCallback::ISecureElementCallback,
    LogicalChannelResponse::LogicalChannelResponse,
};
use android_se_omapi::aidl::android::se::omapi::ISecureElementListener::{
    BnSecureElementListener, ISecureElementListener,
};
use binder::{BinderFeatures, ExceptionCode, Result, Strong};

use super::AidlReader;
use crate::utils::binder_exception;

use super::BASIC_CHANNEL;

fn init() {
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("se_service_test")
            .with_max_level(log::LevelFilter::Trace)
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
}

struct TestListener {}
impl TestListener {
    fn new_binder() -> Strong<dyn ISecureElementListener> {
        BnSecureElementListener::new_binder(TestListener {}, BinderFeatures::default())
    }
}
impl binder::Interface for TestListener {}
impl ISecureElementListener for TestListener {}

fn reader_name(prefix: &str) -> String {
    format!("{prefix}{:?}", thread::current().id())
}

#[test]
fn open_session() -> Result<()> {
    init();
    let (_se_impl, se) = FakeSe::create();
    let reader = AidlReader::new_native_binder(&reader_name("ESE0"), se.clone())?;

    let session = reader.openSession()?;
    assert!(!session.isClosed()?);

    session.close()?;
    assert!(session.isClosed()?);

    Ok(())
}

#[test]
fn no_atr_until_callback() -> Result<()> {
    init();
    let (_se_impl, se) = FakeSe::create();
    let reader = AidlReader::new_native_binder(&reader_name("ESE0"), se.clone())?;

    let session = reader.openSession()?;
    assert_eq!(session.getAtr()?, None);

    Ok(())
}

#[test]
fn get_atr() -> Result<()> {
    init();

    let (se_impl, se) = FakeSe::create();

    let reader = AidlReader::new_native_binder(&reader_name("ESE0"), se.clone())?;
    se_impl.lock().unwrap().se_connected("Hello")?;

    let session = reader.openSession()?;
    assert!(!session.isClosed()?);

    assert_eq!(session.getAtr()?, Some("Hello".as_bytes().to_vec()));

    Ok(())
}

#[test]
fn no_channel_until_callback() -> Result<()> {
    init();

    let (se_impl, se) = FakeSe::create();
    let death_listener = TestListener::new_binder();

    let reader = AidlReader::new_native_binder(&reader_name("ESE0"), se.clone())?;
    let session = reader.openSession()?;

    // Try to get a channel.  Should fail because SE hasn't called back yet.
    let channel = session.openBasicChannel(None, 0x00, &death_listener);
    let err = channel.expect_err("Should be an error");
    assert_eq!(err.exception_code(), ExceptionCode::SERVICE_SPECIFIC);
    assert!(err.get_description().contains("Secure Element is not connected"));

    se_impl.lock().unwrap().se_connected("Hello")?;
    let _channel = session.openBasicChannel(None, 0x00, &death_listener);

    Ok(())
}

#[test]
fn open_basic_channel() -> Result<()> {
    init();

    let (se_impl, se) = FakeSe::create();
    let death_listener = TestListener::new_binder();

    let reader = AidlReader::new_native_binder(&reader_name("ESE0"), se)?;
    se_impl.lock().unwrap().se_connected("Hello")?;

    let session = reader.openSession()?;
    let channel = session.openBasicChannel(None, 0x00, &death_listener)?.unwrap();

    assert!(channel.isBasicChannel()?);
    assert!(!channel.isClosed()?);

    let response = channel.transmit(&[0x00, 0x00, 0x00, 0x00, 0x00])?;
    assert_eq!(response, vec![0x90, 0x00]);

    channel.close()?;

    assert!(channel.isClosed()?);

    Ok(())
}

#[test]
fn open_logical_channel_before_basic() -> Result<()> {
    init();

    let (se_impl, se) = FakeSe::create();
    let death_listener = TestListener::new_binder();

    let reader = AidlReader::new_native_binder(&reader_name("ESE0"), se.clone())?;
    se_impl.lock().unwrap().se_connected("Hello")?;

    let session = reader.openSession()?;
    let err = session.openLogicalChannel(None, 0x00, &death_listener).unwrap_err();
    assert_eq!(err.exception_code(), ExceptionCode::ILLEGAL_STATE);
    assert!(err.get_description().contains("Can't open logical channel before basic channel"));

    Ok(())
}

#[test]
fn open_logical_channel() -> Result<()> {
    init();

    let (se_impl, se) = FakeSe::create();
    let death_listener = TestListener::new_binder();

    let reader = AidlReader::new_native_binder(&reader_name("ESE0"), se.clone())?;
    se_impl.lock().unwrap().se_connected("Hello")?;

    let session = reader.openSession()?;

    let _basic_channel = session.openBasicChannel(None, 0x00, &death_listener)?;

    let _logical_channel = session.openLogicalChannel(None, 0x00, &death_listener)?;
    Ok(())
}

#[test]
fn close_session_channels() -> Result<()> {
    init();

    let (se_impl, se) = FakeSe::create();
    let death_listener = TestListener::new_binder();

    let reader = AidlReader::new_native_binder(&reader_name("ESE0"), se.clone())?;
    se_impl.lock().unwrap().se_connected("Hello")?;

    let session = reader.openSession()?;

    let _basic_channel = session.openBasicChannel(None, 0x00, &death_listener)?;
    let _logical_channel = session.openLogicalChannel(None, 0x00, &death_listener)?;

    session.closeChannels()?;

    Ok(())
}

#[derive(Debug)]
struct FakeSe {
    data: Arc<Mutex<FakeSeInternal>>,
}

#[derive(Debug)]
struct FakeSeInternal {
    atr: Option<Vec<u8>>,
    se_callback: Option<Strong<dyn ISecureElementCallback>>,
    channels: Vec<bool>,
}

impl FakeSeInternal {
    fn new() -> Arc<Mutex<FakeSeInternal>> {
        Arc::new(Mutex::new(FakeSeInternal {
            atr: None,
            se_callback: None,
            channels: vec![false; 20],
        }))
    }

    fn se_connected(&mut self, atr_str: &str) -> Result<()> {
        self.atr = Some(atr_str.as_bytes().to_vec());

        match &self.se_callback {
            Some(se_callback) => se_callback.onStateChange(true, "SE Connected"),
            None => binder_exception(ExceptionCode::ILLEGAL_STATE, "No callback set"),
        }
    }

    fn channel_index_to_number(&self, index: usize) -> Result<i8> {
        if index >= self.channels.len() || index > i8::MAX as usize {
            binder_exception(
                ExceptionCode::ILLEGAL_ARGUMENT,
                format!("Invalid channel index {index}").as_str(),
            )?;
        }
        Ok(index as i8)
    }

    fn channel_number_to_index(&self, number: i8) -> Result<usize> {
        if number < 0 || number as usize >= self.channels.len() {
            binder_exception(
                ExceptionCode::ILLEGAL_ARGUMENT,
                format!("Invalid channel number {number}").as_str(),
            )?;
        }
        Ok(number as usize)
    }
}

impl FakeSe {
    fn create() -> (Arc<Mutex<FakeSeInternal>>, Strong<dyn ISecureElement>) {
        let se_impl = FakeSeInternal::new();
        let se_binder = Self::new_binder(se_impl.clone());
        (se_impl, se_binder)
    }

    fn new_binder(se_internal: Arc<Mutex<FakeSeInternal>>) -> Strong<dyn ISecureElement> {
        BnSecureElement::new_binder(FakeSe { data: se_internal }, BinderFeatures::default())
    }
}

impl binder::Interface for FakeSe {}
impl ISecureElement for FakeSe {
    fn closeChannel(&self, channel_number: i8) -> binder::Result<()> {
        let mut data = self.data.lock().unwrap();
        let channel_index = data.channel_number_to_index(channel_number)?;
        if !data.channels[channel_index] {
            binder_exception(ExceptionCode::ILLEGAL_STATE, "Channel is not open")
        } else {
            data.channels[channel_index] = false;
            Ok(())
        }
    }

    fn getAtr(&self) -> binder::Result<Vec<u8>> {
        Ok(self.data.lock().unwrap().atr.clone().unwrap_or_default())
    }

    fn init(
        &self,
        client_callback: &binder::Strong<dyn ISecureElementCallback>,
    ) -> binder::Result<()> {
        self.data.lock().unwrap().se_callback = Some(client_callback.clone());
        Ok(())
    }

    fn isCardPresent(&self) -> binder::Result<bool> {
        Ok(true)
    }

    fn openBasicChannel(&self, _aid: &[u8], _p2: i8) -> binder::Result<Vec<u8>> {
        self.data.lock().unwrap().channels[BASIC_CHANNEL as usize] = true;
        Ok(vec![0x90, 0x00])
    }

    fn openLogicalChannel(&self, _aid: &[u8], _p2: i8) -> binder::Result<LogicalChannelResponse> {
        let mut data = self.data.lock().unwrap();

        let mut avail_channel = usize::MAX;
        for (index, channel_open) in data.channels.iter().enumerate() {
            if !channel_open {
                avail_channel = index;
                break;
            }
        }
        if avail_channel != usize::MAX {
            data.channels[avail_channel] = true;
            let channel_number = data.channel_index_to_number(avail_channel)?;
            Ok(LogicalChannelResponse { channelNumber: channel_number, selectResponse: vec![] })
        } else {
            binder_exception(ExceptionCode::ILLEGAL_STATE, "No channels available")
        }
    }

    fn reset(&self) -> binder::Result<()> {
        todo!()
    }

    fn transmit(&self, _data: &[u8]) -> binder::Result<Vec<u8>> {
        Ok(vec![0x90, 0x00])
    }
}
