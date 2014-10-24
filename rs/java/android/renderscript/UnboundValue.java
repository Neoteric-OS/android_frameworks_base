package android.renderscript;

import android.util.Pair;
import java.util.ArrayList;
import java.lang.Integer;
import java.util.List;

/**
   @hide Pending Android public API approval.
 */
public class UnboundValue {
  // Either mFieldID or mArgIndex should be set but not both.
  List<Pair<Closure, Script.FieldID>> mFieldID;
  // -1 means unset. Legal values are 0 .. n-1, where n is the number of
  // arguments for the referencing closure.
  List<Pair<Closure, Integer>> mArgIndex;

  UnboundValue() {
    mFieldID = new ArrayList<Pair<Closure, Script.FieldID>>();
    mArgIndex = new ArrayList<Pair<Closure, Integer>>();
  }

  void addReference(Closure closure, int index) {
    mArgIndex.add(Pair.create(closure, Integer.valueOf(index)));
  }

  void addReference(Closure closure, Script.FieldID fieldID) {
    mFieldID.add(Pair.create(closure, fieldID));
  }

  void set(Object value) {
    for (Pair<Closure, Integer> p : mArgIndex) {
      Closure closure = p.first;
      int index = p.second.intValue();
      closure.setArg(index, value);
    }
    for (Pair<Closure, Script.FieldID> p : mFieldID) {
      Closure closure = p.first;
      Script.FieldID fieldID = p.second;
      closure.setGlobal(fieldID, value);
    }
  }
}