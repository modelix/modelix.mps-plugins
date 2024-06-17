package org.modelix.mps.sync.mps.util

import com.intellij.openapi.project.Project
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.MPSProject
import org.jetbrains.mps.openapi.module.SRepository

internal fun Project.toMpsProject(): MPSProject =
    ProjectHelper.fromIdeaProject(this) ?: throw IllegalStateException("MPS project is not found for Project $name.")

internal fun MPSProject.runReadAction(action: (SRepository) -> Unit) = this.repository.runReadAction(action)
