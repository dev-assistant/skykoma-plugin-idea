// Copyright 2000-2022 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package cn.hylstudio.skykoma.plugin.idea.action;


import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.AnsiEscapeDecoder;
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
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
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
import java.io.Writer;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TestAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(TestAction.class);

    private static final String DEFAULT_FILE_NAME = "ide-scripting";

    private static final Key<IdeScriptEngineManager.EngineInfo> SELECTED_ENGINE_INFO_KEY = Key.create("SELECTED_ENGINE_INFO_KEY");
    private static final Key<Trinity<IdeScriptEngine, IdeScriptEngineManager.EngineInfo, VirtualFile>> SCRIPT_ENGINE_KEY =
            Key.create("SCRIPT_ENGINE_KEY");

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        IdeScriptEngineManager ideScriptEngineManager = IdeScriptEngineManager.getInstance();
        List<IdeScriptEngineManager.EngineInfo> infos = ideScriptEngineManager.getEngineInfos();
        infos = infos.stream()
                .filter(v -> v.languageName.equalsIgnoreCase("kotlin"))
                .filter(v -> v.plugin.getPluginId().getIdString().equals("cn.hylstudio.skykoma.plugin.idea"))
//                .filter(v -> v.engineName.startsWith("SkyKoma"))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(infos)) {
            return;
        }
        IdeScriptEngineManager.EngineInfo engineInfo = infos.get(0);
        runConsole(project, engineInfo);
    }

    private static void runConsole(@NotNull Project project, @NotNull IdeScriptEngineManager.EngineInfo engineInfo) {
        List<String> extensions = engineInfo.fileExtensions;
        try {
            String pathName = PathUtil.makeFileName(DEFAULT_FILE_NAME, ContainerUtil.getFirstItem(extensions));
            VirtualFile virtualFile = IdeConsoleRootType.getInstance().findFile(project, pathName, ScratchFileService.Option.create_if_missing);
            if (virtualFile == null) {
                return;
            }
            FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
            Editor editor = getEditor(fileEditorManager, virtualFile);
            fileEditorManager.openFile(virtualFile, true);
            if (editor == null) return;
            virtualFile.putUserData(SELECTED_ENGINE_INFO_KEY, engineInfo);
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            executeQuery(project, virtualFile, editor, engineInfo);
        } catch (IOException ex) {
            LOG.error(ex);
        }
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

    private static void executeQuery(@NotNull Project project,
                                     @NotNull VirtualFile file,
                                     @NotNull Editor editor,
                                     @NotNull IdeScriptEngineManager.EngineInfo engineInfo) {
        String command = getCommandText(project, editor);
        if (StringUtil.isEmptyOrSpaces(command)) return;
        String profile = getProfileText(file);
        RunContentDescriptor descriptor = getConsoleView(project, file, engineInfo);
        IdeScriptEngine engine;
        Content content = descriptor.getAttachedContent();
        if (content == null) {
            LOG.error("Attached content expected");
            return;
        }
        Trinity<IdeScriptEngine, IdeScriptEngineManager.EngineInfo, VirtualFile> data = content.getUserData(SCRIPT_ENGINE_KEY);
        if (data != null) {
            engine = data.first;
        } else {
            engine = IdeScriptEngineManager.getInstance().getEngine(engineInfo, null);
            if (engine == null) {
                LOG.error("Script engine not found for: " + file.getName());
                return;
            }
            content.putUserData(SCRIPT_ENGINE_KEY, Trinity.create(engine, engineInfo, file));
        }

        ConsoleViewImpl consoleView = (ConsoleViewImpl) descriptor.getExecutionConsole();

        prepareEngine(project, engine, descriptor);
        try {
            processHistory(engine, consoleView);
            long ts = System.currentTimeMillis();
            //myHistoryController.getModel().addToHistory(command);
            consoleView.print("> " + command, ConsoleViewContentType.USER_INPUT);
            consoleView.print("\n", ConsoleViewContentType.USER_INPUT);
            String script = profile == null ? command : profile + "\n" + command;
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
        selectContent(descriptor);
    }

    private static void processHistory(IdeScriptEngine engine, ConsoleViewImpl consoleView) throws NoSuchFieldException, IllegalAccessException {
        Object binding = engine.getBinding("kotlin.script.state");
        if (binding == null) {
            return;
        }
//        Field myLoaderField = engine.getClass().getDeclaredField("myLoader");
//        myLoaderField.setAccessible(true);
//        ClassLoader myclassLoader = (ClassLoader) myLoaderField.get(engine);
        ClassLoader bindingClassLoader = binding.getClass().getClassLoader();
        ClassLoaderUtil.computeWithClassLoader(bindingClassLoader, () -> {
            try {
                AggregatedReplStageState<Unit, EvalClassWithInstanceAndLoader> aggregatedReplStageState = (AggregatedReplStageState) binding;
                IReplStageHistory<Pair<Unit, EvalClassWithInstanceAndLoader>> history = aggregatedReplStageState.getHistory();
                System.out.println("Test");
            } catch (Throwable ex) {
                consoleView.print(ex.getMessage(), ConsoleViewContentType.ERROR_OUTPUT);
            }
            return null;
        });
    }

    private static void prepareEngine(@NotNull Project project, @NotNull IdeScriptEngine engine, @NotNull RunContentDescriptor descriptor) {
        IdeConsoleScriptBindings.ensureIdeIsBound(project, engine);
        ensureOutputIsRedirected(engine, descriptor);
    }

    @Nullable
    private static String getProfileText(@NotNull VirtualFile file) {
        try {
            VirtualFile folder = file.getParent();
            VirtualFile profileChild = folder == null ? null : folder.findChild(".profile." + file.getExtension());
            return profileChild == null ? null : StringUtil.nullize(VfsUtilCore.loadText(profileChild));
        } catch (IOException ignored) {
        }
        return null;
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
    private static RunContentDescriptor getConsoleView(@NotNull Project project,
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

    private static void ensureOutputIsRedirected(@NotNull IdeScriptEngine engine, @NotNull RunContentDescriptor descriptor) {
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

    private static final class ConsoleWriter extends Writer {
        private final @NotNull Reference<? extends RunContentDescriptor> myDescriptor;
        private final Key<?> myOutputType;
        private final AnsiEscapeDecoder myAnsiEscapeDecoder;

        private ConsoleWriter(@NotNull Reference<? extends RunContentDescriptor> descriptor, Key<?> outputType) {
            myDescriptor = descriptor;
            myOutputType = outputType;
            myAnsiEscapeDecoder = new AnsiEscapeDecoder();
        }

        @Nullable
        public RunContentDescriptor getDescriptor() {
            return myDescriptor.get();
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            RunContentDescriptor descriptor = myDescriptor.get();
            ConsoleViewImpl console = ObjectUtils.tryCast(descriptor != null ? descriptor.getExecutionConsole() : null, ConsoleViewImpl.class);
            String text = new String(cbuf, off, len);
            if (console == null) {
                LOG.info(myOutputType + ": " + text);
            } else {
                myAnsiEscapeDecoder.escapeText(text, myOutputType, (s, attr) -> {
                    console.print(s, ConsoleViewContentType.getConsoleViewType(attr));
                });
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(true);
    }

}