package cn.hylstudio.skykoma.plugin.idea.toolwindow.model;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

public class PackageViewNode {

    private final String packageName;
    private final List<VirtualFile> classes;

    public PackageViewNode(String packageName) {
        this.packageName = packageName;
        this.classes = new ArrayList<>();
    }

    public String getPackageName() {
        return packageName;
    }

    public String getSimpleName() {
        int lastDot = packageName.lastIndexOf('.');
        return lastDot >= 0 ? packageName.substring(lastDot + 1) : packageName;
    }

    public List<VirtualFile> getClasses() {
        return classes;
    }

    public void addClass(VirtualFile classFile) {
        classes.add(classFile);
    }

    @Override
    public String toString() {
        return packageName;
    }
}
