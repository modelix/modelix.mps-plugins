package org.modelix.model.mpsplugin

import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.smodel.SNode
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.INodeWrapper
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PNodeAdapter.Companion.wrap
import org.modelix.model.area.IArea
import org.modelix.model.lazy.unwrap
import org.modelix.model.mpsadapters.mps.ANode
import org.modelix.model.mpsadapters.mps.MPSArea
import org.modelix.model.mpsadapters.mps.NodeToSNodeAdapter
import org.modelix.model.mpsadapters.mps.SNodeToNodeAdapter

/*Generated by MPS */
object TransactionUtil {
    fun extractArea(obj: Any?): IArea? {
        if (obj is PNodeAdapter) {
            return obj.getArea()
        } else if (obj is NodeToSNodeAdapter) {
            return extractArea(SNodeToNodeAdapter.Companion.wrap((obj as NodeToSNodeAdapter?)))
        } else if (obj is SNodeToNodeAdapter) {
            return extractArea(obj.getWrapped())
        } else if (obj is IArea) {
            return obj
        } else if (obj is INodeWrapper) {
            return extractArea(obj.getWrappedNode())
        } else if (obj is ANode) {
            return extractArea(ANode.Companion.unwrap((obj as ANode?)))
        } else if (obj is SNode) {
            val repository: SRepository? = check_276zg0_a0a0f0a1(obj.getModel())
            return (if (repository == null) MPSArea() else MPSArea(repository))
        } else {
            return null
        }
    }

    fun runWriteOnNodes(nodesToRead: Iterable<Any>, r: Runnable) {
        runWriteOnAreas(nodesToRead.mapNotNull { extractArea(it) }, r)
    }

    fun runWriteOnNode(nodeToRead: Any, r: Runnable) {
        runWriteOnNodes(Sequence.singleton(nodeToRead), r)
    }

    fun runWriteOnAreas(areasToRead: Iterable<IArea>, r: Runnable) {
        if (Sequence.fromIterable(areasToRead).isEmpty()) {
            r.run()
        } else {
            Sequence.fromIterable(areasToRead).first().executeWrite({
                runReadOnAreas(Sequence.fromIterable(areasToRead).skip(1), r)
                Unit
            })
        }
    }

    fun runReadOnNode(nodesToRead: Any, r: Runnable) {
        runReadOnNodes(Sequence.singleton(nodesToRead), r)
    }

    fun runReadOnNodes(nodesToRead: Iterable<Any>, r: Runnable) {
        runReadOnAreas(nodesToRead.mapNotNull { extractArea(it) }, r)
    }

    fun runReadOnAreas(areasToRead: Iterable<IArea>?, r: Runnable) {
        if (Sequence.fromIterable(areasToRead).isEmpty()) {
            r.run()
        } else {
            Sequence.fromIterable(areasToRead).first().executeRead({
                runReadOnAreas(Sequence.fromIterable(areasToRead).skip(1), r)
                Unit
            })
        }
    }

    private fun check_276zg0_a0a0f0a1(checkedDotOperand: SModel?): SRepository? {
        if (null != checkedDotOperand) {
            return checkedDotOperand.getRepository()
        }
        return null
    }
}
