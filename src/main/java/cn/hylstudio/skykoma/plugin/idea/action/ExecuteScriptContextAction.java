package cn.hylstudio.skykoma.plugin.idea.action;

import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.script.IdeConsoleRootType;
import com.intellij.ide.script.IdeConsoleScriptBindings;
import com.intellij.ide.script.IdeScriptEngine;
import com.intellij.ide.script.IdeScriptEngineManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.content.Content;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Pair;
import kotlin.Unit;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.cli.common.repl.AggregatedReplStageState;
import org.jetbrains.kotlin.cli.common.repl.EvalClassWithInstanceAndLoader;
import org.jetbrains.kotlin.cli.common.repl.IReplStageHistory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ExecuteScriptContextAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(ExecuteScriptContextAction.class);

    private static final String DEFAULT_FILE_NAME = "ide-scripting";

    private static final Key<Trinity<IdeScriptEngine, IdeScriptEngineManager.EngineInfo, VirtualFile>> SCRIPT_ENGINE_KEY =
            Key.create("SCRIPT_ENGINE_KEY");

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        IdeScriptEngineManager.EngineInfo engineInfo = getEngineInfo();
        if (engineInfo == null) return;
        VirtualFile virtualFile = getSelectedScriptFile(project, engineInfo);//TODO multi script file mgmt
        if (virtualFile == null) return;
        Editor editor = getEditor(project, virtualFile);
        if (editor == null) return;
        executeScript(project, engineInfo, virtualFile, editor);
    }

    private void executeScript(Project project, IdeScriptEngineManager.EngineInfo engineInfo, VirtualFile virtualFile, Editor editor) {
        String script = getCommandText(project, editor);
        if (StringUtil.isEmptyOrSpaces(script)) return;
        RunContentDescriptor descriptor = prepareConsoleView(project, virtualFile, engineInfo);
        Content content = descriptor.getAttachedContent();
        if (content == null) return;
        ScriptEngineProvider scriptEngineProvider = this::genScriptEngine;
        IdeScriptEngine engine = scriptEngineProvider.getEngine(engineInfo, content);
        if (engine == null) return;
        if (content.getUserData(SCRIPT_ENGINE_KEY) == null) {
            content.putUserData(SCRIPT_ENGINE_KEY, Trinity.create(engine, engineInfo, virtualFile));
        }
        ConsoleViewImpl consoleView = (ConsoleViewImpl) descriptor.getExecutionConsole();
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        IdeConsoleScriptBindings.ensureIdeIsBound(project, engine);
        ensureOutputIsRedirected(engine, descriptor);
        doExecute(script, engine, consoleView);
        selectContent(descriptor);
        updateHistory(engine, consoleView);
    }

    @Nullable
    private static IdeScriptEngineManager.EngineInfo getEngineInfo() {
        IdeScriptEngineManager ideScriptEngineManager = IdeScriptEngineManager.getInstance();
        List<IdeScriptEngineManager.EngineInfo> infos = ideScriptEngineManager.getEngineInfos();
        infos = infos.stream()
                .filter(v -> v.plugin != null && v.plugin.getPluginId().getIdString().equals("cn.hylstudio.skykoma.plugin.idea"))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(infos)) {
            return null;
        }
        IdeScriptEngineManager.EngineInfo engineInfo = infos.get(0);
        return engineInfo;
    }

    @Nullable
    private static VirtualFile getSelectedScriptFile(Project project, IdeScriptEngineManager.EngineInfo engineInfo) {
        VirtualFile virtualFile = null;
        try {
            List<String> extensions = engineInfo.fileExtensions;
            String pathName = PathUtil.makeFileName(DEFAULT_FILE_NAME, ContainerUtil.getFirstItem(extensions));
            virtualFile = IdeConsoleRootType.getInstance().findFile(project, pathName, ScratchFileService.Option.create_if_missing);
        } catch (IOException ex) {
            LOG.error(ex);
        }
        return virtualFile;
    }

    @Nullable
    private static Editor getEditor(Project project, VirtualFile file) {
        FileEditorManager source = FileEditorManager.getInstance(project);
        for (FileEditor fileEditor : source.getEditors(file)) {
            if (!(fileEditor instanceof TextEditor)) continue;
            Editor editor = ((TextEditor) fileEditor).getEditor();
            return editor;
        }
        return null;
    }


    @Nullable
    private IdeScriptEngine genScriptEngine(@NotNull IdeScriptEngineManager.EngineInfo engineInfo, Content content) {
        Trinity<IdeScriptEngine, IdeScriptEngineManager.EngineInfo, VirtualFile> data = content.getUserData(SCRIPT_ENGINE_KEY);
        if (data != null) {
            return data.first;
        }
        IdeScriptEngineManager ideScriptEngineManager = IdeScriptEngineManager.getInstance();
        return ideScriptEngineManager.getEngine(engineInfo, null);
    }

    private static void doExecute(@NotNull String script, IdeScriptEngine engine, ConsoleViewImpl consoleView) {
        try {
            long ts = System.currentTimeMillis();
            //myHistoryController.getModel().addToHistory(command);
            consoleView.print("> " + script, ConsoleViewContentType.USER_INPUT);
            consoleView.print("\n", ConsoleViewContentType.USER_INPUT);
            Object o = engine.eval(script);
            String suffix = "[" + (StringUtil.formatDuration(System.currentTimeMillis() - ts)) + "]";
            consoleView.print(o + " " + suffix, ConsoleViewContentType.NORMAL_OUTPUT);
            consoleView.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
        } catch (Throwable e) {
            Throwable ex = ExceptionUtil.getRootCause(e);
            String message = StringUtil.notNullize(StringUtil.nullize(ex.getMessage()), ex.toString());
            consoleView.print(ex.getClass().getSimpleName() + ": " + message, ConsoleViewContentType.ERROR_OUTPUT);
            consoleView.print("\n", ConsoleViewContentType.ERROR_OUTPUT);
        }
    }

    private static void updateHistory(IdeScriptEngine engine, ConsoleViewImpl consoleView) {
        Object binding = engine.getBinding("kotlin.script.state");
        if (binding == null) {
            return;
        }
        try {
            AggregatedReplStageState<Unit, EvalClassWithInstanceAndLoader> aggregatedReplStageState = (AggregatedReplStageState) binding;
            IReplStageHistory<Pair<Unit, EvalClassWithInstanceAndLoader>> history = aggregatedReplStageState.getHistory();
            System.out.println(history);
        } catch (Throwable ex) {
            consoleView.print(ex.getMessage(), ConsoleViewContentType.ERROR_OUTPUT);
        }
    }


    @NotNull
    private static String getCommandText(@NotNull Project project, @NotNull Editor editor) {
        TextRange selectedRange = EditorUtil.getSelectionInAnyMode(editor);
        Document document = editor.getDocument();
        if (!selectedRange.isEmpty()) {
            return document.getText(selectedRange);
        }
        int line = document.getLineNumber(selectedRange.getStartOffset());
        int lineStart = document.getLineStartOffset(line);
        int lineEnd = document.getLineEndOffset(line);
        String lineText = document.getText(TextRange.create(lineStart, lineEnd));

        // try to detect a non-trivial composite PSI element if there's a PSI file
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (file != null && !StringUtil.isEmptyOrSpaces(lineText)) {
            int start = lineStart, end = lineEnd;
            while (start < end && Character.isWhitespace(lineText.charAt(start - lineStart))) start++;
            while (end > start && Character.isWhitespace(lineText.charAt(end - 1 - lineStart))) end--;
            PsiElement e1 = file.findElementAt(start);
            PsiElement e2 = file.findElementAt(end > start ? end - 1 : end);
            PsiElement parent = e1 != null && e2 != null ? PsiTreeUtil.findCommonParent(e1, e2) : ObjectUtils.chooseNotNull(e1, e2);
            if (parent != null && parent != file) {
                TextRange combined = parent.getTextRange().union(TextRange.create(lineStart, lineEnd));
                editor.getSelectionModel().setSelection(combined.getStartOffset(), combined.getEndOffset());
                return document.getText(combined);
            }
        }
        return lineText;
    }

    private static void selectContent(RunContentDescriptor descriptor) {
        Executor executor = DefaultRunExecutor.getRunExecutorInstance();
        ConsoleViewImpl consoleView = Objects.requireNonNull((ConsoleViewImpl) descriptor.getExecutionConsole());
        RunContentManager.getInstance(consoleView.getProject()).toFrontRunContent(executor, descriptor);
    }

    @NotNull
    private static RunContentDescriptor prepareConsoleView(@NotNull Project project,
                                                           @NotNull VirtualFile file,
                                                           @NotNull IdeScriptEngineManager.EngineInfo engineInfo) {
        for (RunContentDescriptor existing : RunContentManager.getInstance(project).getAllDescriptors()) {
            Content content = existing.getAttachedContent();
            if (content == null) continue;
            Trinity<IdeScriptEngine, IdeScriptEngineManager.EngineInfo, VirtualFile> data = content.getUserData(SCRIPT_ENGINE_KEY);
            if (data == null) continue;
            if (!Comparing.equal(file, data.third)) continue;
            if (!Comparing.equal(engineInfo, data.second)) continue;
            return existing;
        }

        ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();

        DefaultActionGroup toolbarActions = new DefaultActionGroup();
        JComponent panel = new JPanel(new BorderLayout());
        panel.add(consoleView.getComponent(), BorderLayout.CENTER);
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("RunIdeConsole", toolbarActions, false);
        toolbar.setTargetComponent(consoleView.getComponent());
        panel.add(toolbar.getComponent(), BorderLayout.WEST);

        RunContentDescriptor descriptor = new RunContentDescriptor(consoleView, null, panel, file.getName()) {
            @Override
            public boolean isContentReuseProhibited() {
                return true;
            }
        };
        Executor executor = DefaultRunExecutor.getRunExecutorInstance();
        toolbarActions.addAll(consoleView.createConsoleActions());
        toolbarActions.add(new CloseAction(executor, descriptor, project));
        RunContentManager.getInstance(project).showRunContent(executor, descriptor);

        return descriptor;
    }

    private static void ensureOutputIsRedirected(@NotNull IdeScriptEngine engine, @NotNull RunContentDescriptor
            descriptor) {
        ConsoleWriter stdOutWriter = ObjectUtils.tryCast(engine.getStdOut(), ConsoleWriter.class);
        ConsoleWriter stdErrWriter = ObjectUtils.tryCast(engine.getStdErr(), ConsoleWriter.class);
        if (stdOutWriter != null && stdOutWriter.getDescriptor() == descriptor &&
                stdErrWriter != null && stdErrWriter.getDescriptor() == descriptor) {
            return;
        }

        WeakReference<RunContentDescriptor> ref = new WeakReference<>(descriptor);
        engine.setStdOut(new ConsoleWriter(ref, ProcessOutputTypes.STDOUT));
        engine.setStdErr(new ConsoleWriter(ref, ProcessOutputTypes.STDERR));
    }


    @Override
    public void update(@NotNull final AnActionEvent e) {
        final Editor editor = e.getData(CommonDataKeys.EDITOR);
        // Set visibility and enable only in case of existing project and editor and if a selection exists
        e.getPresentation().setEnabledAndVisible(
                editor != null && editor.getSelectionModel().hasSelection()
        );
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}