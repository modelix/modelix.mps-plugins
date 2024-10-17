/*
 * Copyright (c) 2024.
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

package org.modelix.mps.sync.tasks

import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.tasks.SyncDirection.MODELIX_TO_MPS
import org.modelix.mps.sync.tasks.SyncDirection.MPS_TO_MODELIX
import org.modelix.mps.sync.tasks.SyncDirection.NONE

/**
 * Specifies the direction of the [SyncTask]:
 *   - [MODELIX_TO_MPS]: a synchronization task from the model server to MPS.
 *   - [MPS_TO_MODELIX]: a synchronization task from MPS to the model server.
 *   - [NONE]: a synchronization task that does something else.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
enum class SyncDirection {
    MODELIX_TO_MPS,
    MPS_TO_MODELIX,
    NONE, // other, non-sync tasks
}
