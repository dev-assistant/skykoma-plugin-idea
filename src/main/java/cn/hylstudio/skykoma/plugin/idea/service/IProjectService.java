package cn.hylstudio.skykoma.plugin.idea.service;

import com.intellij.openapi.project.Project;

public interface IProjectService {
    void onProjectSmartModeReady(Project project);

    void parseProjectInfo(Project project);
}
