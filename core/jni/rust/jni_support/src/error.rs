//! JNI error handling types and helpers.
//!
//! Provides a typed error enum that maps to Java exceptions, plus a `jni_call`
//! wrapper that catches errors and throws the appropriate Java exception.

use std::fmt;

/// Error type representing common Java exceptions that JNI methods may throw.
///
/// Each variant maps to a specific Java exception class. Use `throw_on` to
/// throw the corresponding exception on the JNI environment.
#[derive(Debug)]
pub enum JniError {
    /// java.lang.NullPointerException
    NullPointer(&'static str),
    /// java.lang.IllegalArgumentException
    IllegalArgument(String),
    /// java.lang.RuntimeException
    Runtime(String),
    /// java.lang.OutOfMemoryError
    OutOfMemory,
    /// java.lang.IllegalStateException
    IllegalState(String),
    /// java.lang.UnsupportedOperationException
    UnsupportedOperation(String),
    /// java.lang.IndexOutOfBoundsException
    IndexOutOfBounds(String),
    /// java.lang.SecurityException
    SecurityException(String),
}

impl fmt::Display for JniError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            JniError::NullPointer(msg) => write!(f, "NullPointerException: {}", msg),
            JniError::IllegalArgument(msg) => write!(f, "IllegalArgumentException: {}", msg),
            JniError::Runtime(msg) => write!(f, "RuntimeException: {}", msg),
            JniError::OutOfMemory => write!(f, "OutOfMemoryError"),
            JniError::IllegalState(msg) => write!(f, "IllegalStateException: {}", msg),
            JniError::UnsupportedOperation(msg) => {
                write!(f, "UnsupportedOperationException: {}", msg)
            }
            JniError::IndexOutOfBounds(msg) => write!(f, "IndexOutOfBoundsException: {}", msg),
            JniError::SecurityException(msg) => write!(f, "SecurityException: {}", msg),
        }
    }
}

impl std::error::Error for JniError {}
impl JniError {
    /// Returns the fully-qualified Java exception class name for this error.
    pub fn exception_class(&self) -> &'static str {
        match self {
            JniError::NullPointer(_) => "java/lang/NullPointerException",
            JniError::IllegalArgument(_) => "java/lang/IllegalArgumentException",
            JniError::Runtime(_) => "java/lang/RuntimeException",
            JniError::OutOfMemory => "java/lang/OutOfMemoryError",
            JniError::IllegalState(_) => "java/lang/IllegalStateException",
            JniError::UnsupportedOperation(_) => "java/lang/UnsupportedOperationException",
            JniError::IndexOutOfBounds(_) => "java/lang/IndexOutOfBoundsException",
            JniError::SecurityException(_) => "java/lang/SecurityException",
        }
    }

    /// Returns the error message string.
    pub fn message(&self) -> String {
        match self {
            JniError::NullPointer(msg) => msg.to_string(),
            JniError::IllegalArgument(msg) => msg.clone(),
            JniError::Runtime(msg) => msg.clone(),
            JniError::OutOfMemory => "Out of memory".to_string(),
            JniError::IllegalState(msg) => msg.clone(),
            JniError::UnsupportedOperation(msg) => msg.clone(),
            JniError::IndexOutOfBounds(msg) => msg.clone(),
            JniError::SecurityException(msg) => msg.clone(),
        }
    }

    /// Throws the corresponding Java exception on the given JNI environment.
    ///
    /// # Arguments
    /// * `env` - The JNI environment to throw the exception on
    pub fn throw_on(&self, env: &mut jni::JNIEnv<'_>) {
        env.throw_new(self.exception_class(), self.message())
            .expect("Failed to throw Java exception");
    }
}

/// Wraps a fallible JNI operation, catching errors and throwing Java exceptions.
///
/// If the closure returns `Ok(value)`, the value is returned directly.
/// If it returns `Err(error)`, the error is thrown as a Java exception and the
/// default value for `T` is returned.
///
/// # Arguments
/// * `env` - The JNI environment
/// * `f` - The fallible operation to execute
///
/// # Returns
/// The success value, or `T::default()` if an error was thrown.
pub fn jni_call<T: Default>(
    env: &mut jni::JNIEnv<'_>,
    f: impl FnOnce(&mut jni::JNIEnv<'_>) -> Result<T, JniError>,
) -> T {
    match f(env) {
        Ok(val) => val,
        Err(err) => {
            err.throw_on(env);
            T::default()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_exception_class_names() {
        assert_eq!(
            JniError::NullPointer("test").exception_class(),
            "java/lang/NullPointerException"
        );
        assert_eq!(
            JniError::IllegalArgument("test".into()).exception_class(),
            "java/lang/IllegalArgumentException"
        );
        assert_eq!(
            JniError::Runtime("test".into()).exception_class(),
            "java/lang/RuntimeException"
        );
        assert_eq!(JniError::OutOfMemory.exception_class(), "java/lang/OutOfMemoryError");
        assert_eq!(
            JniError::IllegalState("test".into()).exception_class(),
            "java/lang/IllegalStateException"
        );
        assert_eq!(
            JniError::UnsupportedOperation("test".into()).exception_class(),
            "java/lang/UnsupportedOperationException"
        );
        assert_eq!(
            JniError::IndexOutOfBounds("test".into()).exception_class(),
            "java/lang/IndexOutOfBoundsException"
        );
        assert_eq!(
            JniError::SecurityException("test".into()).exception_class(),
            "java/lang/SecurityException"
        );
    }

    #[test]
    fn test_error_messages() {
        assert_eq!(JniError::NullPointer("null ptr").message(), "null ptr");
        assert_eq!(JniError::IllegalArgument("bad arg".into()).message(), "bad arg");
        assert_eq!(JniError::Runtime("oops".into()).message(), "oops");
        assert_eq!(JniError::OutOfMemory.message(), "Out of memory");
    }

    #[test]
    fn test_display() {
        assert_eq!(format!("{}", JniError::NullPointer("test")), "NullPointerException: test");
        assert_eq!(
            format!("{}", JniError::IllegalArgument("bad".into())),
            "IllegalArgumentException: bad"
        );
        assert_eq!(format!("{}", JniError::OutOfMemory), "OutOfMemoryError");
    }
}
