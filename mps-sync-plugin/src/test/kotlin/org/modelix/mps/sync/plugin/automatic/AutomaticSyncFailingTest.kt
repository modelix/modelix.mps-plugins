/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.mps.sync.plugin.automatic

import SyncPluginTestBase
import org.junit.Assert
import org.modelix.mps.sync.plugin.configuration.env.MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_ENABLED

class AutomaticSyncFailingTest : SyncPluginTestBase() {
    override fun getByKeyCustomEnvValue(): Map<String, String> {
        return mapOf(MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_ENABLED to "true")
    }

    fun `test setting up automatic mode with missing configuration fails`() {
        val startupError = loggedErrorsDuringStartup.single()
        Assert.assertEquals("Value for MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_URL required.", startupError.message)
    }
}
