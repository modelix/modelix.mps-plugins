package org.modelix.model.mpsadapters.mps

import jetbrains.mps.smodel.adapter.ids.MetaIdHelper
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept

/*Generated by MPS */
class SContainmentLinkAdapter(private val link: SContainmentLink?) : IChildLink {
    public override fun getUID(): String {
        return MetaIdHelper.getAggregation(link).serialize()
    }

    public override fun getConcept(): IConcept {
        return (SConceptAdapter.Companion.wrap(link!!.getOwner()))!!
    }

    fun getLink(): SContainmentLink? {
        return link
    }

    public override fun getSimpleName(): String {
        return link!!.getName()
    }

    public override val isMultiple: Boolean
        get() {
            return link!!.isMultiple()
        }

    public override fun toString(): String {
        return link!!.getOwner().getName() + "." + link.getName()
    }

    public override val childConcept: IConcept
        get() {
            return targetConcept
        }

    public override val targetConcept: IConcept
        get() {
            return SConceptAdapter(link!!.getTargetConcept())
        }

    public override val isOptional: Boolean
        get() {
            return link!!.isOptional()
        }

    public override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || this.javaClass != o.javaClass) {
            return false
        }
        val that: SContainmentLinkAdapter = o as SContainmentLinkAdapter
        if ((if (link != null) !((link == that.link)) else that.link != null)) {
            return false
        }
        return true
    }

    public override fun hashCode(): Int {
        var result: Int = 0
        result = 31 * result + ((if (link != null) (link as Any).hashCode() else 0))
        return result
    }
}