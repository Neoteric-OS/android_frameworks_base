//! Bounded, allocation-free copies of Java string contents.
//!
//! [`BoundedUtfChars`] is the truncating analog of `ScopedUtfChars`: instead
//! of borrowing the whole string from the JVM, it copies a bounded prefix
//! into a stack buffer via `GetStringUTFRegion`, so hot native methods that
//! only need a length-capped C string (trace section names, for example)
//! never allocate and never pay for pathologically long inputs.

#![deny(unsafe_op_in_unsafe_fn)]

use std::ffi::{c_char, CStr};

use crate::JniError;

/// A NUL-terminated, fixed-capacity copy of a Java string prefix in
/// [Modified UTF-8](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/types.html#modified-utf-8-strings).
///
/// At most [`MAX_UTF16_UNITS`](Self::MAX_UTF16_UNITS) UTF-16 code units are
/// copied — `(CAP - 1) / 3`, since JNI encodes every UTF-16 unit (including
/// each half of a supplementary character's surrogate pair) as at most 3
/// bytes. Longer strings are truncated silently; that bound is the point of
/// this type. `CAP` must be at least 1, for the NUL terminator.
///
/// The buffer lives inline, so the whole value is `CAP` bytes on the stack
/// and construction performs no heap allocation.
///
/// ```no_run
/// # use jni_support::BoundedUtfChars;
/// # fn demo(env: &mut jni::Env<'_>, name: &jni::objects::JString<'_>) -> Result<(), jni_support::JniError> {
/// let mut copied = BoundedUtfChars::<3073>::copy_from(env, name)?;
/// for byte in copied.as_bytes_mut() {
///     if *byte == b'|' {
///         *byte = b' ';
///     }
/// }
/// let c_string: &std::ffi::CStr = copied.as_cstr();
/// # Ok(())
/// # }
/// ```
pub struct BoundedUtfChars<const CAP: usize> {
    /// Zero-filled before the JNI copy writes at most `CAP - 1` content
    /// bytes, so the buffer always contains at least one NUL and everything
    /// after the copied bytes is NUL.
    buf: [u8; CAP],
    /// Offset of the first NUL right after construction: the length of the
    /// copied bytes. Not updated by [`as_bytes_mut`](Self::as_bytes_mut)
    /// mutations; see that method for what writing a NUL means.
    len: usize,
}

impl<const CAP: usize> BoundedUtfChars<CAP> {
    const VALID_CAPACITY: () = assert!(CAP > 0, "BoundedUtfChars capacity must be non-zero");

    /// The maximum number of UTF-16 code units copied from the source string.
    ///
    /// Each unit needs at most 3 bytes of Modified UTF-8, and one byte is
    /// reserved so the buffer is always NUL-terminated.
    pub const MAX_UTF16_UNITS: usize = CAP.saturating_sub(1) / 3;

    /// Copies the first [`MAX_UTF16_UNITS`](Self::MAX_UTF16_UNITS) UTF-16
    /// code units of `string` into a new buffer.
    ///
    /// The copy is made with `GetStringUTFRegion`, which neither allocates
    /// nor throws here: the requested range is clamped to the string's
    /// length, so it is always in bounds. Returns `NullPointerException` for
    /// a null `JString` wrapper rather than passing null into the JNI table.
    ///
    /// [`JString`]: jni::objects::JString
    pub fn copy_from(
        env: &mut jni::Env<'_>,
        string: &jni::objects::JString<'_>,
    ) -> Result<BoundedUtfChars<CAP>, JniError> {
        let () = Self::VALID_CAPACITY;
        if string.is_null() {
            return Err(JniError::NullPointer("string must not be null"));
        }
        let string = string.as_raw();

        // The jni crate does not wrap GetStringUTFRegion, so call it through
        // the raw JNI function table. jni-sys groups the table's entries by the
        // JNI version that introduced them (`#[jni_to_union]`), so reaching a
        // function means dereferencing the env pointer twice to the
        // `JNINativeInterface_` and selecting the version union: GetStringLength
        // is a JNI 1.1 entry, GetStringUTFRegion a JNI 1.2 entry, both present
        // in every conforming table.
        let raw_env = env.get_raw();
        // SAFETY: `raw_env` comes from a live `Env`, which guarantees a non-null
        // pointer to a fully populated function table, so reading these version
        // union fields yields valid function pointers.
        let get_string_length = unsafe { (**raw_env).v1_1.GetStringLength };
        // SAFETY: As above.
        let get_string_utf_region = unsafe { (**raw_env).v1_2.GetStringUTFRegion };

        // SAFETY: `raw_env` is the current thread's env and `string` is the raw
        // reference behind a live `JString`, so it is a valid `java.lang.String`.
        // GetStringLength only reads.
        let length = unsafe { get_string_length(raw_env, string) };
        let max_units = Self::MAX_UTF16_UNITS.min(jni::sys::jsize::MAX as usize) as jni::sys::jsize;
        let units = length.clamp(0, max_units);

        let mut buf = [0u8; CAP];
        // SAFETY: env/string validity as above. The range [0, units) is
        // within the string because `units <= length`, so the call cannot
        // throw. The write fits: `units <= (CAP - 1) / 3` UTF-16 units at 3
        // bytes of Modified UTF-8 each is at most `CAP - 1` bytes, leaving
        // room even for VMs that append a NUL terminator.
        unsafe {
            get_string_utf_region(raw_env, string, 0, units, buf.as_mut_ptr().cast::<c_char>())
        };

        Ok(Self::from_zero_padded(buf))
    }

    /// Builds the value from a buffer whose content bytes are followed only
    /// by NULs, computing the cached length. `buf` must contain at least one
    /// NUL; `copy_from` guarantees that by zero-filling before the bounded
    /// write, since Modified UTF-8 has no interior NUL bytes (U+0000 is
    /// encoded as `C0 80`).
    fn from_zero_padded(buf: [u8; CAP]) -> BoundedUtfChars<CAP> {
        let () = Self::VALID_CAPACITY;
        let len = buf.iter().position(|&b| b == 0).expect("buffer must contain a NUL");
        BoundedUtfChars { buf, len }
    }

    /// The copied bytes as a C string.
    ///
    /// Runs up to the first NUL, so a NUL written through
    /// [`as_bytes_mut`](Self::as_bytes_mut) truncates the result.
    pub fn as_cstr(&self) -> &CStr {
        CStr::from_bytes_until_nul(&self.buf).expect("buffer always contains a NUL")
    }

    /// The copied bytes, mutably, for in-place sanitization.
    ///
    /// Only ASCII-for-ASCII replacements (both bytes below `0x80`) are safe
    /// at the byte level: every byte of a multi-byte Modified UTF-8 sequence
    /// has its high bit set, so such replacements can never split one.
    /// Writing a byte with the high bit set can corrupt a sequence, and
    /// writing a NUL truncates the string [`as_cstr`](Self::as_cstr) sees.
    pub fn as_bytes_mut(&mut self) -> &mut [u8] {
        &mut self.buf[..self.len]
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // `copy_from` needs a live JVM, so it is exercised by on-device tests of
    // its callers rather than faked here. These tests cover everything after
    // the JNI copy: the capacity math and the buffer-backed accessors, via
    // the same `from_zero_padded` seam `copy_from` uses.

    /// A zero-padded CAP-sized buffer holding `content`, as `copy_from`'s
    /// bounded JNI write would leave it.
    fn zero_padded<const CAP: usize>(content: &[u8]) -> [u8; CAP] {
        assert!(content.len() < CAP, "test content must leave room for the NUL");
        let mut buf = [0u8; CAP];
        buf[..content.len()].copy_from_slice(content);
        buf
    }

    #[test]
    fn capacity_math_reserves_the_nul_byte() {
        assert_eq!(BoundedUtfChars::<3073>::MAX_UTF16_UNITS, 1024);
        assert_eq!(BoundedUtfChars::<1>::MAX_UTF16_UNITS, 0);
        assert_eq!(BoundedUtfChars::<3>::MAX_UTF16_UNITS, 0);
        assert_eq!(BoundedUtfChars::<4>::MAX_UTF16_UNITS, 1);
        assert_eq!(BoundedUtfChars::<6>::MAX_UTF16_UNITS, 1);
        assert_eq!(BoundedUtfChars::<7>::MAX_UTF16_UNITS, 2);
    }

    #[test]
    fn as_cstr_yields_the_copied_bytes() {
        let copied = BoundedUtfChars::from_zero_padded(zero_padded::<16>(b"traceName"));
        assert_eq!(copied.as_cstr(), c"traceName");
    }

    #[test]
    fn empty_content_yields_an_empty_cstr() {
        let mut copied = BoundedUtfChars::from_zero_padded(zero_padded::<8>(b""));
        assert_eq!(copied.as_cstr(), c"");
        assert!(copied.as_bytes_mut().is_empty());
    }

    #[test]
    fn as_bytes_mut_spans_exactly_the_content() {
        let mut copied = BoundedUtfChars::from_zero_padded(zero_padded::<16>(b"abc"));
        assert_eq!(copied.as_bytes_mut(), b"abc");
    }

    #[test]
    fn ascii_sanitization_preserves_multibyte_sequences() {
        // "é|β\n" in Modified UTF-8 (same as UTF-8 for these): the reserved
        // ASCII bytes get replaced, the multi-byte sequences pass through
        // untouched because their bytes all have the high bit set.
        let content = b"\xC3\xA9|\xCE\xB2\n";
        let mut copied = BoundedUtfChars::from_zero_padded(zero_padded::<16>(content));
        for byte in copied.as_bytes_mut() {
            if *byte == b'\n' || *byte == b'|' {
                *byte = b' ';
            }
        }
        assert_eq!(copied.as_cstr().to_bytes(), b"\xC3\xA9 \xCE\xB2 ");
    }

    #[test]
    fn writing_a_nul_truncates_as_cstr() {
        let mut copied = BoundedUtfChars::from_zero_padded(zero_padded::<16>(b"abcdef"));
        copied.as_bytes_mut()[3] = 0;
        assert_eq!(copied.as_cstr(), c"abc");
        // The mutable span still covers the originally copied region.
        assert_eq!(copied.as_bytes_mut().len(), 6);
    }

    #[test]
    fn content_filling_the_buffer_to_one_short_of_cap_still_terminates() {
        let copied = BoundedUtfChars::from_zero_padded(zero_padded::<4>(b"abc"));
        assert_eq!(copied.as_cstr(), c"abc");
    }
}
