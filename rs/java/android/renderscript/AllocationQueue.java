/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.renderscript;

import java.util.HashMap;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Surface;
import android.util.Log;
import android.graphics.Canvas;
import android.os.Trace;

/**
 * @hide
 * <p> This class provides the primary method through which data is passed to
 * and from RenderScript kernels.  An Allocation provides the backing store for
 * a given {@link android.renderscript.Type}.  </p>
 *
 * <p>An Allocation also contains a set of usage flags that denote how the
 * Allocation could be used. For example, an Allocation may have usage flags
 * specifying that it can be used from a script as well as input to a {@link
 * android.renderscript.Sampler}. A developer must synchronize across these
 * different usages using {@link android.renderscript.Allocation#syncAll} in
 * order to ensure that different users of the Allocation have a consistent view
 * of memory. For example, in the case where an Allocation is used as the output
 * of one kernel and as Sampler input in a later kernel, a developer must call
 * {@link #syncAll syncAll(Allocation.USAGE_SCRIPT)} prior to launching the
 * second kernel to ensure correctness.
 *
 * <p>An Allocation can be populated with the {@link #copyFrom} routines. For
 * more complex Element types, the {@link #copyFromUnchecked} methods can be
 * used to copy from byte arrays or similar constructs.</p>
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For more information about creating an application that uses RenderScript, read the
 * <a href="{@docRoot}guide/topics/renderscript/index.html">RenderScript</a> developer guide.</p>
 * </div>
 **/

public class AllocationQueue {
    RenderScript mRS;
    Type mType;
    int mUsage;
    Allocation[] mAllocationArray;
    int mNumAlloc = 0;

    private Surface mGetSurfaceSurface = null;

   /**
     * Get the {@link android.renderscript.Element} of the {@link
     * android.renderscript.Type} of the Allocations.
     *
     * @return Element
     *
     */
    public Element getElement() {
        return mType.getElement();
    }

    /**
     * Get the usage flags of the Allocations.
     *
     * @return usage this Allocation's set of the USAGE_* flags OR'd together
     *
     */
    public int getUsage() {
        return mUsage;
    }

    /**
     * Enable/Disable AutoPadding for Vec3 elements.
     * By default: Diabled.
     *
     * @param useAutoPadding True: enable AutoPadding; False: disable AutoPadding
     *
     */
    public void setAutoPadding(boolean useAutoPadding) {
        for (Allocation alloc : mAllocationArray) {
            alloc.setAutoPadding(useAutoPadding);
        }
    }

    AllocationQueue(RenderScript rs, Type t, int usage, int numAlloc) {
        if ((usage & Allocation.USAGE_IO_INPUT) == 0) {
            throw new RSIllegalArgumentException("AllocationQueue only support USAGE_IO_INPUT.");
        }

        mRS = rs;
        mType = t;
        mUsage = usage;
        mNumAlloc = numAlloc;
    }

    /**
     * Get the {@link android.renderscript.Type} of the Allocations.
     *
     * @return Type
     *
     */
    public Type getType() {
        return mType;
    }

    /**
     * Receive the latest input into the Allocations. This operation
     * is only valid if {@link #USAGE_IO_INPUT} is set on the Allocation.
     *
     * The operation will update all the Allocations based on their position
     * in the Allocation array.
     */
    public void ioReceive() {
        try {
            Trace.traceBegin(RenderScript.TRACE_TAG, "ioReceive");
            if ((mUsage & Allocation.USAGE_IO_INPUT) == 0) {
                throw new RSIllegalArgumentException(
                    "Can only receive if IO_INPUT usage specified.");
            }
            mRS.validate();
            for (Allocation alloc : mAllocationArray) {
                alloc.ioReceive();
            }
        } finally {
            Trace.traceEnd(RenderScript.TRACE_TAG);
        }
    }

    /**
     * Creates a new Allocation Array with the given {@link
     * android.renderscript.Type}, and usage flags.
     *
     * @param type RenderScript type describing data layout
     * @param mips specifies desired mipmap behaviour for the
     *             allocation
     * @param usage bit field specifying how the Allocation is
     *              utilized
     * @return Allocation[]
     */
    public Allocation[] getAllocationArray() {
        try {
            Trace.traceBegin(RenderScript.TRACE_TAG, "getAllocationArray");
            if (mAllocationArray != null) {
                return mAllocationArray;
            }
            mRS.validate();
            if (mType.getID(mRS) == 0) {
                throw new RSInvalidStateException("Bad Type");
            }
            mAllocationArray = new Allocation[mNumAlloc];
            Allocation bufferQueueOwner = Allocation.createTyped(mRS, mType, mUsage);
            bufferQueueOwner.setupBufferQueue(mNumAlloc);
            mAllocationArray[0] = bufferQueueOwner;
            for (int i=1; i<mNumAlloc; i++) {
                mAllocationArray[i] = Allocation.createFromAllcation(mRS, bufferQueueOwner);
            }
            return mAllocationArray;
        } finally {
            Trace.traceEnd(RenderScript.TRACE_TAG);
        }
    }

    /**
     * Returns the handle to a raw buffer that is being managed by the screen
     * compositor. This operation is only valid for Allocations with {@link
     * #USAGE_IO_INPUT}.
     *
     * @return Surface object associated with allocation
     *
     */
    public Surface getSurface() {
        if (mNumAlloc == 0) {
            throw new RSInvalidStateException("No Allocation initialized.");
        }
        if ((mUsage & Allocation.USAGE_IO_INPUT) == 0) {
            throw new RSInvalidStateException("Allocation is not a surface texture.");
        }

        if (mGetSurfaceSurface == null) {
            mGetSurfaceSurface = mAllocationArray[0].getSurface();
        }

        return mGetSurfaceSurface;
    }
}
