package org.modelix.ui.diff

import org.jetbrains.mps.openapi.model.SNodeId
import java.awt.Dimension
import java.awt.image.BufferedImage

class DiffImage(
    val image: BufferedImage,
    val size: Dimension,
    val affectedFile: String,
    val rootNodeId: SNodeId?,
    val rootNodePresentation: String,
) {
    val id: String
        get() {
            val id = affectedFile + "-" + rootNodeId + "-" + rootNodePresentation
            return id.replace("[^a-zA-Z0-9\\.\\-]".toRegex(), "_")
        }
}
