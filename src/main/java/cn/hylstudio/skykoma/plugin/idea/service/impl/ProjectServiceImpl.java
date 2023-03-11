package cn.hylstudio.skykoma.plugin.idea.service.impl;

import cn.hylstudio.skykoma.plugin.idea.model.FileDto;
import cn.hylstudio.skykoma.plugin.idea.model.ModuleDto;
import cn.hylstudio.skykoma.plugin.idea.model.ProjectInfoDto;
import cn.hylstudio.skykoma.plugin.idea.model.VCSEntityDto;
import cn.hylstudio.skykoma.plugin.idea.service.IProjectService;
import cn.hylstudio.skykoma.plugin.idea.util.GsonUtils;
import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static cn.hylstudio.skykoma.plugin.idea.util.GsonUtils.GSON;
import static cn.hylstudio.skykoma.plugin.idea.util.LogUtils.info;

public class ProjectServiceImpl implements IProjectService {
    private static final Logger LOGGER = Logger.getInstance(ProjectServiceImpl.class);
    private static ProjectInfoDto projectInfoDto = new ProjectInfoDto();

    public ProjectServiceImpl() {
        info(LOGGER, "ProjectServiceImpl init");
    }

    @Override
    public void onProjectSmartModeReady(Project project) {
        String projectName = project.getName();
        info(LOGGER, String.format("onProjectSmartModeReady, project = %s", projectName));
        projectInfoDto.setProject(project);
        projectInfoDto.setName(projectName);
    }

    @Override
    public void parseProjectInfo(Project project) {
        //TODO register projectId
        String projectId = registerProjectId(projectInfoDto);
        projectInfoDto.setId(projectId);

        String basePath = project.getBasePath();
        assert basePath != null;
        File rootFolder = new File(basePath);
        //vcs info
        VCSEntityDto vcsEntityDto = new VCSEntityDto();
        String rootFolderName = rootFolder.getName();
        String rootFolderPath = rootFolder.getPath();
        vcsEntityDto.setPath(rootFolderPath);
        vcsEntityDto.setVcsType("git");
        vcsEntityDto.setName(rootFolderName);//TODO 不够严谨
        projectInfoDto.setVcsEntityDto(vcsEntityDto);
        //modules
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        List<Module> modules = Arrays.asList(moduleManager.getModules());
        List<ModuleDto> moduleDtos = modules.stream().map(v -> new ModuleDto(v)).collect(Collectors.toList());
        projectInfoDto.setModules(moduleDtos);
        //module dirs
        for (ModuleDto v : moduleDtos) {
            Module module = v.getModule();
            ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
            List<VirtualFile> sourceRoots = moduleRootManager.getSourceRoots(JavaSourceRootType.SOURCE);
            List<FileDto> srcRoots = sourceRoots.stream().map(vv -> mapToDto(vv, rootFolderPath)).collect(Collectors.toList());
            v.setSrcRoots(srcRoots);
            List<VirtualFile> testSourceRoots = moduleRootManager.getSourceRoots(JavaSourceRootType.TEST_SOURCE);
            List<FileDto> testSrcRoots = testSourceRoots.stream().map(vv -> mapToDto(vv, rootFolderPath)).collect(Collectors.toList());
            v.setTestSrcRoots(testSrcRoots);
            List<VirtualFile> resourceRoots = moduleRootManager.getSourceRoots(JavaResourceRootType.RESOURCE);
            List<FileDto> resRoots = resourceRoots.stream().map(vv -> mapToDto(vv, rootFolderPath)).collect(Collectors.toList());
            v.setResRoots(resRoots);
            List<VirtualFile> testResourceRoots = moduleRootManager.getSourceRoots(JavaResourceRootType.TEST_RESOURCE);
            List<FileDto> testResRoots = testResourceRoots.stream().map(vv -> mapToDto(vv, rootFolderPath)).collect(Collectors.toList());
            v.setTestResRoots(testResRoots);
        }
        String result = GSON.toJson(projectInfoDto);
        System.out.println(result);
    }

    @NotNull
    private static FileDto mapToDto(VirtualFile v, String rootFolderPath) {
        File file = new File(v.getPath());
        return new FileDto(file, rootFolderPath);
    }

    private String registerProjectId(ProjectInfoDto projectInfoDto) {
        return "1";
    }
}
