package cn.hylstudio.skykoma.plugin.idea.listener;

import cn.hylstudio.skykoma.plugin.idea.service.IProjectService;
import com.intellij.openapi.diagnostic.Logger;
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
        IProjectService projectService = project.getService(IProjectService.class);
        projectService.onProjectSmartModeReady(project);
    }
}
