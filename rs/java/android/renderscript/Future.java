package android.renderscript;

/**
   @hide Pending Android public API approval.
 */
public class Future {
  Closure mClosure;
  Script.FieldID mFieldID;
  Object mValue;

  Future(Closure closure, Script.FieldID fieldID, Object value) {
    mClosure = closure;
    mFieldID = fieldID;
    mValue = value;
  }

  Closure getClosure() { return mClosure; }
  Script.FieldID getFieldID() { return mFieldID; }
  Object getValue() { return mValue; }
}