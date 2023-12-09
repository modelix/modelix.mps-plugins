package org.modelix.common

import org.modelix.kotlin.utils.ContextValue

/*Generated by MPS */
object AuthorOverride {
    var AUTHOR: ContextValue<String> = ContextValue()
    private var instanceOwner: String? = null
    fun setInstanceOwner(owner: String?) {
        instanceOwner = owner
    }

    fun apply(author: String?): String? {
        val override: String? = AUTHOR.getValueOrNull()
        if ((override != null && override.length > 0)) {
            return override
        }
        if ((author == null || author.length == 0) && (instanceOwner != null && instanceOwner!!.length > 0)) {
            return instanceOwner
        }
        return author
    }
}