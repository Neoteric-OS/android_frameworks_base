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

/**
 *
 * BLAS
 *
 **/
public final class ScriptIntrinsicBLAS extends ScriptIntrinsic {
    private Allocation mLUT;

    private ScriptIntrinsicBLAS(long id, RenderScript rs) {
        super(id, rs);
    }

    /**
     */
    public static ScriptIntrinsicBLAS create(RenderScript rs) {
        long id = rs.nScriptIntrinsicCreate(13, Element.U32(rs).getID(rs));
        return new ScriptIntrinsicBLAS(id, rs);
    }

    /**
     * GEMM
     *
     */
    public void SGEMM(int TransA, int TransB, float alpha, Allocation A,
                      Allocation B, float beta, Allocation C) {
        mRS.nScriptIntrinsicBLAS_SGEMM(getID(mRS), TransA, TransB, A.getType().getX(), A.getType().getY(), B.getType().getY(), alpha, A.getID(mRS), B.getID(mRS),
                                       beta, C.getID(mRS));
    }
    /*    public void DGEMM(int TransA, int TransB, double alpha, Allocation A,
                      Allocation B, double beta, Allocation C) {
        mRS.nScriptIntrinsicBLAS_DGEMM(id, TransA, TransB, alpha, A.getID(mRS), B.getID(mRS),
                                       beta, C.getID(mRS));
    }
    public void CGEMM(int TransA, int TransB, Float2 alpha, Allocation A,
                      Allocation B, Float2 beta, Allocation C) {
        mRS.nScriptIntrinsicBLAS_CGEMM(id, TransA, TransB, alpha.x, alpha.y, A.getID(mRS), B.getID(mRS),
                                       beta.x, beta.y, C.getID(mRS));
    }
    public void ZGEMM(int TransA, int TransB, Double2 alpha, Allocation A,
                      Allocation B, Double2 beta, Allocation C) {
        mRS.nScriptIntrinsicBLAS_ZGEMM(id, TransA, TransB, alpha.x, alpha.y, A.getID(mRS), B.getID(mRS),
                                       beta.x, beta.y, C.getID(mRS));
                                       }*/


}

