package cn.hylstudio.skykoma.plugin.idea.service.impl;

import cn.hylstudio.skykoma.plugin.idea.SkykomaConstants;
import cn.hylstudio.skykoma.plugin.idea.model.*;
import cn.hylstudio.skykoma.plugin.idea.model.payload.ProjectInfoQueryPayload;
import com.intellij.ide.util.PropertiesComponent;
import cn.hylstudio.skykoma.plugin.idea.model.payload.UploadProjectPayload;
import cn.hylstudio.skykoma.plugin.idea.model.result.BizCode;
import cn.hylstudio.skykoma.plugin.idea.model.result.JsonResult;
import cn.hylstudio.skykoma.plugin.idea.service.IHttpService;
import cn.hylstudio.skykoma.plugin.idea.service.IProjectInfoService;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import static cn.hylstudio.skykoma.plugin.idea.util.GsonUtils.GSON;
import static cn.hylstudio.skykoma.plugin.idea.util.GsonUtils.PSI_GSON;
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
        // vcs info
        FileDto rootFolderDto = parseRootFolder(rootFolder);
        projectInfoDto.setRootFolder(rootFolderDto);
        VCSEntityDto vcsEntityDto = parseVcsEntityDto(rootFolderDto);
        projectInfoDto.setVcsEntityDto(vcsEntityDto);
        // modules
        List<ModuleDto> moduleDtos = parseModulesDto(project);
        projectInfoDto.setModules(moduleDtos);
        // scanAllFiles
        scanAllFiles();
        if (autoUpload) {
            uploadProjectInfo();
        }
        return projectInfoDto;
    }

    @NotNull
    private static <T> BinaryOperator<List<T>> mergeList() {
        return (v1, v2) -> {
            List<T> objects = new ArrayList<>(v1.size() + v2.size());
            objects.addAll(v1);
            objects.addAll(v2);
            return objects;
        };
    }

    private FileDto parseRootFolder(File rootFolder) {
        FileDto rootFolderDto = new FileDto(rootFolder, "");
        VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
        Path rootPath = rootFolder.toPath();
        VirtualFile rootVirtualFile = virtualFileManager.findFileByNioPath(rootPath);
        if (rootVirtualFile == null) {
            String msg = String.format("parseRootFolder error, virtualFile not found, pathStr = [%s]", rootPath);
            LOGGER.warn(msg);
            throw new RuntimeException(msg);
        }
        String rootFolderPath = rootFolderDto.getAbsolutePath();
        List<FileDto> subFiles = getFileDtos(rootVirtualFile, rootFolderPath);
        rootFolderDto.setSubFiles(subFiles);
        return rootFolderDto;
    }

    @Override
    public ProjectInfoDto uploadProjectInfo() {
        Project project = projectInfoDto.getProject();
        assert project != null;
        String projectKey = queryProjectKey(projectInfoDto);
        if (StringUtils.isEmpty(projectKey)) {
            LOGGER.error(
                    String.format("uploadProjectInfo error, projectKey empty, path = [%s]", project.getBasePath()));
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
    private List<ModuleDto> parseModulesDto(Project project) {
        FileDto rootFolder = projectInfoDto.getRootFolder();
        String absolutePath = rootFolder.getAbsolutePath();
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        List<Module> modules = Arrays.asList(moduleManager.getModules());
        List<ModuleDto> moduleDtos = modules.stream().map(v -> mapToModuleDto(absolutePath, v))
                .collect(Collectors.toList());
        return moduleDtos;
    }

    private static ModuleDto mapToModuleDto(String absolutePath, Module v) {
        ModuleDto moduleDto = new ModuleDto(v);
        ModuleRootManager manager = ModuleRootManager.getInstance(v);
        List<FileDto> srcRoots = mapToModuleRoot(manager.getSourceRoots(JavaSourceRootType.SOURCE), absolutePath);
        List<FileDto> testSrcRoots = mapToModuleRoot(manager.getSourceRoots(JavaSourceRootType.TEST_SOURCE),
                absolutePath);
        List<FileDto> resourceRoots = mapToModuleRoot(manager.getSourceRoots(JavaResourceRootType.RESOURCE),
                absolutePath);
        List<FileDto> testResourceRoots = mapToModuleRoot(manager.getSourceRoots(JavaResourceRootType.TEST_RESOURCE),
                absolutePath);
        ArrayList<ModuleRootDto> roots = Lists.newArrayList(
                new ModuleRootDto("src", srcRoots),
                new ModuleRootDto("testSrc", testSrcRoots),
                new ModuleRootDto("resources", resourceRoots),
                new ModuleRootDto("testResources", testResourceRoots));
        moduleDto.setRoots(roots);
        return moduleDto;
    }

    private void scanAllFiles() {
        Project project = projectInfoDto.getProject();
        PsiManager psiManager = PsiManager.getInstance(project);
        VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
        FileDto rootFolder = projectInfoDto.getRootFolder();
        List<ModuleDto> modules = projectInfoDto.getModules();
        Set<String> srcType = Sets.newHashSet("src", "testSrc");
        Set<String> srcRelativePaths = modules.stream()
                .map(ModuleDto::getRoots)
                .reduce(mergeList()).orElse(Collections.emptyList())
                .stream().filter(v -> srcType.contains(v.getType()))
                .map(ModuleRootDto::getFolders)
                .reduce(mergeList()).orElse(Collections.emptyList())
                .stream().map(FileDto::getRelativePath).collect(Collectors.toSet());
        scanFileRecursively(rootFolder, virtualFileManager, psiManager, srcRelativePaths);
    }

    private void scanFileRecursively(FileDto fileDto, VirtualFileManager virtualFileManager,
            PsiManager psiManager, Set<String> srcRelativePaths) {
        String type = fileDto.getType();
        if (type.equals("folder")) {
            fileDto.getSubFiles()
                    .forEach(v -> scanFileRecursively(v, virtualFileManager, psiManager, srcRelativePaths));
        } else if (type.equals("file")) {
            scanOneFile(fileDto, virtualFileManager, psiManager, srcRelativePaths);
        } else {
        }
    }

    private void scanOneFile(FileDto fileDto, VirtualFileManager virtualFileManager,
            PsiManager psiManager, Set<String> srcRelativePaths) {
        File file = fileDto.getFile();
        Path path = file.toPath();
        LOGGER.info(String.format("scanOneFile, scanning file = [%s]", path));
        // src files
        if (!inSrcPaths(fileDto, srcRelativePaths)) {
            LOGGER.warn(String.format("scanOneFile ignore non src folders, pathStr = [%s]", path));
            return;
        }
        VirtualFile virtualFile = virtualFileManager.findFileByNioPath(path);
        if (virtualFile == null) {
            LOGGER.warn(String.format("scanOneFile error, virtualFile not found, pathStr = [%s]", path));
            return;
        }
        String fileName = fileDto.getName();
        if (!fileName.endsWith(".java")) {
            LOGGER.warn(String.format("scanOneFile ignore non java, pathStr = [%s]", path));
            return;
        }
        PsiFile psiFile = psiManager.findFile(virtualFile);
        if (psiFile == null) {
            LOGGER.warn(String.format("scanOneFile error, psiFile not found, pathStr = [%s]", path));
            return;
        }
        PsiElement[] childrenElements = psiFile.getChildren();
        String psiFileJson = PSI_GSON.toJson(childrenElements);
        fileDto.setPsiFileJson(psiFileJson);
    }

    private boolean inSrcPaths(FileDto fileDto, Set<String> srcRelativePaths) {
        String relativePath = fileDto.getRelativePath();
        for (String srcPath : srcRelativePaths) {
            if (relativePath.startsWith(srcPath)) {
                return true;
            }
        }
        return false;
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

    private List<FileDto> getFileDtos(VirtualFile virtualFile, String rootFolderPath) {
        Project project = projectInfoDto.getProject();
        ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
        List<VirtualFile> virtualFiles = Arrays.asList(virtualFile.getChildren());
        List<FileDto> subFiles = virtualFiles.stream()
                .filter(v -> !v.getName().startsWith("."))// filter hide files
                .filter(v -> !vcsManager.isIgnored(v))
                .map(v -> {
                    File file = new File(v.getPath());
                    return new FileDto(file, rootFolderPath).fillSubFiles(rootFolderPath);
                })
                .collect(Collectors.toList());
        return subFiles;
    }

    private static List<FileDto> mapToModuleRoot(List<VirtualFile> virtualFiles, String absoluteRootPath) {
        return virtualFiles.stream().map(v -> {
            File file = new File(v.getPath());
            return new FileDto(file, absoluteRootPath);
        }).collect(Collectors.toList());
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

    private static final String UPDATE_PROJECT_API = "%s/api/project/updateProjectInfo";
    private static final String QUERY_PROJECT_API = "%s/api/project/queryProjectInfo";

    // TODO move to api service
    private ProjectInfoDto queryProjectKeyFromServer(ProjectInfoDto projectInfoDto) {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        String apiHost = propertiesComponent.getValue(SkykomaConstants.DATA_SERVER_API_HOST);
        if (StringUtils.isEmpty(apiHost)) {
            return null;
        }
        Project project = projectInfoDto.getProject();
        ProjectInfoQueryPayload payload = new ProjectInfoQueryPayload(projectInfoDto);
        payload.setKey(project.getName());
        payload.setCreateIfNotExists(true);
        String requestJson = GSON.toJson(payload);
        // System.out.println(requestJson);
        String url = String.format(QUERY_PROJECT_API, apiHost);
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
            LOGGER.error(
                    String.format("queryProjectKey failed, response format error1, responseJson = [%s]", responseJson),
                    e);
            return null;
        }
        if (respone == null) {
            LOGGER.error(
                    String.format("queryProjectKey failed, response format error2, responseJson = [%s]", responseJson));
            return null;
        }
        String code = respone.getCode();
        if (code == null) {
            LOGGER.error(String.format(
                    "queryProjectKey failed, response format error3, code missing, responseJson = [%s]", responseJson));
            return null;
        }
        if (!BizCode.SUCC.getCode().equals(code)) {
            LOGGER.error(String.format(
                    "queryProjectKey failed, response format error4, code error, responseJson = [%s]", responseJson));
            return null;
        }
        ProjectInfoDto data = respone.getData();
        if (data == null) {
            LOGGER.error(String.format(
                    "queryProjectKey failed, response format error5, code missing, responseJson = [%s]", responseJson));
            return null;
        }
        return data;
    }

    private ProjectInfoDto uploadProjectInfoToServer(UploadProjectPayload payload) {
        String requestJson = GSON.toJson(payload);
        // System.out.println(requestJson);
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        String apiHost = propertiesComponent.getValue(SkykomaConstants.DATA_SERVER_API_HOST);
        if (StringUtils.isEmpty(apiHost)) {
            return null;
        }
        String url = String.format(UPDATE_PROJECT_API, apiHost);
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
            LOGGER.error(String.format("upload projectInfo failed, response format error1, responseJson = [%s]",
                    responseJson), e);
            return null;
        }
        if (respone == null) {
            LOGGER.error(String.format("upload projectInfo failed, response format error2, data = [%s]", responseJson));
            return null;
        }
        String code = respone.getCode();
        if (code == null) {
            LOGGER.error(String.format("upload projectInfo failed, response format error3, code missing, data = [%s]",
                    responseJson));
            return null;
        }
        return respone.getData();
    }
}
