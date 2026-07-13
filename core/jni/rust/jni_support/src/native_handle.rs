//! Safe wrappers for jlong ↔ Box<T> conversions.
//!
//! Replaces the error-prone C++ pattern of `reinterpret_cast<T*>(jlong)` with
//! safe Rust abstractions that handle null checks and ownership semantics.

/// Provides safe conversions between `jlong` (Java's long, used to store native
/// pointers) and Rust `Box<T>` values.
///
/// The C++ JNI pattern stores native object pointers as Java `long` fields:
/// ```cpp
/// auto* ptr = reinterpret_cast<NativeObject*>(nativePtr);
/// ```
///
/// This module replaces that with safe Rust equivalents that:
/// - Handle null pointers gracefully (returning `None`)
/// - Preserve ownership semantics (Box for owned, references for borrowed)
/// - Prevent use-after-free through Rust's ownership system
pub struct NativeHandle;

/// Type alias for Java's jlong (i64), used to store native pointers.
pub type JLong = i64;

impl NativeHandle {
    /// Borrows a native object from a jlong pointer without taking ownership.
    ///
    /// Returns `None` if the pointer is null (0).
    ///
    /// # Safety
    /// - The `ptr` must have been created by `into_jlong` and not yet freed.
    /// - The caller must ensure no mutable references exist to the same object.
    /// - The referenced object must outlive the returned reference.
    pub unsafe fn borrow<'a, T>(ptr: JLong) -> Option<&'a T> {
        if ptr == 0 {
            return None;
        }
        let raw = ptr as *const T;
        // SAFETY: The caller guarantees `ptr` came from `into_jlong`, is not yet
        // freed, and that the object outlives the returned reference with no
        // concurrent mutable references. Non-null is checked above.
        Some(unsafe { &*raw })
    }

    /// Mutably borrows a native object from a jlong pointer without taking ownership.
    ///
    /// Returns `None` if the pointer is null (0).
    ///
    /// # Safety
    /// - The `ptr` must have been created by `into_jlong` and not yet freed.
    /// - The caller must ensure no other references exist to the same object.
    /// - The referenced object must outlive the returned reference.
    pub unsafe fn borrow_mut<'a, T>(ptr: JLong) -> Option<&'a mut T> {
        if ptr == 0 {
            return None;
        }
        let raw = ptr as *mut T;
        // SAFETY: The caller guarantees `ptr` came from `into_jlong`, is not yet
        // freed, and that no other references to the object exist for the
        // lifetime of the returned reference. Non-null is checked above.
        Some(unsafe { &mut *raw })
    }

    /// Converts a boxed value into a jlong for storage in Java.
    ///
    /// The value is leaked (not dropped) and can be recovered later with
    /// `from_jlong`. The caller is responsible for eventually calling
    /// `from_jlong` to reclaim the memory.
    pub fn into_jlong<T>(val: Box<T>) -> JLong {
        let raw = Box::into_raw(val);
        raw as JLong
    }

    /// Recovers an owned Box<T> from a jlong, taking back ownership.
    ///
    /// Returns `None` if the pointer is null (0).
    /// After this call, the jlong value must not be used again.
    ///
    /// # Safety
    /// - The `ptr` must have been created by `into_jlong`.
    /// - This must only be called once per pointer (double-free protection
    ///   is the caller's responsibility).
    /// - No references to the object may exist when this is called.
    pub unsafe fn from_jlong<T>(ptr: JLong) -> Option<Box<T>> {
        if ptr == 0 {
            return None;
        }
        let raw = ptr as *mut T;
        // SAFETY: The caller guarantees `ptr` came from `into_jlong` for a value
        // of type `T`, that ownership has not already been reclaimed, and that
        // no other references to the object exist. Non-null is checked above.
        Some(unsafe { Box::from_raw(raw) })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_round_trip_simple() {
        let val = Box::new(42i32);
        let ptr = NativeHandle::into_jlong(val);
        assert_ne!(ptr, 0);

        // SAFETY: `ptr` was produced by `into_jlong` above (or is 0 for the
        // null-case tests) and ownership is reclaimed exactly once.
        unsafe {
            let recovered = NativeHandle::from_jlong::<i32>(ptr).unwrap();
            assert_eq!(*recovered, 42);
        }
    }

    #[test]
    fn test_round_trip_struct() {
        #[derive(Debug, PartialEq)]
        struct TestStruct {
            x: f64,
            y: f64,
            name: String,
        }

        let val = Box::new(TestStruct { x: 1.0, y: 2.0, name: "test".to_string() });
        let ptr = NativeHandle::into_jlong(val);

        // SAFETY: `ptr` was produced by `into_jlong` above (or is 0 for the
        // null-case tests) and ownership is reclaimed exactly once.
        unsafe {
            let recovered = NativeHandle::from_jlong::<TestStruct>(ptr).unwrap();
            assert_eq!(recovered.x, 1.0);
            assert_eq!(recovered.y, 2.0);
            assert_eq!(recovered.name, "test");
        }
    }

    #[test]
    fn test_borrow() {
        let val = Box::new(100u64);
        let ptr = NativeHandle::into_jlong(val);

        // SAFETY: `ptr` was produced by `into_jlong` above (or is 0 for the
        // null-case tests) and ownership is reclaimed exactly once.
        unsafe {
            let borrowed = NativeHandle::borrow::<u64>(ptr).unwrap();
            assert_eq!(*borrowed, 100);

            // Clean up
            let _ = NativeHandle::from_jlong::<u64>(ptr);
        }
    }

    #[test]
    fn test_borrow_mut() {
        let val = Box::new(100u64);
        let ptr = NativeHandle::into_jlong(val);

        // SAFETY: `ptr` was produced by `into_jlong` above (or is 0 for the
        // null-case tests) and ownership is reclaimed exactly once.
        unsafe {
            let borrowed = NativeHandle::borrow_mut::<u64>(ptr).unwrap();
            *borrowed = 200;

            let borrowed2 = NativeHandle::borrow::<u64>(ptr).unwrap();
            assert_eq!(*borrowed2, 200);

            // Clean up
            let _ = NativeHandle::from_jlong::<u64>(ptr);
        }
    }

    #[test]
    fn test_null_borrow() {
        // SAFETY: `ptr` was produced by `into_jlong` above (or is 0 for the
        // null-case tests) and ownership is reclaimed exactly once.
        unsafe {
            assert!(NativeHandle::borrow::<i32>(0).is_none());
        }
    }

    #[test]
    fn test_null_borrow_mut() {
        // SAFETY: `ptr` was produced by `into_jlong` above (or is 0 for the
        // null-case tests) and ownership is reclaimed exactly once.
        unsafe {
            assert!(NativeHandle::borrow_mut::<i32>(0).is_none());
        }
    }

    #[test]
    fn test_null_from_jlong() {
        // SAFETY: `ptr` was produced by `into_jlong` above (or is 0 for the
        // null-case tests) and ownership is reclaimed exactly once.
        unsafe {
            assert!(NativeHandle::from_jlong::<i32>(0).is_none());
        }
    }

    #[test]
    fn test_multiple_borrows() {
        let val = Box::new(String::from("hello"));
        let ptr = NativeHandle::into_jlong(val);

        // SAFETY: `ptr` was produced by `into_jlong` above (or is 0 for the
        // null-case tests) and ownership is reclaimed exactly once.
        unsafe {
            let b1 = NativeHandle::borrow::<String>(ptr).unwrap();
            let b2 = NativeHandle::borrow::<String>(ptr).unwrap();
            assert_eq!(b1, b2);
            assert_eq!(b1.as_str(), "hello");

            // Clean up
            let _ = NativeHandle::from_jlong::<String>(ptr);
        }
    }

    #[test]
    fn test_into_jlong_nonzero() {
        // Ensure that into_jlong never returns 0 for a valid value
        let val = Box::new(0i32); // Even a zero value should produce a non-zero pointer
        let ptr = NativeHandle::into_jlong(val);
        assert_ne!(ptr, 0);

        // Clean up
        // SAFETY: `ptr` was produced by `into_jlong` above (or is 0 for the
        // null-case tests) and ownership is reclaimed exactly once.
        unsafe {
            let _ = NativeHandle::from_jlong::<i32>(ptr);
        }
    }
}
