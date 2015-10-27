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
 *
 * <p> This class provides the primary method creating an array of
 * {@link android.renderscript.Allocation} for multi-frame processing </p>
 *
 * <p>An AllocationQueue also contains a set of usage flags that denote how the
 * the array of Allocations could be used. Note, an AllocationQueue must have
 * usage flags specifying that it can be used as a {@link android.view.Surface}
 * consumer {@link android.renderscript.Allocation#USAGE_IO_INPUT}. </p>
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For more information about creating an application that uses RenderScript, read the
 * <a href="{@docRoot}guide/topics/renderscript/index.html">RenderScript</a> developer guide.</p>
 * </div>
 **/

public class AllocationQueue {
    static final int MAX_NUM_ALLOCATION = 16;

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
     * Get the {@link android.renderscript.Type} of the Allocations.
     *
     * @return Type
     *
     */
    public Type getType() {
        return mType;
    }


    AllocationQueue(RenderScript rs, Type t, int usage, int numAlloc) {
        if ((usage & Allocation.USAGE_IO_INPUT) == 0) {
            throw new RSIllegalArgumentException("AllocationQueue only support USAGE_IO_INPUT.");
        }
        if (numAlloc > MAX_NUM_ALLOCATION) {
            throw new RSIllegalArgumentException("Exceeds max number of Allocations allowed: " +
                                                 MAX_NUM_ALLOCATION);
        }

        mRS = rs;
        mType = t;
        mUsage = usage;
        mNumAlloc = numAlloc;
    }

    /**
     * Creates an AllocationQueue with a specified number of buffers needed
     *
     * @param rs Context to which the AllocationQueue will belong.
     * @param t Type to use in the AllocationQueue. All Allocations in this
     *          AllocationQueue will share the same Type.
     * @param usage Usage flags to use in the AllocationQueue. All Allocations in this
     *              AllocationQueue will share the same usage flag.
     * @param numAlloc the number of Buffers needed in the AllocationQueue.
     *
     * @return AllocationQueue
     */
    static public AllocationQueue create(RenderScript rs, Type t, int usage, int numAlloc) {
        try {
            Trace.traceBegin(RenderScript.TRACE_TAG, "create");
            rs.validate();
            return new AllocationQueue(rs, t, usage, numAlloc);
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
            Allocation bqOwner = Allocation.createTyped(mRS, mType, mUsage);
            bqOwner.setupBufferQueue(mNumAlloc);
            mAllocationArray[0] = bqOwner;
            for (int i=1; i<mNumAlloc; i++) {
                mAllocationArray[i] = Allocation.createFromAllcation(mRS, bqOwner);
            }
            return mAllocationArray;
        } finally {
            Trace.traceEnd(RenderScript.TRACE_TAG);
        }
    }

    /**
     * Returns the handle to a raw buffer that is being managed by the screen
     * compositor. This operation is only valid for Allocations with
     * {@link android.renderscript.Allocation#USAGE_IO_INPUT}.
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
