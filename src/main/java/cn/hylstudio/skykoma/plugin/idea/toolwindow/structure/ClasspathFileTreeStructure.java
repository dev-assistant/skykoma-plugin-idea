package cn.hylstudio.skykoma.plugin.idea.toolwindow.structure;

import cn.hylstudio.skykoma.plugin.idea.toolwindow.model.FileViewNode;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ClasspathFileTreeStructure {

    private static final String CAT_SYSTEM = "System ClassPath";
    private static final String CAT_PLUGIN = "Plugin ClassPath";
    private static final String CAT_EXTRA = "Extra ClassPath";

    private List<FileViewNode> allNodes = new ArrayList<>();

    public void rebuild(List<String> systemCp, List<String> pluginCp, List<String> extraCp) {
        List<FileViewNode> nodes = new ArrayList<>();

        addCategory(nodes, CAT_SYSTEM, systemCp);
        addCategory(nodes, CAT_PLUGIN, pluginCp);
        addCategory(nodes, CAT_EXTRA, extraCp);

        this.allNodes = nodes;
    }

    private void addCategory(List<FileViewNode> nodes, String catLabel, List<String> entries) {
        if (entries.isEmpty()) return;
        nodes.add(new FileViewNode(FileViewNode.Type.CATEGORY, catLabel, null, null, catLabel));
        for (String entry : entries) {
            File f = new File(entry);
            if (!f.exists()) continue;
            VirtualFile vf = resolveVirtualFile(f);
            FileViewNode.Type type = f.isDirectory() ? FileViewNode.Type.DIRECTORY : FileViewNode.Type.JAR;
            nodes.add(new FileViewNode(type, f.getName(), vf, f, catLabel));
        }
    }

    private static VirtualFile resolveVirtualFile(File file) {
        if (!file.exists()) return null;
        if (file.isDirectory()) {
            return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        }
        if (file.getName().endsWith(".jar") || file.getName().endsWith(".zip")) {
            VirtualFile localVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
            if (localVf != null) {
                VirtualFile jarRoot = JarFileSystem.getInstance().getRootByLocal(localVf);
                if (jarRoot != null) return jarRoot;
            }
        }
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    }

    public Object getRootElement() {
        return "Classpath";
    }

    public Object[] getChildElements(Object element) {
        if ("Classpath".equals(element)) {
            List<FileViewNode> cats = new ArrayList<>();
            for (FileViewNode node : allNodes) {
                if (node.getType() == FileViewNode.Type.CATEGORY) cats.add(node);
            }
            return cats.toArray();
        }
        if (element instanceof FileViewNode) {
            FileViewNode node = (FileViewNode) element;
            if (node.getType() == FileViewNode.Type.CATEGORY) {
                String cat = node.getCategoryLabel();
                List<FileViewNode> children = new ArrayList<>();
                for (FileViewNode n : allNodes) {
                    if (n.getType() != FileViewNode.Type.CATEGORY && cat.equals(n.getCategoryLabel())) {
                        children.add(n);
                    }
                }
                return children.toArray();
            }
            if (node.getType() == FileViewNode.Type.JAR || node.getType() == FileViewNode.Type.DIRECTORY) {
                VirtualFile vf = node.getVFile();
                if (vf != null) return getPackageChildren(vf, node.getSourceFile(), node.getCategoryLabel());
            }
            if (node.getType() == FileViewNode.Type.PACKAGE) {
                VirtualFile vf = node.getVFile();
                if (vf != null && vf.isDirectory()) {
                    List<FileViewNode> children = new ArrayList<>();
                    for (VirtualFile child : vf.getChildren()) {
                        if (!child.isDirectory() && "class".equals(child.getExtension())) {
                            children.add(new FileViewNode(FileViewNode.Type.CLASS, child.getNameWithoutExtension(),
                                    child, node.getSourceFile(), node.getCategoryLabel()));
                        } else if (child.isDirectory()) {
                            children.add(new FileViewNode(FileViewNode.Type.PACKAGE, child.getName(),
                                    child, node.getSourceFile(), node.getCategoryLabel()));
                        }
                    }
                    return children.toArray();
                }
            }
        }
        return new Object[0];
    }

    private Object[] getPackageChildren(VirtualFile root, File sourceFile, String categoryLabel) {
        List<FileViewNode> children = new ArrayList<>();
        if (root.isDirectory()) {
            for (VirtualFile child : root.getChildren()) {
                if (child.isDirectory()) {
                    children.add(new FileViewNode(FileViewNode.Type.PACKAGE, child.getName(), child,
                            sourceFile, categoryLabel));
                } else if ("class".equals(child.getExtension())) {
                    children.add(new FileViewNode(FileViewNode.Type.CLASS, child.getNameWithoutExtension(), child,
                            sourceFile, categoryLabel));
                }
            }
        }
        return children.toArray();
    }

    public boolean isAlwaysLeaf(Object element) {
        if (element instanceof FileViewNode) {
            return ((FileViewNode) element).getType() == FileViewNode.Type.CLASS;
        }
        return false;
    }
}
