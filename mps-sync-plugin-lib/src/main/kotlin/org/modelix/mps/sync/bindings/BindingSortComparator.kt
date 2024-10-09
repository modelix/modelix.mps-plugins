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

package org.modelix.mps.sync.bindings

import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.IBinding

/**
 * This comparator is used to sort the [IBinding]s in the correct order.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class BindingSortComparator : Comparator<IBinding> {

    /**
     * Decides the order of two [IBinding]s: [ModelBinding]s should come first, then [ModuleBinding]s. If both bindings
     * have the same type, then they are sorted lexicographically by their names.
     *
     * @param left one of the [IBinding]s to compare.
     * @param right the other [IBinding] to compare to.
     *
     * @return if positive, then [left] should be before [right]. If negative, then [right] should be before [left]. If
     * zero, then the order does not matter.
     */
    override fun compare(left: IBinding, right: IBinding): Int {
        val leftName = left.name()
        val rightName = right.name()

        if (left is ModelBinding) {
            if (right is ModelBinding) {
                return leftName.compareTo(rightName)
            } else if (right is ModuleBinding) {
                return -1
            }
        } else if (left is ModuleBinding) {
            if (right is ModelBinding) {
                return 1
            } else if (right is ModuleBinding) {
                return leftName.compareTo(rightName)
            }
        }
        return 0
    }
}
