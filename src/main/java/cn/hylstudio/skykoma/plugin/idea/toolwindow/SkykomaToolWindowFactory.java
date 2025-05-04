package cn.hylstudio.skykoma.plugin.idea.toolwindow;

import cn.hylstudio.skykoma.plugin.idea.service.IdeaPluginAgentServer;
import cn.hylstudio.skykoma.plugin.idea.util.ProjectUtils;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.EditorTextField;
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
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class SkykomaToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        JComponent rootComponent = createRootComponent(project);
        Content content = ContentFactory.getInstance().createContent(rootComponent, null, false);
        toolWindow.getContentManager().addContent(content);
    }

    private final JButton btnStartAgentServer = new JButton("start");
    private final JButton btnStopAgentServer = new JButton("stop");
    private final JButton btnRestartAgentServer = new JButton("restart");
    private final JButton btnRegisterKernel = new JButton("register");
    private final JButton btnOpenKernelFolder = new JButton("openFolder");
    private final JButton btnRefreshKernelStatus = new JButton("refresh");
    private final JButton btnStopKernel = new JButton("stop");

    private JComponent createRootComponent(Project project) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        // 创建滚动面板的包裹层
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
//        contentPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JScrollPane scrollPane = new JBScrollPane(contentPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        container.add(scrollPane, BorderLayout.CENTER);

        // 原内容面板改为填充滚动区域
        VerticalBox vbox = new VerticalBox();
        contentPanel.add(vbox, BorderLayout.NORTH); // 使用 NORTH 确保内容顶部对齐
//        vbox.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        vbox.add(new TitledSeparator("Agent Server"));

        JPanel agentServerControlPanel = new JBPanel<>(new WrapLayout(FlowLayout.LEFT));
//        agentServerControlPanel.setLayout(new BoxLayout(agentServerControlPanel, BoxLayout.X_AXIS));
        agentServerControlPanel.add(btnStartAgentServer);
        agentServerControlPanel.add(btnStopAgentServer);
        agentServerControlPanel.add(btnRestartAgentServer);
        btnStartAgentServer.addActionListener(e -> getIdeaPluginAgentServer().start());
        btnStopAgentServer.addActionListener(e -> getIdeaPluginAgentServer().stop());
        btnRestartAgentServer.addActionListener(e -> getIdeaPluginAgentServer().restart());
        vbox.add(agentServerControlPanel);

        vbox.add(new TitledSeparator("Jupyter Kernel"));
        JPanel jupyterPanel = new JPanel();
        jupyterPanel.setLayout(new BoxLayout(jupyterPanel, BoxLayout.Y_AXIS));

        JPanel statusPanel = new JBPanel<>(new WrapLayout(FlowLayout.LEFT));
        statusPanel.add(new JBLabel("Status:"));
        JBLabel kernelStatus = new JBLabel("UNKNOWN");
        AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> refreshStatusText(kernelStatus), 0, 1, TimeUnit.SECONDS);
        statusPanel.add(kernelStatus);
        statusPanel.add(btnRefreshKernelStatus);
        statusPanel.add(btnStopKernel);
        btnRefreshKernelStatus.addActionListener(e -> refreshStatusText(kernelStatus));
        btnStopKernel.addActionListener(e -> getIdeaPluginAgentServer().stopJupyterKernel());
        jupyterPanel.add(statusPanel);

        JPanel registerCmdPanel = new JBPanel<>();
        registerCmdPanel.setLayout(new BorderLayout());
//        registerCmdPanel.add(new JBLabel("Cmd:"));
        String registerKernelCmd = getIdeaPluginAgentServer().genRegisterKernelCmd();
        EditorTextField txtRegisterKernelCmd = new EditorTextField(registerKernelCmd);
        txtRegisterKernelCmd.setOneLineMode(false);
        // 添加编辑器初始化完成后的回调
        txtRegisterKernelCmd.addSettingsProvider(editor -> {
            // 此处 editor 已确保非 null
            editor.getSettings().setUseSoftWraps(true);
        });
        registerCmdPanel.add(txtRegisterKernelCmd, BorderLayout.CENTER);
        jupyterPanel.add(registerCmdPanel);

        JPanel registerBtnPanel = new JBPanel<>(new WrapLayout(FlowLayout.LEFT));
//        registerBtnPanel.setLayout(new BoxLayout(registerBtnPanel, BoxLayout.X_AXIS));
        registerBtnPanel.add(btnOpenKernelFolder);
        registerBtnPanel.add(btnRegisterKernel);
        btnOpenKernelFolder.addActionListener(e -> RevealFileAction.openFile(new File(getIdeaPluginAgentServer().getKernelJsonPath())));
        btnRegisterKernel.addActionListener(e -> getIdeaPluginAgentServer().registerAsJupyterKernel());
        jupyterPanel.add(registerBtnPanel);
        txtRegisterKernelCmd.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                txtRegisterKernelCmd.setText(getIdeaPluginAgentServer().genRegisterKernelCmd());
//                com.intellij.openapi.editor.Document doc = txtRegisterKernelCmd.getDocument();
//                if (doc != null) {
//                    txtRegisterKernelCmd.setCaretPosition(doc.getTextLength());
                txtRegisterKernelCmd.selectAll();
//                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                txtRegisterKernelCmd.setCaretPosition(0);
                txtRegisterKernelCmd.removeSelection();
            }
        });

        vbox.add(jupyterPanel);

        vbox.add(new TitledSeparator("Project Structure"));
        JPanel projectStructurePanel = new JBPanel<>();
        projectStructurePanel.setLayout(new BoxLayout(projectStructurePanel, BoxLayout.Y_AXIS));

        JPanel pathSelectorPanel = new JBPanel<>(new BorderLayout());
        TextFieldWithBrowseButton sdkSelector = new TextFieldWithBrowseButton();
        FileChooserDescriptor singleDir = new FileChooserDescriptor(false, true, false, false, false, false);
        sdkSelector.addBrowseFolderListener(new TextBrowseFolderListener(singleDir.withTitle("Select JDK Path"), project));
        sdkSelector.setText(ProjectUtils.getCurrentSdkHomePath(project));
        JBLabel jdkLabel = new JBLabel("JDK:");
        pathSelectorPanel.add(jdkLabel, BorderLayout.WEST); // 左侧固定标签
        pathSelectorPanel.add(sdkSelector, BorderLayout.CENTER); // 文本框占据剩余空间
        projectStructurePanel.add(pathSelectorPanel);

        JPanel mavenSelectorPanel = new JBPanel<>(new BorderLayout());
        TextFieldWithBrowseButton mavenSelector = new TextFieldWithBrowseButton();
        FileChooserDescriptor singleDir2 = new FileChooserDescriptor(false, true, false, false, false, false);
        mavenSelector.addBrowseFolderListener(new TextBrowseFolderListener(singleDir2.withTitle("Select Maven Path"), project));
        mavenSelector.setText(ProjectUtils.getCurrentMavenHomePath(project));
        JBLabel mavenLabel = new JBLabel("Maven:");
        mavenSelectorPanel.add(mavenLabel, BorderLayout.WEST); // 左侧固定标签
        mavenSelectorPanel.add(mavenSelector, BorderLayout.CENTER); // 文本框占据剩余空间
        projectStructurePanel.add(mavenSelectorPanel);

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

//        JButton testBtn = new JButton("test1");
//        testBtn.addActionListener(e -> checkoutRemoteBranch(project));
//        pathSelectorBtnsPanel.add(testBtn);
        projectStructurePanel.add(pathSelectorBtnsPanel);
        vbox.add(projectStructurePanel);

        return container;
    }


//    private void checkoutRemoteBranch(Project project) {
//        ApplicationManager.getApplication().runWriteAction(() -> {
//            GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
//            List<GitRepository> allRepositories = repositoryManager.getRepositories();
//            @NotNull String remoteBranchName = "origin/dev";
//            GitBranchPopupActions.RemoteBranchActions.CheckoutRemoteBranchAction.checkoutRemoteBranch(project, allRepositories, remoteBranchName);
//        });
//    }

    private static IdeaPluginAgentServer getIdeaPluginAgentServer() {
        return ApplicationManager.getApplication().getService(IdeaPluginAgentServer.class);
    }

    private static void refreshStatusText(JBLabel kernelStatus) {
        IdeaPluginAgentServer ideaPluginAgentServer = getIdeaPluginAgentServer();
        String stautsText = ideaPluginAgentServer.queryJupyterKernelStatus("");
        kernelStatus.setText(stautsText);
    }
}