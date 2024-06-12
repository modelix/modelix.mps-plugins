package org.modelix.mps.sync.mps.util

import org.jetbrains.mps.openapi.module.SRepository

internal fun SRepository.runReadAction(action: (SRepository) -> Unit) = modelAccess.runReadAction { action(this) }
