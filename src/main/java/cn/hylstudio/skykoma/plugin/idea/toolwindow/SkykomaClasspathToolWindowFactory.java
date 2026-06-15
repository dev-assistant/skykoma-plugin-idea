package cn.hylstudio.skykoma.plugin.idea.toolwindow;

import cn.hylstudio.skykoma.plugin.idea.service.IdeaPluginAgentServer;
import cn.hylstudio.skykoma.plugin.idea.toolwindow.ui.ClasspathFileTreePanel;
import cn.hylstudio.skykoma.plugin.idea.toolwindow.ui.ClasspathPackageTreePanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SkykomaClasspathToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ClasspathFileTreePanel fileTreePanel = new ClasspathFileTreePanel(project);
        ClasspathPackageTreePanel packageTreePanel = new ClasspathPackageTreePanel(project);

        Runnable refreshAction = () -> doRefresh(fileTreePanel, packageTreePanel);

        toolWindow.setTitleActions(List.of(new AnAction("Refresh Classpath", "Refresh classpath view", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                refreshAction.run();
            }
        }));

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content fileContent = contentFactory.createContent(fileTreePanel, "File", false);
        fileContent.setIcon(AllIcons.Nodes.Folder);
        Content packageContent = contentFactory.createContent(packageTreePanel, "Package", false);
        packageContent.setIcon(AllIcons.Nodes.Package);

        toolWindow.getContentManager().addContent(fileContent);
        toolWindow.getContentManager().addContent(packageContent);

        refreshAction.run();
    }

    private void doRefresh(ClasspathFileTreePanel fileTreePanel,
                           ClasspathPackageTreePanel packageTreePanel) {
        fileTreePanel.setStatus("Loading...");
        packageTreePanel.setStatus("Loading...");

        ProgressManager.getInstance().run(new Task.Backgroundable(null, "Scanning Classpath", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                IdeaPluginAgentServer server = ApplicationManager.getApplication().getService(IdeaPluginAgentServer.class);
                List<String> systemCp = server.getSystemClasspath();
                List<String> pluginCp = server.getPluginClasspath();
                List<String> extraCp = server.getExtraClasspath();

                List<String> allCp = new ArrayList<>();
                allCp.addAll(systemCp);
                allCp.addAll(pluginCp);
                allCp.addAll(extraCp);

                if (allCp.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        fileTreePanel.setStatus("No classpath available. Start the kernel first.");
                        packageTreePanel.setStatus("No classpath available. Start the kernel first.");
                    });
                    return;
                }

                indicator.setText("Scanning " + allCp.size() + " classpath entries...");
                indicator.setIndeterminate(true);

                ApplicationManager.getApplication().invokeLater(() -> {
                    fileTreePanel.refresh(systemCp, pluginCp, extraCp);
                    packageTreePanel.refresh(allCp.stream().distinct().toList());
                });
            }
        });
    }
}
