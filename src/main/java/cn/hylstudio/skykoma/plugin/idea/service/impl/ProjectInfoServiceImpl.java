package cn.hylstudio.skykoma.plugin.idea.service.impl;

import cn.hylstudio.skykoma.plugin.idea.SkykomaConstants;
import cn.hylstudio.skykoma.plugin.idea.model.*;
import cn.hylstudio.skykoma.plugin.idea.model.payload.*;
import cn.hylstudio.skykoma.plugin.idea.model.result.BizCode;
import cn.hylstudio.skykoma.plugin.idea.model.result.JsonResult;
import cn.hylstudio.skykoma.plugin.idea.service.IHttpService;
import cn.hylstudio.skykoma.plugin.idea.service.IProjectInfoService;
import cn.hylstudio.skykoma.plugin.idea.util.GsonUtils;
import cn.hylstudio.skykoma.plugin.idea.util.SkykomaNotifier;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.util.concurrency.AppExecutorUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static cn.hylstudio.skykoma.plugin.idea.util.GsonUtils.GSON;
import static cn.hylstudio.skykoma.plugin.idea.util.GsonUtils.PSI_GSON;
import static cn.hylstudio.skykoma.plugin.idea.util.LogUtils.error;
import static cn.hylstudio.skykoma.plugin.idea.util.LogUtils.info;

public class ProjectInfoServiceImpl implements IProjectInfoService {
    private static final Logger LOGGER = Logger.getInstance(ProjectInfoServiceImpl.class);
    private ProjectInfoDto projectInfoDto = new ProjectInfoDto();
    private IHttpService httpService = ApplicationManager.getApplication().getService(IHttpService.class);

    public ProjectInfoServiceImpl() {
        info(LOGGER, "ProjectInfoServiceImpl init");
    }

    @Override
    public void setCurrentProject(Project project) {
        Project mProject = projectInfoDto.getProject();
        if (mProject != null) {
            return;
        }
        String projectName = project.getName();
        info(LOGGER, String.format("setCurrentProject, project = %s", projectName));
        projectInfoDto.setProject(project);
        projectInfoDto.setName(projectName);
    }

//    @Override
//    public ProjectInfoDto updateProjectInfo() {
//        return updateProjectInfo(false);
//    }

    @Override
    public ProjectInfoDto updateProjectInfo(boolean autoUpload) {
        return updateProjectInfo(autoUpload, v -> {
        }, v -> {
        });
    }

    public ProjectInfoDto updateProjectInfo(boolean autoUpload, Consumer<String> progressText, Consumer<Double> fraction) {
        Project project = projectInfoDto.getProject();
        assert project != null;
        if (!autoUpload) {
            return projectInfoDto;
        }
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        boolean dataServerEnabled = propertiesComponent.getBoolean(SkykomaConstants.DATA_SERVER_ENABLED, false);
        if (!dataServerEnabled) {
            info(LOGGER, "updateProjectInfo failed, dataServerEnabled is false");
            return null;
        }
        String projectKey = queryOrCreateProjectKey(projectInfoDto);
        if (StringUtils.isEmpty(projectKey)) {
            LOGGER.error(String.format("parseProjectInfo error, projectKey empty, path = [%s]", project.getBasePath()));
            return null;
        }
        projectInfoDto.setKey(projectKey);
        doScan(true, progressText, fraction);
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

//    @Override
//    public ProjectInfoDto uploadProjectInfo() {
//        return updateProjectInfo(false);
//    }

    @Override
    public void doScan(boolean autoUpload) {
        doScan(autoUpload, v -> {
        }, v -> {
        });
    }

    @Override
    public void doScan(boolean autoUpload, Consumer<String> progressText, Consumer<Double> fraction) {
        progressText.accept("init project info start");
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        int threads = propertiesComponent.getInt(SkykomaConstants.DATA_SERVER_UPLOAD_THREADS, 16);
        long begin = System.currentTimeMillis();
        Project project = projectInfoDto.getProject();
        String scanId = genScanId();
        projectInfoDto.setScanId(scanId);
        if (autoUpload) {
            ScanRecordDto scanRecordDto = beginScan(new BeginScanPayload(scanId, projectInfoDto));
            if (scanRecordDto == null || !ScanRecordDto.STATUS_INIT.equals(scanRecordDto.getStatus())) {
                info(LOGGER, String.format("begin scan error, scanRecordDto = %s", scanRecordDto));
                return;
            }
        }
        // scanAllFiles
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
        if (autoUpload) {
            UploadProjectBasicInfoPayload payload = new UploadProjectBasicInfoPayload(scanId, projectInfoDto);
            ProjectInfoDto result = uploadProjectBasicInfoToServer(payload);
            waitScanStatus(scanId, null, ScanRecordDto.STATUS_UPLOADED);
        }
        progressText.accept("init project info end, scanning files");
        updateScanStatus(new UpdateScanStatusPayload(scanId, null, ScanRecordDto.STATUS_SCANNING));
        List<FileDto> neededUpload = new ArrayList<>();
        scanAllFiles(neededUpload::add);
        Map<String, Map<String, String>> errorMap = GsonUtils.reportErrorMap();
        progressText.accept("scanning files end");
        if (autoUpload) {
            uploadToServer(progressText, threads, scanId, neededUpload, errorMap);
        }
        long dur = System.currentTimeMillis() - begin;
        SkykomaNotifier.notifyInfo(String.format("scan finished, scanId = %s, project = %s, dur = %sms", scanId, project.getName(), dur));
    }

    private void uploadToServer(Consumer<String> progressText, int threads, String scanId, List<FileDto> neededUpload, Map<String, Map<String, String>> errorMap) {
        progressText.accept(String.format("scanning files end, uploading using %s threads", threads));
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch countDownLatch = new CountDownLatch(neededUpload.size());
        CountDownLatch gsonFinished = new CountDownLatch(neededUpload.size());
        neededUpload.forEach(fileDto -> executorService.execute(() -> {
            String relativePath = fileDto.getRelativePath();
            waitFileStatus(scanId, fileDto, FileDto.STATUS_GSON_FINISHED);
            gsonFinished.countDown();
            updateScanStatus(new UpdateScanStatusPayload(scanId, relativePath, ScanRecordDto.STATUS_SCANNING));
            fileDto.setStatus(ScanRecordDto.STATUS_SCANNING);
            UploadProjectFileInfoPayload payload = new UploadProjectFileInfoPayload(scanId, fileDto);
            FileDto result = uploadProjectFileInfoToServer(payload);
            waitScanStatus(scanId, fileDto, ScanRecordDto.STATUS_SCANNED);
            fileDto.setStatus(ScanRecordDto.STATUS_SCANNED);
            fileDto.setPsiFileJson("");
            countDownLatch.countDown();
        }));
        scheduleUpdateProgress(progressText, neededUpload);
        try {
            gsonFinished.await();
            info("errorMap report");
            errorMap.entrySet().forEach(entry -> entry.getValue().entrySet().forEach(method -> info(entry.getKey() + "|" + method.getKey() + "|" + method.getValue())));
        } catch (Exception e) {
            error(LOGGER, String.format("await error, e = %s", e.getMessage()), e);
        }
        try {
            countDownLatch.await();
        } catch (Exception e) {
            error(LOGGER, String.format("await error, e = %s", e.getMessage()), e);
        }
        updateScanStatus(new UpdateScanStatusPayload(scanId, null, ScanRecordDto.STATUS_FINISHED));
    }


    private static void scheduleUpdateProgress(Consumer<String> progressText, List<FileDto> neededUpload) {
        Thread monitor = new Thread(() -> {
            long start = System.currentTimeMillis();
            while (true) {
                long scanningFilesSize = neededUpload.stream().filter(v -> ScanRecordDto.STATUS_SCANNING.equals(v.getStatus())).count();
                long scannedFilesSize = neededUpload.stream().filter(v -> ScanRecordDto.STATUS_SCANNED.equals(v.getStatus())).count();
                long dur = System.currentTimeMillis() - start;
                progressText.accept(String.format("scanned files %s(%sscanning,%sscanned), dur = %s", neededUpload.size(), scanningFilesSize, scannedFilesSize, dur));
                if (scannedFilesSize == neededUpload.size()) {
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
            }

        });
        monitor.setName("monitor");
        monitor.start();
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

    private void scanAllFiles(Consumer<FileDto> fileDtoConsumer) {
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
        List<FileDto> scanTasks = new ArrayList<>();
        genFileScanTasks(rootFolder, scanTasks);
        info(LOGGER, String.format("genFileScanTasks finished, fileCount = [%s]", scanTasks.size()));
        Project project = projectInfoDto.getProject();
        PsiManager psiManager = PsiManager.getInstance(project);
        VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
        for (int i = 0; i < scanTasks.size(); i++) {
            FileDto fileDto = scanTasks.get(i);
            PsiFile psiFile = ReadAction.compute(() -> getPsiFile(fileDto, virtualFileManager, psiManager, srcRelativePaths));
            ;
            if (psiFile == null) {
                continue;
            }
            fileDto.setPsiFile(psiFile);
//            scanOneFile(fileDto);
            ReadAction.nonBlocking(() -> scanOneFile(fileDto))
                    .inSmartMode(project)
                    .expireWhen(() -> project.isDisposed())
                    .submit(AppExecutorUtil.getAppExecutorService());
//            if (StringUtils.isBlank(fileDto.getPsiFileJson())) {
//                continue;
//            }
            long begin2 = System.currentTimeMillis();
            fileDtoConsumer.accept(fileDto);
            info(LOGGER, String.format("scanOneFile file = [%s], callback end %s/%s, dur = %sms", fileDto.getName(), i + 1, scanTasks.size(), System.currentTimeMillis() - begin2));
        }
    }

    private void genFileScanTasks(FileDto fileDto, List<FileDto> scanTasks) {
        String type = fileDto.getType();
        if (type.equals("folder")) {
            fileDto.getSubFiles()
                    .forEach(v -> genFileScanTasks(v, scanTasks));
        } else if (type.equals("file")) {
            scanTasks.add(fileDto);
        } else {
        }
    }

    private void scanOneFile(FileDto fileDto) {
        long start = System.currentTimeMillis();
        info(LOGGER, String.format("scanOneFile file = [%s], begin,thread = %s", fileDto.getName(), Thread.currentThread().getName()));
        PsiFile psiFile = fileDto.getPsiFile();
        PsiElement[] childrenElements = psiFile.getChildren();
        String psiFileJson = ReadAction.compute(() -> {
            return PSI_GSON.toJson(childrenElements);
        });
        fileDto.setPsiFileJson(psiFileJson);
        fileDto.setStatus(FileDto.STATUS_GSON_FINISHED);
        long dur = System.currentTimeMillis() - start;
        info(LOGGER, String.format("scanOneFile file = [%s], end dur = %sms", fileDto.getName(), dur));
    }

    @Nullable
    private PsiFile getPsiFile(FileDto fileDto, VirtualFileManager virtualFileManager, PsiManager psiManager, Set<String> srcRelativePaths) {
        File file = fileDto.getFile();
        Path path = file.toPath();
        info(LOGGER, String.format("scanOneFile, scanning file = [%s]", path));
        // src files
        if (!inSrcPaths(fileDto, srcRelativePaths)) {
            LOGGER.warn(String.format("scanOneFile ignore non src folders, pathStr = [%s]", path));
            return null;
        }
        VirtualFile virtualFile = virtualFileManager.findFileByNioPath(path);
        if (virtualFile == null) {
            LOGGER.warn(String.format("scanOneFile error, virtualFile not found, pathStr = [%s]", path));
            return null;
        }
        String fileName = fileDto.getName();
        if (!fileName.endsWith(".java")) {
            LOGGER.warn(String.format("scanOneFile ignore non java, pathStr = [%s]", path));
            return null;
        }
        PsiFile psiFile = psiManager.findFile(virtualFile);
        if (psiFile == null) {
            LOGGER.warn(String.format("scanOneFile error, psiFile not found, pathStr = [%s]", path));
            return null;
        }
        fileDto.setStatus(FileDto.STATUS_WAIT);
        return psiFile;
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

    private String queryOrCreateProjectKey(ProjectInfoDto projectInfoDto) {
        ProjectInfoDto projectInfoDtoFromServer = queryProjectKeyFromServer(projectInfoDto);
        if (projectInfoDtoFromServer == null) {
            return null;
        }
//        String key = projectInfoDto.getKey();
//        if (!StringUtils.isEmpty(key)) {
//            return key;
//        }
        return projectInfoDtoFromServer.getKey();
    }

    private String genScanId() {
        return System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replaceAll("-", "");
    }

    private static final String UPDATE_PROJECT_BASIC_INFO_API = "%s/api/project/updateProjectBasicInfo";
    private static final String UPDATE_PROJECT_FILE_INFO_API = "%s/api/project/updateProjectFileInfo";
    private static final String QUERY_SCAN_STATUS_API = "%s/api/project/queryScan";
    private static final String BEGIN_SCAN_STATUS_API = "%s/api/project/beginScan";
    private static final String UPDATE_SCAN_STATUS_API = "%s/api/project/updateScanStatus";
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
        info(LOGGER, String.format("queryProjectKey, responseJson = [%s]", responseJson));
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

    private ProjectInfoDto uploadProjectBasicInfoToServer(UploadProjectBasicInfoPayload payload) {
        String requestJson = GSON.toJson(payload);
        // System.out.println(requestJson);
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        String apiHost = propertiesComponent.getValue(SkykomaConstants.DATA_SERVER_API_HOST);
        if (StringUtils.isEmpty(apiHost)) {
            return null;
        }
        String url = String.format(UPDATE_PROJECT_BASIC_INFO_API, apiHost);
        Type type = new TypeToken<JsonResult<ProjectInfoDto>>() {
        }.getType();
        String responseJson = httpService.postJsonBody(url, requestJson);
        info(LOGGER, String.format("upload projectBasicInfo, responseJson = [%s]", responseJson));
        if (StringUtils.isEmpty(responseJson)) {
            LOGGER.error("upload projectBasicInfo failed, response is empty");
            return null;
        }
        JsonResult<ProjectInfoDto> respone = null;
        try {
            respone = GSON.fromJson(responseJson, type);
        } catch (Exception e) {
            LOGGER.error(String.format("upload projectBasicInfo failed, response format error1, responseJson = [%s]",
                    responseJson), e);
            return null;
        }
        if (respone == null) {
            LOGGER.error(String.format("upload projectBasicInfo failed, response format error2, data = [%s]", responseJson));
            return null;
        }
        String code = respone.getCode();
        if (code == null) {
            LOGGER.error(String.format("upload projectBasicInfo failed, response format error3, code missing, data = [%s]",
                    responseJson));
            return null;
        }
        return respone.getData();
    }

    private FileDto uploadProjectFileInfoToServer(UploadProjectFileInfoPayload payload) {
        String requestJson = GSON.toJson(payload);
        // System.out.println(requestJson);
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        String apiHost = propertiesComponent.getValue(SkykomaConstants.DATA_SERVER_API_HOST);
        if (StringUtils.isEmpty(apiHost)) {
            return null;
        }
        String url = String.format(UPDATE_PROJECT_FILE_INFO_API, apiHost);
        Type type = new TypeToken<JsonResult<FileDto>>() {
        }.getType();
        String responseJson = httpService.postJsonBody(url, requestJson);
        info(LOGGER, String.format("upload projectFileInfo, file = [%s] responseJson = [%s]", payload.getFileDto().getName(), responseJson));
        if (StringUtils.isEmpty(responseJson)) {
            LOGGER.error("upload projectFileInfo failed, response is empty");
            return null;
        }
        JsonResult<FileDto> respone = null;
        try {
            respone = GSON.fromJson(responseJson, type);
        } catch (Exception e) {
            LOGGER.error(String.format("upload projectFileInfo failed, response format error1, responseJson = [%s]",
                    responseJson), e);
            return null;
        }
        if (respone == null) {
            LOGGER.error(String.format("upload projectFileInfo failed, response format error2, data = [%s]", responseJson));
            return null;
        }
        String code = respone.getCode();
        if (code == null) {
            LOGGER.error(String.format("upload projectFileInfo failed, response format error3, code missing, data = [%s]",
                    responseJson));
            return null;
        }
        return respone.getData();
    }

    private ScanRecordDto beginScan(BeginScanPayload payload) {
        String requestJson = GSON.toJson(payload);
        // System.out.println(requestJson);
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        String apiHost = propertiesComponent.getValue(SkykomaConstants.DATA_SERVER_API_HOST);
        if (StringUtils.isEmpty(apiHost)) {
            return null;
        }
        String url = String.format(BEGIN_SCAN_STATUS_API, apiHost);
        Type type = new TypeToken<JsonResult<ScanRecordDto>>() {
        }.getType();
        String responseJson = httpService.postJsonBody(url, requestJson);
        info(LOGGER, String.format("beginScan, responseJson = [%s]", responseJson));
        if (StringUtils.isEmpty(responseJson)) {
            LOGGER.error("beginScan failed, response is empty");
            return null;
        }
        JsonResult<ScanRecordDto> respone = null;
        try {
            respone = GSON.fromJson(responseJson, type);
        } catch (Exception e) {
            LOGGER.error(String.format("beginScan failed, response format error1, responseJson = [%s]",
                    responseJson), e);
            return null;
        }
        if (respone == null) {
            LOGGER.error(String.format("beginScan failed, response format error2, data = [%s]", responseJson));
            return null;
        }
        String code = respone.getCode();
        if (code == null) {
            LOGGER.error(String.format("beginScan failed, response format error3, code missing, data = [%s]",
                    responseJson));
            return null;
        }
        return respone.getData();
    }

    private void waitFileStatus(String scanId, FileDto fileDto, String waitStatus) {
        long start = System.currentTimeMillis();
        String fileName = "";
        if (fileDto != null) {
            fileName = fileDto.getName();
        }
        while (true) {
            boolean succ = fileDto != null && waitStatus.equals(fileDto.getStatus());
            long dur = System.currentTimeMillis() - start;
            info(LOGGER, String.format("waiting for file status to %s, dur = %sms, succ = %s, scanId = %s, file = %s",
                    waitStatus, dur, succ, scanId, fileName));
            if (succ) {
                return;
            }
            try {
                Thread.sleep(5000);
            } catch (Exception e) {

            }
        }
    }

    private void waitScanStatus(String scanId, FileDto fileDto, String waitStatus) {
        long start = System.currentTimeMillis();
        String relativePath = "";
        String fileName = "";
        if (fileDto != null) {
            relativePath = fileDto.getRelativePath();
            fileName = fileDto.getName();
        }
        while (true) {
            ScanRecordDto scanRecordDto = queryScanStatus(new QueryScanPayload(scanId, relativePath));
            boolean succ = scanRecordDto != null && waitStatus.equals(scanRecordDto.getStatus());
            long dur = System.currentTimeMillis() - start;
            info(LOGGER, String.format("waiting for status to %s, dur = %sms, succ = %s, scanId = %s, file = %s",
                    waitStatus, dur, succ, scanId, fileName));
            if (succ) {
                return;
            }
            try {
                Thread.sleep(5000);
            } catch (Exception e) {

            }
        }
    }

    private ScanRecordDto queryScanStatus(QueryScanPayload payload) {
        String requestJson = GSON.toJson(payload);
        // System.out.println(requestJson);
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        String apiHost = propertiesComponent.getValue(SkykomaConstants.DATA_SERVER_API_HOST);
        if (StringUtils.isEmpty(apiHost)) {
            return null;
        }
        String url = String.format(QUERY_SCAN_STATUS_API, apiHost);
        Type type = new TypeToken<JsonResult<ScanRecordDto>>() {
        }.getType();
        String responseJson = httpService.postJsonBody(url, requestJson);
        info(LOGGER, String.format("queryScanStatus payload = [%s], responseJson = [%s]", payload, responseJson));
        if (StringUtils.isEmpty(responseJson)) {
            LOGGER.error("queryScanStatus failed, response is empty");
            return null;
        }
        JsonResult<ScanRecordDto> respone = null;
        try {
            respone = GSON.fromJson(responseJson, type);
        } catch (Exception e) {
            LOGGER.error(String.format("queryScanStatus failed, response format error1, responseJson = [%s]",
                    responseJson), e);
            return null;
        }
        if (respone == null) {
            LOGGER.error(String.format("queryScanStatus failed, response format error2, data = [%s]", responseJson));
            return null;
        }
        String code = respone.getCode();
        if (code == null) {
            LOGGER.error(String.format("queryScanStatus failed, response format error3, code missing, data = [%s]",
                    responseJson));
            return null;
        }
        return respone.getData();
    }

    private ScanRecordDto updateScanStatus(UpdateScanStatusPayload payload) {
        String requestJson = GSON.toJson(payload);
        // System.out.println(requestJson);
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        String apiHost = propertiesComponent.getValue(SkykomaConstants.DATA_SERVER_API_HOST);
        if (StringUtils.isEmpty(apiHost)) {
            return null;
        }
        String url = String.format(UPDATE_SCAN_STATUS_API, apiHost);
        Type type = new TypeToken<JsonResult<ScanRecordDto>>() {
        }.getType();
        String responseJson = httpService.postJsonBody(url, requestJson);
        info(LOGGER, String.format("updateScanStatus, responseJson = [%s]", responseJson));
        if (StringUtils.isEmpty(responseJson)) {
            LOGGER.error("updateScanStatus failed, response is empty");
            return null;
        }
        JsonResult<ScanRecordDto> respone = null;
        try {
            respone = GSON.fromJson(responseJson, type);
        } catch (Exception e) {
            LOGGER.error(String.format("updateScanStatus failed, response format error1, responseJson = [%s]",
                    responseJson), e);
            return null;
        }
        if (respone == null) {
            LOGGER.error(String.format("updateScanStatus failed, response format error2, data = [%s]", responseJson));
            return null;
        }
        String code = respone.getCode();
        if (code == null) {
            LOGGER.error(String.format("updateScanStatus failed, response format error3, code missing, data = [%s]",
                    responseJson));
            return null;
        }
        return respone.getData();
    }
}
