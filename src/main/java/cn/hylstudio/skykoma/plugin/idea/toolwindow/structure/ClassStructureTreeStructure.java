package cn.hylstudio.skykoma.plugin.idea.toolwindow.structure;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ClassStructureTreeStructure {

    public enum StructureNodeType {
        CLASS, METHOD, FIELD
    }

    public static class StructureNode {
        private final StructureNodeType type;
        private final String name;
        private final String detail;
        private final Icon icon;
        private final List<StructureNode> children;

        public StructureNode(StructureNodeType type, String name, String detail, Icon icon) {
            this.type = type;
            this.name = name;
            this.detail = detail;
            this.icon = icon;
            this.children = new ArrayList<>();
        }

        public StructureNodeType getType() { return type; }
        public String getName() { return name; }
        public String getDetail() { return detail; }
        public Icon getIcon() { return icon; }
        public List<StructureNode> getChildren() { return children; }
        public void addChild(StructureNode child) { children.add(child); }

        @Override
        public String toString() { return name; }
    }

    private StructureNode rootNode;

    public void setClass(Project project, VirtualFile classFile) {
        if (classFile == null) {
            rootNode = null;
            return;
        }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(classFile);
        if (psiFile instanceof PsiClassOwner) {
            PsiClassOwner classOwner = (PsiClassOwner) psiFile;
            PsiClass[] classes = classOwner.getClasses();
            if (classes.length > 0) {
                rootNode = buildStructureNode(classes[0]);
                return;
            }
        }
        rootNode = null;
    }

    private StructureNode buildStructureNode(PsiClass psiClass) {
        StructureNode node = new StructureNode(StructureNodeType.CLASS,
                psiClass.getName(),
                psiClass.getQualifiedName(),
                psiClass.getIcon(0));

        for (PsiField field : psiClass.getFields()) {
            String detail = field.getType().getPresentableText() + " " + field.getName();
            node.addChild(new StructureNode(StructureNodeType.FIELD,
                    field.getName(), detail, field.getIcon(0)));
        }

        for (PsiMethod method : psiClass.getMethods()) {
            StringBuilder detail = new StringBuilder();
            detail.append(method.getReturnType() != null ? method.getReturnType().getPresentableText() : "void");
            detail.append(" ").append(method.getName()).append("(");
            PsiParameter[] params = method.getParameterList().getParameters();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) detail.append(", ");
                detail.append(params[i].getType().getPresentableText()).append(" ").append(params[i].getName());
            }
            detail.append(")");
            node.addChild(new StructureNode(StructureNodeType.METHOD,
                    method.getName(), detail.toString(), method.getIcon(0)));
        }

        for (PsiClass innerClass : psiClass.getInnerClasses()) {
            node.addChild(buildStructureNode(innerClass));
        }

        return node;
    }

    public Object getRootElement() {
        return "Structure";
    }

    public Object[] getChildElements(Object element) {
        if ("Structure".equals(element)) {
            return rootNode != null ? new Object[]{rootNode} : new Object[0];
        }
        if (element instanceof StructureNode) {
            return ((StructureNode) element).getChildren().toArray();
        }
        return new Object[0];
    }

    public boolean isAlwaysLeaf(Object element) {
        if (element instanceof StructureNode) {
            StructureNode node = (StructureNode) element;
            return node.getType() != StructureNodeType.CLASS || node.getChildren().isEmpty();
        }
        return true;
    }
}
