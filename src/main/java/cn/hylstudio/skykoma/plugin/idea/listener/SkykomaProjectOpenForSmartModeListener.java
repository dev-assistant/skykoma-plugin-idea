package cn.hylstudio.skykoma.plugin.idea.listener;

import cn.hylstudio.skykoma.plugin.idea.service.IProjectInfoService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import static cn.hylstudio.skykoma.plugin.idea.util.LogUtils.info;

public class SkykomaProjectOpenForSmartModeListener implements StartupActivity.RequiredForSmartMode {
    private static final Logger LOGGER = Logger.getInstance(SkykomaProjectOpenForSmartModeListener.class);

    @Override
    public void runActivity(@NotNull Project project) {
        String name = project.getName();
        info(LOGGER, String.format("project smart mode ready, project = [%s]", name));
        IProjectInfoService projectService = project.getService(IProjectInfoService.class);
        projectService.setCurrentProject(project);//in DefaultDispatcher-Worker
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Upload projectInfo Task") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Waiting smart mode");
                DumbService.getInstance(project).waitForSmartMode();
                //in polled thread
                IProjectInfoService projectService = project.getService(IProjectInfoService.class);
                projectService.setCurrentProject(project);
                projectService.updateProjectInfo(true, indicator::setText, indicator::setFraction);
            }
        });
    }
}
