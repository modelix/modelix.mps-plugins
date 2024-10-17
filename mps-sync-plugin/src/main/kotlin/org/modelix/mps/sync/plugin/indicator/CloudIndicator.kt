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

package org.modelix.mps.sync.plugin.indicator

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.ClickListener
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBLabel
import org.jetbrains.annotations.Nls
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.plugin.icons.CloudIcons
import java.awt.event.MouseEvent

/**
 * A factory to create a status bar widget about the connection status to the model sever.
 *
 * @see [StatusBarWidgetFactory].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class CloudIndicator : StatusBarWidgetFactory {

    companion object {

        /**
         * The ID of the widget.
         */
        private const val ID = "CloudStatus"
    }

    override fun getId(): String = ID

    @Nls
    override fun getDisplayName() = "Cloud Status"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project): StatusBarWidget = CloudToolWidget()
    override fun canBeEnabledOn(statusBar: StatusBar) = true
    override fun disposeWidget(widget: StatusBarWidget) {}

    /**
     * The status bar widget about the connection status to the model server.
     *
     * @see [JBLabel].
     * @see [CustomStatusBarWidget].
     */
    private class CloudToolWidget : JBLabel(), CustomStatusBarWidget {

        companion object {
            /**
             * The tooltip content placeholder string formatted as a table with one row and two columns.
             */
            private const val TOOLTIP_CONTENT = "<table><tr><td>{0}:</td><td align=right>{1}</td></tr></table>"
        }

        /**
         * If true then the widget is toggled on otherwise off.
         */
        private var isToggledOn = true

        init {
            object : ClickListener() {
                override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                    // TODO maybe even allow opening the tool bar from here
                    // ToolWindowManager.getInstance(e.project!!).getToolWindow("Modelix Model Synchronization")!!.show()
                    toggleStatus()
                    return true
                }
            }.installOn(this, true)
        }

        override fun ID() = ID

        override fun install(statusBar: StatusBar) = toggleStatus()

        override fun getComponent() = this

        override fun dispose() {}

        /**
         * Flips [isToggledOn] and updates the icon and tooltip text accordingly.
         *
         * @see [setIcon].
         * @see [setToolTipText].
         */
        private fun toggleStatus() {
            isToggledOn = !isToggledOn
            if (isToggledOn) {
                icon = CloudIcons.CONNECTION_ON
                toolTipText = UIBundle.message(TOOLTIP_CONTENT, TOOLTIP_CONTENT.format("Server 1", "ON"))
            } else {
                icon = CloudIcons.CONNECTION_OFF
                toolTipText = UIBundle.message(TOOLTIP_CONTENT, TOOLTIP_CONTENT.format("Server 1", "OFF"))
            }
        }
    }
}
