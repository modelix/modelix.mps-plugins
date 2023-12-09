package org.modelix.model.mpsplugin.plugin;

/*Generated by MPS */

import jetbrains.mps.workbench.action.BaseAction;
import javax.swing.Icon;
import com.intellij.openapi.actionSystem.AnActionEvent;
import java.util.Map;
import org.modelix.model.mpsplugin.history.TreeNodeClassification;
import jetbrains.mps.ide.actions.MPSCommonDataKeys;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import javax.swing.tree.TreeNode;
import org.modelix.model.mpsplugin.history.CloudNodeTreeNode;
import org.modelix.model.mpsplugin.CloudRepository;
import org.modelix.model.mpsplugin.history.CloudNodeTreeNodeBinding;
import jetbrains.mps.ide.project.ProjectHelper;
import org.modelix.model.mpsplugin.ModuleCheckout;
import org.modelix.model.api.PNodeAdapter;

public class CheckoutModule_Action extends BaseAction {
  private static final Icon ICON = null;

  public CheckoutModule_Action() {
    super("Checkout", "", ICON);
    this.setIsAlwaysVisible(false);
    this.setExecuteOutsideCommand(true);
  }
  @Override
  public boolean isDumbAware() {
    return true;
  }
  @Override
  public boolean isApplicable(AnActionEvent event, final Map<String, Object> _params) {
    // TODO verify it does not exist a module with such name
    return TreeNodeClassification.isModuleNode(event.getData(MPSCommonDataKeys.TREE_NODE));
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
    CloudNodeTreeNode nodeTreeNode = (CloudNodeTreeNode) event.getData(MPSCommonDataKeys.TREE_NODE);
    CloudRepository treeInRepository = CloudNodeTreeNodeBinding.getTreeInRepository(nodeTreeNode);
    jetbrains.mps.project.Project mpsProject = ProjectHelper.toMPSProject(event.getData(CommonDataKeys.PROJECT));
    new ModuleCheckout(mpsProject, treeInRepository).checkoutCloudModule(((PNodeAdapter) nodeTreeNode.getNode()));
  }
}
