//! Java exception throwing support.
//!
//! Provides a [`JavaException`] enum mapping common Java exception types to their
//! JNI class paths, and a [`throw!`] macro for concise exception throwing from
//! native methods.

/// Common Java exception types that native code may need to throw.
///
/// Each variant maps to its fully-qualified JNI class path via [`class_path()`].
///
/// [`class_path()`]: JavaException::class_path
pub enum JavaException {
    /// `java.lang.NullPointerException`
    NullPointerException,
    /// `java.lang.IllegalArgumentException`
    IllegalArgumentException,
    /// `java.lang.IllegalStateException`
    IllegalStateException,
    /// `java.lang.RuntimeException`
    RuntimeException,
    /// `java.lang.OutOfMemoryError`
    OutOfMemoryError,
    /// `java.lang.UnsupportedOperationException`
    UnsupportedOperationException,
    /// `java.lang.IndexOutOfBoundsException`
    IndexOutOfBoundsException,
    /// `java.lang.SecurityException`
    SecurityException,
}

impl JavaException {
    /// Returns the JNI class path for this exception type.
    pub const fn class_path(&self) -> &'static str {
        match self {
            Self::NullPointerException => "java/lang/NullPointerException",
            Self::IllegalArgumentException => "java/lang/IllegalArgumentException",
            Self::IllegalStateException => "java/lang/IllegalStateException",
            Self::RuntimeException => "java/lang/RuntimeException",
            Self::OutOfMemoryError => "java/lang/OutOfMemoryError",
            Self::UnsupportedOperationException => "java/lang/UnsupportedOperationException",
            Self::IndexOutOfBoundsException => "java/lang/IndexOutOfBoundsException",
            Self::SecurityException => "java/lang/SecurityException",
        }
    }
}

/// Throws a Java exception and returns a default value from the current function.
///
/// # Usage
///
/// ```ignore
/// // With message:
/// throw!(env, NullPointerException, "tag was null");
///
/// // Without message:
/// throw!(env, NullPointerException);
/// ```
///
/// Calls `env.throw_new(class_path, msg)` and then `return Default::default()`.
#[macro_export]
macro_rules! throw {
    ($env:expr, $exception:ident, $msg:expr) => {{
        let _ = $env.throw_new($crate::JavaException::$exception.class_path(), $msg);
        return Default::default();
    }};
    ($env:expr, $exception:ident) => {
        $crate::throw!($env, $exception, "")
    };
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_null_pointer_exception() {
        assert_eq!(
            JavaException::NullPointerException.class_path(),
            "java/lang/NullPointerException"
        );
    }

    #[test]
    fn test_illegal_argument_exception() {
        assert_eq!(
            JavaException::IllegalArgumentException.class_path(),
            "java/lang/IllegalArgumentException"
        );
    }

    #[test]
    fn test_illegal_state_exception() {
        assert_eq!(
            JavaException::IllegalStateException.class_path(),
            "java/lang/IllegalStateException"
        );
    }

    #[test]
    fn test_runtime_exception() {
        assert_eq!(JavaException::RuntimeException.class_path(), "java/lang/RuntimeException");
    }

    #[test]
    fn test_out_of_memory_error() {
        assert_eq!(JavaException::OutOfMemoryError.class_path(), "java/lang/OutOfMemoryError");
    }

    #[test]
    fn test_unsupported_operation_exception() {
        assert_eq!(
            JavaException::UnsupportedOperationException.class_path(),
            "java/lang/UnsupportedOperationException"
        );
    }

    #[test]
    fn test_index_out_of_bounds_exception() {
        assert_eq!(
            JavaException::IndexOutOfBoundsException.class_path(),
            "java/lang/IndexOutOfBoundsException"
        );
    }

    #[test]
    fn test_security_exception() {
        assert_eq!(JavaException::SecurityException.class_path(), "java/lang/SecurityException");
    }
}
