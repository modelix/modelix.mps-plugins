package org.modelix.model.mpsplugin.history;

/*Generated by MPS */

import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import javax.swing.JTable;
import jetbrains.mps.baseLanguage.closures.runtime._FunctionTypes;
import org.modelix.model.lazy.CLVersion;
import java.util.List;
import jetbrains.mps.internal.collections.runtime.ListSequence;
import java.util.ArrayList;
import org.modelix.model.mpsplugin.ModelServerConnection;
import org.modelix.model.lazy.RepositoryId;
import javax.swing.JButton;
import java.awt.BorderLayout;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import org.modelix.model.client.ActiveBranch;
import java.util.UUID;
import org.modelix.model.api.IBranch;
import kotlin.jvm.functions.Function1;
import org.modelix.model.api.IWriteTransaction;
import kotlin.Unit;
import org.modelix.model.operations.OTWriteTransactionKt;
import org.modelix.model.operations.RevertToOp;
import org.modelix.model.lazy.KVEntryReference;
import org.modelix.model.persistent.CPVersion;
import com.intellij.openapi.application.ApplicationManager;
import jetbrains.mps.ide.ThreadUtils;
import org.modelix.model.lazy.IDeserializingKeyValueStore;
import org.modelix.model.LinearHistory;
import jetbrains.mps.internal.collections.runtime.IterableUtils;
import jetbrains.mps.internal.collections.runtime.Sequence;
import org.modelix.model.operations.IOperation;
import jetbrains.mps.internal.collections.runtime.ISelector;
import java.util.Vector;

public class HistoryView extends JPanel {
  private DefaultTableModel tableModel;
  private JTable table;
  private _FunctionTypes._return_P0_E0<? extends CLVersion> versionGetter;
  private List<CLVersion> versions = ListSequence.fromList(new ArrayList<CLVersion>());
  private ModelServerConnection modelServer;
  private RepositoryId repositoryId;
  private String previousBranchName;
  private JButton resetButton;
  private JButton revertButton;

  public HistoryView() {
    setLayout(new BorderLayout());

    tableModel = new DefaultTableModel();
    tableModel.addColumn("ID");
    tableModel.addColumn("Author");
    tableModel.addColumn("Time");
    tableModel.addColumn("Operations");
    tableModel.addColumn("Hash");

    table = new JTable(tableModel);
    JScrollPane scrollPane = new JScrollPane(table);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    add(scrollPane, BorderLayout.CENTER);

    JPanel buttonPanel = new JPanel(new FlowLayout());
    JButton loadButton = new JButton("Load Selected Version");
    loadButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        loadSelectedVersion();
      }
    });
    buttonPanel.add(loadButton);

    resetButton = new JButton("Reset to ...");
    resetButton.setEnabled(false);
    resetButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        restoreBranch();
      }
    });
    buttonPanel.add(resetButton);

    revertButton = new JButton("Revert to Selected Version");
    revertButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        revertToSelectedVersion();
      }
    });
    buttonPanel.add(revertButton);

    add(buttonPanel, BorderLayout.SOUTH);
  }

  public void loadSelectedVersion() {
    int index = table.getSelectedRow();
    if (0 <= index && index < ListSequence.fromList(versions).count()) {
      CLVersion version = ListSequence.fromList(versions).getElement(index);
      ActiveBranch branch = modelServer.getActiveBranch(repositoryId);
      String branchName = "history" + UUID.randomUUID();
      String branchKey = repositoryId.getBranchKey(branchName);
      modelServer.getClient().put(branchKey, version.getHash());
      branch.switchBranch(branchName);
      resetButton.setEnabled(true);
    }
  }

  public void revertToSelectedVersion() {
    int index = table.getSelectedRow();
    if (0 <= index && index < ListSequence.fromList(versions).count()) {
      ActiveBranch activeBranch = modelServer.getActiveBranch(repositoryId);
      final CLVersion versionToRevertTo = ListSequence.fromList(versions).getElement(index);
      final CLVersion latestKnownVersion = activeBranch.getVersion();
      IBranch branch = activeBranch.getBranch();
      branch.runWriteT(new Function1<IWriteTransaction, Unit>() {
        public Unit invoke(IWriteTransaction t) {
          OTWriteTransactionKt.applyOperation(t, new RevertToOp(new KVEntryReference<CPVersion>(latestKnownVersion.getData()), new KVEntryReference<CPVersion>(versionToRevertTo.getData())));
          return Unit.INSTANCE;
        }
      });
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          refreshHistory();
        }
      });
    }
  }

  public void restoreBranch() {
    modelServer.getActiveBranch(repositoryId).switchBranch(previousBranchName);
    resetButton.setEnabled(false);
  }

  public void refreshHistory() {
    loadHistory(modelServer, repositoryId, versionGetter);
  }

  public void loadHistory(ModelServerConnection modelServer, RepositoryId repositoryId, final _FunctionTypes._return_P0_E0<? extends CLVersion> headVersion) {
    this.versionGetter = headVersion;
    this.modelServer = modelServer;
    this.repositoryId = repositoryId;
    this.previousBranchName = modelServer.getActiveBranch(repositoryId).getBranchName();
    resetButton.setText("Reset to " + previousBranchName);
    ThreadUtils.runInUIThreadAndWait(new Runnable() {
      public void run() {
        while (tableModel.getRowCount() > 0) {
          tableModel.removeRow(0);
        }
        ListSequence.fromList(versions).clear();
      }
    });
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        CLVersion version = headVersion.invoke();
        while (version != null) {
          createTableRow(version);
          if (version.isMerge()) {
            IDeserializingKeyValueStore store = version.getStore();
            for (CLVersion v : ListSequence.fromList(new LinearHistory(version.getBaseVersion().getHash()).load(new CLVersion(version.getData().getMergedVersion1().getValue(store), store), new CLVersion(version.getData().getMergedVersion2().getValue(store), store)))) {
              createTableRow(v);
            }
          }
          if (ListSequence.fromList(versions).count() >= 500) {
            break;
          }
          version = version.getBaseVersion();
        }
      }
    });
  }
  private void createTableRow(final CLVersion version) {
    ThreadUtils.runInUIThreadAndWait(new Runnable() {
      public void run() {
        String opsDescription;
        if (version.isMerge()) {
          opsDescription = "merge " + version.getMergedVersion1().getId() + " + " + version.getMergedVersion2().getId() + " (base " + version.getBaseVersion() + ")";
        } else {
          opsDescription = "(" + version.getNumberOfOperations() + ") " + ((version.operationsInlined() ? IterableUtils.join(Sequence.fromIterable(((Iterable<IOperation>) version.getOperations())).select(new ISelector<IOperation, String>() {
            public String select(IOperation it) {
              return it.toString();
            }
          }), " # ") : "..."));
        }
        tableModel.addRow(new Vector<Object>(ListSequence.fromListAndArray(new ArrayList<Object>(), Long.toHexString(version.getId()), version.getAuthor(), version.getTime(), opsDescription, version.getHash())));
        ListSequence.fromList(versions).addElement(version);
      }
    });
  }
}
