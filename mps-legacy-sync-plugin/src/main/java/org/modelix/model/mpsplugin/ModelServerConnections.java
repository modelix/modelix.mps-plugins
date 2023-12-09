package org.modelix.model.mpsplugin;

/*Generated by MPS */

import io.ktor.client.HttpClient;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.modelix.model.api.IBranch;
import org.modelix.model.api.PBranch;
import org.modelix.model.client.IdGenerator;
import org.modelix.model.api.IBranchListener;
import org.jetbrains.annotations.Nullable;
import org.modelix.model.api.ITree;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import jetbrains.mps.internal.collections.runtime.ListSequence;
import java.util.ArrayList;
import java.util.Set;
import jetbrains.mps.internal.collections.runtime.SetSequence;
import java.util.HashSet;
import org.modelix.model.area.IArea;
import org.jetbrains.mps.openapi.module.SRepository;
import org.modelix.model.area.PArea;
import jetbrains.mps.internal.collections.runtime.Sequence;
import jetbrains.mps.internal.collections.runtime.IWhereFilter;
import jetbrains.mps.internal.collections.runtime.ITranslator2;
import org.modelix.model.client.ActiveBranch;
import jetbrains.mps.internal.collections.runtime.ISelector;
import jetbrains.mps.internal.collections.runtime.NotNullWhereFilter;
import org.modelix.model.area.CompositeArea;
import org.modelix.model.mpsadapters.mps.MPSArea;
import java.util.Objects;
import org.apache.log4j.Level;
import org.jetbrains.mps.openapi.model.SNode;
import org.modelix.model.lazy.RepositoryId;
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations;
import org.modelix.model.mpsadapters.mps.NodeToSNodeAdapter;
import org.modelix.model.api.PNodeAdapter;
import org.modelix.model.api.IConcept;
import org.modelix.model.mpsadapters.mps.SConceptAdapter;
import jetbrains.mps.smodel.MPSModuleRepository;
import org.jetbrains.mps.openapi.language.SConcept;
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory;

public class ModelServerConnections {
  private static final Logger LOG = LogManager.getLogger(ModelServerConnections.class);
  private static ModelServerConnections ourInstance = new ModelServerConnections();

  public static ModelServerConnections getInstance() {
    return ourInstance;
  }

  private List<ModelServerConnection> modelServers = ListSequence.fromList(new ArrayList<ModelServerConnection>());
  private Set<IListener> listeners = SetSequence.fromSet(new HashSet<IListener>());

  public ModelServerConnections() {
    // we used to initialize repositories here, reading the configuration
    // we do not do that here anymore
  }

  public IArea getArea() {
    return getArea(CommandHelper.getSRepository());
  }

  public IArea getArea(SRepository mpsRepository) {
    Iterable<PArea> cloudAreas = Sequence.fromIterable(getModelServers()).where(new IWhereFilter<ModelServerConnection>() {
      public boolean accept(ModelServerConnection it) {
        return it.isConnected();
      }
    }).translate(new ITranslator2<ModelServerConnection, ActiveBranch>() {
      public Iterable<ActiveBranch> translate(ModelServerConnection it) {
        it.getActiveBranch(ModelServerConnection.UI_STATE_REPOSITORY_ID);
        return it.getActiveBranches();
      }
    }).select(new ISelector<ActiveBranch, PArea>() {
      public PArea select(ActiveBranch it) {
        IBranch branch = it.getBranch();
        return new PArea(branch);
      }
    }).where(new NotNullWhereFilter());
    CompositeArea area = new CompositeArea(Sequence.fromIterable(Sequence.<IArea>singleton(new MPSArea(mpsRepository))).concat(Sequence.fromIterable(cloudAreas)).toListSequence());
    return area;
  }

  public void addListener(IListener l) {
    SetSequence.fromSet(listeners).addElement(l);
  }

  public void removeListener(IListener l) {
    SetSequence.fromSet(listeners).removeElement(l);
  }

  public boolean existModelServer(String url) {
    return getModelServer(url) != null;
  }
  public ModelServerConnection getModelServer(final String url) {
    if (!(url.endsWith("/"))) {
      return getModelServer(url + "/");
    }
    return ListSequence.fromList(this.modelServers).findFirst(new IWhereFilter<ModelServerConnection>() {
      public boolean accept(ModelServerConnection it) {
        return Objects.equals(url, it.getBaseUrl());
      }
    });
  }

  public ModelServerConnection addModelServer(String url) {
    return addModelServer(url, null);
  }

  public ModelServerConnection addModelServer(String url, HttpClient providedHttpClient) {
    if (url == null) {
      throw new IllegalArgumentException("url should not be null");
    }
    if (!(url.endsWith("/"))) {
      return addModelServer(url + "/");
    }
    if (existModelServer(url)) {
      throw new IllegalStateException("The repository with url " + url + " is already present");
    }
    ModelServerConnection result = doAddModelServer(url, providedHttpClient);
    // we do not automatically change the persisted configuration, to avoid cycles
    return result;
  }

  protected ModelServerConnection doAddModelServer(String url, HttpClient providedHttpClient) {
    ModelServerConnection newRepo = ListSequence.fromList(modelServers).addElement(new ModelServerConnection(url, providedHttpClient));
    try {
      for (IListener l : SetSequence.fromSet(listeners)) {
        l.repositoriesChanged();
      }
    } catch (Exception t) {
      if (LOG.isEnabledFor(Level.ERROR)) {
        LOG.error("", t);
      }
      ModelixNotifications.notifyError("Failure while adding model server " + url, t.getMessage());
    }
    return newRepo;
  }

  public void removeModelServer(ModelServerConnection repo) {
    ListSequence.fromList(modelServers).removeElement(repo);
    // we do not automatically change the persisted configuration, to avoid cycles
    for (IListener l : SetSequence.fromSet(listeners)) {
      l.repositoriesChanged();
    }
  }

  public Iterable<ModelServerConnection> getModelServers() {
    return modelServers;
  }

  public Iterable<ModelServerConnection> getConnectedModelServers() {
    return ListSequence.fromList(modelServers).where(new IWhereFilter<ModelServerConnection>() {
      public boolean accept(ModelServerConnection it) {
        return it.isConnected();
      }
    });
  }

  public Iterable<CloudRepository> getConnectedTreesInRepositories() {
    return ListSequence.fromList(modelServers).where(new IWhereFilter<ModelServerConnection>() {
      public boolean accept(ModelServerConnection it) {
        return it.isConnected();
      }
    }).translate(new ITranslator2<ModelServerConnection, CloudRepository>() {
      public Iterable<CloudRepository> translate(ModelServerConnection it) {
        return ModelServerNavigation.trees(it);
      }
    });
  }

  public SNode resolveCloudModel(String repositoryId) {
    ModelServerConnection repo = Sequence.fromIterable(getModelServers()).where(new IWhereFilter<ModelServerConnection>() {
      public boolean accept(ModelServerConnection it) {
        return it.isConnected();
      }
    }).first();
    ActiveBranch activeBranch = repo.getActiveBranch(new RepositoryId(repositoryId));

    return SNodeOperations.cast(NodeToSNodeAdapter.wrap(new PNodeAdapter(ITree.ROOT_ID, activeBranch.getBranch()) {
      @Override
      public IConcept getConcept() {
        return SConceptAdapter.wrap(CONCEPTS.Repository$db);
      }
    }, MPSModuleRepository.getInstance()), CONCEPTS.Repository$db);
  }

  public void dispose() {
    for (ModelServerConnection modelServer : ListSequence.fromList(modelServers)) {
      modelServer.dispose();
    }
    ListSequence.fromList(modelServers).clear();
  }

  public interface IListener {
    void repositoriesChanged();
  }

  public ModelServerConnection ensureModelServerIsPresent(String url) {
    ModelServerConnection modelServerConnection = this.getModelServer(url);
    if (modelServerConnection != null) {
      return modelServerConnection;
    } else {
      return ModelServerConnections.getInstance().addModelServer(url);
    }
  }


  private static final class CONCEPTS {
    /*package*/ static final SConcept Repository$db = MetaAdapterFactory.getConcept(0xa7577d1d4e5431dL, 0x98b1fae38f9aee80L, 0x69652614fd1c516L, "org.modelix.model.repositoryconcepts.structure.Repository");
  }
}
