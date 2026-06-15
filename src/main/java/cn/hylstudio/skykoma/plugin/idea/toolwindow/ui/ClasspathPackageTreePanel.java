package cn.hylstudio.skykoma.plugin.idea.toolwindow.ui;

import cn.hylstudio.skykoma.plugin.idea.toolwindow.model.PackageViewNode;
import cn.hylstudio.skykoma.plugin.idea.toolwindow.structure.ClasspathPackageTreeStructure;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class ClasspathPackageTreePanel extends JPanel {

    private final Project project;
    private final SearchTextField searchField;
    private final Tree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final JBLabel statusLabel;

    public ClasspathPackageTreePanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        JPanel topPanel = new JPanel(new BorderLayout());
        searchField = new SearchTextField();
        searchField.getTextEditor().getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterTree(); }
            @Override public void removeUpdate(DocumentEvent e) { filterTree(); }
            @Override public void changedUpdate(DocumentEvent e) { filterTree(); }
        });
        topPanel.add(searchField, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        rootNode = new DefaultMutableTreeNode("Packages");
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
                            if (userObject instanceof VirtualFile) {
                                FileEditorManager.getInstance(project).openFile((VirtualFile) userObject, true);
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

    public void refresh(List<String> classpathEntries) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ClasspathPackageTreeStructure structure = new ClasspathPackageTreeStructure();
            structure.rebuild(classpathEntries);

            DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode("Packages");
            for (PackageViewNode pkgNode : structure.getPackageNodes()) {
                String packageName = pkgNode.getPackageName();
                DefaultMutableTreeNode parent = getOrCreatePackageNode(newRoot, packageName);
                for (VirtualFile classFile : pkgNode.getClasses()) {
                    parent.add(new DefaultMutableTreeNode(classFile));
                }
            }

            ApplicationManager.getApplication().invokeLater(() -> {
                rootNode.removeAllChildren();
                for (int i = 0; i < newRoot.getChildCount(); i++) {
                    rootNode.add((DefaultMutableTreeNode) newRoot.getChildAt(i));
                }
                treeModel.reload();
                statusLabel.setText(classpathEntries.size() + " entries, " + structure.getPackageNodes().size() + " packages");
            });
        });
    }

    private void filterTree() {
        String filter = searchField.getText().trim().toLowerCase();
        if (filter.isEmpty()) {
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
            return;
        }
        collapseAll(tree, new TreePath(rootNode));
        expandMatching(tree, new TreePath(rootNode), filter);
    }

    private void collapseAll(JTree tree, TreePath parent) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) parent.getLastPathComponent();
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            TreePath childPath = parent.pathByAddingChild(child);
            collapseAll(tree, childPath);
        }
        tree.collapsePath(parent);
    }

    private boolean expandMatching(JTree tree, TreePath parent, String filter) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) parent.getLastPathComponent();
        boolean anyMatch = false;
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            TreePath childPath = parent.pathByAddingChild(child);
            Object uo = child.getUserObject();
            String text = "";
            if (uo instanceof PackageViewNode) {
                text = ((PackageViewNode) uo).getPackageName().toLowerCase();
            } else if (uo instanceof VirtualFile) {
                text = ((VirtualFile) uo).getNameWithoutExtension().toLowerCase();
            }
            boolean childMatch = text.contains(filter) || expandMatching(tree, childPath, filter);
            if (childMatch) {
                tree.expandPath(parent);
                anyMatch = true;
            }
        }
        return anyMatch;
    }

    private DefaultMutableTreeNode getOrCreatePackageNode(DefaultMutableTreeNode root, String packageName) {
        String[] parts = packageName.split("\\.");
        DefaultMutableTreeNode current = root;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            StringBuilder prefix = new StringBuilder();
            for (int j = 0; j <= i; j++) {
                if (j > 0) prefix.append(".");
                prefix.append(parts[j]);
            }
            DefaultMutableTreeNode found = null;
            for (int c = 0; c < current.getChildCount(); c++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) current.getChildAt(c);
                Object uo = child.getUserObject();
                if (uo instanceof PackageViewNode) {
                    PackageViewNode pvn = (PackageViewNode) uo;
                    if (pvn.getPackageName().equals(prefix.toString())) {
                        found = child;
                        break;
                    }
                }
            }
            if (found != null) {
                current = found;
            } else {
                PackageViewNode newNode = new PackageViewNode(prefix.toString());
                DefaultMutableTreeNode newTreeNode = new DefaultMutableTreeNode(newNode);
                current.add(newTreeNode);
                current = newTreeNode;
            }
        }
        return current;
    }
}
