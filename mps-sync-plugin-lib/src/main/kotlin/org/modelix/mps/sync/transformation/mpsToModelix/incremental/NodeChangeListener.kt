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

package org.modelix.mps.sync.transformation.mpsToModelix.incremental

import org.jetbrains.mps.openapi.event.SNodeAddEvent
import org.jetbrains.mps.openapi.event.SNodeRemoveEvent
import org.jetbrains.mps.openapi.event.SPropertyChangeEvent
import org.jetbrains.mps.openapi.event.SReferenceChangeEvent
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeChangeListener
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.mpsadapters.MPSProperty
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.transformation.mpsToModelix.initial.NodeSynchronizer

/**
 * The change listener that is called, when a change on in an [SNode] in MPS occurred. This change will be played onto
 * the model server MPS in a way that the MPS elements are transformed to modelix elements by the corresponding
 * transformer methods.
 *
 * @param branch the modelix branch we are connected to.
 * @param serviceLocator a collector class to simplify injecting the commonly used services in the sync plugin.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class NodeChangeListener(branch: IBranch, serviceLocator: ServiceLocator) : SNodeChangeListener {

    /**
     * Synchronizes an [SNode] to an [INode] on the model server.
     */
    private val synchronizer = NodeSynchronizer(branch, serviceLocator = serviceLocator)

    /**
     * Handles an [SNode] added event. The added [SNode] should be synced to the model server.
     *
     * @param event contains the [SNode] that has to be synced to the model server.
     *
     * @see [NodeSynchronizer.addNode].
     * @see [SNodeChangeListener.nodeAdded].
     */
    override fun nodeAdded(event: SNodeAddEvent) {
        synchronizer.addNode(event.child)
    }

    /**
     * Handles an [SNode] removed event. The removed [SNode] should be removed from the model server.
     *
     * @param event contains the [SNode] that has to be removed from the model server.
     *
     * @see [NodeSynchronizer.removeNode].
     * @see [SNodeChangeListener.nodeRemoved].
     */
    override fun nodeRemoved(event: SNodeRemoveEvent) {
        synchronizer.removeNode(
            parentNodeIdProducer = {
                if (event.isRoot) {
                    it[event.model]!!
                } else {
                    it[event.parent!!]!!
                }
            },
            childNodeIdProducer = { it[event.child]!! },
        )
    }

    /**
     * Handles a property changed event of an [SNode].
     *
     * @param event contains the [SNode], the property and its new value that have to be synced to the model server.
     *
     * @see [NodeSynchronizer.setProperty].
     * @see [SNodeChangeListener.propertyChanged].
     */
    override fun propertyChanged(event: SPropertyChangeEvent) {
        synchronizer.setProperty(MPSProperty(event.property), event.newValue) { it[event.node]!! }
    }

    /**
     * Handles a reference changed event of an [SNode].
     *
     * @param event contains the [SNode], the reference and its new target node that have to be synced to the model
     * server.
     *
     * @see [NodeSynchronizer.setReference].
     * @see [SNodeChangeListener.referenceChanged].
     */
    override fun referenceChanged(event: SReferenceChangeEvent) {
        // TODO fix me: it does not work correctly, if event.newValue.targetNode points to a node that is in a different model, that has not been synced yet to model server...
        synchronizer.setReference(event.associationLink, event.node, event.newValue?.targetNode)
    }
}
