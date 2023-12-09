package org.modelix.model.mpsplugin;

/*Generated by MPS */

import jetbrains.mps.smodel.*;
import jetbrains.mps.smodel.language.LanguageRegistry;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.jetbrains.mps.openapi.model.SModel;
import org.modelix.model.api.IBranch;
import org.modelix.model.area.PArea;
import kotlin.jvm.functions.Function0;
import kotlin.Unit;
import org.modelix.model.api.ITree;
import java.util.List;
import jetbrains.mps.project.Project;
import jetbrains.mps.project.ProjectManager;
import jetbrains.mps.internal.collections.runtime.ListSequence;
import org.apache.log4j.Level;
import org.modelix.model.mpsadapters.mps.SModelAsNode;
import org.modelix.model.api.INode;
import org.modelix.model.api.PNodeAdapter;
import org.modelix.model.mpsadapters.mps.DevKitDependencyAsNode;
import jetbrains.mps.internal.collections.runtime.Sequence;
import jetbrains.mps.internal.collections.runtime.IWhereFilter;
import java.util.Objects;
import org.modelix.model.mpsadapters.mps.SingleLanguageDependencyAsNode;
import org.jetbrains.mps.openapi.module.SRepository;
import jetbrains.mps.project.DevKit;
import jetbrains.mps.project.ModuleId;
import java.util.UUID;
import org.jetbrains.mps.openapi.module.SModuleReference;
import org.jetbrains.mps.openapi.language.SLanguage;
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory;
import de.slisson.mps.reflection.runtime.ReflectionUtil;
import org.modelix.model.mpsadapters.mps.ModelImportAsNode;
import jetbrains.mps.project.structure.modules.ModuleReference;
import org.jetbrains.mps.openapi.model.SModelId;
import jetbrains.mps.extapi.model.SModelDescriptorStub;
import org.jetbrains.mps.openapi.language.SContainmentLink;
import org.jetbrains.mps.openapi.language.SReferenceLink;
import org.jetbrains.mps.openapi.language.SProperty;
import org.jetbrains.mps.openapi.language.SConcept;

/*package*/ class ModelPropertiesSynchronizer {
  private static final Logger LOG = LogManager.getLogger(ModelPropertiesSynchronizer.class);
  protected SModel model;
  protected long modelNodeId;
  private ICloudRepository cloudRepository;

  public ModelPropertiesSynchronizer(long modelNodeId, SModel model, ICloudRepository cloudRepository) {
    this.model = model;
    this.modelNodeId = modelNodeId;
    this.cloudRepository = cloudRepository;
  }

  private IBranch getBranch() {
    return cloudRepository.getBranch();
  }

  public void syncModelPropertiesFromMPS() {
    new PArea(getBranch()).executeWrite(new Function0<Unit>() {
      public Unit invoke() {
        syncUsedLanguagesAndDevKitsFromMPS();
        syncModelImportsFromMPS();
        return Unit.INSTANCE;
      }
    });
  }

  public void syncModelPropertiesToMPS(ITree tree, ICloudRepository cloudRepository) {
    syncModelPropertiesToMPS(tree, model, modelNodeId, cloudRepository);
  }

  /*package*/ static void syncModelPropertiesToMPS(ITree tree, SModel model, long modelNodeId, ICloudRepository cloudRepository) {
    syncUsedLanguagesAndDevKitsToMPS(tree, model, modelNodeId, cloudRepository);
    syncModelImportsToMPS(tree, model, modelNodeId, cloudRepository);

    try {
      List<Project> projects = ProjectManager.getInstance().getOpenedProjects();
      if (ListSequence.fromList(projects).isNotEmpty()) {
        var project = ListSequence.fromList(projects).first();
        new ModuleDependencyVersions(LanguageRegistry.getInstance(project.getRepository()), project.getRepository()).update(model.getModule());
      }
    } catch (Exception ex) {
      if (LOG.isEnabledFor(Level.ERROR)) {
        LOG.error("Failed to update language version after change in model " + model.getName().getValue(), ex);
      }
    }
  }

  public void syncUsedLanguagesAndDevKitsFromMPS() {
    new PArea(getBranch()).<Unit>executeWrite(new Function0<Unit>() {
      public Unit invoke() {
        // First get the dependencies in MPS
        SModelAsNode mpsModelNode = SModelAsNode.wrap(model);
        List<INode> dependenciesInMPS = IterableOfINodeUtils.toList(mpsModelNode.getChildren(LINKS.usedLanguages$QK4E.getName()));

        //  Then get the dependencies in the cloud
        IBranch branch = getBranch();
        INode cloudModelNode = new PNodeAdapter(modelNodeId, branch);
        Iterable<INode> dependenciesInCloud = cloudModelNode.getChildren(LINKS.usedLanguages$QK4E.getName());

        // For each import in MPS, add it if not present in the cloud, or otherwise ensure all properties are the same
        for (final INode dependencyInMPS : ListSequence.fromList(dependenciesInMPS)) {
          if (dependencyInMPS instanceof DevKitDependencyAsNode) {
            INode matchingDependencyInCloud = Sequence.fromIterable(dependenciesInCloud).findFirst(new IWhereFilter<INode>() {
              public boolean accept(INode dependencyInCloud) {
                return Objects.equals(dependencyInMPS.getPropertyValue(PROPS.uuid$lpJp.getName()), dependencyInCloud.getPropertyValue(PROPS.uuid$lpJp.getName()));
              }
            });
            if (matchingDependencyInCloud == null) {
              INodeUtils.replicateChild(cloudModelNode, LINKS.usedLanguages$QK4E.getName(), dependencyInMPS);
            } else {
              INodeUtils.copyProperty(cloudModelNode, dependencyInMPS, PROPS.name$lpYq);
            }
          } else if (dependencyInMPS instanceof SingleLanguageDependencyAsNode) {
            INode matchingDependencyInCloud = Sequence.fromIterable(dependenciesInCloud).findFirst(new IWhereFilter<INode>() {
              public boolean accept(INode dependencyInCloud) {
                return Objects.equals(dependencyInMPS.getPropertyValue(PROPS.uuid$lpJp.getName()), dependencyInCloud.getPropertyValue(PROPS.uuid$lpJp.getName()));
              }
            });
            if (matchingDependencyInCloud == null) {
              INodeUtils.replicateChild(cloudModelNode, LINKS.usedLanguages$QK4E.getName(), dependencyInMPS);
            } else {
              INodeUtils.copyProperty(cloudModelNode, dependencyInMPS, PROPS.name$lpYq);
              INodeUtils.copyProperty(cloudModelNode, dependencyInMPS, PROPS.version$ApUL);
            }
          } else {
            throw new RuntimeException("Unknown dependency type: " + dependencyInMPS.getClass().getName());
          }
        }

        // For each import not in MPS, remove it
        for (final INode dependencyInCloud : Sequence.fromIterable(dependenciesInCloud)) {
          INode matchingDependencyInMPS = Sequence.fromIterable(dependenciesInCloud).findFirst(new IWhereFilter<INode>() {
            public boolean accept(INode dependencyInMPS) {
              return Objects.equals(dependencyInCloud.getPropertyValue(PROPS.uuid$lpJp.getName()), dependencyInMPS.getPropertyValue(PROPS.uuid$lpJp.getName()));
            }
          });
          if (matchingDependencyInMPS == null) {
            cloudModelNode.removeChild(dependencyInCloud);
          }
        }
        return Unit.INSTANCE;
      }
    });
  }


  public static void syncUsedLanguagesAndDevKitsToMPS(ITree tree, final SModel model, final long modelNodeId, final ICloudRepository cloudRepository) {
    new PArea(cloudRepository.getBranch()).executeRead(new Function0<Unit>() {
      public Unit invoke() {
        ModelAccess.runInWriteActionIfNeeded(model, new Runnable() {
          @Override
          public void run() {
            // First get the dependencies in MPS
            SModelAsNode mpsModelNode = SModelAsNode.wrap(model);
            List<INode> dependenciesInMPS = IterableOfINodeUtils.toList(mpsModelNode.getChildren(LINKS.usedLanguages$QK4E.getName()));

            //  Then get the dependencies in the cloud
            IBranch branch = cloudRepository.getBranch();
            INode cloudModelNode = new PNodeAdapter(modelNodeId, branch);
            Iterable<INode> dependenciesInCloud = cloudModelNode.getChildren(LINKS.usedLanguages$QK4E.getName());

            // For each import in the cloud add it if not present in MPS or otherwise ensure all properties are the same
            for (final INode dependencyInCloud : Sequence.fromIterable(dependenciesInCloud)) {
              INode matchingDependencyInMPS = ListSequence.fromList(dependenciesInMPS).findFirst(new IWhereFilter<INode>() {
                public boolean accept(INode dependencyInMPS) {
                  return Objects.equals(dependencyInCloud.getPropertyValue(PROPS.uuid$lpJp.getName()), dependencyInMPS.getPropertyValue(PROPS.uuid$lpJp.getName()));
                }
              });
              if (matchingDependencyInMPS == null) {
                if (Objects.equals(dependencyInCloud.getConcept().getLongName(), (CONCEPTS.DevkitDependency$Ns.getLanguage().getQualifiedName() + "." + CONCEPTS.DevkitDependency$Ns.getName()))) {
                  SRepository repo = model.getRepository();
                  String devKitUUID = dependencyInCloud.getPropertyValue(PROPS.uuid$lpJp.getName());
                  DevKit devKit = ((DevKit) repo.getModule(ModuleId.regular(UUID.fromString(devKitUUID))));
                  SModuleReference devKitModuleReference = devKit.getModuleReference();
                  SModelUtils.addDevKit(mpsModelNode.getElement(), devKitModuleReference);
                } else if (Objects.equals(dependencyInCloud.getConcept().getLongName(), (CONCEPTS.SingleLanguageDependency$_9.getLanguage().getQualifiedName() + "." + CONCEPTS.SingleLanguageDependency$_9.getName()))) {
                  SRepository repo = model.getRepository();
                  String languageUUID = dependencyInCloud.getPropertyValue(PROPS.uuid$lpJp.getName());
                  Language language = ((Language) repo.getModule(ModuleId.regular(UUID.fromString(languageUUID))));
                  SLanguage sLanguage = MetaAdapterFactory.getLanguage(language.getModuleReference());
                  SModelUtils.addLanguageImport(mpsModelNode.getElement(), sLanguage, Integer.parseInt(dependencyInCloud.getPropertyValue(PROPS.version$ApUL.getName())));
                } else {
                  throw new UnsupportedOperationException("Unknown dependency with concept " + dependencyInCloud.getConcept().getLongName());
                }
              } else {
                // We use this method to avoid using set, if it is not strictly necessary, which may be not supported
                INodeUtils.copyPropertyIfNecessary(matchingDependencyInMPS, dependencyInCloud, PROPS.name$lpYq);
                INodeUtils.copyPropertyIfNecessary(matchingDependencyInMPS, dependencyInCloud, PROPS.version$ApUL);
              }
            }

            // For each import not in Cloud remove it
            for (INode dependencyInMPS : ListSequence.fromList(dependenciesInMPS)) {
              if (dependencyInMPS instanceof DevKitDependencyAsNode) {
                INode matchingDependencyInCloud = null;
                for (INode dependencyInCloud : Sequence.fromIterable(dependenciesInCloud)) {
                  if (Objects.equals(dependencyInMPS.getPropertyValue(PROPS.uuid$lpJp.getName()), dependencyInCloud.getPropertyValue(PROPS.uuid$lpJp.getName()))) {
                    matchingDependencyInCloud = dependencyInCloud;
                  }
                }
                if (matchingDependencyInCloud == null) {
                  DefaultSModelDescriptor dsmd = ((DefaultSModelDescriptor) mpsModelNode.getElement());
                  DevKitDependencyAsNode depToRemove = (DevKitDependencyAsNode) dependencyInMPS;
                  SModuleReference moduleReference = ((SModuleReference) ReflectionUtil.readField(SingleLanguageDependencyAsNode.class, depToRemove, "moduleReference"));
                  SLanguage languageToRemove = MetaAdapterFactory.getLanguage(moduleReference);
                  dsmd.deleteLanguageId(languageToRemove);
                }
              } else if (dependencyInMPS instanceof SingleLanguageDependencyAsNode) {
                INode matchingDependencyInCloud = null;
                for (INode dependencyInCloud : Sequence.fromIterable(dependenciesInCloud)) {
                  if (Objects.equals(dependencyInMPS.getPropertyValue(PROPS.uuid$lpJp.getName()), dependencyInCloud.getPropertyValue(PROPS.uuid$lpJp.getName()))) {
                    matchingDependencyInCloud = dependencyInCloud;
                  }
                }
                if (matchingDependencyInCloud == null) {
                  DefaultSModelDescriptor dsmd = ((DefaultSModelDescriptor) mpsModelNode.getElement());
                  SingleLanguageDependencyAsNode depToRemove = (SingleLanguageDependencyAsNode) dependencyInMPS;
                  SModuleReference moduleReference = depToRemove.getModuleReference();
                  SLanguage languageToRemove = MetaAdapterFactory.getLanguage(moduleReference);
                  dsmd.deleteLanguageId(languageToRemove);
                }
              } else {
                throw new RuntimeException("Unknown dependency type: " + dependencyInMPS.getClass().getName());
              }

            }
          }
        });
        return Unit.INSTANCE;
      }
    });
  }

  public void syncModelImportsFromMPS() {
    new PArea(getBranch()).executeWrite(new Function0<Unit>() {
      public Unit invoke() {
        // First get the dependencies in MPS. Model imports do not include implicit ones
        SModelAsNode mpsModelNode = SModelAsNode.wrap(model);
        List<ModelImportAsNode> dependenciesInMPS = IterableOfINodeUtils.toCastedList(mpsModelNode.getChildren(LINKS.modelImports$8DOI.getName()));

        //  Then get the dependencies in the cloud
        IBranch branch = getBranch();
        INode cloudModelNode = new PNodeAdapter(modelNodeId, branch);
        Iterable<INode> dependenciesInCloud = cloudModelNode.getChildren(LINKS.modelImports$8DOI.getName());

        // For each import in MPS, add it if not present in the cloud, or otherwise ensure all properties are the same
        for (ModelImportAsNode dependencyInMPS : ListSequence.fromList(dependenciesInMPS)) {
          INode modelImportedInMps = dependencyInMPS.getReferenceTarget(LINKS.model$GJHn.getName());
          if (modelImportedInMps != null) {
            final String modelIDimportedInMPS = modelImportedInMps.getPropertyValue(PROPS.id$lDUo.getName());
            INode matchingDependencyInCloud = Sequence.fromIterable(dependenciesInCloud).findFirst(new IWhereFilter<INode>() {
              public boolean accept(INode dependencyInCloud) {
                INode modelImportedInCloud = dependencyInCloud.getReferenceTarget(LINKS.model$GJHn.getName());
                if (modelImportedInCloud == null) {
                  return false;
                }
                String modelIDimportedInCloud = modelImportedInCloud.getPropertyValue(PROPS.id$lDUo.getName());
                return Objects.equals(modelIDimportedInMPS, modelIDimportedInCloud);
              }
            });
            if (matchingDependencyInCloud == null) {
              INodeUtils.replicateChild(cloudModelNode, LINKS.modelImports$8DOI.getName(), dependencyInMPS);
            } else {
              // no properties to set here
            }
          }
        }

        // For each import not in MPS, remove it
        for (INode dependencyInCloud : Sequence.fromIterable(dependenciesInCloud)) {
          INode modelImportedInCloud = dependencyInCloud.getReferenceTarget(LINKS.model$GJHn.getName());
          if (modelImportedInCloud != null) {
            final String modelIDimportedInCloud = modelImportedInCloud.getPropertyValue(PROPS.id$lDUo.getName());
            INode matchingDependencyInMPS = Sequence.fromIterable(dependenciesInCloud).findFirst(new IWhereFilter<INode>() {
              public boolean accept(INode dependencyInMPS) {
                INode modelImportedInMPS = dependencyInMPS.getReferenceTarget(LINKS.model$GJHn.getName());
                if (modelImportedInMPS == null) {
                  return false;
                }
                String modelIDimportedInMPS = modelImportedInMPS.getPropertyValue(PROPS.id$lDUo.getName());
                return Objects.equals(modelIDimportedInCloud, modelIDimportedInMPS);
              }
            });
            if (matchingDependencyInMPS == null) {
              cloudModelNode.removeChild(dependencyInCloud);
            }
          }
        }
        return Unit.INSTANCE;
      }
    });
  }

  private static boolean isNullModel(INode model) {
    if (model == null) {
      return true;
    }
    if (model instanceof SModelAsNode) {
      SModelAsNode sModelAsNode = ((SModelAsNode) model);
      if (sModelAsNode.getElement() == null) {
        return true;
      }
    }
    return false;
  }

  public static void syncModelImportsToMPS(final ITree tree, final SModel model, final long modelNodeId, final ICloudRepository cloudRepository) {
    new PArea(cloudRepository.getBranch()).executeRead(new Function0<Unit>() {
      public Unit invoke() {
        ModelAccess.runInWriteActionIfNeeded(model, new Runnable() {
          @Override
          public void run() {
            // First get the dependencies in MPS
            SModelAsNode mpsModelNode = SModelAsNode.wrap(model);
            List<ModelImportAsNode> dependenciesInMPS = IterableOfINodeUtils.toCastedList(mpsModelNode.getChildren(LINKS.modelImports$8DOI.getName()));

            //  Then get the dependencies in the cloud
            IBranch branch = cloudRepository.getBranch();
            INode cloudModelNode = new PNodeAdapter(modelNodeId, branch);
            Iterable<INode> dependenciesInCloud = cloudModelNode.getChildren(LINKS.modelImports$8DOI.getName());

            // For each import in Cloud add it if not present in MPS or otherwise ensure all properties are the same
            for (INode dependencyInCloud : Sequence.fromIterable(dependenciesInCloud)) {
              INode modelImportedInCloud = dependencyInCloud.getReferenceTarget(LINKS.model$GJHn.getName());
              if (!(isNullModel(modelImportedInCloud))) {
                final String modelIDimportedInCloud = modelImportedInCloud.getPropertyValue(PROPS.id$lDUo.getName());
                INode matchingDependencyInMps = ListSequence.fromList(dependenciesInMPS).findFirst(new IWhereFilter<ModelImportAsNode>() {
                  public boolean accept(ModelImportAsNode dependencyInMPS) {
                    INode modelImportedInMPS = dependencyInMPS.getReferenceTarget(LINKS.model$GJHn.getName());
                    if (modelImportedInMPS == null) {
                      return false;
                    }
                    String modelIDimportedInMPS = modelImportedInMPS.getPropertyValue(PROPS.id$lDUo.getName());
                    return Objects.equals(modelIDimportedInCloud, modelIDimportedInMPS);
                  }
                });
                if (matchingDependencyInMps == null) {
                  // Model imports have to be added to the underlying SModel using MPS APIs, not the generic reflective INode APIs

                  // First we build the Module Reference
                  INode moduleContainingModelImportedInCloud = modelImportedInCloud.getParent();
                  String nameOfModuleContainingModelImportedInCloud = moduleContainingModelImportedInCloud.getPropertyValue(PROPS.name$MnvL.getName());
                  String idOfModuleContainingModelImportedInCloud = moduleContainingModelImportedInCloud.getPropertyValue(PROPS.id$7MjP.getName());
                  SModuleReference moduleRef = new ModuleReference(nameOfModuleContainingModelImportedInCloud, ModuleId.fromString(idOfModuleContainingModelImportedInCloud));

                  // Then we build the ModelReference
                  SModelId modelID = jetbrains.mps.smodel.SModelId.fromString(modelIDimportedInCloud);
                  String modelName = modelImportedInCloud.getPropertyValue(PROPS.name$MnvL.getName());
                  SModelReference refToModelToImport = new SModelReference(moduleRef, modelID, modelName);

                  // We can now add the import
                  ModelImports modelImports = new ModelImports(model);
                  modelImports.addModelImport(refToModelToImport);
                } else {
                  // no properties to set here
                }
              }
            }

            // For each import not in Cloud remove it
            for (ModelImportAsNode dependencyInMPS : ListSequence.fromList(dependenciesInMPS)) {
              INode modelImportedInMPS = dependencyInMPS.getReferenceTarget(LINKS.model$GJHn.getName());
              if (modelImportedInMPS != null) {
                final String modelIDimportedInMPS = modelImportedInMPS.getPropertyValue(PROPS.id$lDUo.getName());
                INode matchingDependencyInCloud = Sequence.fromIterable(dependenciesInCloud).findFirst(new IWhereFilter<INode>() {
                  public boolean accept(INode dependencyInCloud) {
                    INode modelImportedInCloud = dependencyInCloud.getReferenceTarget(LINKS.model$GJHn.getName());
                    if (isNullModel(modelImportedInCloud)) {
                      return false;
                    }
                    String modelIDimportedInCloud = modelImportedInCloud.getPropertyValue(PROPS.id$lDUo.getName());
                    return Objects.equals(modelIDimportedInMPS, modelIDimportedInCloud);
                  }
                });
                if (matchingDependencyInCloud == null) {
                  SModelDescriptorStub dsmd = ((SModelDescriptorStub) mpsModelNode.getElement());
                  ModelImportAsNode depToRemove = dependencyInMPS;
                  org.jetbrains.mps.openapi.model.SModelReference modelReferenceToRemove = depToRemove.getElement().getReference();
                  dsmd.deleteModelImport(modelReferenceToRemove);
                }
              }
            }
          }
        });
        return Unit.INSTANCE;
      }
    });
  }

  private static final class LINKS {
    /*package*/ static final SContainmentLink usedLanguages$QK4E = MetaAdapterFactory.getContainmentLink(0xa7577d1d4e5431dL, 0x98b1fae38f9aee80L, 0x69652614fd1c50cL, 0x4aaf28cf2092e98eL, "usedLanguages");
    /*package*/ static final SContainmentLink modelImports$8DOI = MetaAdapterFactory.getContainmentLink(0xa7577d1d4e5431dL, 0x98b1fae38f9aee80L, 0x69652614fd1c50cL, 0x58dbe6e4d4f32eb8L, "modelImports");
    /*package*/ static final SReferenceLink model$GJHn = MetaAdapterFactory.getReferenceLink(0xa7577d1d4e5431dL, 0x98b1fae38f9aee80L, 0x58dbe6e4d4f332a3L, 0x58dbe6e4d4f332a4L, "model");
  }

  private static final class PROPS {
    /*package*/ static final SProperty uuid$lpJp = MetaAdapterFactory.getProperty(0xa7577d1d4e5431dL, 0x98b1fae38f9aee80L, 0x7c527144386aca0fL, 0x7c527144386aca12L, "uuid");
    /*package*/ static final SProperty name$lpYq = MetaAdapterFactory.getProperty(0xa7577d1d4e5431dL, 0x98b1fae38f9aee80L, 0x7c527144386aca0fL, 0x7c527144386aca13L, "name");
    /*package*/ static final SProperty version$ApUL = MetaAdapterFactory.getProperty(0xa7577d1d4e5431dL, 0x98b1fae38f9aee80L, 0x1e9fde953529917dL, 0x1e9fde9535299183L, "version");
    /*package*/ static final SProperty id$lDUo = MetaAdapterFactory.getProperty(0xa7577d1d4e5431dL, 0x98b1fae38f9aee80L, 0x69652614fd1c50cL, 0x244b85440ee67212L, "id");
    /*package*/ static final SProperty name$MnvL = MetaAdapterFactory.getProperty(0xceab519525ea4f22L, 0x9b92103b95ca8c0cL, 0x110396eaaa4L, 0x110396ec041L, "name");
    /*package*/ static final SProperty id$7MjP = MetaAdapterFactory.getProperty(0xa7577d1d4e5431dL, 0x98b1fae38f9aee80L, 0x69652614fd1c50fL, 0x3aa34013f2a802e0L, "id");
  }

  private static final class CONCEPTS {
    /*package*/ static final SConcept DevkitDependency$Ns = MetaAdapterFactory.getConcept(0xa7577d1d4e5431dL, 0x98b1fae38f9aee80L, 0x7c527144386aca16L, "org.modelix.model.repositoryconcepts.structure.DevkitDependency");
    /*package*/ static final SConcept SingleLanguageDependency$_9 = MetaAdapterFactory.getConcept(0xa7577d1d4e5431dL, 0x98b1fae38f9aee80L, 0x1e9fde953529917dL, "org.modelix.model.repositoryconcepts.structure.SingleLanguageDependency");
  }
}
