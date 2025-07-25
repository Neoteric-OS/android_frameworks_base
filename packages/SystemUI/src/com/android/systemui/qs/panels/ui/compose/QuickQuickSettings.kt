/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.grid.ui.compose.VerticalSpannedGrid
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.panels.ui.compose.infinitegrid.Tile
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.TileHeight
import com.android.systemui.qs.panels.ui.viewmodel.QuickQuickSettingsViewModel
import com.android.systemui.qs.shared.ui.ElementKeys.toElementKey
import com.android.systemui.res.R

@Composable
fun ContentScope.QuickQuickSettings(
    viewModel: QuickQuickSettingsViewModel,
    modifier: Modifier = Modifier,
    listening: () -> Boolean,
) {

    val sizedTiles = viewModel.tileViewModels
    val tiles = sizedTiles.fastMap { it.tile }
    val squishiness by viewModel.squishinessViewModel.squishiness.collectAsStateWithLifecycle()

    val spans by remember(sizedTiles) { derivedStateOf { sizedTiles.fastMap { it.width } } }

    val density = LocalDensity.current
    var containerWidthPx by remember { mutableStateOf(0) }

    val columns = viewModel.columns
    BoxWithConstraints(modifier = modifier
        .fillMaxWidth()
        .onSizeChanged { containerWidthPx = it.width }
    ) {
        GridAnchor()

        val containerWidth = if (containerWidthPx > 0) {
            density.run { containerWidthPx.toDp() }
        } else {
            maxWidth
        }

        val columnSpacing = ((containerWidth - (TileHeight * columns)) / (columns - 1))
            .coerceAtLeast(dimensionResource(R.dimen.qs_tile_margin_horizontal))
    
        VerticalSpannedGrid(
            columns = columns,
            columnSpacing = columnSpacing,
            rowSpacing = dimensionResource(R.dimen.qs_tile_margin_vertical),
            spans = spans,
            modifier = Modifier.sysuiResTag("qqs_tile_layout"),
            keys = { sizedTiles[it].tile.spec },
        ) { spanIndex, column, isFirstInColumn, isLastInColumn ->
            val it = sizedTiles[spanIndex]
            Element(it.tile.spec.toElementKey(spanIndex), Modifier) {
                Tile(
                    tile = it.tile,
                    iconOnly = it.isIcon,
                    squishiness = { squishiness },
                    tileHapticsViewModelFactoryProvider =
                        viewModel.tileHapticsViewModelFactoryProvider,
                    // There should be no QuickQuickSettings when the details view is enabled.
                    detailsViewModel = null,
                    isVisible = listening,
                )
            }
        }
    }

    TileListener(tiles, listening)
}
