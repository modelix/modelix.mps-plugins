/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.mps.sync.mps.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * Tracks the active [Project]'s lifecycle and sets [projectClosing] to true, if the project is closing.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ProjectLifecycleTracker : InjectableService {

    /**
     * True if the active [Project] is closing.
     */
    var projectClosing = false
        private set

    override fun initService(serviceLocator: ServiceLocator) {
        /**
         * Subscribe for project closing event and do not delete the modules and models in that case.
         * Explanation: when closing MPS, MPS unregisters all modules from the repository then it calls the
         * moduleRemoved and modelRemoved methods after the module was unregistered. At that point of time,
         * it might happen that the binding is still living, but we do not want to remove the module/model from
         * the server.
         */
        val projectToListenTo = serviceLocator.project
        projectToListenTo.messageBus.connect().subscribe(
            ProjectManager.TOPIC,
            object : ProjectManagerListener {
                override fun projectClosing(project: Project) {
                    if (project == projectToListenTo) {
                        projectClosing = true
                    }
                }
            },
        )
    }
}
