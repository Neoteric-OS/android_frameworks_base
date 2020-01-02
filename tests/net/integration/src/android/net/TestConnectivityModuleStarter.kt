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
 * limitations under the License
 */

package android.net

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.android.server.net.integrationtests.TestNetworkStackService
import kotlin.test.fail

const val TEST_ACTION_SUFFIX = ".Test"

class TestConnectivityModuleStarter(context: Context, callback: ConnectivityModuleCallback)
        : ConnectivityModuleStarter(TestDependencies(context, callback)) {
    private class TestDependencies(private val context: Context,
            private val callback: ConnectivityModuleCallback) : Dependencies {
        override fun addToServiceManager(name: String?, service: IBinder) = Unit

        override fun getConnectivityModuleConnector(): ConnectivityModuleConnector {
            return ConnectivityModuleConnector { _, _, _, inSystemProcess ->
                getNetworkStackIntent(inSystemProcess)
            }.also { it.init(context) }
        }

        override fun onServiceRegistered(ignore: ConnectivityModuleCallback,
                service: IBinder) {
            callback.onServiceRegistered(service)
        }

        private fun getNetworkStackIntent(inSystemProcess: Boolean): Intent? {
            // Simulate out-of-system-process config: in-process service not found (null intent)
            if (inSystemProcess) return null
            val intent = Intent(INetworkStackConnector::class.qualifiedName + TEST_ACTION_SUFFIX)
            val serviceName = TestNetworkStackService::class.qualifiedName
                    ?: fail("TestNetworkStackService name not found")
            intent.component = ComponentName(context.packageName, serviceName)
            return intent
        }
    }
}
