/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.systemconfig

import android.content.Context
import android.content.pm.FeatureInfo
import android.util.Xml

import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.SystemConfig
import com.google.common.truth.Truth.assertThat
import org.junit.runner.RunWith
import org.junit.Rule

import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class SystemConfigCompiledFeaturesTest {

    companion object {
        private const val FEATURE_ONE = "feature.test.1"
        private const val FEATURE_TWO = "feature.test.2"
        private const val FEATURE_RUNTIME_AVAILABLE_TEMPLATE =
        """
            <permissions>
                <feature name="%s" />
            </permissions>
        """
        private const val FEATURE_RUNTIME_UNAVAILABLE_TEMPLATE =
        """
            <permissions>
                <unavailable-feature name="%s" />
            </permissions>
        """

        fun featureInfo(featureName: String) = FeatureInfo().apply { name = featureName }
    }

    private val context: Context = InstrumentationRegistry.getInstrumentation().context

    @get:Rule
    val tempFolder = TemporaryFolder(context.filesDir)

    private val injector = TestInjector()

    private var uniqueCounter = 0

    @Test
    fun empty() {
        assertFeatures().isEmpty()
    }

    @Test
    fun compiledAvailable() {
        addCompileTimeAvailableFeature(FEATURE_ONE)
        addCompileTimeAvailableFeature(FEATURE_TWO)

        assertFeatures().containsAtLeast(FEATURE_ONE, FEATURE_TWO)
    }

    @Test
    fun compiledAndRuntimeAvailable() {
        addCompileTimeAvailableFeature(FEATURE_ONE)
        addRuntimeAvailableFeature(FEATURE_TWO)

        // No issues with matching availability.
        assertFeatures().containsAtLeast(FEATURE_ONE, FEATURE_TWO)
    }

    @Test
    fun compiledAvailableRuntimeUnavailable() {
        addCompileTimeAvailableFeature(FEATURE_ONE)
        addRuntimeUnavailableFeature(FEATURE_ONE)

        // Compiled feature availability should take precedence.
        assertFeatures().contains(FEATURE_ONE)
    }

    @Test
    fun compiledUnavailable() {
        addCompileTimeUnavailableFeature(FEATURE_ONE)

        assertFeatures().doesNotContain(FEATURE_ONE)
    }

    @Test
    fun compiledAndRuntimeUnavailable() {
        addCompileTimeUnavailableFeature(FEATURE_ONE)
        addRuntimeUnavailableFeature(FEATURE_ONE)

        // No issues with matching (un)availability.
        assertFeatures().doesNotContain(FEATURE_ONE)
    }

    @Test
    fun compiledUnavailableRuntimeAvailable() {
        addCompileTimeUnavailableFeature(FEATURE_ONE)
        addRuntimeAvailableFeature(FEATURE_ONE)
        addRuntimeAvailableFeature(FEATURE_TWO)

        // Compiled feature (un)availability should take precedence.
        assertFeatures().doesNotContain(FEATURE_ONE)
        assertFeatures().contains(FEATURE_TWO)
    }

    fun addCompileTimeAvailableFeature(featureName: String) {
        injector.compiledAvailableFeatures[featureName] = featureInfo(featureName)
    }

    fun addCompileTimeUnavailableFeature(featureName: String) {
        injector.compiledUnavailableFeatures.add(featureName)
    }

    fun addRuntimeAvailableFeature(featureName: String) {
        FEATURE_RUNTIME_AVAILABLE_TEMPLATE.format(featureName).write()
    }

    fun addRuntimeUnavailableFeature(featureName: String) {
        FEATURE_RUNTIME_UNAVAILABLE_TEMPLATE.format(featureName).write()
    }

    private fun String.write() = tempFolder.root.resolve("${uniqueCounter++}.xml")
            .writeText(this.trimIndent())

    private fun assertFeatures() = assertThat(availableFeatures().keys)

    private fun availableFeatures() = SystemConfig(false, injector).apply {
        val parser = Xml.newPullParser()
        readPermissions(parser, tempFolder.root, /*Grant all permission flags*/ 0.inv())
    }.let { it.availableFeatures }

    internal class TestInjector() : SystemConfig.Injector() {
        val compiledAvailableFeatures = mutableMapOf<String, FeatureInfo>()
        val compiledUnavailableFeatures = mutableSetOf<String>()

        override fun isCompileTimeAvailableFeature(featureName: String, version: Int): Boolean {
            return compiledAvailableFeatures.containsKey(featureName)
        }

        override fun isCompileTimeUnavailableFeature(featureName: String, version: Int): Boolean {
            return compiledUnavailableFeatures.contains(featureName)
        }

        override fun getCompileTimeAvailableFeatures(): Map<String, FeatureInfo> {
            return compiledAvailableFeatures
        }
    }
}
