package cn.hylstudio.skykoma.plugin.idea.toolwindow.ui;

import cn.hylstudio.skykoma.plugin.idea.toolwindow.model.FileViewNode;
import cn.hylstudio.skykoma.plugin.idea.toolwindow.structure.ClasspathFileTreeStructure;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class ClasspathFileTreePanel extends JPanel {

    private final Project project;
    private final Tree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final JBLabel statusLabel;

    public ClasspathFileTreePanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        rootNode = new DefaultMutableTreeNode("Classpath");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new Tree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new ClasspathTreeCellRenderer());
        new TreeSpeedSearch(tree);

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        Object lastComponent = path.getLastPathComponent();
                        if (lastComponent instanceof DefaultMutableTreeNode) {
                            Object userObject = ((DefaultMutableTreeNode) lastComponent).getUserObject();
                            if (userObject instanceof FileViewNode) {
                                FileViewNode node = (FileViewNode) userObject;
                                if (node.getType() == FileViewNode.Type.CLASS && node.getVFile() != null) {
                                    FileEditorManager.getInstance(project).openFile(node.getVFile(), true);
                                }
                            }
                        }
                    }
                }
            }
        });

        statusLabel = new JBLabel("Click Refresh to load classpath");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        add(new JBScrollPane(tree), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    public void setStatus(String text) {
        ApplicationManager.getApplication().invokeLater(() -> statusLabel.setText(text));
    }

    public void refresh(List<String> systemCp, List<String> pluginCp, List<String> extraCp) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ClasspathFileTreeStructure structure = new ClasspathFileTreeStructure();
            structure.rebuild(systemCp, pluginCp, extraCp);

            DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode("Classpath");
            buildTreeNodes(newRoot, structure.getRootElement(), structure);

            ApplicationManager.getApplication().invokeLater(() -> {
                rootNode.removeAllChildren();
                for (int i = 0; i < newRoot.getChildCount(); i++) {
                    rootNode.add((DefaultMutableTreeNode) newRoot.getChildAt(i));
                }
                treeModel.reload();
                for (int i = 0; i < tree.getRowCount(); i++) {
                    tree.expandRow(i);
                }
                int total = systemCp.size() + pluginCp.size() + extraCp.size();
                statusLabel.setText(total + " entries loaded");
            });
        });
    }

    private void buildTreeNodes(DefaultMutableTreeNode parent, Object element, ClasspathFileTreeStructure structure) {
        Object[] children = structure.getChildElements(element);
        for (Object child : children) {
            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);
            parent.add(childNode);
            if (!structure.isAlwaysLeaf(child)) {
                buildTreeNodes(childNode, child, structure);
            }
        }
    }
}
