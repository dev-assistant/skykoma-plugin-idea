package cn.hylstudio.skykoma.plugin.idea.service;

import cn.hylstudio.skykoma.plugin.idea.model.ProjectInfoDto;
import com.intellij.openapi.project.Project;

import java.util.function.Consumer;

public interface IProjectInfoService {
    void setCurrentProject(Project project);

    ProjectInfoDto updateProjectInfo(boolean autoUpload);

    ProjectInfoDto updateProjectInfo(boolean autoUpload, Consumer<String> progressText, Consumer<Double> fraction);

    void doScan(boolean autoUpload);

    void doScan(boolean autoUpload, Consumer<String> progressText, Consumer<Double> fraction);
}
