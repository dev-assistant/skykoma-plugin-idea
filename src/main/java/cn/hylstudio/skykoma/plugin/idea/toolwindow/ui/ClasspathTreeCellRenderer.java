package cn.hylstudio.skykoma.plugin.idea.toolwindow.ui;

import cn.hylstudio.skykoma.plugin.idea.toolwindow.model.FileViewNode;
import cn.hylstudio.skykoma.plugin.idea.toolwindow.model.PackageViewNode;
import cn.hylstudio.skykoma.plugin.idea.toolwindow.structure.ClassStructureTreeStructure;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public class ClasspathTreeCellRenderer extends ColoredTreeCellRenderer {

    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected,
                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
            if (userObject instanceof FileViewNode) {
                FileViewNode node = (FileViewNode) userObject;
                switch (node.getType()) {
                    case CATEGORY:
                        setIcon(AllIcons.Nodes.ModuleGroup);
                        append(node.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                        break;
                    case JAR:
                        setIcon(AllIcons.Nodes.PpLib);
                        append(node.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                        break;
                    case DIRECTORY:
                        setIcon(AllIcons.Nodes.Folder);
                        append(node.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                        break;
                    case PACKAGE:
                        setIcon(AllIcons.Nodes.Package);
                        append(node.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                        break;
                    case CLASS:
                        setIcon(AllIcons.Nodes.Class);
                        if ("(empty)".equals(node.getName())) {
                            append(node.getName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                        } else {
                            append(node.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                        }
                        break;
                }
            } else if (userObject instanceof PackageViewNode) {
                PackageViewNode node = (PackageViewNode) userObject;
                setIcon(AllIcons.Nodes.Package);
                append(node.getSimpleName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            } else if (userObject instanceof VirtualFile) {
                VirtualFile vf = (VirtualFile) userObject;
                setIcon(AllIcons.Nodes.Class);
                append(vf.getNameWithoutExtension(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            } else if (userObject instanceof ClassStructureTreeStructure.StructureNode) {
                ClassStructureTreeStructure.StructureNode node = (ClassStructureTreeStructure.StructureNode) userObject;
                setIcon(node.getIcon());
                append(node.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            } else if (userObject instanceof String) {
                append((String) userObject, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
        }
    }
}
