package org.modelix.model.mpsplugin.plugin;

/*Generated by MPS */

import jetbrains.mps.workbench.action.BaseAction;
import javax.swing.Icon;
import org.modelix.model.api.INode;
import org.jetbrains.mps.openapi.language.SAbstractConcept;
import org.jetbrains.mps.openapi.language.SContainmentLink;
import jetbrains.mps.workbench.action.ActionAccess;
import com.intellij.openapi.actionSystem.AnActionEvent;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import javax.swing.tree.TreeNode;
import jetbrains.mps.ide.actions.MPSCommonDataKeys;
import org.modelix.model.mpsplugin.history.CloudNodeTreeNode;
import jetbrains.mps.baseLanguage.closures.runtime.Wrappers;
import com.intellij.openapi.ui.Messages;
import org.modelix.model.area.PArea;
import kotlin.jvm.functions.Function0;
import kotlin.Unit;
import org.modelix.model.mpsadapters.mps.SConceptAdapter;
import org.jetbrains.mps.openapi.language.SProperty;
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory;

public class AddChildNode_Action extends BaseAction {
  private static final Icon ICON = null;

  private INode parentNode;
  private SAbstractConcept childConcept;
  private SContainmentLink role;
  public AddChildNode_Action(INode parentNode_par, SAbstractConcept childConcept_par, SContainmentLink role_par) {
    super("Add new child of concept ... in role ...", "", ICON);
    this.parentNode = parentNode_par;
    this.childConcept = childConcept_par;
    this.role = role_par;
    this.setIsAlwaysVisible(false);
    this.setActionAccess(ActionAccess.NONE);
  }
  @Override
  public boolean isDumbAware() {
    return true;
  }
  @Override
  public boolean isApplicable(AnActionEvent event, final Map<String, Object> _params) {
    if (AddChildNode_Action.this.childConcept == null) {
      event.getPresentation().setText("To '" + AddChildNode_Action.this.role.getName());
    } else {
      event.getPresentation().setText("To '" + AddChildNode_Action.this.role.getName() + "' add '" + AddChildNode_Action.this.childConcept.getLanguage().getQualifiedName() + "." + AddChildNode_Action.this.childConcept.getName() + "'");
    }
    return true;
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

    final Wrappers._T<String> name = new Wrappers._T<String>(null);
    if (AddChildNode_Action.this.childConcept.getProperties().contains(PROPS.name$MnvL)) {
      name.value = Messages.showInputDialog(event.getData(CommonDataKeys.PROJECT), "Name", "Add " + AddChildNode_Action.this.childConcept.getName(), null);
      if (isEmptyString(name.value)) {
        return;
      }
    }

    new PArea(nodeTreeNode.getBranch()).executeWrite(new Function0<Unit>() {
      public Unit invoke() {
        INode newModule = AddChildNode_Action.this.parentNode.addNewChild(AddChildNode_Action.this.role.getName(), -1, SConceptAdapter.wrap(AddChildNode_Action.this.childConcept));
        if (isNotEmptyString(name.value)) {
          newModule.setPropertyValue(PROPS.name$MnvL.getName(), name.value);
        }
        return Unit.INSTANCE;
      }
    });
  }
  @NotNull
  public String getActionId() {
    StringBuilder res = new StringBuilder();
    res.append(super.getActionId());
    res.append("#");
    res.append(parentNode_State((INode) this.parentNode));
    res.append("!");
    res.append(childConcept_State((SAbstractConcept) this.childConcept));
    res.append("!");
    res.append(role_State((SContainmentLink) this.role));
    res.append("!");
    return res.toString();
  }
  public static String parentNode_State(INode object) {
    return object.toString();
  }
  public static String childConcept_State(SAbstractConcept object) {
    if (object == null) {
      return "null";
    }
    return object.getName();
  }
  public static String role_State(SContainmentLink object) {
    return object.getName();
  }
  private static boolean isEmptyString(String str) {
    return str == null || str.isEmpty();
  }
  private static boolean isNotEmptyString(String str) {
    return str != null && str.length() > 0;
  }

  private static final class PROPS {
    /*package*/ static final SProperty name$MnvL = MetaAdapterFactory.getProperty(0xceab519525ea4f22L, 0x9b92103b95ca8c0cL, 0x110396eaaa4L, 0x110396ec041L, "name");
  }
}
