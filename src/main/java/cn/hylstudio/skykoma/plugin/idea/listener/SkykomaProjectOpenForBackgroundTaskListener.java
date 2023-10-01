package cn.hylstudio.skykoma.plugin.idea.listener;

import cn.hylstudio.skykoma.plugin.idea.service.IProjectInfoService;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import static cn.hylstudio.skykoma.plugin.idea.util.LogUtils.info;

public class SkykomaProjectOpenForBackgroundTaskListener implements StartupActivity.Background {
    private static final Logger LOGGER = Logger.getInstance(SkykomaProjectOpenForBackgroundTaskListener.class);

    @Override
    public void runActivity(@NotNull Project project) {
        String name = project.getName();
        info(LOGGER, String.format("background task trigger succ, project = [%s]", name));
        Application application = ApplicationManager.getApplication();
        application.runReadAction(() -> {
            IProjectInfoService projectService = project.getService(IProjectInfoService.class);
            projectService.setCurrentProject(project);
            projectService.updateProjectInfo(true);
        });
    }
}
