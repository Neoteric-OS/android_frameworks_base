package android.renderscript;

import android.util.Log;
import android.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**

******************************
You have tried to change the API from what has been previously approved.

To make these errors go away, you have two choices:
   1) You can add "@hide" javadoc comments to the methods, etc. listed in the
      errors above.

   2) You can update current.txt by executing the following command:
         make update-api

To submit the revised current.txt to the main Android repository,
you will need approval.
******************************

   @hide Pending Android public API approval.
 */
public class ScriptGroup2 extends BaseObj {
  List<Closure> mClosures;
  List<UnboundValue> mInputs;
  Future[] mOutputs;

  private static final String TAG = "ScriptGroup2";

  public ScriptGroup2(long id, RenderScript rs) {
    super(id, rs);
  }

  ScriptGroup2(RenderScript rs, List<Closure> closures,
      List<UnboundValue> inputs, Future[] outputs) {
    super(0, rs);
    mClosures = closures;
    mInputs = inputs;
    mOutputs = outputs;

    long[] closureIDs = new long[closures.size()];
    for (int i = 0; i < closureIDs.length; i++) {
      closureIDs[i] = closures.get(i).getID(rs);
    }
    long id = rs.nScriptGroup2Create(closureIDs);
    setID(id);
  }

  // TODO: If this was reflected method, we could enforce the number of
  // arguments.
  public Object[] execute(Object... inputs) {
    if (inputs.length < mInputs.size()) {
      Log.e(TAG, this.toString() + " receives " + inputs.length + " inputs, " +
          "less than expected " + mInputs.size());
      return null;
    }

    if (inputs.length > mInputs.size()) {
      Log.i(TAG, this.toString() + " receives " + inputs.length + " inputs, " +
          "more than expected " + mInputs.size());
    }

    for (int i = 0; i < mInputs.size(); i++) {
      Object obj = inputs[i];
      if (obj instanceof Future || obj instanceof UnboundValue) {
        Log.e(TAG, this.toString() + ": input " + i +
            " is a future or unbound value");
        return null;
      }
      UnboundValue unbound = mInputs.get(i);
      unbound.set(obj);
    }

    mRS.nScriptGroup2Execute(getID(mRS));

    Object[] outputObjs = new Object[mOutputs.length];
    int i = 0;
    for (Future f : mOutputs) {
      outputObjs[i++] = f.getValue();
    }
    return outputObjs;
  }

  /**
     @hide Pending Android public API approval.
   */
  public static final class Builder {
    RenderScript mRS;
    List<Closure> mClosures;
    List<UnboundValue> mInputs;

    private static final String TAG = "ScriptGroup2.Builder";

    public Builder(RenderScript rs) {
      mRS = rs;
      mClosures = new ArrayList<Closure>();
      mInputs = new ArrayList<UnboundValue>();
    }

    public Closure addKernel(Script.KernelID k, Type returnType, Object[] args,
        Map<Script.FieldID, Object> globalBindings) {
      Closure c = new Closure(mRS, k, returnType, args, globalBindings);
      mClosures.add(c);
      return c;
    }

    public UnboundValue addInput() {
      UnboundValue unbound = new UnboundValue();
      mInputs.add(unbound);
      return unbound;
    }

    public ScriptGroup2 create(Future... outputs) {
      // TODO: Save all script groups that have been created and return one that was
      // saved and matches the outputs.
      ScriptGroup2 ret = new ScriptGroup2(mRS, mClosures, mInputs, outputs);
      return ret;
    }

  }
}
