/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net.nsd;

import android.app.SystemServiceRegistry;
import android.content.Context;

/**
 * Class for performing registration for nsd services.
 *
 * @hide
 */
public final class NsdFrameworkInitializer {
    private NsdFrameworkInitializer() {}

    /**
     * Called by {@link SystemServiceRegistry}'s static initializer and registers nsd services to
     * {@link Context}, so that {@link Context#getSystemService} can return them.
     *
     * @throws IllegalStateException if this is called anywhere besides
     * {@link SystemServiceRegistry}.
     */
    public static void registerServiceWrappers() {
        SystemServiceRegistry.registerContextAwareService(
                Context.NSD_SERVICE,
                NsdManager.class,
                (context, serviceBinder) -> {
                    INsdManager service = INsdManager.Stub.asInterface(serviceBinder);
                    return new NsdManager(context, service);
                }
        );
    }
}
