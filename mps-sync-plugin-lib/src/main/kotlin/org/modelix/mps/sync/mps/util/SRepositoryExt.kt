package org.modelix.mps.sync.mps.util

import org.jetbrains.mps.openapi.module.SRepository

/**
 * Runs the parameter action inside a read action via [SRepository.getModelAccess].
 *
 * @param action the callback to execute.
 *
 * @see [org.jetbrains.mps.openapi.module.ModelAccess.runReadAction]
 */
internal fun SRepository.runReadAction(action: (SRepository) -> Unit) = modelAccess.runReadAction { action(this) }
