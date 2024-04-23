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
import kotlinx.coroutines.sync.Mutex
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
import org.modelix.mps.sync.mps.notifications.AlertNotifier
import org.modelix.mps.sync.mps.notifications.BalloonNotifier
import org.modelix.mps.sync.plugin.ModelSyncService
import org.modelix.mps.sync.plugin.icons.CloudIcons
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
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
        private val mutex = Mutex()

        val contentPanel = JPanel()
        val bindingsRefresher: BindingsComboBoxRefresher

        // the actual intelliJ service handling the synchronization
        private val modelSyncService = service<ModelSyncService>()
        private val serverURL = JBTextField(TEXTFIELD_WIDTH)
        private val repositoryName = JBTextField(TEXTFIELD_WIDTH)
        private val branchName = JBTextField(TEXTFIELD_WIDTH)
        private val moduleName = JBTextField(TEXTFIELD_WIDTH)
        private val jwt = JBTextField(TEXTFIELD_WIDTH)

        private val connectionsCB = ComboBox<ModelClientV2>()
        private val projectsCB = ComboBox<Project>()
        private val reposCB = ComboBox<RepositoryId>()
        private val branchesCB = ComboBox<BranchReference>()
        private val modulesCB = ComboBox<ModuleIdWithName>()

        private val connectButton = JButton("Connect")
        private val disconnectButton = JButton("Disconnect")
        private val bindButton = JButton("Bind Selected")

        private val connectionsModel = DefaultComboBoxModel<ModelClientV2>()
        private val projectsModel = DefaultComboBoxModel<Project>()
        private val reposModel = DefaultComboBoxModel<RepositoryId>()
        private val branchesModel = DefaultComboBoxModel<BranchReference>()
        private val modulesModel = DefaultComboBoxModel<ModuleIdWithName>()
        private val bindingsModel = DefaultComboBoxModel<IBinding>()

        private lateinit var activeProject: Project

        init {
            logger.info { "-------------------------------------------- ModelSyncGui init" }
            toolWindow.setIcon(CloudIcons.ROOT_ICON)
            bindingsRefresher = BindingsComboBoxRefresher(this)
            contentPanel.layout = FlowLayout()
            contentPanel.add(createInputBox())
            populateProjectsCB()

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
            refreshButton.addActionListener { populateProjectsCB() }
            urlPanel.add(refreshButton)
            inputBox.add(urlPanel)

            val jwtPanel = JPanel()
            jwtPanel.add(JLabel("JWT:           "))
            jwtPanel.add(jwt)

            connectButton.addActionListener { _: ActionEvent ->
                if (connectionsModel.size != 0) {
                    val message =
                        "<html>Only one client connection is allowed. <a href=\"disconnect\">Disconnect</a> the existing client.</html>"
                    BalloonNotifier(activeProject).error(message) { disconnectClient() }
                    return@addActionListener
                }

                val client = modelSyncService.connectModelServer(serverURL.text, jwt.text)
                triggerRefresh(client)
            }
            jwtPanel.add(connectButton)
            inputBox.add(jwtPanel)

            inputBox.add(JSeparator())

            val connectionsPanel = JPanel()
            connectionsCB.isEnabled = false
            connectionsCB.model = connectionsModel
            connectionsCB.renderer = CustomCellRenderer()
            connectionsPanel.add(JLabel("Existing Connection:"))
            connectionsPanel.add(connectionsCB)

            disconnectButton.addActionListener { _: ActionEvent? -> disconnectClient() }
            connectionsPanel.add(disconnectButton)
            inputBox.add(connectionsPanel)

            inputBox.add(JSeparator())

            val targetPanel = JPanel()
            projectsCB.model = projectsModel
            projectsCB.renderer = CustomCellRenderer()
            projectsCB.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    activeProject = it.item as Project
                    modelSyncService.setActiveProject(activeProject)
                }
            }

            targetPanel.add(JLabel("Target Project:"))
            targetPanel.add(projectsCB)
            inputBox.add(targetPanel)

            val repoPanel = JPanel()
            reposCB.model = reposModel
            reposCB.renderer = CustomCellRenderer()
            reposCB.addActionListener {
                if (it.actionCommand == COMBOBOX_CHANGED_COMMAND) {
                    callOnlyIfNotFetching(::populateBranchCB)
                }
            }
            repoPanel.add(JLabel("Remote Repo:   "))
            repoPanel.add(reposCB)
            inputBox.add(repoPanel)

            val branchPanel = JPanel()
            branchesCB.model = branchesModel
            branchesCB.renderer = CustomCellRenderer()
            branchesCB.addActionListener {
                if (it.actionCommand == COMBOBOX_CHANGED_COMMAND) {
                    callOnlyIfNotFetching(::populateModuleCB)
                }
            }
            branchPanel.add(JLabel("Remote Branch: "))
            branchPanel.add(branchesCB)
            inputBox.add(branchPanel)
            val connectBranchButton = JButton("Connect to Branch without downloading Modules")
            connectBranchButton.addActionListener {
                val selectedConnection = connectionsModel.selectedItem
                val selectedBranch = branchesModel.selectedItem

                if (selectedConnection != null && selectedBranch != null) {
                    modelSyncService.connectToBranch(
                        selectedConnection as ModelClientV2,
                        selectedBranch as BranchReference,
                    )
                }
            }
            branchPanel.add(connectBranchButton)

            val modulePanel = JPanel()
            modulesCB.model = modulesModel
            modulesCB.renderer = CustomCellRenderer()
            modulePanel.add(JLabel("Remote Module:  "))
            modulePanel.add(modulesCB)

            bindButton.addActionListener { _: ActionEvent? ->
                val selectedConnection = connectionsModel.selectedItem
                val selectedBranch = branchesModel.selectedItem
                val selectedModule = modulesModel.selectedItem
                val selectedRepo = reposModel.selectedItem
                val inputsExist =
                    listOf(selectedConnection, selectedBranch, selectedModule, selectedRepo).all { it != null }

                if (inputsExist) {
                    logger.info { "Binding Module ${moduleName.text} to project: ${ActiveMpsProjectInjector.activeMpsProject?.name}" }
                    modelSyncService.bindModuleFromServer(
                        selectedConnection as ModelClientV2,
                        (selectedBranch as BranchReference).branchName,
                        (selectedModule as ModuleIdWithName).id,
                        (selectedRepo as RepositoryId).id,
                    )
                }
            }
            modulePanel.add(bindButton)
            inputBox.add(modulePanel)

            inputBox.add(JSeparator())

            val bindingsPanel = JPanel()
            val existingBindingCB = ComboBox<IBinding>()
            existingBindingCB.model = bindingsModel
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

        private fun disconnectClient() {
            if (connectionsModel.size > 0) {
                val message = "By disconnecting, the synchronized modules and models will be removed locally."
                AlertNotifier(activeProject).warning(message) { response ->
                    if (true.toString() == response) {
                        val originalClient = connectionsModel.selectedItem as ModelClientV2
                        val clientAfterDisconnect = modelSyncService.disconnectServer(originalClient)
                        if (clientAfterDisconnect == null) {
                            triggerRefresh(null)
                        }
                    }
                }
            }
        }

        private fun triggerRefresh(client: ModelClientV2?) {
            populateConnectionsCB(client)
            callOnlyIfNotFetching(::populateRepoCB)
        }

        private fun populateProjectsCB() {
            projectsModel.removeAllElements()
            projectsModel.addAll(ProjectManager.getInstance().openProjects.toMutableList())
            if (projectsModel.size > 0) {
                projectsModel.selectedItem = projectsModel.getElementAt(0)
            }
        }

        private fun populateConnectionsCB(client: ModelClientV2?) {
            connectionsModel.removeAllElements()
            if (client != null) {
                connectionsModel.addAll(listOf(client))
                connectionsModel.selectedItem = connectionsModel.getElementAt(0)
            }
        }

        private suspend fun populateRepoCB() {
            reposModel.removeAllElements()

            if (connectionsModel.size != 0) {
                val client = connectionsModel.selectedItem as ModelClientV2
                val repositories = client.listRepositories()

                reposModel.removeAllElements()
                reposModel.addAll(repositories)
                if (reposModel.size > 0) {
                    reposModel.selectedItem = reposModel.getElementAt(0)
                }
            }

            populateBranchCB()
        }

        private suspend fun populateBranchCB() {
            branchesModel.removeAllElements()

            if (connectionsModel.size != 0 && reposModel.size != 0) {
                val client = connectionsModel.selectedItem as ModelClientV2
                val repositoryId = reposModel.selectedItem as RepositoryId
                val branches = client.listBranches(repositoryId)

                branchesModel.addAll(branches)
                if (branchesModel.size > 0) {
                    branchesModel.selectedItem = branchesModel.getElementAt(0)
                }
            }

            populateModuleCB()
        }

        private suspend fun populateModuleCB() {
            modulesModel.removeAllElements()

            if (connectionsModel.size != 0 && reposModel.size != 0 && branchesModel.size != 0) {
                val client = connectionsModel.selectedItem as ModelClientV2
                val branchReference = branchesModel.selectedItem as BranchReference

                val moduleNodes = client.query(branchReference) {
                    it.allChildren().ofConcept(BuiltinLanguages.MPSRepositoryConcepts.Module).toList()
                }.map {
                    val name = it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
                        ?: it.toString()
                    val id = it.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id) ?: ""
                    ModuleIdWithName(id, name)
                }.sortedBy { it.name }

                modulesModel.addAll(moduleNodes.toList())
                if (modulesModel.size > 0) {
                    modulesModel.selectedItem = modulesModel.getElementAt(0)
                }
            }
        }

        private fun callOnlyIfNotFetching(action: suspend () -> Unit) {
            coroutineScope.launch {
                if (mutex.tryLock()) {
                    try {
                        setUiControlsEnabled(false)
                        action()
                    } catch (ex: Exception) {
                        logger.error(ex) { "Failed to fetch data from server." }
                    } finally {
                        setUiControlsEnabled(true)
                        mutex.unlock()
                    }
                }
            }
        }

        private fun setUiControlsEnabled(isEnabled: Boolean) {
            projectsCB.isEnabled = isEnabled
            reposCB.isEnabled = isEnabled
            branchesCB.isEnabled = isEnabled
            modulesCB.isEnabled = isEnabled

            connectButton.isEnabled = isEnabled
            disconnectButton.isEnabled = isEnabled
            bindButton.isEnabled = isEnabled
        }

        fun populateBindingCB(bindings: List<IBinding>) {
            bindingsModel.removeAllElements()
            bindingsModel.addAll(bindings)
            if (bindingsModel.size > 0) {
                bindingsModel.selectedItem = bindingsModel.getElementAt(0)
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
