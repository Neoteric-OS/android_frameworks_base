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

//! This module implements a simple TLV-BER parser, parsing a byte array and returning nested
//! [`Object`]s that represent the contents.  It does not define/implement standard ASN.1 types
//! (e.g. INTEGER, SEQUENCE, etc.) because they're not required by the ARA data structures.
//!
//! The reason this was written rather than using an existing crate, is that the existing options
//! parse TLV-DER, and diagnose and report non-canonical encodings.  The ARA rule set is TLV-BER
//! encoded, not TLV-DER, because canonicalization is not required.

use anyhow::{anyhow, Context, Result};
use strum_macros::{Display, EnumIter};
use thiserror::Error;

/// Parse the provided buffer, returning an [`Object`] representing the parsed data and a slice that
/// references the unused portion of the buffer (if any; callers should probably assume that a
/// non-empty unused buffer means the input data was malformed).
pub fn parse(buf: &[u8]) -> Result<(Object, &[u8])> {
    parse_internal(buf, &mut 0)
}

/// The parsed TLV data structure.
///
/// The content is simple: a [`Tag`] object that specifies the extracted tag, and a [`Value`] object
/// with the contained value.
#[derive(Debug, Clone)]
pub struct Object<'a> {
    tag: Tag,
    value: Value<'a>,
}

impl<'a> Object<'a> {
    #[cfg(test)]
    pub fn new(tag: Tag, value: Value<'a>) -> Self {
        Object { tag, value }
    }

    pub fn tag(&self) -> &Tag {
        &self.tag
    }

    pub fn content(&self) -> &Value {
        &self.value
    }
}

/// Value represents the content of a TLV object, whether [`Value::Empty`], meaning the TLV value
/// was empty, [`Value::Constructed`], meaning the value consists of a set of zero or more contained
/// TLV objects, or [`Value::Primitive`], meaning the value does not contain other TLV objects, but
/// only some primitive content.  Primitive content is provided only as a byte array.
#[derive(Display, Debug, Clone)]
pub enum Value<'a> {
    Empty,
    Primitive(&'a [u8]),
    Constructed(Vec<Object<'a>>),
}

/// The set of supported tags.  Additional tags can be added if needed, though unknown tags are
/// handled cleanly as [`Tag::Unknown`].
#[derive(Display, Debug, Clone, EnumIter, PartialEq)]
pub enum Tag {
    AidRefDoSpecificApplet,
    AidRefDoImplicit,
    DeviceAppIdRefDo,
    ApduArDo,
    NfcArDo,
    RefDo,
    RefArDo,
    ArDo,
    ResponseRefreshTagDo,
    ResponseAllRefArDo,
    Unknown(Vec<u8>),
}

impl Tag {
    fn new(tag_val: &[u8]) -> Self {
        match tag_val {
            [0x4F] => Self::AidRefDoSpecificApplet,
            [0xC0] => Self::AidRefDoImplicit,
            [0xC1] => Self::DeviceAppIdRefDo,
            [0xD0] => Self::ApduArDo,
            [0xD1] => Self::NfcArDo,
            [0xE1] => Self::RefDo,
            [0xE2] => Self::RefArDo,
            [0xE3] => Self::ArDo,
            [0xDF, 0x20] => Self::ResponseRefreshTagDo,
            [0xFF, 0x40] => Self::ResponseAllRefArDo,
            tag_val => Self::Unknown(tag_val.to_vec()),
        }
    }

    pub fn bytes(&self) -> &[u8] {
        match self {
            Tag::AidRefDoSpecificApplet => &[0x4F],
            Tag::AidRefDoImplicit => &[0xC0],
            Tag::DeviceAppIdRefDo => &[0xC1],
            Tag::ApduArDo => &[0xD0],
            Tag::NfcArDo => &[0xD1],
            Tag::RefDo => &[0xE1],
            Tag::RefArDo => &[0xE2],
            Tag::ArDo => &[0xE3],
            Tag::ResponseRefreshTagDo => &[0xDF, 0x20],
            Tag::ResponseAllRefArDo => &[0xFF, 0x40],
            Tag::Unknown(vec) => vec,
        }
    }

    fn is_constructed(&self) -> Result<bool> {
        Ok(self.first_byte()? & 0x20 == 0x20)
    }

    fn first_byte(&self) -> Result<&u8, anyhow::Error> {
        self.bytes().first().ok_or(anyhow!("Can't get class of empty tag value"))
    }
}

#[derive(Debug, Error)]
pub enum TlvParseError {
    #[error("Parse buffer exhausted; needed {0} bytes, found {1}")]
    BufferInsufficient(usize, usize),
}

fn parse_internal<'a>(buf: &'a [u8], offset: &mut usize) -> Result<(Object<'a>, &'a [u8])> {
    let obj_pos = *offset;
    let (tag, length, buf) = parse_header(buf, offset)?;

    if buf.len() < length {
        return Err(anyhow!(TlvParseError::BufferInsufficient(length, buf.len())));
    }

    let err_ctx =
        || format!("While parsing content of constructed object (tag {}) at {obj_pos}", tag);

    let (content, buf) = if length == 0 {
        (Value::Empty, buf)
    } else if tag.is_constructed()? {
        let contents_buf = &buf[..length];
        let remaining_buf = &buf[length..];
        (parse_constructed_content(contents_buf, offset).with_context(err_ctx)?, remaining_buf)
    } else {
        (Value::Primitive(&buf[..length]), &buf[length..])
    };

    Ok((Object { tag, value: content }, buf))
}

fn parse_header<'a>(
    buf: &'a [u8],
    offset: &mut usize,
) -> Result<(Tag, /* length */ usize, &'a [u8])> {
    let header_pos = *offset;

    let (tag, buf) = parse_tag(buf, offset)
        .with_context(|| format!("While parsing tag from header at offset {header_pos}"))?;
    let (length, buf) = parse_len(buf, offset)
        .with_context(|| format!("While parsing length from header at offset {header_pos}"))?;

    Ok((tag, length, buf))
}

fn parse_tag<'a>(buf: &'a [u8], offset: &mut usize) -> Result<(Tag, &'a [u8])> {
    if buf.is_empty() {
        return Err(anyhow!(TlvParseError::BufferInsufficient(1, 0)));
    }

    let (tag, buf) = if buf[0] & 0x1F != 0x1F {
        *offset += 1;
        (Tag::new(&buf[0..1]), &buf[1..])
    } else {
        parse_multi_byte_tag(buf, offset).context("While parsing multi-byte tag.")?
    };

    Ok((tag, buf))
}

fn parse_multi_byte_tag<'a>(buf: &'a [u8], offset: &mut usize) -> Result<(Tag, &'a [u8])> {
    let mut pos = 0;
    while pos == 0 || buf[pos] & 0x80 == 0x80 {
        pos += 1;
        if pos >= buf.len() {
            return Err(anyhow!(TlvParseError::BufferInsufficient(pos, buf.len(),)));
        }
    }
    pos += 1;
    *offset += pos;
    Ok((Tag::new(&buf[..pos]), &buf[pos..]))
}

fn parse_len<'a>(buf: &'a [u8], offset: &mut usize) -> Result<(usize, &'a [u8])> {
    if buf.is_empty() {
        return Err(anyhow!(TlvParseError::BufferInsufficient(1, 0)));
    }

    if buf[0] & 0x80 == 0x00 {
        *offset += 1;
        Ok(((buf[0] & 0x7F) as usize, &buf[1..]))
    } else {
        parse_multi_byte_length(buf, offset)
    }
}

fn parse_multi_byte_length<'a>(
    buf: &'a [u8],
    offset: &mut usize,
) -> std::result::Result<(usize, &'a [u8]), anyhow::Error> {
    let field_len = (buf[0] & 0x7F) as usize;
    if buf.len() <= field_len + 1 {
        return Err(anyhow!(TlvParseError::BufferInsufficient(field_len + 1, buf.len())));
    }

    let buf = &buf[1..];
    let mut len: usize = 0;
    for b in &buf[0..field_len] {
        len = len * 256 + *b as usize;
    }
    *offset += field_len + 1;
    Ok((len, &buf[field_len..]))
}

fn parse_constructed_content<'a>(buf: &'a [u8], offset: &mut usize) -> Result<Value<'a>> {
    let mut objects = Vec::new();
    let mut buf = buf;
    while !buf.is_empty() {
        let obj_pos = *offset;
        let (object, remaining_buf) = parse_internal(buf, offset)
            .with_context(|| format!("While parsing sub-object at offset {}", obj_pos))?;
        buf = remaining_buf;
        objects.push(object);
    }
    Ok(Value::Constructed(objects))
}

#[cfg(test)]
mod tests {
    use super::*;

    use anyhow::Result;
    use strum::IntoEnumIterator;

    #[test]
    fn parse_empty_tag() -> Result<()> {
        let data = [];
        let result = parse_tag(&data, &mut 0);
        assert!(result.is_err());
        if let Err(e) = result {
            assert_eq!(
                e.chain().last().unwrap().to_string(),
                "Parse buffer exhausted; needed 1 bytes, found 0"
            );
        }

        Ok(())
    }

    #[test]
    fn unknown_tag() -> Result<()> {
        let data = [
            0x00, // Tag
            0x00, // Length
        ];

        let (tag, _unused_buf) = parse_tag(&data, &mut 0)?;
        assert_eq!(tag, Tag::Unknown(vec![0x00]));

        Ok(())
    }

    #[test]
    fn parse_empty_len() -> Result<()> {
        let data = [];
        let result = parse_len(&data, &mut 0);
        assert!(result.is_err());
        if let Err(e) = result {
            assert_eq!(
                e.chain().last().unwrap().to_string(),
                "Parse buffer exhausted; needed 1 bytes, found 0"
            );
        }

        Ok(())
    }

    #[test]
    fn parse_short_buf() -> Result<()> {
        let data = [
            0x00, // Tag
            0x02, // Length - need two bytes
        ];

        let result = parse(&data);
        assert!(result.is_err());
        if let Err(e) = result {
            assert_eq!(
                e.chain().last().unwrap().to_string(),
                "Parse buffer exhausted; needed 2 bytes, found 0"
            );
        }

        Ok(())
    }

    #[test]
    fn parse_rule_set_with_nop_rule() -> Result<()> {
        let data = [
            0xFF, 0x40, // Response-ALL-REF-AR-DO
            0x0D, // 13 bytes long
            0xE2, // REF-AR-DO tag
            0x0B, // 11 bytes long (6 REF-DO, 5 AR-DO)
            0xE1, // REF-DO tag
            0x04, // 4 bytes long
            0x4F, // AID-REF-DO tag
            0x00, // 0 bytes (empty AID)
            0xC1, // DeviceAppId-REF-DO tag
            0x00, // 0 bytes (empty device ID)
            0xE3, // AR-DO tag
            0x03, // 3 bytes long
            0xD0, // APDU-AR-DO tag
            0x01, // 1 byte long
            0x01, // 0x01 means ALWAYS allow.
        ];

        let tlv_obj = parse(&data)?;

        println!("{tlv_obj:#X?}");

        Ok(())
    }

    #[test]
    fn test_tag_mappings() -> Result<()> {
        for tag in Tag::iter() {
            assert_eq!(Tag::new(tag.bytes()), tag);
        }
        Ok(())
    }

    #[test]
    fn test_incomplete_multi_byte_len() -> Result<()> {
        let data = [
            0x00, 0x82, // Length; shoud be followed by two bytes.
        ];

        let result = parse_header(&data, &mut 0);
        assert!(result.is_err());
        if let Err(e) = result {
            assert_eq!(
                e.chain().last().unwrap().to_string(),
                "Parse buffer exhausted; needed 3 bytes, found 1"
            );
        }

        Ok(())
    }

    #[test]
    fn test_multi_byte_len() -> Result<()> {
        let data = [
            0x82, // Length field, two-byte value
            0x01, // Byte 1: 1 * 256
            0x01, // Byte 2: 1
            0xFF, // Extra byte; shouldn't be used.
        ];

        let (val, remaining_buf) = parse_len(&data, &mut 0)?;
        assert_eq!(val, 257);
        assert_eq!(remaining_buf.len(), 1);

        Ok(())
    }

    #[test]
    fn test_error_in_constructed_object() -> Result<()> {
        let data = [
            Tag::RefDo.bytes()[0],                  // RefDo tag
            0x02,                                   // Length
            Tag::AidRefDoSpecificApplet.bytes()[0], // AidRefDo tag,
            0x82,                                   // Invalid length
        ];

        let result = parse(&data);
        assert!(result.is_err());
        if let Err(e) = result {
            assert_eq!(
                e.chain().last().unwrap().to_string(),
                "Parse buffer exhausted; needed 3 bytes, found 1"
            );
        }

        Ok(())
    }
}
