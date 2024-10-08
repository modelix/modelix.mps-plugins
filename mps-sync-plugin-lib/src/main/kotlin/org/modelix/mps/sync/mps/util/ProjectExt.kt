package org.modelix.mps.sync.mps.util

import com.intellij.openapi.project.Project
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.MPSProject
import org.jetbrains.mps.openapi.module.SRepository

/**
 * @return the [Project] transformed to an [MPSProject] by [ProjectHelper].
 */
internal fun Project.toMpsProject(): MPSProject =
    ProjectHelper.fromIdeaProject(this) ?: throw IllegalStateException("MPS project is not found for Project $name.")

/**
 * Runs the parameter action inside a read action.
 *
 * @param action the callback to execute.
 *
 * @see [SRepository.runReadAction]
 */
internal fun MPSProject.runReadAction(action: (SRepository) -> Unit) = this.repository.runReadAction(action)
