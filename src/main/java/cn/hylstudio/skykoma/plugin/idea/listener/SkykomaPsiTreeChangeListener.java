package cn.hylstudio.skykoma.plugin.idea.listener;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import org.jetbrains.annotations.NotNull;

import static cn.hylstudio.skykoma.plugin.idea.util.LogUtils.info;

public class SkykomaPsiTreeChangeListener implements PsiTreeChangeListener {
    private static final Logger LOGGER = Logger.getInstance(SkykomaBulkFileListener.class);

    @Override
    public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
        PsiElement element = event.getElement();
        if (element == null) return;
        info(LOGGER,String.format("beforeChildAddition, element = [%s]", element.getText()));
    }

    @Override
    public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
        PsiElement element = event.getElement();
        if (element == null) return;
        info(LOGGER,String.format("beforeChildRemoval, element = [%s]", element.getText()));
    }

    @Override
    public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
        PsiElement element = event.getElement();
        if (element == null) return;
        info(LOGGER,String.format("beforeChildReplacement, element = [%s]", element.getText()));

    }

    @Override
    public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
        PsiElement element = event.getElement();
        if (element == null) return;
        info(LOGGER,String.format("beforeChildMovement, element = [%s]", element.getText()));

    }

    @Override
    public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
        PsiElement element = event.getElement();
        if (element == null) return;
        info(LOGGER,String.format("beforeChildrenChange, element = [%s]", element.getText()));
    }

    @Override
    public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
        PsiElement element = event.getElement();
        if (element == null) return;
        info(LOGGER,String.format("beforePropertyChange, element = [%s]", element.getText()));
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
        PsiElement element = event.getElement();
        if (element == null) return;
        info(LOGGER,String.format("childAdded, element = [%s]", element.getText()));

    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        PsiElement element = event.getElement();
        if (element == null) return;
        info(LOGGER,String.format("childRemoved, element = [%s]", element.getText()));

    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        PsiElement element = event.getElement();
        if (element == null) return;
        info(LOGGER,String.format("childReplaced, element = [%s]", element.getText()));

    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        PsiElement element = event.getElement();
        if (element == null) return;
        info(LOGGER,String.format("childrenChanged, element = [%s]", element.getText()));
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
        PsiElement element = event.getElement();
        if (element == null) return;
        info(LOGGER,String.format("childMoved, element = [%s]", element.getText()));
    }

    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
        PsiElement element = event.getElement();
        if (element == null) return;
        info(LOGGER,String.format("propertyChanged, element = [%s]", element.getText()));
    }
}
