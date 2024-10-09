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
import org.modelix.mps.sync.tasks.SyncLock.MODELIX_READ
import org.modelix.mps.sync.tasks.SyncLock.MODELIX_WRITE
import org.modelix.mps.sync.tasks.SyncLock.MPS_READ
import org.modelix.mps.sync.tasks.SyncLock.MPS_WRITE
import org.modelix.mps.sync.tasks.SyncLock.NONE

/**
 * Represents a lock that is used by a [SyncTask] during synchronization:
 *   - [MPS_WRITE]: we need a write lock in MPS.
 *   - [MPS_READ]: we need a read lock in MPS.
 *   - [MODELIX_WRITE]: we need a write lock (transaction) in modelix.
 *   - [MODELIX_READ]: we need a read lock (transaction) in modelix.
 *   - [NONE]: we do not need any locks.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
enum class SyncLock {
    MPS_WRITE,
    MPS_READ,
    MODELIX_WRITE,
    MODELIX_READ,
    NONE,
}

/**
 * A comparator that can be used to sort the different [SyncLock]s in a collection.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class SyncLockComparator : Comparator<SyncLock> {

    /**
     * Order of locks is important, because MPS executes the action on a separate thread, where the modelix
     * transactions might not be available.
     *
     * Lock priority order: [MPS_WRITE] > [MPS_READ] > [MODELIX_WRITE] > [MODELIX_READ] > [NONE]
     *
     * @param left one of the locks to compare.
     * @param right the other lock to compare.
     *
     * @return if positive, then "left" should be before "right". If negative, then "right" should be before "left". If
     * zero then the order does not matter.
     */
    override fun compare(left: SyncLock, right: SyncLock) =
        if (left == MPS_WRITE) {
            if (left == right) {
                0
            } else {
                -1
            }
        } else if (left == MPS_READ) {
            if (right == MPS_WRITE) {
                1
            } else if (left == right) {
                0
            } else {
                -1
            }
        } else if (left == MODELIX_WRITE) {
            if (right == MPS_READ || right == MPS_WRITE) {
                1
            } else if (left == right) {
                0
            } else {
                -1
            }
        } else if (left == MODELIX_READ) {
            if (right == NONE) {
                -1
            } else if (left == right) {
                0
            } else {
                1
            }
        } else if (left == NONE) {
            if (left == right) {
                0
            } else {
                1
            }
        } else {
            0
        }
}
