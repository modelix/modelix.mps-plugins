package org.modelix.model.mpsplugin.plugin;

/*Generated by MPS */

import jetbrains.mps.workbench.action.BaseAction;
import javax.swing.Icon;
import jetbrains.mps.workbench.action.ActionAccess;
import com.intellij.openapi.actionSystem.AnActionEvent;
import java.util.Map;
import org.modelix.model.mpsplugin.history.CloudBindingTreeNode;
import jetbrains.mps.ide.actions.MPSCommonDataKeys;
import org.modelix.model.mpsplugin.Binding;
import org.modelix.model.mpsplugin.ModuleBinding;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import javax.swing.tree.TreeNode;
import org.modelix.model.mpsplugin.ModelServerConnection;
import org.modelix.model.mpsplugin.CloudRepository;

public class Unbind_Action extends BaseAction {
  private static final Icon ICON = null;

  public Unbind_Action() {
    super("Unbind", "", ICON);
    this.setIsAlwaysVisible(false);
    this.setActionAccess(ActionAccess.UNDO_PROJECT);
  }
  @Override
  public boolean isDumbAware() {
    return true;
  }
  @Override
  public boolean isApplicable(AnActionEvent event, final Map<String, Object> _params) {
    CloudBindingTreeNode bindingTreeNode = ((CloudBindingTreeNode) event.getData(MPSCommonDataKeys.TREE_NODE));
    Binding binding = bindingTreeNode.getBinding();
    // Project binding cannot currently be removed
    return binding instanceof ModuleBinding;
  }
  @Override
  public void doUpdate(@NotNull AnActionEvent event, final Map<String, Object> _params) {
    this.setEnabledState(event.getPresentation(), this.isApplicable(event, _params));
  }
  @Override
  protected boolean collectActionData(AnActionEvent event, final Map<String, Object> _params) {
    if (!(super.collectActionData(event, _params))) {
      return false;
    }
    {
      Project p = event.getData(CommonDataKeys.PROJECT);
      if (p == null) {
        return false;
      }
    }
    {
      TreeNode p = event.getData(MPSCommonDataKeys.TREE_NODE);
      if (p == null) {
        return false;
      }
    }
    return true;
  }
  @Override
  public void doExecute(@NotNull final AnActionEvent event, final Map<String, Object> _params) {
    CloudBindingTreeNode bindingTreeNode = ((CloudBindingTreeNode) event.getData(MPSCommonDataKeys.TREE_NODE));
    Binding binding = bindingTreeNode.getBinding();
    // Project binding cannot currently be removed
    ModuleBinding moduleBinding = ((ModuleBinding) binding);
    ModelServerConnection modelServer = bindingTreeNode.getModelServer();
    modelServer.removeBinding(moduleBinding);
    CloudRepository repositoryInModelServer = bindingTreeNode.getRepositoryInModelServer();
    PersistedBindingConfiguration.getInstance(event.getData(CommonDataKeys.PROJECT)).removeBoundModule(repositoryInModelServer, moduleBinding);
  }
}
