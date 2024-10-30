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
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
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
import org.modelix.mps.sync.SyncServiceImpl
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.bindings.ModuleBinding
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
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSeparator

/**
 * A factory class to create the modelix sync plugin's GUI in MPS.
 *
 * @see [ToolWindowFactory].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelSyncGuiFactory : ToolWindowFactory {

    /**
     * Instantiates the [ModelSyncGui] and shows its content in the [toolWindow].
     *
     * @param project the active [Project] in MPS.
     * @param toolWindow the window in MPS.
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val gui = project.service<ModelSyncGui>()
        gui.init(toolWindow)
    }

    /**
     * The GUI of the modelix sync plugin. It is a simple form organized in a [BoxLayout] in a [ToolWindow].
     *
     * The UI shows to which branch of the model server's repository we are connected to, and which Modules and Models
     * of the opened [Project] we have bound to the model server. Besides the UI can initiate the synchronization of
     * the Modules from the model server to MPS locally. We can deactivate the established [IBinding]s individually and
     * thereby stop the synchronization to the server. See the [createContentBox] method for the details of the GUI
     * layout and the instantiated elements.
     *
     * Some GUI elements call the [ModelSyncService] that is the bridge between the UI and the sync plugin lib.
     *
     * This class is a [Service] class whose lifecycle is bound to the opened [Project]. Note that the singleton
     * instance of this class will be automatically created if you use the `project.service<ModelSyncGui>()` call.
     *
     * @property activeProject the active [Project] in MPS.
     *
     * @see [Service].
     * @see [Disposable].
     */
    @UnstableModelixFeature(
        reason = "The new modelix MPS plugin is under construction",
        intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
    )
    @Service(Service.Level.PROJECT)
    class ModelSyncGui(private val activeProject: Project) : Disposable {

        companion object {
            /**
             * The name of the value changed command in a ComboBox.
             */
            private const val COMBOBOX_CHANGED_COMMAND = "comboBoxChanged"

            /**
             * The default width of the text fields.
             */
            private const val TEXTFIELD_WIDTH = 20
        }

        /**
         * Just a normal logger to log messages.
         */
        private val logger = KotlinLogging.logger {}

        /**
         * A mutex to guarantee that we only run one action at a time in the [callDisablingUiControls] method.
         */
        private val mutex = Mutex()

        /**
         * A coroutine dispatcher for rather CPU-intensive tasks.
         */
        private val dispatcher = Dispatchers.Default

        // -------------------------------------------------------------------------------------------------------

        /**
         * The bridge between the GUI and the [SyncServiceImpl] of the sync plugin.
         */
        private val modelSyncService = activeProject.service<ModelSyncService>()

        /**
         * Keeps the [bindingsModel] ComboBox in sync with the active [IBinding]s from the [BindingsRegistry].
         */
        private val bindingsComboBoxRefresher = BindingsComboBoxRefresher(this, activeProject)

        // -------------------------------------------------------------------------------------------------------

        /**
         * The modelix model server URL input field.
         */
        private val serverURL = JBTextField(TEXTFIELD_WIDTH)

        /**
         * The modelix repository name input field.
         */
        private val repositoryName = JBTextField(TEXTFIELD_WIDTH)

        /**
         * The modelix branch name input field.
         */
        private val branchName = JBTextField(TEXTFIELD_WIDTH)

        /**
         * The Module's name input field.
         */
        private val moduleName = JBTextField(TEXTFIELD_WIDTH)

        /**
         * The JWT token's input field used for authentication to the model server.
         */
        private val jwt = JBTextField(TEXTFIELD_WIDTH)

        // -------------------------------------------------------------------------------------------------------

        /**
         * The ComboBox of the active modelix model server connections (model clients).
         */
        private val connectionsCB = ComboBox<ModelClientV2>()

        /**
         * The ComboBox of the opened [Project]s in MPS.
         */
        private val projectsCB = ComboBox<Project>()

        /**
         * The ComboBox of the available repositories on the model server.
         */
        private val reposCB = ComboBox<RepositoryId>()

        /**
         * The ComboBox of the available branches on the model server.
         */
        private val branchesCB = ComboBox<BranchReference>()

        /**
         * The ComboBox of the available Modules with their IDs on the chosen branch, see [branchesModel]).
         */
        private val modulesCB = ComboBox<ModuleIdWithName>()

        // -------------------------------------------------------------------------------------------------------

        /**
         * The connect to model server button.
         */
        private val connectButton = JButton("Connect")

        /**
         * The disconnect from the branch button. Pressing the button deactivates all active [IBinding]s and closes the
         * connection to the model server.
         */
        private val disconnectButton = JButton("Disconnect")

        /**
         * The bind selected Module button.
         */
        private val bindButton = JButton("Bind Selected")

        /**
         * The connect to branch button without downloading the Modules on that branch.
         */
        private val connectBranchButton = JButton("Connect to Branch without downloading Modules")

        /**
         * The disconnect from branch button to deactivate all active [IBinding]s but does not close the connection to
         * the model server.
         */
        private val disconnectBranchButton = JButton("Disconnect from Branch")

        // -------------------------------------------------------------------------------------------------------

        /**
         * [connectionsCB]'s model value.
         */
        private val connectionsModel = DefaultComboBoxModel<ModelClientV2>()

        /**
         * [reposCB]'s model value.
         */
        private val reposModel = DefaultComboBoxModel<RepositoryId>()

        /**
         * [branchesCB]'s model value.
         */
        private val branchesModel = DefaultComboBoxModel<BranchReference>()

        /**
         * [modulesCB]'s model value.
         */
        private val modulesModel = DefaultComboBoxModel<ModuleIdWithName>()

        /**
         * The model value of the existing bindings' ComboBox, see the [createContentBox] method.
         */
        private val bindingsModel = DefaultComboBoxModel<IBinding>()

        // -------------------------------------------------------------------------------------------------------

        /**
         * The modelix branch we are connected to.
         */
        private var activeBranch: ActiveBranch? = null

        /**
         * The modelix branch we selected from the [branchesCB].
         */
        private var selectedBranch: BranchReference? = null

        /**
         * Initializes the modelix sync plugin's GUI. First, it creates the plugin GUI, and then it restores the last
         * persisted state of the plugin to reestablish the connection and bindings to the model server.
         *
         * @param toolWindow the MPS [ToolWindow] in which the plugin's GUI will be shown.
         *
         * @see [initializeToolWindowContent].
         * @see [SyncPluginState.latestState].
         */
        fun init(toolWindow: ToolWindow) {
            // create GUI
            initializeToolWindowContent(toolWindow)

            // restore persisted state
            val loadedState = activeProject.service<SyncPluginState>()
            loadedState.latestState?.let {
                val restoredStateContext = it.restoreState(modelSyncService, activeProject)
                restoredStateContext?.let { context ->
                    val branch = context.branchReference
                    setActiveConnection(context.modelClient, branch.repositoryId, branch)
                }
            }
        }

        /**
         * Populates the [bindingsModel] with the [IBinding]s from [bindings].
         *
         * @param bindings the [IBinding]s to populate the [bindingsModel] with.
         */
        fun populateBindingCB(bindings: List<IBinding>) {
            bindingsModel.removeAllElements()
            bindingsModel.addAll(bindings)
            if (bindingsModel.size > 0) {
                bindingsModel.selectedItem = bindingsModel.getElementAt(0)
            }
        }

        /**
         * @see [BindingsComboBoxRefresher.dispose].
         */
        override fun dispose() = bindingsComboBoxRefresher.dispose()

        /**
         * Creates the plugin GUI content by populating the UI elements in the [toolWindow] and registering the event
         * handlers. It is a simple form organized in a [BoxLayout] in a [ToolWindow].
         *
         * @param toolWindow the MPS [ToolWindow] in which the plugin's GUI will be shown.
         */
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

        /**
         * Creates the content of the [BoxLayout] with all input fields, buttons, labels, ComboBoxes. Besides, it
         * registers the event handlers of the UI elements so the user can interact with them.
         *
         * @return the modelix sync plugin's UI in a [BoxLayout].
         */
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

                    logger.info { "Binding Module ${moduleName.text} to project: ${activeProject.name}" }
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

        /**
         * Disconnects the model client from the modelix model server. It deactivates the active [IBinding]s and closes
         * the model client in the end.
         *
         * @see [ModelSyncService.disconnectServer].
         */
        private fun disconnectClient() {
            val disconnectAction = {
                val originalClient = connectionsModel.selectedItem as ModelClientV2
                val clientAfterDisconnect = modelSyncService.disconnectServer(originalClient)
                if (clientAfterDisconnect == null) {
                    triggerRefresh(null)
                }
            }

            if (connectionsModel.size > 0) {
                disconnectAction()
            }
        }

        /**
         * Disconnects MPS from the active branch. It deactivates the active [IBinding]s, but does not close the model
         * client.
         */
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
                disconnectAction()
            }
        }

        /**
         * Calls [populateConnectionsCB] and [populateRepoCB] to update their content based on the newly connected
         * model client ([client]).
         *
         * @param client the model client we are connected to the server with.
         */
        private fun triggerRefresh(client: ModelClientV2?) {
            callDisablingUiControls(
                suspend {
                    populateConnectionsCB(client)
                    populateRepoCB()
                },
            )
        }

        /**
         * Clears the content of the [connectionsModel] and then adds the [client] to it.
         *
         * @param client the model client we are connected to the server with.
         */
        private fun populateConnectionsCB(client: ModelClientV2?) {
            connectionsModel.removeAllElements()
            if (client != null) {
                connectionsModel.addAll(listOf(client))
                connectionsModel.selectedItem = connectionsModel.getElementAt(0)
            }
        }

        /**
         * Clears the content of the [reposModel] and then populates it with the list of repositories fetched by the
         * active model client.
         */
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

        /**
         * Clears the content of the [branchesModel] and then populates it with the list of branches in the chosen
         * modelix repository (see [reposModel]).
         */
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

        /**
         * Clears the content of the [modulesModel] and then populates it with the list of Modules that are on the
         * chosen branch (see [branchesModel]).
         */
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

        /**
         * Disable all UI controls while [action] is running. Only one [action] is allowed to run at a time, this is
         * controlled by the [mutex]. After the [action] completed, all UI controls are enabled again.
         *
         * This disabling is needed so that the user cannot bring the plugin to an inconsistent state by clicking on the
         * UI while the plugin is performing an operation.
         *
         * @param action the action to run while the UI controls are disabled.
         *
         * @see [setUiControlsEnabled].
         */
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

        /**
         * Sets all ComboBoxes and buttons to [isEnabled].
         *
         * @param isEnabled to enable or disable all ComboBoxes and buttons on the UI.
         */
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

        /**
         * Sets the ComboBoxes and the [activeBranch] field based on the parameters of this method. We assume that the
         * [client] is already connected to the [branchReference] branch of the [repositoryId] repository on the modelix
         * model server, when this method is called. It is just to keep the UI in sync with the sync plugin's internal
         * state.
         *
         * @param client the model client we used to connect to the model server.
         * @param repositoryId the repository which we are connected to.
         * @param branchReference the branch which we are connected to.
         */
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

        /**
         * A custom renderer to render the model value of the ComboBoxes depending on the model value's type.
         *
         * @see [DefaultListCellRenderer]
         */
        private class CustomCellRenderer : DefaultListCellRenderer() {

            /**
             * A custom renderer to render the model value of the ComboBoxes depending on the model value's type. In all
             * cases we show the most informative field of the given type as text in the ComboBox. If we do not know
             * the [value]'s type then we fall back to [getListCellRendererComponent].
             *
             * @return the correctly rendered ComboBox value.
             *
             * @see [DefaultListCellRenderer.getListCellRendererComponent].
             */
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

        /**
         * A data class to store the active [IBranch] together with its [branchName] name, because [IBranch] does not
         * store the branch's name.
         *
         * @property branch the active branch we are connected to.
         * @property branchName the name of the branch.
         */
        private data class ActiveBranch(val branch: IBranch, val branchName: String) {
            /**
             * A constructor to get the branch name from the [branchReference].
             *
             * @param branch the active branch we are connected to.
             * @param branchReference a reference for the [branch] from which we have to read the branch name.
             */
            constructor(branch: IBranch, branchReference: BranchReference) : this(branch, branchReference.branchName)
        }
    }
}
