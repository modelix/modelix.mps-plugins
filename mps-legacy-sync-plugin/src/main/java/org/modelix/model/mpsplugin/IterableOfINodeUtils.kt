package org.modelix.model.mpsplugin

import jetbrains.mps.internal.collections.runtime.ListSequence
import org.modelix.model.api.INode
import java.util.LinkedList

/*Generated by MPS */
object IterableOfINodeUtils {
    fun toList(_this: Iterable<INode>): List<INode> {
        val res: List<INode> = ListSequence.fromList(LinkedList())
        val it: Iterator<INode> = _this.iterator()
        while (it.hasNext()) {
            ListSequence.fromList(res).addElement(it.next())
        }
        return res
    }

    fun <T : INode?> toCastedList(_this: Iterable<INode>): List<T> {
        val res: List<T> = ListSequence.fromList(LinkedList())
        val it: Iterator<INode> = _this.iterator()
        while (it.hasNext()) {
            ListSequence.fromList(res).addElement((it.next() as T))
        }
        return res
    }
}