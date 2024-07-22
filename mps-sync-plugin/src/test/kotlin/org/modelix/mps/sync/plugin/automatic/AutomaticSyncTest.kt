/*
 * Copyright (c) 2023.
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

// TODO Olekz remove
@file:OptIn(UnstableModelixFeature::class)

package org.modelix.mps.sync.plugin.automatic

import SyncPluginWithModelServerTestBase
import com.intellij.openapi.application.runReadAction
import executeCommandMps
import getModel
import getMpsModel
import jetbrains.mps.smodel.SNode
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.BuiltinLanguages.jetbrains_mps_lang_core.addConcept
import org.modelix.model.api.SimpleConcept
import org.modelix.model.api.SimpleProperty
import waitUntilModuleIsInMps
import waitUntilSynced
import writeModelServer

// TODO Olekz https://github.com/modelix/modelix.core/pull/903
private val iNamedConcept = object : SimpleConcept(
    conceptName = "NodeAttribute",
    is_abstract = true,
    uid = "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/1169194658468",
) {
    init { addConcept(this) }
    val name = SimpleProperty(
        "name",
        uid = "ceab5195-25ea-4f22-9b92-103b95ca8c0c/1169194658468/1169194664001",
    ).also(this::addProperty)
}
private val moduleConcept = BuiltinLanguages.MPSRepositoryConcepts.Module
private val modelConcept = BuiltinLanguages.MPSRepositoryConcepts.Model
private val iNamedMpsConcept = MetaAdapterFactory.getConcept(
    -0x3154ae6ada15b0deL,
    -0x646defc46a3573f4L,
    0x110396eaaa4L,
    "jetbrains.mps.lang.core.structure.INamedConcept",
)

private val iNamedMpsConceptNameProperty = MetaAdapterFactory.getProperty(
    -0x3154ae6ada15b0deL,
    -0x646defc46a3573f4L,
    0x110396eaaa4L,
    0x110396ec041L,
    "name",
)

class AutomaticSyncTest : SyncPluginWithModelServerTestBase() {
    fun testUpdateFromMps_with_initialModuleFromModelServer() {
        waitUntilModuleIsInMps("aSolution")

        executeCommandMps {
            val model = getMpsModel("aSolution.aModel")
            val root = SNode(iNamedMpsConcept)
            model.addRootNode(root)
            root.setProperty(iNamedMpsConceptNameProperty, "aName")
        }
        waitUntilSynced()

        val model = getModel("aSolution.aModel")
        val root = model.getChildren(modelConcept.rootNodes).single()
        assertEquals("aName", root.getPropertyValue(iNamedConcept.name))
    }

    fun testUpdateFromModelServer_with_initialModuleFromModelServer() {
        waitUntilModuleIsInMps("aSolution")

        writeModelServer { branch ->
            val model = branch.getModel("aSolution.aModel")
            val root = model.addNewChild(modelConcept.rootNodes, -1, iNamedConcept)
            root.setPropertyValue(iNamedConcept.name, "aName")
        }
        waitUntilSynced()

        runReadAction {
            val model = getMpsModel("aSolution.aModel")
            val root = model.rootNodes.single()
            assertEquals("aName", root.getProperty(iNamedMpsConceptNameProperty))
        }
    }

//    fun testSyncTwoModulesFromModelServer() {
//        waitUntilModuleIsInMps("aSolution1", "aSolution2")
//    }
//
//    fun testSyncOneModuleToModelServer() {
//        waitUntilModuleIsOnServer("aSolution")
//        runBlocking {
//            modelClient.runWriteOnBranch(branchRef) {
//                val nodeData = it.getRootNode().asData()
//                val modelData = ModelData(null, nodeData)
//                println(modelData.toJson())
//            }
//        }
//    }
//
//    fun testSyncTwoModulesToModelServer() {
//        waitUntilModuleIsOnServer("aSolution1", "aSolution2")
//        runBlocking {
//            modelClient.runWriteOnBranch(branchRef) {
//                val nodeData = it.getRootNode().asData()
//                val modelData = ModelData(null, nodeData)
//                println(modelData.toJson())
//            }
//        }
//    }
//
//    // Initially bound module from MPS is synced
//    // TODO this should not be needed
//    // - mps to model server
//    // - model server to mpp
//
//    // Initially bound module from Model Server synced
//    // - mps to model server
//    // - model server to mpp
//    // Module added on model server
//    // - mps to model server
//    // - model server to mps
//    // Module added in MPS
//    // - mps to model server
//    // - model server to mpp
//    // Module remove on model server
//    // Module removed in MPS
}
