package cn.hylstudio.skykoma.plugin.idea.toolwindow;

import cn.hylstudio.skykoma.plugin.idea.service.SkykomaConsoleService;
import cn.hylstudio.skykoma.plugin.idea.service.impl.SkykomaConsoleServiceImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class SkykomaConsoleToolWindowFactory implements ToolWindowFactory, DumbAware {

    private static final int IDX_WORD_WRAP = 2;
    private static final int IDX_SCROLL_TO_END = 3;
    private static final int IDX_CLEAR = 5;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        SkykomaConsoleServiceImpl consoleService = (SkykomaConsoleServiceImpl) ApplicationManager.getApplication()
                .getService(SkykomaConsoleService.class);
        ConsoleView consoleView = consoleService.initConsole(project);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(consoleView.getComponent(), BorderLayout.CENTER);

        ApplicationManager.getApplication().invokeLater(() -> {
            DefaultActionGroup toolbarGroup = new DefaultActionGroup();
            AnAction[] allActions = consoleView.createConsoleActions();
            if (allActions.length > IDX_WORD_WRAP) toolbarGroup.add(allActions[IDX_WORD_WRAP]);
            if (allActions.length > IDX_SCROLL_TO_END) toolbarGroup.add(allActions[IDX_SCROLL_TO_END]);
            if (allActions.length > IDX_CLEAR) toolbarGroup.add(allActions[IDX_CLEAR]);

            ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("skykoma-console", toolbarGroup, false);
            toolbar.setTargetComponent(consoleView.getComponent());
            panel.add(toolbar.getComponent(), BorderLayout.WEST);
            panel.revalidate();
        });

        Content content = ContentFactory.getInstance().createContent(panel, null, false);
        toolWindow.getContentManager().addContent(content);
    }
}
