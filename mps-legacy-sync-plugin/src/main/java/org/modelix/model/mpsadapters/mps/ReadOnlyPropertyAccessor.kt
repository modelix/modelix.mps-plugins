package org.modelix.model.mpsadapters.mps

/*Generated by MPS */
abstract class ReadOnlyPropertyAccessor<E> : TreeElementAsNode.IPropertyAccessor<E> {
    override fun set(element: E, value: String?): String? {
        throw UnsupportedOperationException("This property is readonly")
    }
}
