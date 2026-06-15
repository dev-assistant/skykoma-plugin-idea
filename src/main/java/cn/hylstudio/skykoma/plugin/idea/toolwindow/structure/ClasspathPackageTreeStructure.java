package cn.hylstudio.skykoma.plugin.idea.toolwindow.structure;

import cn.hylstudio.skykoma.plugin.idea.toolwindow.model.PackageViewNode;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.*;

public class ClasspathPackageTreeStructure {

    private List<PackageViewNode> packageNodes = new ArrayList<>();

    public void rebuild(List<String> classpathEntries) {
        Map<String, PackageViewNode> packageMap = new TreeMap<>();

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
            if (root == null) {
                continue;
            }
            collectClasses(root, "", packageMap);
        }

        this.packageNodes = new ArrayList<>(packageMap.values());
    }

    private void collectClasses(VirtualFile dir, String parentPackage, Map<String, PackageViewNode> packageMap) {
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                String childPackage = parentPackage.isEmpty() ? child.getName() : parentPackage + "." + child.getName();
                collectClasses(child, childPackage, packageMap);
            } else if ("class".equals(child.getExtension())) {
                PackageViewNode node = packageMap.computeIfAbsent(parentPackage, PackageViewNode::new);
                node.addClass(child);
            }
        }
    }

    public List<PackageViewNode> getPackageNodes() {
        return packageNodes;
    }
}
