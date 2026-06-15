package cn.hylstudio.skykoma.plugin.idea.toolwindow.model;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class FileViewNode {

    public enum Type {
        CATEGORY,
        JAR,
        DIRECTORY,
        PACKAGE,
        CLASS
    }

    private final Type type;
    private final String name;
    private final VirtualFile vFile;
    private final File sourceFile;
    private final String categoryLabel;

    public FileViewNode(Type type, String name, VirtualFile vFile, File sourceFile, String categoryLabel) {
        this.type = type;
        this.name = name;
        this.vFile = vFile;
        this.sourceFile = sourceFile;
        this.categoryLabel = categoryLabel;
    }

    public Type getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public VirtualFile getVFile() {
        return vFile;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public String getCategoryLabel() {
        return categoryLabel;
    }

    @Override
    public String toString() {
        return name;
    }
}
