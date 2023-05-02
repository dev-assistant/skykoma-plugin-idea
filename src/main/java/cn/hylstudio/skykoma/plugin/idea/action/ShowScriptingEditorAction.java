package cn.hylstudio.skykoma.plugin.idea.action;

import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.script.IdeConsoleRootType;
import com.intellij.ide.script.IdeScriptEngineManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class ShowScriptingEditorAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(ShowScriptingEditorAction.class);

    private static final String DEFAULT_FILE_NAME = "ide-scripting";

    private static final Key<IdeScriptEngineManager.EngineInfo> SELECTED_ENGINE_INFO_KEY = Key.create("SELECTED_ENGINE_INFO_KEY");

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        IdeScriptEngineManager ideScriptEngineManager = IdeScriptEngineManager.getInstance();
        List<IdeScriptEngineManager.EngineInfo> infos = ideScriptEngineManager.getEngineInfos();
        infos = infos.stream()
                .filter(v -> v.plugin != null && v.plugin.getPluginId().getIdString().equals("cn.hylstudio.skykoma.plugin.idea"))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(infos)) {
            return;
        }
        IdeScriptEngineManager.EngineInfo engineInfo = infos.get(0);
        runConsole(project, engineInfo);
    }

    private static void runConsole(@NotNull Project project, @NotNull IdeScriptEngineManager.EngineInfo engineInfo) {
        VirtualFile virtualFile = null;
        try {
            List<String> extensions = engineInfo.fileExtensions;
            String pathName = PathUtil.makeFileName(DEFAULT_FILE_NAME, ContainerUtil.getFirstItem(extensions));
            virtualFile = IdeConsoleRootType.getInstance().findFile(project, pathName, ScratchFileService.Option.create_if_missing);
        } catch (IOException ex) {
            LOG.error(ex);
        }
        if (virtualFile == null) {
            return;
        }
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        fileEditorManager.openFile(virtualFile, true);
        Editor editor = getEditor(fileEditorManager, virtualFile);
        if (editor == null) return;
        virtualFile.putUserData(SELECTED_ENGINE_INFO_KEY, engineInfo);
    }

    @Nullable
    private static Editor getEditor(FileEditorManager source, VirtualFile file) {
        for (FileEditor fileEditor : source.getEditors(file)) {
            if (!(fileEditor instanceof TextEditor)) continue;
            Editor editor = ((TextEditor) fileEditor).getEditor();
            return editor;
        }
        return null;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(true);
    }

}