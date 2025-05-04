package cn.hylstudio.skykoma.plugin.idea.listener;

import cn.hylstudio.skykoma.plugin.idea.service.IdeaPluginAgentServer;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

public class ProjectOpenCloseListener implements ProjectManagerListener {
    private static final Logger LOGGER = Logger.getInstance(ProjectOpenCloseListener.class);

    /**
     * Invoked on project close.
     *
     * @param project closing project
     */
    @Override
    public void projectClosed(@NotNull Project project) {
        // Ensure this isn't part of testing
        Application application = ApplicationManager.getApplication();
        if (application == null) {
            LOGGER.info("listener projectOpened, application is null");
            return;
        }
        if (application.isUnitTestMode()) {
            return;
        }
        LOGGER.info(String.format("listener projectClosed, name = %s", project.getName()));
    }
}
