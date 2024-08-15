package cn.hylstudio.skykoma.plugin.idea.service;

import cn.hylstudio.skykoma.plugin.idea.model.ProjectInfoDto;
import com.intellij.openapi.project.Project;

public interface IProjectInfoService {
    void setCurrentProject(Project project);

    ProjectInfoDto updateProjectInfo(boolean autoUpload);

//    ProjectInfoDto uploadProjectInfo();

    void doScan(boolean autoUpload);
}
