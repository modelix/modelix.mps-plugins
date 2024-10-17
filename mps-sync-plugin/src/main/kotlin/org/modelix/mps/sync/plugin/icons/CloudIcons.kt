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

package org.modelix.mps.sync.plugin.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * Some custom rendered icons used on the UI.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
object CloudIcons {

    /**
     * The standard font size in points.
     */
    private const val FONT_SIZE = 14

    /**
     * The icon in the Tool Window.
     */
    val ROOT_ICON = LetterInSquareIcon("C", FONT_SIZE, 3.0f, 13.0f, JBColor.YELLOW, JBColor.BLACK)

    /**
     * The icon to show that we are connected to the model server.
     */
    val CONNECTION_ON = LetterInSquareIcon("", FONT_SIZE, 2.0f, 12.0f, JBColor.GREEN, JBColor.BLACK)

    /**
     * The icon to show that we are disconnected from the model server.
     */
    val CONNECTION_OFF = LetterInSquareIcon("", FONT_SIZE, 2.0f, 12.0f, JBColor.RED, JBColor.BLACK)

    /**
     * The plugin's icon shown in the Plugin's list on the Settings page.
     */
    val PLUGIN_ICON = IconLoader.getIcon("/META-INF/pluginIcon.svg", javaClass)
}
