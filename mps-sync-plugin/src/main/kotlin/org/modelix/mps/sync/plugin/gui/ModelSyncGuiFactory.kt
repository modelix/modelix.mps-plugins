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

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import jetbrains.mps.workbench.MPSDataKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.modelql.core.toList
import org.modelix.modelql.untyped.allChildren
import org.modelix.modelql.untyped.ofConcept
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.bindings.ModuleBinding
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.notifications.AlertNotifier
import org.modelix.mps.sync.mps.notifications.BalloonNotifier
import org.modelix.mps.sync.mps.notifications.UserResponse
import org.modelix.mps.sync.mps.util.ModuleIdWithName
import org.modelix.mps.sync.plugin.ModelSyncService
import org.modelix.mps.sync.plugin.configuration.SyncPluginState
import org.modelix.mps.sync.plugin.icons.CloudIcons
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSeparator

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelSyncGuiFactory : ToolWindowFactory, Disposable {

    private lateinit var toolWindowContent: ModelSyncGui

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindowContent = ModelSyncGui(toolWindow)
    }

    override fun dispose() {
        toolWindowContent.dispose()
    }

    @UnstableModelixFeature(
        reason = "The new modelix MPS plugin is under construction",
        intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
    )
    class ModelSyncGui(toolWindow: ToolWindow) : Disposable {

        companion object {
            private const val COMBOBOX_CHANGED_COMMAND = "comboBoxChanged"
            private const val TEXTFIELD_WIDTH = 20
            private const val DISCONNECT_REMOVES_LOCAL_COPIES =
                "By disconnecting, the synchronized modules and models will be removed locally."
        }

        private val logger = KotlinLogging.logger {}
        private val mutex = Mutex()
        private val dispatcher = Dispatchers.Default

        private val bindingsRefresher: BindingsComboBoxRefresher

        private val activeProject: Project
        private val modelSyncService: ModelSyncService

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
        private val connectBranchButton = JButton("Connect to Branch without downloading Modules")
        private val disconnectBranchButton = JButton("Disconnect from Branch")

        private val connectionsModel = DefaultComboBoxModel<ModelClientV2>()
        private val reposModel = DefaultComboBoxModel<RepositoryId>()
        private val branchesModel = DefaultComboBoxModel<BranchReference>()
        private val modulesModel = DefaultComboBoxModel<ModuleIdWithName>()
        private val bindingsModel = DefaultComboBoxModel<IBinding>()

        private var activeBranch: ActiveBranch? = null
        private var selectedBranch: BranchReference? = null

        init {
            // TODO testme: is it going to work if we open MPS with two projects? Is the tool window going to be recreated in that case with the correct project?
            val project = MPSDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(toolWindow.component))
            checkNotNull(project) { "Modelix Plugin GUI cannot be initialized, because Project is null." }
            activeProject = project
            modelSyncService = activeProject.service<ModelSyncService>()

            // create GUI
            initializeToolWindowContent(toolWindow)
            bindingsRefresher = BindingsComboBoxRefresher(this)

            // restore persisted state
            val loadedState = activeProject.service<SyncPluginState>()
            loadedState.latestState?.let {
                val restoredStateContext = it.restoreState(modelSyncService)
                restoredStateContext?.let { context ->
                    val branch = context.branchReference
                    setActiveConnection(context.modelClient, branch.repositoryId, branch)
                }
            }
        }

        fun populateBindingCB(bindings: List<IBinding>) {
            bindingsModel.removeAllElements()
            bindingsModel.addAll(bindings)
            if (bindingsModel.size > 0) {
                bindingsModel.selectedItem = bindingsModel.getElementAt(0)
            }
        }

        private fun initializeToolWindowContent(toolWindow: ToolWindow) {
            // TODO fixme: hardcoded values
            serverURL.text = "http://127.0.0.1:28101/v2"
            repositoryName.text = "courses"
            branchName.text = "master"
            moduleName.text = "University.Schedule.modelserver.backend.sandbox"
            jwt.text = ""

            val contentPanel = JPanel()
            contentPanel.layout = FlowLayout()
            contentPanel.add(createContentBox())
            val content = ContentFactory.SERVICE.getInstance().createContent(contentPanel, "", false)
            toolWindow.contentManager.addContent(content)

            toolWindow.setIcon(CloudIcons.ROOT_ICON)
        }

        private fun createContentBox(): Box {
            val inputBox = Box.createVerticalBox()

            val urlPanel = JPanel()
            urlPanel.add(JLabel("Server URL:    "))
            urlPanel.add(serverURL)
            inputBox.add(urlPanel)

            val jwtPanel = JPanel()
            jwtPanel.add(JLabel("JWT:           "))
            jwtPanel.add(jwt)

            connectButton.addActionListener {
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

            disconnectButton.addActionListener { disconnectClient() }
            connectionsPanel.add(disconnectButton)
            inputBox.add(connectionsPanel)

            inputBox.add(JSeparator())

            val repoPanel = JPanel()
            reposCB.model = reposModel
            reposCB.renderer = CustomCellRenderer()
            reposCB.addActionListener {
                if (it.actionCommand == COMBOBOX_CHANGED_COMMAND) {
                    callDisablingUiControls(::populateBranchCB)
                }
            }
            repoPanel.add(JLabel("Remote Repo:   "))
            repoPanel.add(reposCB)
            inputBox.add(repoPanel)

            val branchPanel = JPanel()
            branchesCB.model = branchesModel
            branchesCB.renderer = CustomCellRenderer()
            branchesCB.addActionListener {
                val selectedItem = branchesModel.selectedItem
                if (selectedItem != null && it.actionCommand == COMBOBOX_CHANGED_COMMAND) {
                    if (activeBranch != null) {
                        // reset value to the previous one
                        branchesModel.selectedItem = selectedBranch

                        val message =
                            "<a href=\"disconnect\">Disconnect</a> from the active branch before switching to another one.</html>"
                        BalloonNotifier(activeProject).error(message) { disconnectBranch() }
                        return@addActionListener
                    }

                    selectedBranch = selectedItem as BranchReference
                    callDisablingUiControls(::populateModuleCB)
                }
            }

            branchPanel.add(JLabel("Remote Branch: "))
            branchPanel.add(branchesCB)
            inputBox.add(branchPanel)
            connectBranchButton.addActionListener {
                if (bindingsModel.size != 0) {
                    val message =
                        "<html>Bindings exists to remote models and modules. <a href=\"unbind\">Unbind</a> them before connecting to a branch.</html>"
                    BalloonNotifier(activeProject).error(message) {
                        val unbindConfirmation =
                            "Remote models and modules will be unbound and their local copies will be removed from the project."
                        AlertNotifier(activeProject).warning(unbindConfirmation) { response ->
                            if (UserResponse.USER_ACCEPTED == response) {
                                for (i in 0 until bindingsModel.size) {
                                    bindingsModel.getElementAt(i).deactivate(removeFromServer = false)
                                }
                            }
                        }
                    }
                    return@addActionListener
                }

                if (activeBranch != null) {
                    val message =
                        "<html>Already connected to a branch. <a href=\"disconnect\">Disconnect</a> from it before connecting to a new branch.</html>"
                    BalloonNotifier(activeProject).error(message) { disconnectBranch() }
                    return@addActionListener
                }

                val selectedConnection = connectionsModel.selectedItem
                if (selectedConnection != null && selectedBranch != null) {
                    callDisablingUiControls(
                        suspend {
                            val branchReference = selectedBranch as BranchReference
                            val branch = modelSyncService.connectToBranch(
                                selectedConnection as ModelClientV2,
                                branchReference,
                            )
                            if (branch != null) {
                                activeBranch = ActiveBranch(branch, branchReference)
                            }
                        },
                    )
                }
            }
            branchPanel.add(connectBranchButton)

            disconnectBranchButton.addActionListener { disconnectBranch() }
            branchPanel.add(disconnectBranchButton)

            val modulePanel = JPanel()
            modulesCB.model = modulesModel
            modulesCB.renderer = CustomCellRenderer()
            modulePanel.add(JLabel("Remote Module:  "))
            modulePanel.add(modulesCB)

            bindButton.addActionListener {
                val selectedConnection = connectionsModel.selectedItem
                val selectedBranch = branchesModel.selectedItem
                val selectedModule = modulesModel.selectedItem
                val selectedRepo = reposModel.selectedItem
                val inputsExist =
                    listOf(selectedConnection, selectedBranch, selectedModule, selectedRepo).all { it != null }

                if (inputsExist) {
                    val selectedModuleWithName = selectedModule as ModuleIdWithName
                    var bindingExists = false
                    if (bindingsModel.size != 0) {
                        for (i in 0 until bindingsModel.size) {
                            val binding = bindingsModel.getElementAt(i)
                            if (binding is ModuleBinding && selectedModuleWithName.name == binding.module.moduleName) {
                                bindingExists = true
                                break
                            }
                        }
                    }
                    if (bindingExists) {
                        return@addActionListener
                    }

                    logger.info { "Binding Module ${moduleName.text} to project: ${ActiveMpsProjectInjector.activeMpsProject?.name}" }
                    callDisablingUiControls(
                        suspend {
                            val branchName = (selectedBranch as BranchReference).branchName
                            modelSyncService.bindModuleFromServer(
                                selectedConnection as ModelClientV2,
                                branchName,
                                selectedModuleWithName,
                                (selectedRepo as RepositoryId).id,
                            )
                            val branch = modelSyncService.getActiveBranch()
                            requireNotNull(branch) { "Active branch cannot be null after connection. " }
                            activeBranch = ActiveBranch(branch, branchName)
                        },
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
            val disconnectAction = {
                val originalClient = connectionsModel.selectedItem as ModelClientV2
                val clientAfterDisconnect = modelSyncService.disconnectServer(originalClient)
                if (clientAfterDisconnect == null) {
                    triggerRefresh(null)
                }
            }

            if (connectionsModel.size > 0) {
                if (bindingsModel.size == 0) {
                    disconnectAction()
                    return
                }

                AlertNotifier(activeProject).warning(DISCONNECT_REMOVES_LOCAL_COPIES) { response ->
                    if (UserResponse.USER_ACCEPTED == response) {
                        disconnectAction()
                    }
                }
            }
        }

        private fun disconnectBranch() {
            val disconnectAction = {
                callDisablingUiControls(
                    suspend {
                        activeBranch?.let {
                            val branchName = it.branchName
                            modelSyncService.disconnectFromBranch(it.branch, branchName)
                            activeBranch = null
                        }
                    },
                )
            }

            if (activeBranch != null) {
                if (bindingsModel.size == 0) {
                    disconnectAction()
                    return
                }

                AlertNotifier(activeProject).warning(DISCONNECT_REMOVES_LOCAL_COPIES) { response ->
                    if (UserResponse.USER_ACCEPTED == response) {
                        disconnectAction()
                    }
                }
            }
        }

        private fun triggerRefresh(client: ModelClientV2?) {
            callDisablingUiControls(
                suspend {
                    populateConnectionsCB(client)
                    populateRepoCB()
                },
            )
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

        private fun callDisablingUiControls(action: suspend () -> Unit?) {
            CoroutineScope(dispatcher).launch {
                if (mutex.tryLock()) {
                    try {
                        setUiControlsEnabled(false)
                        action()
                    } catch (ex: Exception) {
                        logger.error(ex) { "Failed to execute action from the UI" }
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
            connectBranchButton.isEnabled = isEnabled
            disconnectBranchButton.isEnabled = isEnabled
        }

        private fun setActiveConnection(
            client: ModelClientV2,
            repositoryId: RepositoryId,
            branchReference: BranchReference,
        ) {
            runBlocking(dispatcher) {
                populateConnectionsCB(client)
                populateRepoCB()

                reposModel.removeAllElements()
                reposModel.addElement(repositoryId)

                branchesModel.removeAllElements()
                branchesModel.addElement(branchReference)

                val branch = modelSyncService.getActiveBranch()
                requireNotNull(branch) { "Active branch cannot be null after connection. " }
                activeBranch = ActiveBranch(branch, branchReference.branchName)
            }
        }

        private class CustomCellRenderer : DefaultListCellRenderer() {
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

        private data class ActiveBranch(val branch: IBranch, val branchName: String) {
            constructor(branch: IBranch, branchReference: BranchReference) : this(branch, branchReference.branchName)
        }

        override fun dispose() {
            bindingsRefresher.interrupt()
        }
    }
}
