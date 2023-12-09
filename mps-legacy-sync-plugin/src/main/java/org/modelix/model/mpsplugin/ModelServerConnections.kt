package org.modelix.model.mpsplugin

import io.ktor.client.HttpClient
import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.ITranslator2
import jetbrains.mps.internal.collections.runtime.IWhereFilter
import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.internal.collections.runtime.NotNullWhereFilter
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.internal.collections.runtime.SetSequence
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations
import jetbrains.mps.smodel.MPSModuleRepository
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.log4j.Logger
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.IBranch
import org.modelix.model.api.IConcept
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PNodeAdapter.Companion.wrap
import org.modelix.model.area.CompositeArea
import org.modelix.model.area.IArea
import org.modelix.model.area.PArea
import org.modelix.model.client.ActiveBranch
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.mps.MPSArea
import org.modelix.model.mpsadapters.mps.NodeToSNodeAdapter
import org.modelix.model.mpsadapters.mps.SConceptAdapter
import java.util.Objects

/*Generated by MPS */
class ModelServerConnections() {
    val modelServers: List<ModelServerConnection> = ListSequence.fromList(ArrayList())
    private val listeners: Set<IListener> = SetSequence.fromSet(HashSet())
    val area: IArea
        get() {
            return getArea(CommandHelper.sRepository)
        }

    fun getArea(mpsRepository: SRepository?): IArea {
        val cloudAreas: Iterable<PArea> = Sequence.fromIterable<ModelServerConnection?>(getModelServers())
            .where(object : IWhereFilter<ModelServerConnection>() {
                public override fun accept(it: ModelServerConnection): Boolean {
                    return it.isConnected
                }
            }).translate<ActiveBranch>(object : ITranslator2<ModelServerConnection, ActiveBranch?>() {
                public override fun translate(it: ModelServerConnection): Iterable<ActiveBranch?> {
                    it.getActiveBranch(ModelServerConnection.Companion.UI_STATE_REPOSITORY_ID)
                    return it.getActiveBranches()
                }
            }).select<PArea>(object : ISelector<ActiveBranch, PArea>() {
                public override fun select(it: ActiveBranch): PArea {
                    val branch: IBranch = it.branch
                    return PArea(branch)
                }
            }).where(NotNullWhereFilter<Any?>())
        val area: CompositeArea = CompositeArea(
            Sequence.fromIterable(Sequence.singleton<IArea>(MPSArea(mpsRepository))).concat(
                Sequence.fromIterable(cloudAreas),
            ).toListSequence(),
        )
        return area
    }

    fun addListener(l: IListener) {
        SetSequence.fromSet(listeners).addElement(l)
    }

    fun removeListener(l: IListener) {
        SetSequence.fromSet(listeners).removeElement(l)
    }

    fun existModelServer(url: String?): Boolean {
        return getModelServer(url) != null
    }

    fun getModelServer(url: String?): ModelServerConnection? {
        if (!(url!!.endsWith("/"))) {
            return getModelServer(url + "/")
        }
        return ListSequence.fromList(modelServers).findFirst(object : IWhereFilter<ModelServerConnection>() {
            public override fun accept(it: ModelServerConnection): Boolean {
                return Objects.equals(url, it.baseUrl)
            }
        })
    }

    @JvmOverloads
    fun addModelServer(url: String?, providedHttpClient: HttpClient? = null): ModelServerConnection {
        if (url == null) {
            throw IllegalArgumentException("url should not be null")
        }
        if (!(url.endsWith("/"))) {
            return addModelServer(url + "/")
        }
        if (existModelServer(url)) {
            throw IllegalStateException("The repository with url " + url + " is already present")
        }
        val result = doAddModelServer(url, providedHttpClient)
        // we do not automatically change the persisted configuration, to avoid cycles
        return result
    }

    protected fun doAddModelServer(url: String, providedHttpClient: HttpClient?): ModelServerConnection {
        val newRepo =
            ListSequence.fromList(modelServers).addElement(ModelServerConnection(url, providedHttpClient))
        try {
            for (l: IListener in SetSequence.fromSet(listeners)) {
                l.repositoriesChanged()
            }
        } catch (t: Exception) {
            if (LOG.isEnabledFor(Level.ERROR)) {
                LOG.error("", t)
            }
            ModelixNotifications.notifyError("Failure while adding model server " + url, t.message)
        }
        return newRepo
    }

    fun removeModelServer(repo: ModelServerConnection?) {
        ListSequence.fromList(modelServers).removeElement(repo)
        // we do not automatically change the persisted configuration, to avoid cycles
        for (l: IListener in SetSequence.fromSet(listeners)) {
            l.repositoriesChanged()
        }
    }

    fun getModelServers(): Iterable<ModelServerConnection> {
        return modelServers
    }

    val connectedModelServers: Iterable<ModelServerConnection?>
        get() {
            return ListSequence.fromList(modelServers).where(object : IWhereFilter<ModelServerConnection>() {
                public override fun accept(it: ModelServerConnection): Boolean {
                    return it.isConnected
                }
            })
        }
    val connectedTreesInRepositories: Iterable<CloudRepository>
        get() {
            return ListSequence.fromList(modelServers).where(object : IWhereFilter<ModelServerConnection>() {
                public override fun accept(it: ModelServerConnection): Boolean {
                    return it.isConnected
                }
            }).translate(object : ITranslator2<ModelServerConnection, CloudRepository>() {
                public override fun translate(it: ModelServerConnection): Iterable<CloudRepository?> {
                    return ModelServerNavigation.trees(it)
                }
            })
        }

    fun resolveCloudModel(repositoryId: String?): SNode {
        val repo: ModelServerConnection? =
            Sequence.fromIterable(getModelServers()).where(object : IWhereFilter<ModelServerConnection>() {
                public override fun accept(it: ModelServerConnection): Boolean {
                    return it.isConnected
                }
            }).first()
        val activeBranch: ActiveBranch? = repo!!.getActiveBranch(RepositoryId((repositoryId)!!))
        return SNodeOperations.cast(
            NodeToSNodeAdapter.Companion.wrap(
                object :
                    PNodeAdapter(ITree.ROOT_ID, activeBranch!!.branch) {
                    override val concept: IConcept?
                        get() {
                            return SConceptAdapter.Companion.wrap(CONCEPTS.`Repository$db`)
                        }
                },
                MPSModuleRepository.getInstance(),
            ),
            CONCEPTS.`Repository$db`,
        )
    }

    fun dispose() {
        for (modelServer: ModelServerConnection? in ListSequence.fromList(modelServers)) {
            modelServer!!.dispose()
        }
        ListSequence.fromList(modelServers).clear()
    }

    open interface IListener {
        fun repositoriesChanged()
    }

    fun ensureModelServerIsPresent(url: String?): ModelServerConnection {
        return getModelServer(url) ?: instance.addModelServer(url)
    }

    private object CONCEPTS {
        /*package*/
        val `Repository$db`: SConcept = MetaAdapterFactory.getConcept(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x69652614fd1c516L,
            "org.modelix.model.repositoryconcepts.structure.Repository",
        )
    }

    companion object {
        private val LOG: Logger = LogManager.getLogger(ModelServerConnections::class.java)
        val instance: ModelServerConnections = ModelServerConnections()
    }
}
