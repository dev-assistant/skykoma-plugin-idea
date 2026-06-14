package cn.hylstudio.skykoma.plugin.idea.listener;

import cn.hylstudio.skykoma.plugin.idea.toolwindow.SkykomaToolWindowFactory;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

public class ProjectOpenCloseListener implements ProjectManagerListener {
    private static final Logger LOGGER = Logger.getInstance(ProjectOpenCloseListener.class);

    @Override
    public void projectClosed(@NotNull Project project) {
        Application application = ApplicationManager.getApplication();
        if (application == null) {
            LOGGER.info("listener projectClosed, application is null");
            return;
        }
        if (application.isUnitTestMode()) {
            return;
        }
        LOGGER.info(String.format("listener projectClosed, name = %s", project.getName()));

        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length <= 1) {
            LOGGER.info("Last project closed, shutting down kernel and lab");
            SkykomaToolWindowFactory.shutdownKernelAndLab();
        }
    }
}
