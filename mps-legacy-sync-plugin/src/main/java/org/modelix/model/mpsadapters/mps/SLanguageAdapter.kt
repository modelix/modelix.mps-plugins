package org.modelix.model.mpsadapters.mps

import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.smodel.adapter.ids.MetaIdHelper
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SLanguage
import org.modelix.model.api.IConcept
import org.modelix.model.api.ILanguage

/*Generated by MPS */
class SLanguageAdapter(private val language: SLanguage?) : ILanguage {
    override fun getUID(): String {
        return MetaIdHelper.getLanguage(language).serialize()
    }

    override fun getName(): String {
        return language!!.qualifiedName
    }

    override fun getConcepts(): List<IConcept> {
        val concepts: Iterable<SAbstractConcept> = language!!.concepts
        return Sequence.fromIterable(concepts).select(object : ISelector<SAbstractConcept, IConcept>() {
            override fun select(it: SAbstractConcept): IConcept {
                val c: IConcept = SConceptAdapter(it)
                return c
            }
        }).toListSequence()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || this.javaClass != o.javaClass) {
            return false
        }
        val that: SLanguageAdapter = o as SLanguageAdapter
        return !(if (language != null) !((language == that.language)) else that.language != null)
    }

    override fun hashCode(): Int {
        var result: Int = 0
        result = 31 * result + ((if (language != null) (language as Any).hashCode() else 0))
        return result
    }
}
