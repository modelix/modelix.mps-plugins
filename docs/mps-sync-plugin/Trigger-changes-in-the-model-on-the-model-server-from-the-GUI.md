# Trigger changes in the model on the model server from the GUI

If you are testing the sync plugin in MPS, and want to know if the changes coming from the model server are correctly played into MPS (i.e. the [ModelixTreeChangeVisitor](https://github.com/modelix/modelix.mps-plugins/blob/282bedfd8aae4b26fac7e98af2d3ba26e366dec8/mps-sync-plugin-lib/src/main/kotlin/org/modelix/mps/sync/transformation/modelixToMps/incremental/ModelixTreeChangeVisitor.kt) works correctly), then it can be useful to make changes on the model server without having to start another MPS instance (which does not work if the MPS version is the same).

One approach could be to write some code in the plugin's GUI class and create a button that triggers this code. This way, you can use the same MPS instance to trigger the change on the model server while testing if the changes are correctly played into your MPS.

In the [ModelSyncGuiFactory](https://github.com/modelix/modelix.mps-plugins/blob/282bedfd8aae4b26fac7e98af2d3ba26e366dec8/mps-sync-plugin/src/main/kotlin/org/modelix/mps/sync/plugin/gui/ModelSyncGuiFactory.kt) class, add the following code at the end of the [createContentBox](https://github.com/modelix/modelix.mps-plugins/blob/282bedfd8aae4b26fac7e98af2d3ba26e366dec8/mps-sync-plugin/src/main/kotlin/org/modelix/mps/sync/plugin/gui/ModelSyncGuiFactory.kt#L164) method (basically the place where the visual content of the plugin GUI is constructed):

```kotlin
val p4 = JPanel()
val addModelImportBtn = JButton("Add Model Import")
addModelImportBtn.addActionListener {
	val client = modelSyncService.connectModelServer("http://127.0.0.1:28101/v2")!!
	val branch = modelSyncService.connectToBranch(
		client,
		BranchReference(RepositoryId("repository name"), "branch name")
	)!!

	branch.runWriteT {
		val node = branch.getNode("modelix node ID".toLong())

		val child = node.addNewChild(
			BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports,
			-1,
			BuiltinLanguages.MPSRepositoryConcepts.ModelReference
		)
		child.setReferenceTarget(
			BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model,
			NodeReference("mps-model-import:r:e117f55c-1f24-4b31-a4cc-7557b8737f3e(com.mbeddr.doc.aspect.runtime)#IN#r:9e0bf89b-7c83-426e-8e13-cd21fab7b94a(MethodConfiguration)")
		)
	}

	modelSyncService.disconnectFromBranch(branch, "branch name")
	modelSyncService.disconnectServer(client)
}
p4.add(addModelImportBtn)
inputBox.add(p4)
```

The code creates a new Model Import child below the parent `node` and sets its model reference to an MPS model (that reference will be resolved in MPS locally, c.f. [synchronizing read-only models](https://github.com/modelix/modelix.mps-plugins/pull/154)). In the code, replace the model server URL, the `repository name`, `branch name`, `modelix node ID` [1] strings with the corresponding values. In `branch.runWriteT`, you can write the modelix node manipulation (or reading) code that you want to execute. `inputBox` is the main GUI box inside which all panels are put on the plugin's GUI.

After compiling the code, the `Add Model Import` button should show up at the bottom of the plugin GUI and clicking it should execute the aforementioned action.

[1] the modelix node ID is the long type of value on the model server. If you open the content of a repository on the model server in a browser, then the node ID is the second parameter after the node name. E.g. if you see the following node, then the node ID is `4294967309`.

> University.Schedule.modelserver.backend.sandbox | 4294967309 | PNode10000000d[org.modelix.model.api.BuiltinLanguages$MPSRepositoryConcepts$Module@7c927eab]
