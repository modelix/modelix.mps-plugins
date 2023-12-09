package org.modelix.model.mpsadapters.mps

import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.IWhereFilter
import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.smodel.adapter.ids.MetaIdHelper
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SInterfaceConcept
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ILanguage
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import java.util.Objects

/*Generated by MPS */
class SConceptAdapter(val adapted: SAbstractConcept) : IConcept {

    public override fun getReference(): IConceptReference {
        return ConceptReference(getUID())
    }

    public override fun isAbstract(): Boolean {
        return adapted!!.isAbstract()
    }

    public override fun getUID(): String {
        return "mps:" + MetaIdHelper.getConcept(adapted).serialize()
    }

    override val language: ILanguage?
        get() {
            return SLanguageAdapter(adapted!!.getLanguage())
        }

    public override fun getDirectSuperConcepts(): List<IConcept> {
        val superConcepts: Iterable<SAbstractConcept>
        if (adapted is SConcept) {
            val c: SConcept = adapted
            val superInterfaces: Iterable<SInterfaceConcept> = c.getSuperInterfaces()
            superConcepts = Sequence.fromIterable(Sequence.singleton<SAbstractConcept?>(c.getSuperConcept())).concat(
                Sequence.fromIterable(superInterfaces),
            )
        } else if (adapted is SInterfaceConcept) {
            val superInterfaces: Iterable<SInterfaceConcept> = adapted.getSuperInterfaces()
            superConcepts = Sequence.fromIterable(superInterfaces)
                .select<SAbstractConcept?>(object : ISelector<SInterfaceConcept, SInterfaceConcept>() {
                    public override fun select(it: SInterfaceConcept): SInterfaceConcept {
                        return it
                    }
                })
        } else {
            superConcepts = Sequence.fromIterable(emptyList())
        }
        return Sequence.fromIterable(superConcepts).select(object : ISelector<SAbstractConcept, IConcept>() {
            public override fun select(it: SAbstractConcept): IConcept {
                val adapter: IConcept = SConceptAdapter(it)
                return adapter
            }
        }).toListSequence()
    }

    public override fun getLongName(): String {
        return adapted!!.getLanguage().getQualifiedName() + "." + adapted.getName()
    }

    public override fun getShortName(): String {
        return adapted!!.getName()
    }

    public override fun isSubConceptOf(superConcept: IConcept?): Boolean {
        return adapted!!.isSubConceptOf((superConcept as SConceptAdapter?)!!.adapted)
    }

    public override fun isExactly(otherConcept: IConcept?): Boolean {
        return Objects.equals(adapted, (otherConcept as SConceptAdapter?)!!.adapted)
    }

    public override fun getAllProperties(): List<IProperty> {
        val properties: Iterable<SProperty> = adapted!!.getProperties()
        return Sequence.fromIterable(properties).select(object : ISelector<SProperty, IProperty>() {
            public override fun select(it: SProperty): IProperty {
                val p: IProperty = SPropertyAdapter(it)
                return p
            }
        }).toListSequence()
    }

    public override fun getAllChildLinks(): List<IChildLink> {
        val links: Iterable<SContainmentLink> = adapted!!.getContainmentLinks()
        return Sequence.fromIterable(links).select(object : ISelector<SContainmentLink?, IChildLink>() {
            public override fun select(it: SContainmentLink?): IChildLink {
                val l: IChildLink = SContainmentLinkAdapter(it)
                return l
            }
        }).toListSequence()
    }

    public override fun getAllReferenceLinks(): List<IReferenceLink> {
        val links: Iterable<SReferenceLink> = adapted!!.getReferenceLinks()
        return Sequence.fromIterable(links).select(object : ISelector<SReferenceLink?, IReferenceLink>() {
            public override fun select(it: SReferenceLink?): IReferenceLink {
                val adapter: IReferenceLink = SReferenceLinkAdapter(it)
                return adapter
            }
        }).toListSequence()
    }

    public override fun getChildLink(name: String): IChildLink {
        return ListSequence.fromList(getAllChildLinks()).findFirst(object : IWhereFilter<IChildLink>() {
            public override fun accept(it: IChildLink): Boolean {
                return Objects.equals(it.name, name)
            }
        })
    }

    public override fun getProperty(name: String): IProperty {
        return ListSequence.fromList(getAllProperties()).findFirst(object : IWhereFilter<IProperty>() {
            public override fun accept(it: IProperty): Boolean {
                return Objects.equals(it.name, name)
            }
        })
    }

    public override fun getReferenceLink(name: String): IReferenceLink {
        return ListSequence.fromList(getAllReferenceLinks()).findFirst(object : IWhereFilter<IReferenceLink>() {
            public override fun accept(it: IReferenceLink): Boolean {
                return Objects.equals(it.name, name)
            }
        })
    }

    public override fun getOwnProperties(): List<IProperty> {
        val properties: Iterable<SProperty> = adapted!!.getProperties()
        return Sequence.fromIterable<SProperty>(properties).where(object : IWhereFilter<SProperty>() {
            public override fun accept(it: SProperty): Boolean {
                return Objects.equals(it.getOwner(), adapted)
            }
        }).select<IProperty>(object : ISelector<SProperty, IProperty>() {
            public override fun select(it: SProperty): IProperty {
                val p: IProperty = SPropertyAdapter(it)
                return p
            }
        }).toListSequence()
    }

    public override fun getOwnChildLinks(): List<IChildLink> {
        val containmentLinks: Iterable<SContainmentLink> = adapted!!.getContainmentLinks()
        return Sequence.fromIterable<SContainmentLink>(containmentLinks)
            .where(object : IWhereFilter<SContainmentLink>() {
                public override fun accept(it: SContainmentLink): Boolean {
                    return Objects.equals(it.getOwner(), adapted)
                }
            }).select<IChildLink>(object : ISelector<SContainmentLink?, IChildLink>() {
                public override fun select(it: SContainmentLink?): IChildLink {
                    val l: IChildLink = SContainmentLinkAdapter(it)
                    return l
                }
            }).toListSequence()
    }

    public override fun getOwnReferenceLinks(): List<IReferenceLink> {
        val referenceLinks: Iterable<SReferenceLink> = adapted!!.getReferenceLinks()
        return Sequence.fromIterable<SReferenceLink>(referenceLinks).where(object : IWhereFilter<SReferenceLink>() {
            public override fun accept(it: SReferenceLink): Boolean {
                return Objects.equals(it.getOwner(), adapted)
            }
        }).select<IReferenceLink>(object : ISelector<SReferenceLink?, IReferenceLink>() {
            public override fun select(it: SReferenceLink?): IReferenceLink {
                val adapter: IReferenceLink = SReferenceLinkAdapter(it)
                return adapter
            }
        }).toListSequence()
    }

    public override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || this.javaClass != o.javaClass) {
            return false
        }
        val that: SConceptAdapter = o as SConceptAdapter
        if ((if (adapted != null) !((adapted == that.adapted)) else that.adapted != null)) {
            return false
        }
        return true
    }

    public override fun hashCode(): Int {
        var result: Int = 0
        result = 31 * result + ((if (adapted != null) (adapted as Any).hashCode() else 0))
        return result
    }

    public override fun toString(): String {
        return adapted!!.getName()
    }

    companion object {
        fun unwrap(concept: IConcept?): SAbstractConcept? {
            if (concept == null) {
                return null
            }
            if (concept is SConceptAdapter) {
                return concept.adapted
            }
            return null
        }

        @JvmName("wrap_nullable")
        fun wrap(concept: SAbstractConcept?): IConcept? {
            return concept?.let { wrap(it) }
        }
        fun wrap(concept: SAbstractConcept): IConcept {
            return SConceptAdapter(concept)
        }
    }
}
