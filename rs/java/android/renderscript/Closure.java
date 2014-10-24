package android.renderscript;

import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
   @hide Pending Android public API approval.
 */
public class Closure extends BaseObj {
  private Allocation mReturnValue;
  private Map<Script.FieldID, Object> mBindings;

  private Future mReturnFuture;
  private Map<Script.FieldID, Future> mGlobalFuture;

  private static final String TAG = "Closure";

  public Closure(long id, RenderScript rs) {
    super(id, rs);
  }

  public Closure(RenderScript rs, Script.KernelID kernelID, Type returnType,
      Object[] args, Map<Script.FieldID, Object> globals) {
    super(0, rs);

    mReturnValue = Allocation.createTyped(rs, returnType);
    mBindings = new HashMap<Script.FieldID, Object>();
    mGlobalFuture = new HashMap<Script.FieldID, Future>();

    int numValues = args.length + globals.size();

    long[] fieldIDs = new long[numValues];
    long[] values = new long[numValues];
    int[] sizes = new int[numValues];
    long[] depClosures = new long[numValues];
    long[] depFieldIDs = new long[numValues];

    int i;
    for (i = 0; i < args.length; i++) {
      Object obj = args[i];
      fieldIDs[i] = 0;
      if (obj instanceof UnboundValue) {
        UnboundValue unbound = (UnboundValue)obj;
        unbound.addReference(this, i);
      } else {
        retrieveValueAndDependenceInfo(rs, i, args[i], values, sizes,
            depClosures, depFieldIDs);
      }
    }

    for (Map.Entry<Script.FieldID, Object> entry : globals.entrySet()) {
      Object obj = entry.getValue();
      Script.FieldID fieldID = entry.getKey();
      fieldIDs[i] = fieldID.getID(rs);
      if (obj instanceof UnboundValue) {
        UnboundValue unbound = (UnboundValue)obj;
        unbound.addReference(this, fieldID);
      } else {
        retrieveValueAndDependenceInfo(rs, i, obj, values,
            sizes, depClosures, depFieldIDs);
      }
      i++;
    }

    long id = rs.nClosureCreate(kernelID.getID(rs), mReturnValue.getID(rs),
        fieldIDs, values, sizes, depClosures, depFieldIDs);

    setID(id);
  }

  private static void retrieveValueAndDependenceInfo(RenderScript rs,
      int index, Object obj, long[] values, int[] sizes, long[] depClosures,
      long[] depFieldIDs) {

    if (obj instanceof Future) {
      Future f = (Future)obj;
      obj = f.getValue();
      depClosures[index] = f.getClosure().getID(rs);
      Script.FieldID fieldID = f.getFieldID();
      depFieldIDs[index] = fieldID != null ? fieldID.getID(rs) : 0;
    } else {
      depClosures[index] = 0;
      depFieldIDs[index] = 0;
    }

    ValueAndSize vs = new ValueAndSize(rs, obj);
    values[index] = vs.value;
    sizes[index] = vs.size;
  }

  public Future getReturn() {
    if (mReturnFuture == null) {
      mReturnFuture = new Future(this, null, mReturnValue);
    }

    return mReturnFuture;
  }

  public Future getGlobal(Script.FieldID field) {
    Future f = mGlobalFuture.get(field);

    if (f == null) {
      f = new Future(this, field, mBindings.get(field));
      mGlobalFuture.put(field, f);
    }

    return f;
  }

  // Evaluate this closure.
  public void eval() {
    mRS.nClosureEval(getID(mRS));
  }

  // Evaluate this closure and all its dependences in the right order.
  public void evalAll() {
  }

  void setArg(int index, Object obj) {
    ValueAndSize vs = new ValueAndSize(mRS, obj);
    mRS.nClosureSetArg(getID(mRS), index, vs.value, vs.size);
  }

  void setGlobal(Script.FieldID fieldID, Object obj) {
    ValueAndSize vs = new ValueAndSize(mRS, obj);
    mRS.nClosureSetGlobal(getID(mRS), fieldID.getID(mRS), vs.value, vs.size);
  }

  private static final class ValueAndSize {
    public ValueAndSize(RenderScript rs, Object obj) {
      if (obj instanceof Allocation) {
        value = ((Allocation)obj).getID(rs);
        size = -1;
      } else if (obj instanceof Boolean) {
        value = ((Boolean)obj).booleanValue() ? 1 : 0;
        size = 4;
      } else if (obj instanceof Integer) {
        value = ((Integer)obj).longValue();
        size = 4;
      } else if (obj instanceof Long) {
        value = ((Long)obj).longValue();
        size = 8;
      } else if (obj instanceof Float) {
        value = ((Float)obj).longValue();
        size = 4;
      } else if (obj instanceof Double) {
        value = ((Double)obj).longValue();
        size = 8;
      }
    }

    public long value;
    public int size;
  }
}
