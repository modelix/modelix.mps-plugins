package org.modelix.model.mpsadapters.mps

import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.smodel.SReference
import jetbrains.mps.util.IterableUtil
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.model.SNodeReference
import org.modelix.model.api.PNodeAdapter.Companion.wrap
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Objects

/*Generated by MPS */
class ANode(private val node: SNode) : jetbrains.mps.smodel.SNode(DummyConcept(), DummySNodeId()), SNode {
    init {

        // We don't really want to extend SNode, but some code in the editor is coupled to this class.
        // Here all fields of this class are set to null, because we override all methods.
        for (field: Field in jetbrains.mps.smodel.SNode::class.java.getDeclaredFields()) {
            field.setAccessible(true)
            if (Modifier.isStatic(field.modifiers)) {
                continue
            }
            if (Modifier.isFinal(field.modifiers)) {
                continue
            }
            if (Objects.equals(field.name, "myOwner")) {
                continue
            }
            try {
                field.set(this, null)
            } catch (ex: Exception) {
                throw RuntimeException(ex)
            }
        }
    }

    override fun getModel(): SModel? {
        return node.model
    }

    override fun getNodeId(): SNodeId {
        return node.nodeId
    }

    override fun getReference(): SNodeReference {
        return ANodeReference(node.reference)
    }

    override fun getConcept(): SConcept {
        return node.concept
    }

    override fun isInstanceOfConcept(concept: SAbstractConcept): Boolean {
        return node.isInstanceOfConcept(concept)
    }

    override fun getPresentation(): String {
        return node.presentation
    }

    override fun getName(): String? {
        return node.name
    }

    override fun addChild(link: SContainmentLink, node: SNode) {
        throw UnsupportedOperationException()
    }

    override fun insertChildBefore(link: SContainmentLink, node: SNode, node1: SNode?) {
        throw UnsupportedOperationException()
    }

    override fun insertChildAfter(link: SContainmentLink, node: SNode, node1: SNode?) {
        throw UnsupportedOperationException()
    }

    override fun removeChild(node: SNode) {
        throw UnsupportedOperationException()
    }

    override fun delete() {
        node.delete()
    }

    override fun getContainingRoot(): jetbrains.mps.smodel.SNode {
        return (wrap(node.containingRoot))
    }

    override fun getContainmentLink(): SContainmentLink? {
        return node.containmentLink
    }

    override fun getFirstChild(): SNode? {
        return wrap(node.firstChild)
    }

    override fun getLastChild(): SNode? {
        return wrap(node.lastChild)
    }

    override fun getPrevSibling(): jetbrains.mps.smodel.SNode? {
        return wrap(node.prevSibling)
    }

    override fun getNextSibling(): jetbrains.mps.smodel.SNode? {
        return wrap(node.nextSibling)
    }

    override fun getChildren(link: SContainmentLink): List<jetbrains.mps.smodel.SNode> {
        return node.getChildren(link).map { wrap(it) }
    }

    override fun getChildren(): List<jetbrains.mps.smodel.SNode> {
        return node.children.map { wrap(it) }
    }

    override fun setReferenceTarget(link: SReferenceLink, target: SNode?) {
        node.setReferenceTarget(link, unwrap(target))
    }

    override fun getReferenceTarget(link: SReferenceLink): jetbrains.mps.smodel.SNode? {
        return wrap(node.getReferenceTarget(link))
    }

    override fun getReference(link: SReferenceLink): SReference? {
        return AReference.Companion.wrap(node.getReference(link))
    }

    override fun setReference(link: SReferenceLink, reference: org.jetbrains.mps.openapi.model.SReference?) {
        throw UnsupportedOperationException()
    }

    override fun getReferences(): List<SReference> {
        val references: Iterable<org.jetbrains.mps.openapi.model.SReference> = node.references
        return Sequence.fromIterable(references)
            .select(object : ISelector<org.jetbrains.mps.openapi.model.SReference, SReference>() {
                override fun select(it: org.jetbrains.mps.openapi.model.SReference): SReference {
                    val r: SReference = AReference(it)
                    return r
                }
            }).toListSequence()
    }

    override fun getProperties(): Iterable<SProperty> {
        return node.properties
    }

    override fun hasProperty(property: SProperty): Boolean {
        return node.hasProperty(property)
    }

    override fun getProperty(property: SProperty): String? {
        return node.getProperty(property)
    }

    override fun setProperty(property: SProperty, value: String?) {
        node.setProperty(property, value)
    }

    override fun getUserObject(key: Any): Any {
        return node.getUserObject(key)
    }

    override fun putUserObject(key: Any, value: Any?) {
        node.putUserObject(key, value)
    }

    override fun getUserObjectKeys(): Iterable<Any> {
        return node.userObjectKeys
    }

    @Deprecated("")
    override fun getRoleInParent(): String {
        return node.roleInParent
    }

    @Deprecated("")
    override fun setProperty(string: String, string1: String) {
        throw UnsupportedOperationException()
    }

    @Deprecated("")
    override fun getPropertyNames(): Collection<String> {
        return IterableUtil.asList(node.propertyNames)
    }

    @Deprecated("")
    override fun setReferenceTarget(string: String, node: SNode?) {
        throw UnsupportedOperationException()
    }

    @Deprecated("")
    override fun getReferenceTarget(string: String): jetbrains.mps.smodel.SNode {
        return (wrap(node.getReferenceTarget(string)))
    }

    @Deprecated("")
    override fun getReference(role: String): SReference {
        throw UnsupportedOperationException()
    }

    @Deprecated("")
    override fun setReference(string: String, reference: org.jetbrains.mps.openapi.model.SReference?) {
        throw UnsupportedOperationException()
    }

    @Deprecated("")
    override fun insertChildBefore(role: String, newChild: SNode, anchor: SNode?) {
        node.insertChildBefore(role, newChild, unwrap(anchor))
    }

    @Deprecated("")
    override fun addChild(role: String, newChild: SNode) {
        node.addChild(role, newChild)
    }

    @Deprecated("")
    override fun getChildren(role: String): List<jetbrains.mps.smodel.SNode> {
        return node.getChildren(role).map { it as jetbrains.mps.smodel.SNode }
    }

    override fun toString(): String {
        return "ANode"
    }

    override fun setId(id: SNodeId?) {
        throw UnsupportedOperationException()
    }

    override fun firstChild(): jetbrains.mps.smodel.SNode {
        throw UnsupportedOperationException()
    }

    override fun treePrevious(): jetbrains.mps.smodel.SNode {
        throw UnsupportedOperationException()
    }

    override fun treeNext(): jetbrains.mps.smodel.SNode {
        throw UnsupportedOperationException()
    }

    override fun treeParent(): jetbrains.mps.smodel.SNode {
        return (wrap(node.parent))!!
    }

    override fun children_insertBefore(anchor: jetbrains.mps.smodel.SNode, node: jetbrains.mps.smodel.SNode) {
        throw UnsupportedOperationException()
    }

    override fun children_remove(node: jetbrains.mps.smodel.SNode) {
        throw UnsupportedOperationException()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o is NodeToSNodeAdapter) {
            throw RuntimeException("Forgot to wrap an SNode with an ANode?")
        }
        if (o == null || this.javaClass != o.javaClass) {
            return false
        }
        val that: ANode = o as ANode
        return !(if (node != null) !((node == that.node)) else that.node != null)
    }

    override fun hashCode(): Int {
        var result: Int = 0
        result = 31 * result + ((if (node != null) (node as Any).hashCode() else 0))
        return result
    }

    companion object {
        private val USER_OBJECT_KEY: String = ANode::class.java.getName()

        @JvmName("wrap_nullable")
        fun wrap(nodeToWrap: SNode?): jetbrains.mps.smodel.SNode? {
            return if (nodeToWrap == null) null else wrap(nodeToWrap)
        }
        fun wrap(nodeToWrap: SNode): jetbrains.mps.smodel.SNode {
            if (nodeToWrap is jetbrains.mps.smodel.SNode) {
                // The purpose of ANode is to allow casts to jetbrains.mps.smodel.SNode.
                // No ANode required if it already is a subclass of jetbrains.mps.smodel.SNode.
                return nodeToWrap
            }
            var instance: ANode? = as_ile5t_a0a2a2(nodeToWrap.getUserObject(USER_OBJECT_KEY), ANode::class.java)
            if (instance == null) {
                instance = ANode(nodeToWrap)
                nodeToWrap.putUserObject(USER_OBJECT_KEY, instance)
            }
            return instance
        }

        fun unwrap(nodeToUnwrap: SNode?): SNode? {
            if (nodeToUnwrap == null) {
                return null
            }
            if (nodeToUnwrap is ANode) {
                return nodeToUnwrap.node
            }
            return nodeToUnwrap
        }

        private fun <T> as_ile5t_a0a2a2(o: Any, type: Class<T>): T? {
            return (if (type.isInstance(o)) o as T else null)
        }
    }
}
