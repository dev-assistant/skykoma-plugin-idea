package cn.hylstudio.skykoma.plugin.idea.toolwindow;

import cn.hylstudio.skykoma.plugin.idea.SkykomaConstants;
import cn.hylstudio.skykoma.plugin.idea.service.IdeaPluginAgentServer;
import cn.hylstudio.skykoma.plugin.idea.service.PythonEnvService;
import cn.hylstudio.skykoma.plugin.idea.service.SkykomaConsoleService;
import cn.hylstudio.skykoma.plugin.idea.service.impl.SkykomaConsoleServiceImpl;
import cn.hylstudio.skykoma.plugin.idea.util.ProjectUtils;
import cn.hylstudio.skykoma.plugin.idea.util.SkykomaNotifier;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.WrapLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SkykomaToolWindowFactory implements ToolWindowFactory, DumbAware {

    private static final AtomicReference<Process> jupyterLabProcessRef = new AtomicReference<>();
    private static final AtomicReference<String> initVenvStatus = new AtomicReference<>("IDLE");
    private static final String COLLAPSED_PREFIX = "skykoma.collapsed.";

    private static boolean isSectionCollapsed(String key) {
        return PropertiesComponent.getInstance().getBoolean(COLLAPSED_PREFIX + key, false);
    }

    private static void setSectionCollapsed(String key, boolean collapsed) {
        PropertiesComponent.getInstance().setValue(COLLAPSED_PREFIX + key, collapsed);
    }

    private static TitledSeparator createCollapsibleSeparator(String title, String collapseKey, JComponent contentPanel) {
        TitledSeparator separator = new TitledSeparator(title);
        separator.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        boolean collapsed = isSectionCollapsed(collapseKey);
        contentPanel.setVisible(!collapsed);
        updateSeparatorText(separator, title, collapsed);

        separator.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boolean wasVisible = contentPanel.isVisible();
                contentPanel.setVisible(!wasVisible);
                setSectionCollapsed(collapseKey, wasVisible);
                updateSeparatorText(separator, title, wasVisible);
                Container parent = contentPanel.getParent();
                if (parent != null) {
                    parent.revalidate();
                    parent.repaint();
                }
            }
        });
        return separator;
    }

    private static void updateSeparatorText(TitledSeparator separator, String title, boolean collapsed) {
        separator.setText(collapsed ? "\u25B6 " + title : "\u25BC " + title);
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        SkykomaConsoleServiceImpl consoleServiceImpl = (SkykomaConsoleServiceImpl) ApplicationManager.getApplication()
                .getService(SkykomaConsoleService.class);
        consoleServiceImpl.initConsole(project);

        JComponent rootComponent = createRootComponent(project);
        Content content = ContentFactory.getInstance().createContent(rootComponent, null, false);
        toolWindow.getContentManager().addContent(content);
    }

    private static void bindSaveOnChange(TextFieldWithBrowseButton field, String key) {
        field.getTextField().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { save(); }
            @Override
            public void removeUpdate(DocumentEvent e) { save(); }
            @Override
            public void changedUpdate(DocumentEvent e) { save(); }
            private void save() {
                PropertiesComponent.getInstance().setValue(key, field.getText());
            }
        });
    }

    private static JComponent makeStatusToolbar(AnActionButton... actions) {
        DefaultActionGroup group = new DefaultActionGroup();
        for (AnActionButton action : actions) {
            group.add(action);
        }
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("skykoma-status", group, true);
        toolbar.setTargetComponent(null);
        return toolbar.getComponent();
    }

    private JComponent createRootComponent(Project project) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane scrollPane = new JBScrollPane(contentPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        container.add(scrollPane, BorderLayout.CENTER);

        VerticalBox vbox = new VerticalBox();
        contentPanel.add(vbox, BorderLayout.NORTH);

        AtomicReference<TextFieldWithBrowseButton> jupyterPythonPathSelectorRef = new AtomicReference<>();


        JPanel agentServerContent = new JPanel();
        agentServerContent.setLayout(new BoxLayout(agentServerContent, BoxLayout.Y_AXIS));

        JPanel agentServerStatusPanel = new JBPanel<>(new BorderLayout());
        agentServerStatusPanel.add(new JBLabel("Status:"), BorderLayout.WEST);
        JBLabel agentServerStatusLabel = new JBLabel("UNKNOWN");
        agentServerStatusPanel.add(agentServerStatusLabel, BorderLayout.CENTER);
        JComponent agentServerToolbar = makeStatusToolbar(
                new AnActionButton("Start", AllIcons.Actions.Execute) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) { getIdeaPluginAgentServer().start(); }
                },
                new AnActionButton("Stop", AllIcons.Actions.Suspend) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) { getIdeaPluginAgentServer().stop(); }
                },
                new AnActionButton("Restart", AllIcons.Actions.Restart) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) { getIdeaPluginAgentServer().restart(); }
                },
                new AnActionButton("Refresh", AllIcons.Actions.Refresh) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        IdeaPluginAgentServer server = getIdeaPluginAgentServer();
                        agentServerStatusLabel.setText(server.isAgentServerRunning() ? "RUNNING" : "STOPPED");
                    }
                }
        );
        agentServerStatusPanel.add(agentServerToolbar, BorderLayout.EAST);
        agentServerContent.add(agentServerStatusPanel);

        AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
            IdeaPluginAgentServer server = getIdeaPluginAgentServer();
            ApplicationManager.getApplication().invokeLater(() ->
                    agentServerStatusLabel.setText(server.isAgentServerRunning() ? "RUNNING" : "STOPPED"));
        }, 0, 1, TimeUnit.SECONDS);

        vbox.add(createCollapsibleSeparator("Agent Server", "agentServer", agentServerContent));
        vbox.add(agentServerContent);


        JPanel pythonEnvContent = new JPanel();
        pythonEnvContent.setLayout(new BoxLayout(pythonEnvContent, BoxLayout.Y_AXIS));

        JPanel python312PathPanel = new JBPanel<>(new BorderLayout());
        TextFieldWithBrowseButton python312PathSelector = new TextFieldWithBrowseButton();
        FileChooserDescriptor singleFile = new FileChooserDescriptor(true, false, false, false, false, false);
        python312PathSelector.addBrowseFolderListener(new TextBrowseFolderListener(singleFile.withTitle("Select Python 3.12 Executable"), project));
        String savedPython312Path = PropertiesComponent.getInstance().getValue(SkykomaConstants.PYTHON312_EXECUTABLE, SkykomaConstants.PYTHON312_EXECUTABLE_DEFAULT);
        python312PathSelector.setText(savedPython312Path);
        bindSaveOnChange(python312PathSelector, SkykomaConstants.PYTHON312_EXECUTABLE);
        JBLabel python312Label = new JBLabel("Python 3.12:");
        python312PathPanel.add(python312Label, BorderLayout.WEST);
        python312PathPanel.add(python312PathSelector, BorderLayout.CENTER);
        pythonEnvContent.add(python312PathPanel);

        JPanel venvPathPanel = new JBPanel<>(new BorderLayout());
        TextFieldWithBrowseButton venvPathSelector = new TextFieldWithBrowseButton();
        FileChooserDescriptor singleDirVenv = new FileChooserDescriptor(false, true, false, false, false, false);
        venvPathSelector.addBrowseFolderListener(new TextBrowseFolderListener(singleDirVenv.withTitle("Select Virtualenv Path"), project));
        String savedVenvPath = PropertiesComponent.getInstance().getValue(SkykomaConstants.PYTHON_VENV_PATH, SkykomaConstants.PYTHON_VENV_PATH_DEFAULT);
        venvPathSelector.setText(savedVenvPath);
        bindSaveOnChange(venvPathSelector, SkykomaConstants.PYTHON_VENV_PATH);
        venvPathPanel.add(new JBLabel("Venv Path:"), BorderLayout.WEST);
        venvPathPanel.add(venvPathSelector, BorderLayout.CENTER);
        pythonEnvContent.add(venvPathPanel);

        JPanel venvStatusPanel = new JBPanel<>(new BorderLayout());
        venvStatusPanel.add(new JBLabel("Venv Status:"), BorderLayout.WEST);
        JBLabel venvStatusLabel = new JBLabel("Unknown");
        venvStatusPanel.add(venvStatusLabel, BorderLayout.CENTER);
        JComponent venvToolbar = makeStatusToolbar(
                new AnActionButton("Init Venv", AllIcons.Actions.Download) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        if (!initVenvStatus.compareAndSet("IDLE", "RUNNING")) {
                            SkykomaNotifier.notifyInfo("initVenv is already running, please wait...");
                            return;
                        }
                        ApplicationManager.getApplication().executeOnPooledThread(() -> {
                            try {
                                PythonEnvService pythonEnvService = getPythonEnvService();
                                String venvPath = venvPathSelector.getText();
                                String pythonExe = python312PathSelector.getText();
                                if (pythonExe == null || pythonExe.isEmpty() || pythonExe.equals("python")) {
                                    pythonExe = pythonEnvService.findPython312();
                                    if (pythonExe == null) {
                                        String downloadUrl = PropertiesComponent.getInstance().getValue(SkykomaConstants.PYTHON_DOWNLOAD_URL, SkykomaConstants.PYTHON_DOWNLOAD_URL_DEFAULT);
                                        SkykomaNotifier.notifyError("Python 3.12 not found. Please install manually: " + downloadUrl);
                                        return;
                                    }
                                    python312PathSelector.setText(pythonExe);
                                    PropertiesComponent.getInstance().setValue(SkykomaConstants.PYTHON312_EXECUTABLE, pythonExe);
                                }
                                String pipPackages = PropertiesComponent.getInstance().getValue(SkykomaConstants.PYTHON_PIP_PACKAGES, SkykomaConstants.PYTHON_PIP_PACKAGES_DEFAULT);
                                String pipMirror = PropertiesComponent.getInstance().getValue(SkykomaConstants.PYTHON_PIP_MIRROR, SkykomaConstants.PYTHON_PIP_MIRROR_DEFAULT);
                                SkykomaConsoleService cs = getConsoleService();
                                ApplicationManager.getApplication().invokeLater(() -> cs.clear());
                                String result = pythonEnvService.initVenv(venvPath, pythonExe, pipPackages, pipMirror,
                                        line -> ApplicationManager.getApplication().invokeLater(() -> cs.appendInfo(line)));
                                if (result != null) {
                                    ApplicationManager.getApplication().invokeLater(() -> {
                                        jupyterPythonPathSelectorRef.get().setText(result);
                                        PropertiesComponent.getInstance().setValue(SkykomaConstants.JUPYTER_PYTHON_EXECUTABLE, result);
                                        venvStatusLabel.setText("Initialized");
                                    });
                                }
                            } finally {
                                initVenvStatus.set("IDLE");
                            }
                        });
                    }
                },
                new AnActionButton("Refresh", AllIcons.Actions.Refresh) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        ApplicationManager.getApplication().executeOnPooledThread(() -> {
                            PythonEnvService pythonEnvService = getPythonEnvService();
                            String venvPath = venvPathSelector.getText();
                            boolean initialized = pythonEnvService.isVenvInitialized(venvPath);
                            ApplicationManager.getApplication().invokeLater(() ->
                                    venvStatusLabel.setText(initialized ? "Initialized" : "Not Initialized"));
                        });
                    }
                }
        );
        venvStatusPanel.add(venvToolbar, BorderLayout.EAST);
        pythonEnvContent.add(venvStatusPanel);

        vbox.add(createCollapsibleSeparator("Python Environment", "pythonEnv", pythonEnvContent));
        vbox.add(pythonEnvContent);


        JPanel jupyterContent = new JPanel();
        jupyterContent.setLayout(new BoxLayout(jupyterContent, BoxLayout.Y_AXIS));

        JPanel jupyterPythonPathPanel = new JBPanel<>(new BorderLayout());
        TextFieldWithBrowseButton jupyterPythonPathSelector = new TextFieldWithBrowseButton();
        jupyterPythonPathSelector.addBrowseFolderListener(new TextBrowseFolderListener(singleFile.withTitle("Select Kernel Python Executable"), project));
        String savedJupyterPython = PropertiesComponent.getInstance().getValue(SkykomaConstants.JUPYTER_PYTHON_EXECUTABLE, SkykomaConstants.JUPYTER_PYTHON_EXECUTABLE_DEFAULT);
        jupyterPythonPathSelector.setText(savedJupyterPython);
        bindSaveOnChange(jupyterPythonPathSelector, SkykomaConstants.JUPYTER_PYTHON_EXECUTABLE);
        jupyterPythonPathPanel.add(new JBLabel("Kernel Python:"), BorderLayout.WEST);
        jupyterPythonPathPanel.add(jupyterPythonPathSelector, BorderLayout.CENTER);
        jupyterContent.add(jupyterPythonPathPanel);
        jupyterPythonPathSelectorRef.set(jupyterPythonPathSelector);

        JPanel notebookDirPanel = new JBPanel<>(new BorderLayout());
        TextFieldWithBrowseButton notebookDirSelector = new TextFieldWithBrowseButton();
        FileChooserDescriptor singleDirNotebook = new FileChooserDescriptor(false, true, false, false, false, false);
        notebookDirSelector.addBrowseFolderListener(new TextBrowseFolderListener(singleDirNotebook.withTitle("Select Notebook Directory"), project));
        String savedNotebookDir = PropertiesComponent.getInstance().getValue(SkykomaConstants.JUPYTER_LAB_WORKDIR, SkykomaConstants.JUPYTER_LAB_WORKDIR_DEFAULT);
        notebookDirSelector.setText(savedNotebookDir);
        bindSaveOnChange(notebookDirSelector, SkykomaConstants.JUPYTER_LAB_WORKDIR);
        notebookDirPanel.add(new JBLabel("Notebook Dir:"), BorderLayout.WEST);
        notebookDirPanel.add(notebookDirSelector, BorderLayout.CENTER);
        jupyterContent.add(notebookDirPanel);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            PythonEnvService pythonEnvService = getPythonEnvService();
            String venvPath = venvPathSelector.getText();
            boolean initialized = pythonEnvService.isVenvInitialized(venvPath);
            ApplicationManager.getApplication().invokeLater(() -> {
                venvStatusLabel.setText(initialized ? "Initialized" : "Not Initialized");
                if (initialized && (savedJupyterPython == null || savedJupyterPython.isEmpty())) {
                    String venvPython = pythonEnvService.getVenvPythonExecutable(venvPath);
                    jupyterPythonPathSelector.setText(venvPython);
                    PropertiesComponent.getInstance().setValue(SkykomaConstants.JUPYTER_PYTHON_EXECUTABLE, venvPython);
                }
            });
        });

        JPanel kernelStatusPanel = new JBPanel<>(new BorderLayout());
        kernelStatusPanel.add(new JBLabel("Kernel Status:"), BorderLayout.WEST);
        JBLabel kernelStatus = new JBLabel("UNKNOWN");
        kernelStatusPanel.add(kernelStatus, BorderLayout.CENTER);
        JComponent kernelToolbar = makeStatusToolbar(
                new AnActionButton("Register Kernel", AllIcons.General.Add) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        SkykomaConsoleService cs = getConsoleService();
                        cs.clear();
                        getIdeaPluginAgentServer().registerAsJupyterKernel(cs::appendInfo);
                    }
                },
                new AnActionButton("Start Lab", AllIcons.Actions.Execute) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        ApplicationManager.getApplication().executeOnPooledThread(() -> {
                            SkykomaConsoleService cs = getConsoleService();
                            cs.clearLab();
                            Process existing = jupyterLabProcessRef.getAndSet(null);
                            if (existing != null && existing.isAlive()) {
                                cs.appendLabInfo("[INFO] Stopping existing Jupyter Lab...");
                                killProcessTree(existing, cs);
                            }
                            PythonEnvService pythonEnvService = getPythonEnvService();
                            String venvPath = venvPathSelector.getText();
                            if (!pythonEnvService.isVenvInitialized(venvPath)) {
                                ApplicationManager.getApplication().invokeLater(() ->
                                        cs.appendLabError("[ERROR] Venv not initialized. Please initVenv first."));
                                return;
                            }
                            String venvPython = pythonEnvService.getVenvPythonExecutable(venvPath);
                            PropertiesComponent pc = PropertiesComponent.getInstance();
                            String ip = pc.getValue(SkykomaConstants.JUPYTER_LAB_IP, SkykomaConstants.JUPYTER_LAB_IP_DEFAULT);
                            boolean allowRoot = pc.getBoolean(SkykomaConstants.JUPYTER_LAB_ALLOW_ROOT, SkykomaConstants.JUPYTER_LAB_ALLOW_ROOT_DEFAULT);
                            String token = pc.getValue(SkykomaConstants.JUPYTER_LAB_TOKEN, SkykomaConstants.JUPYTER_LAB_TOKEN_DEFAULT);
                            String workDir = notebookDirSelector.getText();
                            Process labProcess = pythonEnvService.startJupyterLab(venvPython, ip, allowRoot, token, workDir,
                                    line -> ApplicationManager.getApplication().invokeLater(() -> cs.appendLabInfo(line)));
                            jupyterLabProcessRef.set(labProcess);
                        });
                    }
                },
                new AnActionButton("Stop Kernel", AllIcons.Actions.Suspend) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        getIdeaPluginAgentServer().stopJupyterKernel();
                        Process labProcess = jupyterLabProcessRef.getAndSet(null);
                        if (labProcess != null && labProcess.isAlive()) {
                            getConsoleService().appendLabInfo("[OK] Jupyter Lab stopping...");
                            ApplicationManager.getApplication().executeOnPooledThread(() ->
                                    killProcessTree(labProcess, getConsoleService()));
                        }
                    }
                },
                new AnActionButton("Refresh", AllIcons.Actions.Refresh) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) { refreshStatusText(kernelStatus); }
                }
        );
        kernelStatusPanel.add(kernelToolbar, BorderLayout.EAST);
        jupyterContent.add(kernelStatusPanel);

        AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() ->
                refreshStatusText(kernelStatus), 0, 1, TimeUnit.SECONDS);

        vbox.add(createCollapsibleSeparator("Jupyter", "jupyter", jupyterContent));
        vbox.add(jupyterContent);


        JPanel projectStructureContent = new JBPanel<>();
        projectStructureContent.setLayout(new BoxLayout(projectStructureContent, BoxLayout.Y_AXIS));

        JPanel pathSelectorPanel = new JBPanel<>(new BorderLayout());
        TextFieldWithBrowseButton sdkSelector = new TextFieldWithBrowseButton();
        FileChooserDescriptor singleDir = new FileChooserDescriptor(false, true, false, false, false, false);
        sdkSelector.addBrowseFolderListener(new TextBrowseFolderListener(singleDir.withTitle("Select JDK Path"), project));
        sdkSelector.setText(ProjectUtils.getCurrentSdkHomePath(project));
        JBLabel jdkLabel = new JBLabel("JDK:");
        pathSelectorPanel.add(jdkLabel, BorderLayout.WEST);
        pathSelectorPanel.add(sdkSelector, BorderLayout.CENTER);
        projectStructureContent.add(pathSelectorPanel);

        JPanel mavenSelectorPanel = new JBPanel<>(new BorderLayout());
        TextFieldWithBrowseButton mavenSelector = new TextFieldWithBrowseButton();
        FileChooserDescriptor singleDir2 = new FileChooserDescriptor(false, true, false, false, false, false);
        mavenSelector.addBrowseFolderListener(new TextBrowseFolderListener(singleDir2.withTitle("Select Maven Path"), project));
        mavenSelector.setText(ProjectUtils.getCurrentMavenHomePath(project));
        JBLabel mavenLabel = new JBLabel("Maven:");
        mavenSelectorPanel.add(mavenLabel, BorderLayout.WEST);
        mavenSelectorPanel.add(mavenSelector, BorderLayout.CENTER);
        projectStructureContent.add(mavenSelectorPanel);

        JPanel pathSelectorBtnsPanel = new JBPanel<>(new WrapLayout(FlowLayout.LEFT));
        JButton refreshCurrentSdk = new JButton("refreshPaths");
        JButton btnUpdateSdk = new JButton("updatePaths");
        refreshCurrentSdk.addActionListener(e -> {
            sdkSelector.setText(ProjectUtils.getCurrentSdkHomePath(project));
            mavenSelector.setText(ProjectUtils.getCurrentMavenHomePath(project));
        });
        btnUpdateSdk.addActionListener(e ->
                ApplicationManager.getApplication().runWriteAction(() -> {
                            ProjectUtils.updateProjectJdk(sdkSelector.getText(), project);
                            ProjectUtils.updateProjectMaven(mavenSelector.getText(), project);
                        }
                )
        );
        JButton reImportMaven = new JButton("refreshMaven");
        reImportMaven.addActionListener(e ->
                ApplicationManager.getApplication().runWriteAction(() ->
                        ProjectUtils.mavenReImport(project)
                )
        );
        pathSelectorBtnsPanel.add(refreshCurrentSdk);
        pathSelectorBtnsPanel.add(btnUpdateSdk);
        pathSelectorBtnsPanel.add(reImportMaven);

        projectStructureContent.add(pathSelectorBtnsPanel);
        vbox.add(createCollapsibleSeparator("Project Structure", "projectStructure", projectStructureContent));
        vbox.add(projectStructureContent);

        return container;
    }

    private static IdeaPluginAgentServer getIdeaPluginAgentServer() {
        return ApplicationManager.getApplication().getService(IdeaPluginAgentServer.class);
    }

    private static PythonEnvService getPythonEnvService() {
        return ApplicationManager.getApplication().getService(PythonEnvService.class);
    }

    private static SkykomaConsoleService getConsoleService() {
        return ApplicationManager.getApplication().getService(SkykomaConsoleService.class);
    }

    private static void refreshStatusText(JBLabel kernelStatus) {
        IdeaPluginAgentServer ideaPluginAgentServer = getIdeaPluginAgentServer();
        String stautsText = ideaPluginAgentServer.queryJupyterKernelStatus("");
        kernelStatus.setText(stautsText);
    }

    static void killProcessTree(Process process, SkykomaConsoleService cs) {
        if (process == null || !process.isAlive()) {
            return;
        }
        long pid = process.pid();
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("windows")) {
            try {
                new ProcessBuilder("taskkill", "/T", "/F", "/PID", String.valueOf(pid))
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(10, TimeUnit.SECONDS);
                if (cs != null) cs.appendInfo("[OK] Process tree killed (PID: " + pid + ")");
            } catch (Exception ex) {
                process.destroyForcibly();
                killDescendants(pid);
                if (cs != null) cs.appendInfo("[OK] Process force killed (PID: " + pid + ")");
            }
        } else {
            killDescendants(pid);
            process.destroy();
            try {
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    killDescendants(pid);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                killDescendants(pid);
            }
            if (cs != null) cs.appendInfo("[OK] Jupyter Lab process stopped");
        }
    }

    private static void killDescendants(long pid) {
        ProcessHandle.of(pid).ifPresent(ph ->
                ph.descendants().forEach(child -> {
                    child.destroyForcibly();
                    child.descendants().forEach(grandchild -> grandchild.destroyForcibly());
                }));
    }

    public static void shutdownKernelAndLab() {
        IdeaPluginAgentServer server = ApplicationManager.getApplication().getService(IdeaPluginAgentServer.class);
        server.stopJupyterKernel();
        Process labProcess = jupyterLabProcessRef.getAndSet(null);
        if (labProcess != null && labProcess.isAlive()) {
            killProcessTree(labProcess, null);
        }
    }
}
