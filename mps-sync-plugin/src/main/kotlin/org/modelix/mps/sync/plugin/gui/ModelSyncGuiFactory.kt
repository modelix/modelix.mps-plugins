/*
 * Copyright (c) 2023-2024.
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

package org.modelix.mps.sync.plugin.gui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.modelql.core.toList
import org.modelix.modelql.untyped.allChildren
import org.modelix.modelql.untyped.ofConcept
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.plugin.ModelSyncService
import org.modelix.mps.sync.plugin.icons.CloudIcons
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Box
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSeparator

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelSyncGuiFactory : ToolWindowFactory, Disposable {

    private val logger = KotlinLogging.logger {}
    private lateinit var toolWindowContent: ModelSyncGui
    private lateinit var content: Content
    private lateinit var bindingsRefresher: BindingsComboBoxRefresher

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        logger.info { "-------------------------------------------- createToolWindowContent" }

        toolWindowContent = ModelSyncGui(toolWindow)
        content = ContentFactory.SERVICE.getInstance().createContent(toolWindowContent.contentPanel, "", false)
        toolWindow.contentManager.addContent(content)
        bindingsRefresher = toolWindowContent.bindingsRefresher
    }

    override fun dispose() {
        logger.info { "-------------------------------------------- disposing ModelSyncGuiFactory" }
        bindingsRefresher.interrupt()
        content.dispose()
    }

    class ModelSyncGui(toolWindow: ToolWindow) {

        companion object {
            private const val COMBOBOX_CHANGED_COMMAND = "comboBoxChanged"
            private const val TEXTFIELD_WIDTH = 20
        }

        private val logger = KotlinLogging.logger {}
        private val coroutineScope = CoroutineScope(Dispatchers.Default)
        private val isFetchingModulesListFromServer = AtomicBoolean()

        val contentPanel = JPanel()
        val bindingsRefresher: BindingsComboBoxRefresher

        // the actual intelliJ service handling the synchronization
        private val modelSyncService = service<ModelSyncService>()
        private val serverURL = JBTextField(TEXTFIELD_WIDTH)
        private val repositoryName = JBTextField(TEXTFIELD_WIDTH)
        private val branchName = JBTextField(TEXTFIELD_WIDTH)
        private val moduleName = JBTextField(TEXTFIELD_WIDTH)
        private val jwt = JBTextField(TEXTFIELD_WIDTH)

        private val openProjectModel = DefaultComboBoxModel<Project>()
        private val existingConnectionsModel = DefaultComboBoxModel<ModelClientV2>()
        private val existingBindingModel = DefaultComboBoxModel<IBinding>()
        private val repoModel = DefaultComboBoxModel<RepositoryId>()
        private val branchModel = DefaultComboBoxModel<BranchReference>()

        private val moduleModel = DefaultComboBoxModel<ModuleIdWithName>()

        init {
            logger.info { "-------------------------------------------- ModelSyncGui init" }
            toolWindow.setIcon(CloudIcons.ROOT_ICON)
            bindingsRefresher = BindingsComboBoxRefresher(this)
            contentPanel.layout = FlowLayout()
            contentPanel.add(createInputBox())
            triggerRefresh()

            // TODO fixme: hardcoded values
            serverURL.text = "http://127.0.0.1:28101/v2"
            repositoryName.text = "courses"
            branchName.text = "master"
            moduleName.text = "University.Schedule.modelserver.backend.sandbox"
            jwt.text = ""
        }

        private fun createInputBox(): Box {
            val inputBox = Box.createVerticalBox()

            val urlPanel = JPanel()
            urlPanel.add(JLabel("Server URL:    "))
            urlPanel.add(serverURL)

            val refreshButton = JButton("Refresh All")
            refreshButton.addActionListener { triggerRefresh() }
            urlPanel.add(refreshButton)
            inputBox.add(urlPanel)

            val jwtPanel = JPanel()
            jwtPanel.add(JLabel("JWT:           "))
            jwtPanel.add(jwt)

            val connectProjectButton = JButton("Connect")
            connectProjectButton.addActionListener { _: ActionEvent ->
                modelSyncService.connectModelServer(
                    serverURL.text,
                    jwt.text,
                    ::triggerRefresh,
                )
            }
            jwtPanel.add(connectProjectButton)
            inputBox.add(jwtPanel)

            inputBox.add(JSeparator())

            val connectionsPanel = JPanel()
            val existingConnectionsCB = ComboBox<ModelClientV2>()
            existingConnectionsCB.model = existingConnectionsModel
            existingConnectionsCB.renderer = CustomCellRenderer()
            connectionsPanel.add(JLabel("Existing Conn.:"))
            connectionsPanel.add(existingConnectionsCB)

            val disConnectProjectButton = JButton("Disconnect")
            disConnectProjectButton.addActionListener { _: ActionEvent? ->
                if (existingConnectionsModel.size > 0) {
                    modelSyncService.disconnectServer(
                        existingConnectionsModel.selectedItem as ModelClientV2,
                        ::triggerRefresh,
                    )
                }
            }
            connectionsPanel.add(disConnectProjectButton)
            inputBox.add(connectionsPanel)

            inputBox.add(JSeparator())

            val targetPanel = JPanel()
            val projectCB = ComboBox<Project>()
            projectCB.model = openProjectModel
            projectCB.renderer = CustomCellRenderer()
            projectCB.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    ActiveMpsProjectInjector.setActiveProject(it.item as Project)
                }
            }

            targetPanel.add(JLabel("Target Project:"))
            targetPanel.add(projectCB)
            inputBox.add(targetPanel)

            val repoPanel = JPanel()
            val repoCB = ComboBox<RepositoryId>()
            repoCB.model = repoModel
            repoCB.renderer = CustomCellRenderer()
            repoCB.addActionListener {
                if (it.actionCommand == COMBOBOX_CHANGED_COMMAND) {
                    callOnlyIfNotFetching(::populateBranchCB)
                }
            }
            repoPanel.add(JLabel("Remote Repo:   "))
            repoPanel.add(repoCB)
            inputBox.add(repoPanel)

            val branchPanel = JPanel()
            val branchCB = ComboBox<BranchReference>()
            branchCB.model = branchModel
            branchCB.renderer = CustomCellRenderer()
            branchCB.addActionListener {
                if (it.actionCommand == COMBOBOX_CHANGED_COMMAND) {
                    callOnlyIfNotFetching(::populateModuleCB)
                }
            }
            branchPanel.add(JLabel("Remote Branch: "))
            branchPanel.add(branchCB)
            inputBox.add(branchPanel)
            val connectBranchButton = JButton("Connect to Branch without downloading Modules")
            connectBranchButton.addActionListener {
                modelSyncService.connectToBranch(
                    existingConnectionsModel.selectedItem as ModelClientV2,
                    branchModel.selectedItem as BranchReference,
                )
            }
            branchPanel.add(connectBranchButton)

            val modulePanel = JPanel()
            val moduleCB = ComboBox<ModuleIdWithName>()
            moduleCB.model = moduleModel
            moduleCB.renderer = CustomCellRenderer()
            modulePanel.add(JLabel("Remote Module:  "))
            modulePanel.add(moduleCB)

            val bindButton = JButton("Bind Selected")
            bindButton.addActionListener {
                if (existingConnectionsModel.size > 0) {
                    logger.info { "Binding Module ${moduleName.text} to project: ${ActiveMpsProjectInjector.activeMpsProject?.name}" }
                    modelSyncService.bindModuleFromServer(
                        existingConnectionsModel.selectedItem as ModelClientV2,
                        (branchModel.selectedItem as BranchReference).branchName,
                        (moduleModel.selectedItem as ModuleIdWithName).id,
                        (repoModel.selectedItem as RepositoryId).id,
                    )
                }
            }
            modulePanel.add(bindButton)
            inputBox.add(modulePanel)

            inputBox.add(JSeparator())

            val bindingsPanel = JPanel()
            val existingBindingCB = ComboBox<IBinding>()
            existingBindingCB.model = existingBindingModel
            existingBindingCB.renderer = CustomCellRenderer()
            bindingsPanel.add(JLabel("Bindings:      "))
            bindingsPanel.add(existingBindingCB)
            bindingsRefresher.start()

            val unbindButton = JButton("Unbind Selected")
            unbindButton.addActionListener {
                existingBindingCB.selectedItem?.let {
                    (it as IBinding).deactivate(removeFromServer = false)
                }
            }
            bindingsPanel.add(unbindButton)
            inputBox.add(bindingsPanel)

            return inputBox
        }

        private fun triggerRefresh() {
            populateProjectsCB()
            populateConnectionsCB()
            callOnlyIfNotFetching(::populateRepoCB)
        }

        private fun populateProjectsCB() {
            openProjectModel.removeAllElements()
            openProjectModel.addAll(ProjectManager.getInstance().openProjects.toMutableList())
            if (openProjectModel.size > 0) {
                openProjectModel.selectedItem = openProjectModel.getElementAt(0)
            }
        }

        private fun populateConnectionsCB() {
            val activeClients = modelSyncService.activeClients

            existingConnectionsModel.removeAllElements()
            existingConnectionsModel.addAll(activeClients)
            if (existingConnectionsModel.size > 0) {
                existingConnectionsModel.selectedItem = existingConnectionsModel.getElementAt(0)
            }
        }

        private suspend fun populateRepoCB() {
            if (existingConnectionsModel.size != 0) {
                val client = existingConnectionsModel.selectedItem as ModelClientV2
                val repositories = client.listRepositories()

                repoModel.removeAllElements()
                repoModel.addAll(repositories)
                if (repoModel.size > 0) {
                    repoModel.selectedItem = repoModel.getElementAt(0)
                    populateBranchCB()
                }
            }
        }

        private suspend fun populateBranchCB() {
            if (existingConnectionsModel.size != 0 && repoModel.size != 0) {
                val client = existingConnectionsModel.selectedItem as ModelClientV2
                val repositoryId = repoModel.selectedItem as RepositoryId
                val branches = client.listBranches(repositoryId)

                branchModel.removeAllElements()
                branchModel.addAll(branches)
                if (branchModel.size > 0) {
                    branchModel.selectedItem = branchModel.getElementAt(0)
                    populateModuleCB()
                }
            }
        }

        private suspend fun populateModuleCB() {
            if (existingConnectionsModel.size != 0 && repoModel.size != 0 && branchModel.size != 0) {
                val client = existingConnectionsModel.selectedItem as ModelClientV2
                val branchReference = branchModel.selectedItem as BranchReference

                val moduleNodes = client.query(branchReference) {
                    it.allChildren().ofConcept(BuiltinLanguages.MPSRepositoryConcepts.Module).toList()
                }.map {
                    val name = it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
                        ?: it.toString()
                    val id = it.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id) ?: ""
                    ModuleIdWithName(id, name)
                }.sortedBy { it.name }

                moduleModel.removeAllElements()
                moduleModel.addAll(moduleNodes.toList())
                if (moduleModel.size > 0) {
                    moduleModel.selectedItem = moduleModel.getElementAt(0)
                }
            }
        }

        @Synchronized
        private fun callOnlyIfNotFetching(action: suspend () -> Unit) {
            if (!isFetchingModulesListFromServer.get()) {
                isFetchingModulesListFromServer.set(true)

                coroutineScope.launch {
                    try {
                        action()
                    } catch (ex: Exception) {
                        logger.error(ex) { "Unexpected error" }
                    } finally {
                        isFetchingModulesListFromServer.set(false)
                    }
                }
            }
        }

        fun populateBindingCB(bindings: List<IBinding>) {
            existingBindingModel.removeAllElements()
            existingBindingModel.addAll(bindings)
            if (existingBindingModel.size > 0) {
                existingBindingModel.selectedItem = existingBindingModel.getElementAt(0)
            }
        }
    }

    class CustomCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val formatted = when (value) {
                is Project -> value.name
                is IBinding -> value.toString()
                is ModelClientV2 -> value.baseUrl
                is RepositoryId -> value.toString()
                is BranchReference -> value.branchName
                is ModuleIdWithName -> value.name
                else -> return super.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus)
            }
            return super.getListCellRendererComponent(list, formatted, index, isSelected, cellHasFocus)
        }
    }
}

data class ModuleIdWithName(val id: String, val name: String)
