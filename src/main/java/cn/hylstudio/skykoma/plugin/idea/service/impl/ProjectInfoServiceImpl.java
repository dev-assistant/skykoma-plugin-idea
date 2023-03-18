package cn.hylstudio.skykoma.plugin.idea.service.impl;

import cn.hylstudio.skykoma.plugin.idea.model.FileDto;
import cn.hylstudio.skykoma.plugin.idea.model.ModuleDto;
import cn.hylstudio.skykoma.plugin.idea.model.ProjectInfoDto;
import cn.hylstudio.skykoma.plugin.idea.model.VCSEntityDto;
import cn.hylstudio.skykoma.plugin.idea.model.payload.ProjectInfoQueryPayload;
import cn.hylstudio.skykoma.plugin.idea.model.payload.UploadProjectPayload;
import cn.hylstudio.skykoma.plugin.idea.model.result.BizCode;
import cn.hylstudio.skykoma.plugin.idea.model.result.JsonResult;
import cn.hylstudio.skykoma.plugin.idea.service.IHttpService;
import cn.hylstudio.skykoma.plugin.idea.service.IProjectInfoService;
import com.google.common.reflect.TypeToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static cn.hylstudio.skykoma.plugin.idea.util.GsonUtils.GSON;
import static cn.hylstudio.skykoma.plugin.idea.util.LogUtils.info;

public class ProjectInfoServiceImpl implements IProjectInfoService {
    private static final Logger LOGGER = Logger.getInstance(ProjectInfoServiceImpl.class);
    private ProjectInfoDto projectInfoDto = new ProjectInfoDto();
    private IHttpService httpService = ApplicationManager.getApplication().getService(IHttpService.class);

    public ProjectInfoServiceImpl() {
        info(LOGGER, "ProjectInfoServiceImpl init");
    }

    @Override
    public void onProjectSmartModeReady(Project project) {
        Project mProject = projectInfoDto.getProject();
        if (mProject != null) {
            return;
        }
        String projectName = project.getName();
        info(LOGGER, String.format("onProjectSmartModeReady, project = %s", projectName));
        projectInfoDto.setProject(project);
        projectInfoDto.setName(projectName);
    }

    @Override
    public ProjectInfoDto updateProjectInfo() {
        return updateProjectInfo(false);
    }

    @Override
    public ProjectInfoDto updateProjectInfo(boolean autoUpload) {
        Project project = projectInfoDto.getProject();
        assert project != null;
        String projectKey = queryProjectKey(projectInfoDto);
        if (StringUtils.isEmpty(projectKey)) {
            LOGGER.error(String.format("parseProjectInfo error, projectKey empty, path = [%s]", project.getBasePath()));
            return null;
        }
        projectInfoDto.setKey(projectKey);
        String scanId = genScanId(projectKey);
        projectInfoDto.setScanId(scanId);
        long scanTs = System.currentTimeMillis();
        projectInfoDto.setLastScanTs(scanTs);
        String basePath = project.getBasePath();
        assert basePath != null;
        File rootFolder = new File(basePath);
        //vcs info
        FileDto rootFolderDto = new FileDto(rootFolder, false);
        projectInfoDto.setRootFolder(rootFolderDto);
        VCSEntityDto vcsEntityDto = parseVcsEntityDto(rootFolderDto);
        projectInfoDto.setVcsEntityDto(vcsEntityDto);
        //modules
        List<ModuleDto> moduleDtos = parseModulesDto(project);
        projectInfoDto.setModules(moduleDtos);
        fillModuleRootsInfo();
        if (autoUpload) {
            uploadProjectInfo();
        }
        return projectInfoDto;
    }

    @Override
    public ProjectInfoDto uploadProjectInfo() {
        Project project = projectInfoDto.getProject();
        assert project != null;
        String projectKey = queryProjectKey(projectInfoDto);
        if (StringUtils.isEmpty(projectKey)) {
            LOGGER.error(String.format("uploadProjectInfo error, projectKey empty, path = [%s]", project.getBasePath()));
            return null;
        }
        String scanId = projectInfoDto.getScanId();
        UploadProjectPayload payload = new UploadProjectPayload(projectInfoDto);
        payload.setScanId(scanId);
        payload.setProjectInfoDto(projectInfoDto);
        ProjectInfoDto result = uploadProjectInfoToServer(payload);
        return projectInfoDto;
    }

    @NotNull
    private static List<ModuleDto> parseModulesDto(Project project) {
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        List<Module> modules = Arrays.asList(moduleManager.getModules());
        List<ModuleDto> moduleDtos = modules.stream().map(ModuleDto::new).collect(Collectors.toList());
        return moduleDtos;
    }

    @NotNull
    private VCSEntityDto parseVcsEntityDto(FileDto rootFolderDto) {
        VCSEntityDto vcsEntityDto = new VCSEntityDto();
        String rootFolderName = rootFolderDto.getName();
        String absolutePath = rootFolderDto.getAbsolutePath();
        vcsEntityDto.setPath(absolutePath);
        vcsEntityDto.setVcsType("git");
        vcsEntityDto.setName(rootFolderName);
        return vcsEntityDto;
    }

    private void fillModuleRootsInfo() {
        FileDto rootFolderDto = projectInfoDto.getRootFolder();
        String rootFolderPath = rootFolderDto.getAbsolutePath();
        List<ModuleDto> moduleDtos = projectInfoDto.getModules();
        //module dirs
        for (ModuleDto v : moduleDtos) {
            Module module = v.getModule();
            ModuleRootManager manager = ModuleRootManager.getInstance(module);
            v.setSrcRoots(getFileDtos(rootFolderPath, manager.getSourceRoots(JavaSourceRootType.SOURCE)));
            v.setTestSrcRoots(getFileDtos(rootFolderPath, manager.getSourceRoots(JavaSourceRootType.TEST_SOURCE)));
            v.setResRoots(getFileDtos(rootFolderPath, manager.getSourceRoots(JavaResourceRootType.RESOURCE)));
            v.setTestResRoots(getFileDtos(rootFolderPath, manager.getSourceRoots(JavaResourceRootType.TEST_RESOURCE)));
        }
    }

    private static List<FileDto> getFileDtos(String rootFolderPath, List<VirtualFile> virtualFiles) {
        return virtualFiles.stream()
                .map(vf -> mapToDto(vf, rootFolderPath))
                .collect(Collectors.toList());
    }

    private static FileDto mapToDto(VirtualFile v, String rootFolderPath) {
        File file = new File(v.getPath());
        return new FileDto(file, rootFolderPath);
    }

    private String queryProjectKey(ProjectInfoDto projectInfoDto) {
        String key = projectInfoDto.getKey();
        if (!StringUtils.isEmpty(key)) {
            return key;
        }
        ProjectInfoDto projectInfoDtoFromServer = queryProjectKeyFromServer(projectInfoDto);
        if (projectInfoDtoFromServer == null) {
            return "";
        }
        return projectInfoDtoFromServer.getKey();
    }

    private String genScanId(String projectKey) {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    private static final String host = "http://localhost:8080";//TODO from config
    private static final String UPLOAD_PROJECT_API = "%s/api/project/uploadProjectInfo";
    private static final String QUERY_PROJECT_API = "%s/api/project/queryProjectInfo";

    //TODO move to api service
    private ProjectInfoDto queryProjectKeyFromServer(ProjectInfoDto projectInfoDto) {
        Project project = projectInfoDto.getProject();
        ProjectInfoQueryPayload payload = new ProjectInfoQueryPayload(projectInfoDto);
        payload.setKey(project.getName());
        payload.setCreateIfNotExists(true);
        String requestJson = GSON.toJson(payload);
        System.out.println(requestJson);
        String url = String.format(QUERY_PROJECT_API, host);
        Type type = new TypeToken<JsonResult<ProjectInfoDto>>() {
        }.getType();
        String responseJson = httpService.postJsonBody(url, requestJson);
        LOGGER.info(String.format("queryProjectKey, responseJson = [%s]", responseJson));
        if (StringUtils.isEmpty(responseJson)) {
            LOGGER.error("queryProjectKey failed, response is empty");
            return null;
        }
        JsonResult<ProjectInfoDto> respone = null;
        try {
            respone = GSON.fromJson(responseJson, type);
        } catch (Exception e) {
            LOGGER.error(String.format("queryProjectKey failed, response format error1, responseJson = [%s]", responseJson), e);
            return null;
        }
        if (respone == null) {
            LOGGER.error(String.format("queryProjectKey failed, response format error2, responseJson = [%s]", responseJson));
            return null;
        }
        String code = respone.getCode();
        if (code == null) {
            LOGGER.error(String.format("queryProjectKey failed, response format error3, code missing, responseJson = [%s]", responseJson));
            return null;
        }
        if (!BizCode.SUCC.getCode().equals(code)) {
            LOGGER.error(String.format("queryProjectKey failed, response format error4, code error, responseJson = [%s]", responseJson));
            return null;
        }
        ProjectInfoDto data = respone.getData();
        if (data == null) {
            LOGGER.error(String.format("queryProjectKey failed, response format error5, code missing, responseJson = [%s]", responseJson));
            return null;
        }
        return data;
    }

    private ProjectInfoDto uploadProjectInfoToServer(UploadProjectPayload payload) {
        String requestJson = GSON.toJson(payload);
        System.out.println(requestJson);
        String url = String.format(UPLOAD_PROJECT_API, host);
        Type type = new TypeToken<JsonResult<ProjectInfoDto>>() {
        }.getType();
        String responseJson = httpService.postJsonBody(url, requestJson);
        LOGGER.info(String.format("upload projectInfo, responseJson = [%s]", responseJson));
        if (StringUtils.isEmpty(responseJson)) {
            LOGGER.error("upload projectInfo failed, response is empty");
            return null;
        }
        JsonResult<ProjectInfoDto> respone = null;
        try {
            respone = GSON.fromJson(responseJson, type);
        } catch (Exception e) {
            LOGGER.error(String.format("upload projectInfo failed, response format error1, responseJson = [%s]", responseJson), e);
            return null;
        }
        if (respone == null) {
            LOGGER.error(String.format("upload projectInfo failed, response format error2, data = [%s]", responseJson));
            return null;
        }
        String code = respone.getCode();
        if (code == null) {
            LOGGER.error(String.format("upload projectInfo failed, response format error3, code missing, data = [%s]", responseJson));
            return null;
        }
        return respone.getData();
    }
}
