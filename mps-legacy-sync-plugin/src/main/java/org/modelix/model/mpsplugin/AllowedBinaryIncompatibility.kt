package org.modelix.model.mpsplugin

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic

/**
 * This file contains all API calls that lead to a difference in the compiled bytecode.
 * This class file is ignored during the binary compatibility check.
 * This file should contain as little code as possible.
 */

internal fun MessageBus.connectToMessageBus(): MessageBusConnection = connect()
internal fun getProjectManagerTopic(): Topic<ProjectManagerListener> = ProjectManager.TOPIC
