package cn.hylstudio.skykoma.plugin.idea.toolwindow.ui;

import cn.hylstudio.skykoma.plugin.idea.toolwindow.model.FileViewNode;
import cn.hylstudio.skykoma.plugin.idea.toolwindow.model.PackageViewNode;
import cn.hylstudio.skykoma.plugin.idea.toolwindow.structure.ClassStructureTreeStructure;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

public class ClasspathTreeCellRenderer implements TreeCellRenderer {

    private final JLabel label = new JLabel();

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                  boolean leaf, int row, boolean hasFocus) {
        label.setOpaque(false);

        if (value instanceof DefaultMutableTreeNode) {
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
            if (userObject instanceof FileViewNode) {
                FileViewNode node = (FileViewNode) userObject;
                label.setText(node.getName());
                switch (node.getType()) {
                    case CATEGORY:
                        label.setIcon(AllIcons.Nodes.ModuleGroup);
                        break;
                    case JAR:
                        label.setIcon(AllIcons.Nodes.PpLib);
                        break;
                    case DIRECTORY:
                        label.setIcon(AllIcons.Nodes.Folder);
                        break;
                    case PACKAGE:
                        label.setIcon(AllIcons.Nodes.Package);
                        break;
                    case CLASS:
                        label.setIcon(AllIcons.Nodes.Class);
                        break;
                }
            } else if (userObject instanceof PackageViewNode) {
                PackageViewNode node = (PackageViewNode) userObject;
                label.setText(node.getSimpleName());
                label.setIcon(AllIcons.Nodes.Package);
            } else if (userObject instanceof VirtualFile) {
                VirtualFile vf = (VirtualFile) userObject;
                label.setText(vf.getNameWithoutExtension());
                label.setIcon(AllIcons.Nodes.Class);
            } else if (userObject instanceof ClassStructureTreeStructure.StructureNode) {
                ClassStructureTreeStructure.StructureNode node = (ClassStructureTreeStructure.StructureNode) userObject;
                label.setText(node.getName());
                label.setIcon(node.getIcon());
            } else if (userObject instanceof String) {
                label.setText((String) userObject);
                label.setIcon(null);
            }
        }
        return label;
    }
}
