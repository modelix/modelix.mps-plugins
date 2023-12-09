package org.modelix.model.mpsplugin.plugin

import com.intellij.openapi.project.ProjectManager
import jetbrains.mps.project.Project
import jetbrains.mps.project.ProjectManagerListener
import java.util.function.Consumer

/*Generated by MPS */
object ProjectManagerExtensions {
    fun withTheOnlyProject(stuffToDoOnceWeGetAProject: Consumer<Project>) {
        val openProjects: List<Project> = jetbrains.mps.project.ProjectManager.getInstance().openedProjects
        if (openProjects.size == 0) {
            jetbrains.mps.project.ProjectManager.getInstance().addProjectListener(object : ProjectManagerListener {
                override fun projectOpened(project: Project) {
                    stuffToDoOnceWeGetAProject.accept(project)
                }

                override fun projectClosed(project: Project) {
                    // nothing to do here
                }
            })
        } else if (openProjects.size == 1) {
            stuffToDoOnceWeGetAProject.accept(openProjects.get(0))
        } else {
            throw IllegalStateException("Exactly one open project expected. Open projects: " + openProjects)
        }
    }
}
