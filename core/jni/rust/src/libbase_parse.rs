// Copyright (C) 2026 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Value parsers for `android.os.SystemProperties` that implement libbase's
//! `ParseInt<T>` (parseint.h) and `ParseBool` (parsebool.cpp) semantics.
//! `None` means parsing failed, in which case the JNI getters fall back to
//! the caller's default.
//!
//! `ParseInt` semantics: leading C whitespace is
//! skipped; the base is 16 only when the value then starts with `0x`/`0X`
//! (so a sign defeats hex detection and `-0x10` fails on the trailing `x`),
//! otherwise 10 — never 8, so leading zeros stay decimal; parsing follows
//! `strtoll` (optional sign, longest digit run); any trailing bytes,
//! including whitespace, are an error; and out-of-range values fail rather
//! than clamp.
//!
//! This intentionally does not reuse `rustutils::parsers_formatters`: those
//! helpers reject libbase's `y`/`yes`/`on` boolean spellings and its hexadecimal
//! integer form, so substituting them would change SystemProperties behavior.

/// Parses `value` exactly like libbase's `ParseInt<jint>`.
pub(crate) fn parse_int(value: &str) -> Option<i32> {
    parse_in_range(value, i32::MIN as i64, i32::MAX as i64).map(|parsed| parsed as i32)
}

/// Parses `value` exactly like libbase's `ParseInt<jlong>`.
pub(crate) fn parse_long(value: &str) -> Option<i64> {
    parse_in_range(value, i64::MIN, i64::MAX)
}

/// Parses `value` exactly like libbase's `ParseBool`: the exact,
/// case-sensitive tokens `1`/`y`/`yes`/`on`/`true` and
/// `0`/`n`/`no`/`off`/`false`, with no trimming.
pub(crate) fn parse_bool(value: &str) -> Option<bool> {
    match value {
        "1" | "y" | "yes" | "on" | "true" => Some(true),
        "0" | "n" | "no" | "off" | "false" => Some(false),
        _ => None,
    }
}

/// C-locale `isspace`, the predicate both libbase's leading-whitespace skip
/// and `strtoll`'s use.
fn is_c_space(byte: u8) -> bool {
    // space, \t, \n, \v, \f, \r
    matches!(byte, b' ' | b'\t' | b'\n' | 0x0B | 0x0C | b'\r')
}

/// The shared `ParseInt` implementation: parses like
/// `strtoll(s, &end, base)` with libbase's base detection and error policy,
/// then rejects values outside `[min, max]`.
fn parse_in_range(value: &str, min: i64, max: i64) -> Option<i64> {
    let mut rest = value.as_bytes();
    while let [first, tail @ ..] = rest {
        if !is_c_space(*first) {
            break;
        }
        rest = tail;
    }

    // libbase detects hex before the sign is consumed, so a signed hex value
    // parses in base 10 and fails on the 'x'.
    let base: u32 = if rest.starts_with(b"0x") || rest.starts_with(b"0X") { 16 } else { 10 };

    let negative = match rest.first() {
        Some(b'+') => {
            rest = &rest[1..];
            false
        }
        Some(b'-') => {
            rest = &rest[1..];
            true
        }
        _ => false,
    };

    // strtoll consumes a 0x/0X prefix only when a hex digit follows;
    // otherwise the subject sequence is just the "0" and the 'x' is trailing
    // garbage.
    if base == 16 {
        if let [b'0', b'x' | b'X', digit, ..] = rest {
            if digit.is_ascii_hexdigit() {
                rest = &rest[2..];
            }
        }
    }

    // Accumulate negated so i64::MIN, whose magnitude exceeds i64::MAX, stays
    // representable. Overflow is strtoll's ERANGE, which libbase treats as
    // failure rather than clamping.
    let mut parsed: i64 = 0;
    let mut digit_count = 0usize;
    for &byte in rest {
        let Some(digit) = (byte as char).to_digit(base) else {
            break;
        };
        parsed = parsed.checked_mul(base as i64)?.checked_sub(digit as i64)?;
        digit_count += 1;
    }
    if digit_count == 0 {
        // strtoll consumed nothing (end == s): EINVAL.
        return None;
    }
    if digit_count != rest.len() {
        // Trailing bytes after the digits (*end != '\0'): EINVAL.
        return None;
    }

    let parsed = if negative { parsed } else { parsed.checked_neg()? };
    if parsed < min || max < parsed {
        return None;
    }
    Some(parsed)
}

#[cfg(test)]
mod tests {
    use super::{parse_bool, parse_int, parse_long};

    #[test]
    fn bool_accepts_exactly_the_libbase_tokens() {
        for token in ["1", "y", "yes", "on", "true"] {
            assert_eq!(parse_bool(token), Some(true), "{token}");
        }
        for token in ["0", "n", "no", "off", "false"] {
            assert_eq!(parse_bool(token), Some(false), "{token}");
        }
    }

    #[test]
    fn bool_is_case_sensitive() {
        for token in ["TRUE", "True", "Yes", "Y", "ON", "FALSE", "Off", "N", "NO"] {
            assert_eq!(parse_bool(token), None, "{token}");
        }
    }

    #[test]
    fn bool_does_not_trim_or_guess() {
        for token in ["", " true", "true ", "yess", "2", "10", "01", "-1"] {
            assert_eq!(parse_bool(token), None, "{token:?}");
        }
    }

    #[test]
    fn int_parses_plain_decimal() {
        assert_eq!(parse_int("0"), Some(0));
        assert_eq!(parse_int("42"), Some(42));
        assert_eq!(parse_int("+42"), Some(42));
        assert_eq!(parse_int("-42"), Some(-42));
        // Explicit base 10 means leading zeros are not octal.
        assert_eq!(parse_int("007"), Some(7));
        assert_eq!(parse_int("018"), Some(18));
    }

    #[test]
    fn int_skips_all_c_whitespace_before_the_number() {
        // libbase skips isspace() before parsing; strtoll would too.
        assert_eq!(parse_int(" \t\n\x0B\x0C\r-7"), Some(-7));
    }

    #[test]
    fn int_rejects_trailing_bytes_including_whitespace() {
        // ParseInt requires *end == '\0' after strtoll.
        for value in ["42 ", "42\n", "4 2", "12abc", "1.5", "12-"] {
            assert_eq!(parse_int(value), None, "{value:?}");
        }
    }

    #[test]
    fn int_rejects_values_with_no_digits() {
        for value in ["", " ", "+", "-", "- 5", "abc", "\u{661}\u{662}"] {
            assert_eq!(parse_int(value), None, "{value:?}");
        }
    }

    #[test]
    fn int_parses_hex_with_leading_0x() {
        assert_eq!(parse_int("0x10"), Some(16));
        assert_eq!(parse_int("0X1f"), Some(31));
        assert_eq!(parse_int("0xAB"), Some(171));
        // Whitespace is skipped before base detection.
        assert_eq!(parse_int(" 0x10"), Some(16));
        assert_eq!(parse_int("0x7fffffff"), Some(i32::MAX));
    }

    #[test]
    fn int_rejects_signed_hex() {
        // Base detection looks at the first non-space byte, so a sign forces
        // base 10 and the 'x' becomes trailing garbage — matching libbase.
        assert_eq!(parse_int("-0x10"), None);
        assert_eq!(parse_int("+0x10"), None);
    }

    #[test]
    fn int_rejects_bare_or_malformed_hex_prefixes() {
        // strtoll parses just the "0" and leaves 'x' as trailing garbage.
        for value in ["0x", "0X", "0xg", "0x+5"] {
            assert_eq!(parse_int(value), None, "{value:?}");
        }
    }

    #[test]
    fn int_fails_outside_i32_range_instead_of_clamping() {
        assert_eq!(parse_int("2147483647"), Some(i32::MAX));
        assert_eq!(parse_int("2147483648"), None);
        assert_eq!(parse_int("-2147483648"), Some(i32::MIN));
        assert_eq!(parse_int("-2147483649"), None);
        assert_eq!(parse_int("0x80000000"), None);
    }

    #[test]
    fn long_fails_outside_i64_range_instead_of_clamping() {
        assert_eq!(parse_long("9223372036854775807"), Some(i64::MAX));
        assert_eq!(parse_long("9223372036854775808"), None);
        assert_eq!(parse_long("-9223372036854775808"), Some(i64::MIN));
        assert_eq!(parse_long("-9223372036854775809"), None);
        assert_eq!(parse_long("0x7fffffffffffffff"), Some(i64::MAX));
        assert_eq!(parse_long("0x8000000000000000"), None);
    }

    #[test]
    fn long_handles_absurdly_long_digit_runs() {
        // Overflow detection must not itself overflow, however many digits.
        let huge = "9".repeat(100);
        assert_eq!(parse_long(&huge), None);
        let negative_huge = format!("-{huge}");
        assert_eq!(parse_long(&negative_huge), None);
    }

    #[test]
    fn long_parses_plain_values() {
        assert_eq!(parse_long("0"), Some(0));
        assert_eq!(parse_long("-1"), Some(-1));
        assert_eq!(parse_long("35184372088832"), Some(1 << 45));
        assert_eq!(parse_long("0x200000000"), Some(1 << 33));
    }
}
