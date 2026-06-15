package cn.hylstudio.skykoma.plugin.idea.toolwindow.ui;

import cn.hylstudio.skykoma.plugin.idea.toolwindow.structure.ClassStructureTreeStructure;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ClassSearchPanel extends JPanel {

    public static class ClassEntry {
        private final String className;
        private final String fullName;
        private final VirtualFile vFile;

        public ClassEntry(String className, String fullName, VirtualFile vFile) {
            this.className = className;
            this.fullName = fullName;
            this.vFile = vFile;
        }

        public String getClassName() { return className; }
        public String getFullName() { return fullName; }
        public VirtualFile getVFile() { return vFile; }

        @Override
        public String toString() { return fullName; }
    }

    private final Project project;
    private final SearchTextField searchField;
    private final JBList<ClassEntry> classList;
    private final CollectionListModel<ClassEntry> listModel;
    private final Tree structureTree;
    private final DefaultTreeModel structureTreeModel;
    private final DefaultMutableTreeNode structureRoot;
    private final ClassStructureTreeStructure structureTreeStructure;
    private final JBLabel statusLabel;
    private List<ClassEntry> allClasses;

    public ClassSearchPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.allClasses = new ArrayList<>();

        JPanel topPanel = new JPanel(new BorderLayout());
        searchField = new SearchTextField();
        searchField.getTextEditor().getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterClasses(); }
            @Override public void removeUpdate(DocumentEvent e) { filterClasses(); }
            @Override public void changedUpdate(DocumentEvent e) { filterClasses(); }
        });
        topPanel.add(searchField, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        JBSplitter splitter = new JBSplitter(false, 0.4f);

        listModel = new CollectionListModel<>();
        classList = new JBList<>(listModel);
        classList.setEmptyText("No classes loaded. Click Refresh to scan classpath.");
        new ListSpeedSearch<>(classList);

        classList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    ClassEntry entry = classList.getSelectedValue();
                    if (entry != null) {
                        updateStructureView(entry.getVFile());
                    }
                }
            }
        });

        classList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    ClassEntry entry = classList.getSelectedValue();
                    if (entry != null && entry.getVFile() != null) {
                        FileEditorManager.getInstance(project).openFile(entry.getVFile(), true);
                    }
                }
            }
        });

        JBScrollPane listScrollPane = new JBScrollPane(classList);
        splitter.setFirstComponent(listScrollPane);

        structureTreeStructure = new ClassStructureTreeStructure();
        structureRoot = new DefaultMutableTreeNode("Structure");
        structureTreeModel = new DefaultTreeModel(structureRoot);
        structureTree = new Tree(structureTreeModel);
        structureTree.setCellRenderer(new ClasspathTreeCellRenderer());
        structureTree.setRootVisible(false);
        structureTree.setShowsRootHandles(true);

        structureTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    ClassEntry entry = classList.getSelectedValue();
                    if (entry != null && entry.getVFile() != null) {
                        FileEditorManager.getInstance(project).openFile(entry.getVFile(), true);
                    }
                }
            }
        });

        JBScrollPane structureScrollPane = new JBScrollPane(structureTree);
        splitter.setSecondComponent(structureScrollPane);

        add(splitter, BorderLayout.CENTER);

        statusLabel = new JBLabel("Click Refresh to load classpath");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        add(statusLabel, BorderLayout.SOUTH);
    }

    public void setStatus(String text) {
        ApplicationManager.getApplication().invokeLater(() -> statusLabel.setText(text));
    }

    public void refresh(List<String> classpathEntries) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<ClassEntry> entries = new ArrayList<>();
            for (String entry : classpathEntries) {
                File file = new File(entry);
                if (!file.exists()) {
                    continue;
                }
                VirtualFile root;
                if (file.isDirectory()) {
                    root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
                } else if (file.getName().endsWith(".jar") || file.getName().endsWith(".zip")) {
                    VirtualFile localVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
                    if (localVf != null) {
                        root = JarFileSystem.getInstance().getRootByLocal(localVf);
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
                if (root != null) {
                    collectClasses(root, "", entries);
                }
            }
            entries.sort((a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName()));
            allClasses = entries;

            ApplicationManager.getApplication().invokeLater(() -> {
                filterClasses();
                statusLabel.setText(entries.size() + " classes loaded");
            });
        });
    }

    private void collectClasses(VirtualFile dir, String parentPackage, List<ClassEntry> entries) {
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                String childPackage = parentPackage.isEmpty() ? child.getName() : parentPackage + "." + child.getName();
                collectClasses(child, childPackage, entries);
            } else if ("class".equals(child.getExtension())) {
                String className = child.getNameWithoutExtension();
                String fullName = parentPackage.isEmpty() ? className : parentPackage + "." + className;
                entries.add(new ClassEntry(className, fullName, child));
            }
        }
    }

    private void filterClasses() {
        String filter = searchField.getText().trim().toLowerCase();
        List<ClassEntry> filtered;
        if (filter.isEmpty()) {
            filtered = allClasses;
        } else {
            filtered = allClasses.stream()
                    .filter(e -> e.getFullName().toLowerCase().contains(filter))
                    .collect(Collectors.toList());
        }
        listModel.removeAll();
        listModel.add(filtered);
    }

    private void updateStructureView(VirtualFile classFile) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            structureTreeStructure.setClass(project, classFile);
            DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode("Structure");
            buildStructureNodes(newRoot, structureTreeStructure.getRootElement(), structureTreeStructure);

            ApplicationManager.getApplication().invokeLater(() -> {
                structureRoot.removeAllChildren();
                for (int i = 0; i < newRoot.getChildCount(); i++) {
                    structureRoot.add((DefaultMutableTreeNode) newRoot.getChildAt(i));
                }
                structureTreeModel.reload();
                for (int i = 0; i < structureTree.getRowCount(); i++) {
                    structureTree.expandRow(i);
                }
            });
        });
    }

    private void buildStructureNodes(DefaultMutableTreeNode parent, Object element, ClassStructureTreeStructure structure) {
        Object[] children = structure.getChildElements(element);
        for (Object child : children) {
            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);
            parent.add(childNode);
            if (!structure.isAlwaysLeaf(child)) {
                buildStructureNodes(childNode, child, structure);
            }
        }
    }
}
