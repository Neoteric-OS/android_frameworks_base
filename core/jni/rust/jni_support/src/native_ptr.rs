//! Typed abstractions for native pointers carried through Java `long` fields.
//!
//! A `jlong` does not describe either the pointee type or its ownership.  This
//! module deliberately keeps those concerns separate:
//!
//! * [`JLongHandle<T>`] is an inert, typed carrier for the pointer bits.
//! * [`ForeignPeer`] and [`ForeignPeerMut`] are call-scoped borrows of an
//!   object owned elsewhere (for example, a C++ `MotionEvent`).
//! * [`BoxPeer<T>`] is the ownership-taking case for a Rust `Box<T>`.
//!
//! Converting a live native pointer into a borrow is necessarily unsafe, but
//! dereferencing it after that conversion is safe and its lifetime is tied to
//! the peer value. This keeps the pointer audit at the JNI edge instead of
//! repeating raw dereferences throughout method bodies.

#![deny(unsafe_op_in_unsafe_fn)]

use std::marker::PhantomData;
use std::pin::Pin;
use std::ptr::NonNull;

use jni::sys::jlong;

/// The pointer bits stored in a Java `long`, tagged with their Rust pointee.
///
/// Constructing or inspecting this value never dereferences the pointer. A
/// mismatched pointee type is therefore caught by Rust's type checker without
/// imposing a registry lookup on hot JNI calls.
#[repr(transparent)]
pub struct JLongHandle<T> {
    raw: jlong,
    // Function-pointer variance avoids claiming ownership of, or auto-trait
    // properties from, T. This value only carries address bits.
    _pointee: PhantomData<fn() -> T>,
}

impl<T> Copy for JLongHandle<T> {}

impl<T> Clone for JLongHandle<T> {
    fn clone(&self) -> Self {
        *self
    }
}

impl<T> JLongHandle<T> {
    /// Tags pointer bits read from Java with their expected pointee type.
    pub const fn from_jlong(raw: jlong) -> Self {
        Self { raw, _pointee: PhantomData }
    }

    /// Tags a pointer without dereferencing it, for transfer through Java.
    pub fn from_ptr(ptr: *mut T) -> Self {
        Self::from_jlong(ptr as usize as jlong)
    }

    /// Returns the bits to store in, or compare with, a Java `long` field.
    pub const fn as_jlong(self) -> jlong {
        self.raw
    }

    /// Whether Java supplied its null-handle sentinel.
    pub const fn is_null(self) -> bool {
        self.raw == 0
    }

    /// Returns the typed pointer bits without dereferencing them.
    pub fn as_ptr(self) -> *mut T {
        self.raw as usize as *mut T
    }

    fn as_non_null(self) -> Option<NonNull<T>> {
        NonNull::new(self.as_ptr())
    }
}

/// A call-scoped shared borrow of a native object owned outside Rust.
pub struct ForeignPeer<'a, T: ?Sized> {
    ptr: NonNull<T>,
    _borrow: PhantomData<&'a T>,
}

impl<'a, T> ForeignPeer<'a, T> {
    /// Borrows a non-null foreign peer for `'a`.
    ///
    /// # Safety
    ///
    /// `handle` must point to a live, initialized `T` that remains at a stable
    /// address for `'a`, and no mutable reference to it may overlap `'a`.
    pub unsafe fn from_handle(handle: JLongHandle<T>) -> Option<Self> {
        Some(Self { ptr: handle.as_non_null()?, _borrow: PhantomData })
    }

    /// Returns the borrowed object. Pointer validation is confined to the
    /// unsafe constructor.
    pub fn get(&self) -> &T {
        // SAFETY: `from_handle` established validity for `'a`; borrowing self
        // can only shorten that lifetime.
        unsafe { self.ptr.as_ref() }
    }
}

/// A call-scoped exclusive borrow of a stable native object owned elsewhere.
pub struct ForeignPeerMut<'a, T: ?Sized> {
    ptr: NonNull<T>,
    _borrow: PhantomData<&'a mut T>,
}

impl<'a, T> ForeignPeerMut<'a, T> {
    /// Exclusively borrows a non-null foreign peer for `'a`.
    ///
    /// # Safety
    ///
    /// `handle` must point to a live, initialized `T` at a stable address for
    /// `'a`, and no other reference to it may overlap `'a`.
    pub unsafe fn from_handle(handle: JLongHandle<T>) -> Option<Self> {
        Some(Self { ptr: handle.as_non_null()?, _borrow: PhantomData })
    }

    /// Returns a pinned mutable borrow, suitable for C++ opaque types whose
    /// address must remain stable.
    pub fn pin(&mut self) -> Pin<&mut T> {
        // SAFETY: `from_handle` established exclusive access and a stable
        // address for `'a`; borrowing self can only shorten that lifetime.
        unsafe { Pin::new_unchecked(self.ptr.as_mut()) }
    }

    /// Returns an ordinary mutable borrow when the pointee does not require
    /// pinning.
    pub fn get_mut(&mut self) -> &mut T {
        // SAFETY: same exclusive-access invariant as `pin`.
        unsafe { self.ptr.as_mut() }
    }
}

/// A Rust `Box<T>` whose ownership is temporarily stored in a Java `long`.
///
/// This is intentionally distinct from [`ForeignPeer`]: reclaiming a pointer
/// allocated by C++ (or owned by a registry) with `Box::from_raw` is invalid.
pub struct BoxPeer<T>(Box<T>);

impl<T> BoxPeer<T> {
    /// Transfers a box to Java and returns the value for its native field.
    pub fn into_jlong(value: Box<T>) -> jlong {
        Box::into_raw(value) as usize as jlong
    }

    /// Reconstitutes the unique Rust-owned peer represented by `raw`.
    ///
    /// Returns `None` for zero.
    ///
    /// # Safety
    ///
    /// A non-zero `raw` must come from [`Self::into_jlong`] for the same `T`,
    /// must not already have been reclaimed, and must have no live borrows.
    pub unsafe fn from_jlong(raw: jlong) -> Option<Self> {
        let ptr = JLongHandle::<T>::from_jlong(raw).as_non_null()?;
        // SAFETY: required by this function's contract; reconstructing the Box
        // immediately gives early returns and panics normal RAII behavior.
        Some(Self(unsafe { Box::from_raw(ptr.as_ptr()) }))
    }

    /// Borrows the Rust-owned peer.
    pub fn get(&self) -> &T {
        &self.0
    }

    /// Mutably borrows the Rust-owned peer.
    pub fn get_mut(&mut self) -> &mut T {
        &mut self.0
    }

    /// Returns the box to Rust ownership.
    pub fn into_box(self) -> Box<T> {
        self.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn typed_handle_is_an_inert_bit_carrier() {
        let handle = JLongHandle::<String>::from_jlong(1234);
        assert_eq!(handle.as_jlong(), 1234);
        assert!(!handle.is_null());
        assert!(JLongHandle::<String>::from_jlong(0).is_null());
    }

    #[test]
    fn rust_owned_peer_round_trips() {
        let raw = BoxPeer::into_jlong(Box::new(String::from("hello")));
        // SAFETY: `raw` came from `BoxPeer::into_jlong` immediately above.
        let mut peer = unsafe { BoxPeer::<String>::from_jlong(raw) }.unwrap();
        assert_eq!(peer.get(), "hello");
        peer.get_mut().push_str(" world");
        assert_eq!(*peer.into_box(), "hello world");
    }

    #[test]
    fn null_box_peer_is_none() {
        // SAFETY: zero is explicitly accepted without dereferencing it.
        assert!(unsafe { BoxPeer::<i32>::from_jlong(0) }.is_none());
    }

    #[test]
    fn rust_owned_peer_drops_on_early_return() {
        use std::cell::Cell;

        struct DropFlag<'a>(&'a Cell<bool>);
        impl Drop for DropFlag<'_> {
            fn drop(&mut self) {
                self.0.set(true);
            }
        }

        let dropped = Cell::new(false);
        let raw = BoxPeer::into_jlong(Box::new(DropFlag(&dropped)));
        // SAFETY: `raw` came from `BoxPeer::into_jlong` immediately above.
        let peer = unsafe { BoxPeer::<DropFlag<'_>>::from_jlong(raw) }.unwrap();
        drop(peer);
        assert!(dropped.get());
    }

    #[test]
    fn foreign_shared_and_pinned_mut_borrows_are_scoped() {
        let mut value = 41;
        let raw = (&mut value as *mut i32) as usize as jlong;

        {
            // SAFETY: `value` is live and not mutably accessed in this scope.
            let peer = unsafe {
                ForeignPeer::from_handle(JLongHandle::<i32>::from_jlong(raw))
            }
            .unwrap();
            assert_eq!(*peer.get(), 41);
        }
        {
            // SAFETY: this scope has exclusive access to the live value.
            let mut peer = unsafe {
                ForeignPeerMut::from_handle(JLongHandle::<i32>::from_jlong(raw))
            }
            .unwrap();
            *peer.pin() = 42;
        }
        assert_eq!(value, 42);
    }
}
