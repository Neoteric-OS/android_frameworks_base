/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.wallpapers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.service.wallpaper.WallpaperService.Engine
import android.testing.TestableLooper.RunWithLooper
import android.view.Surface
import android.view.SurfaceHolder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.spy
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class GradientColorWallpaperTest : SysuiTestCase() {

    @Mock private lateinit var surfaceHolder: SurfaceHolder

    @Mock private lateinit var surface: Surface

    @Mock private lateinit var canvas: Canvas

    @Mock private lateinit var mockContext: Context

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        whenever(surfaceHolder.surface).thenReturn(surface)
        whenever(surfaceHolder.surfaceFrame).thenReturn(surfaceFrame)
        whenever(surface.lockHardwareCanvas()).thenReturn(canvas)
        whenever(mockContext.getColor(anyInt())).thenReturn(1)
    }

    private fun createGradientColorWallpaperEngine(): Engine {
        val gradientColorWallpaper = GradientColorWallpaper()
        val engine = spy(gradientColorWallpaper.onCreateEngine())
        whenever(engine.displayContext).thenReturn(mockContext)
        return engine
    }

    @Test
    fun onSurfaceRedrawNeeded_shouldDrawInCanvas() {
        val engine = createGradientColorWallpaperEngine()
        engine.onCreate(surfaceHolder)

        engine.onSurfaceRedrawNeeded(surfaceHolder)

        verify(canvas).drawRect(any<RectF>(), any<Paint>())
    }

    private companion object {
        val surfaceFrame = Rect(0, 0, 100, 100)
    }
}
