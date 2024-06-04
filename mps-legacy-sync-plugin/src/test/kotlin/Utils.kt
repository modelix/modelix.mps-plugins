import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.IRole
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData
import org.modelix.model.data.associateWithNotNull
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.OTBranch
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.emptyList
import kotlin.collections.forEach
import kotlin.collections.iterator
import kotlin.collections.map
import kotlin.collections.plusAssign
import kotlin.collections.set
import kotlin.collections.toList
import kotlin.collections.toTypedArray

suspend fun <T> IModelClientV2.runWrite(branchRef: BranchReference, body: (INode) -> T): T {
    return runWriteOnBranch(branchRef) { body(it.getRootNode()) }
}

suspend fun <T> IModelClientV2.runWriteOnBranch(branchRef: BranchReference, body: (IBranch) -> T): T {
    val client = this
    val baseVersion = client.pull(branchRef, null) as CLVersion
    val branch = OTBranch(TreePointer(baseVersion.getTree(), client.getIdGenerator()), client.getIdGenerator(), baseVersion.store)
    val result = branch.computeWrite {
        body(branch)
    }
    val (ops, newTree) = branch.getPendingChanges()
    val newVersion = CLVersion.createRegularVersion(
        id = client.getIdGenerator().generate(),
        author = client.getUserId(),
        tree = newTree as CLTree,
        baseVersion = baseVersion,
        operations = ops.map { it.getOriginalOp() }.toTypedArray(),
    )
    client.push(branchRef, newVersion, baseVersion)
    return result
}

fun IRole.key(useRoleIds: Boolean) = if (useRoleIds) getUID() else getSimpleName()

fun NodeData.load(t: IWriteTransaction, parentId: Long): Long {
    val pendingReferences = ArrayList<() -> Unit>()
    val createdNodes = HashMap<String, Long>()
    val createNodeId = load(t, parentId, createdNodes, pendingReferences)
    pendingReferences.forEach { it() }
    return createNodeId
}

fun NodeData.load(
    t: IWriteTransaction,
    parentId: Long,
    createdNodes: HashMap<String, Long>,
    pendingReferences: ArrayList<() -> Unit>,
): Long {
    val nodeData = this
    val conceptRef = nodeData.concept?.let { ConceptReference(it) }
    val createdId = t.addNewChild(parentId, nodeData.role, -1, conceptRef)
    val nodeId = nodeData.id
    if (nodeId != null) {
        createdNodes[nodeId] = createdId
        t.setProperty(createdId, NodeData.ID_PROPERTY_KEY, properties[NodeData.ID_PROPERTY_KEY] ?: id)
    }
    for (propertyData in nodeData.properties) {
        t.setProperty(createdId, propertyData.key, propertyData.value)
    }
    for (referenceData in nodeData.references) {
        pendingReferences += {
            val target = createdNodes[referenceData.value]?.let { LocalPNodeReference(it) }
            t.setReferenceTarget(createdId, referenceData.key, target)
        }
    }
    for (childData in nodeData.children) {
        childData.load(t, createdId, createdNodes, pendingReferences)
    }
    return createdId
}

fun INode.asData(includeChildren: Boolean = true): NodeData = NodeData(
    id = reference.serialize(),
    concept = concept?.getUID(),
    role = roleInParent,
    properties = getPropertyRoles().associateWithNotNull { getPropertyValue(it) },
    references = getReferenceRoles().associateWithNotNull { getReferenceTargetRef(it)?.serialize() },
    children = if (includeChildren) allChildren.map { it.asData() } else emptyList(),
)

fun ITree.asData() = TreePointer(this).asData()

fun IBranch.asData() = ModelData(
    id = getId(),
    root = getRootNode().asData(),
)

suspend fun HttpClient.initRepository(baseUrl: String, repository: RepositoryId, useRoleIds: Boolean) {
    post {
        url {
            takeFrom(baseUrl)
            appendPathSegmentsEncodingSlash("repositories", repository.id, "init")
            if (!useRoleIds) {
                parameter("useRoleIds", useRoleIds.toString())
            }
        }
    }
}

private fun URLBuilder.appendPathSegmentsEncodingSlash(vararg components: String): URLBuilder {
    return appendPathSegments(components.toList(), true)
}
