package cn.hylstudio.skykoma.plugin.idea.toolwindow;

import cn.hylstudio.skykoma.plugin.idea.service.IdeaPluginAgentServer;
import cn.hylstudio.skykoma.plugin.idea.util.ProjectUtils;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.Document;
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
        container.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel panel = new JPanel(new BorderLayout()); // 使用 BorderLayout 以便铺满宽度
        VerticalBox vbox = new VerticalBox();
        panel.add(vbox, BorderLayout.NORTH);
        container.add(panel);

        vbox.add(new TitledSeparator("Agent Server"));

        JPanel agentServerControlPanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT));
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

        JPanel statusPanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT));
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
        registerCmdPanel.setLayout(new BoxLayout(registerCmdPanel, BoxLayout.X_AXIS));
//        registerCmdPanel.add(new JBLabel("Cmd:"));

        String registerKernelCmd = getIdeaPluginAgentServer().genRegisterKernelCmd();
        JBTextArea txtRegisterKernelCmd = new JBTextArea(registerKernelCmd);
        txtRegisterKernelCmd.setLineWrap(true);
        registerCmdPanel.add(txtRegisterKernelCmd);
        jupyterPanel.add(registerCmdPanel);

        JPanel registerBtnPanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT));
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
                Document doc = txtRegisterKernelCmd.getDocument();
                if (doc != null) {
                    txtRegisterKernelCmd.setCaretPosition(doc.getLength());
                    txtRegisterKernelCmd.moveCaretPosition(0);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                txtRegisterKernelCmd.select(0, 0);
            }
        });

        vbox.add(jupyterPanel);

        vbox.add(new TitledSeparator("Project Structure"));
        JPanel projectStructurePanel = new JBPanel<>();
        projectStructurePanel.setLayout(new BoxLayout(projectStructurePanel, BoxLayout.Y_AXIS));

        JPanel pathSelectorPanel = new JBPanel<>(new BorderLayout());
        TextFieldWithBrowseButton sdkSelector = new TextFieldWithBrowseButton();
        sdkSelector.addBrowseFolderListener("Select Sdk Path", null, null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor());
        sdkSelector.setText(getCurrentSdkHomePath(project));
        pathSelectorPanel.add(sdkSelector,BorderLayout.NORTH);
        projectStructurePanel.add(pathSelectorPanel);

        JPanel pathSelectorBtnsPanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT));
        JButton refreshCurrentSdk = new JButton("refresh");
        JButton btnUpdateSdk = new JButton("updateSdk");
        refreshCurrentSdk.addActionListener(e -> sdkSelector.setText(getCurrentSdkHomePath(project)));
        btnUpdateSdk.addActionListener(e ->
                ApplicationManager.getApplication().runWriteAction(() ->
                        ProjectUtils.updateProjectJdk(sdkSelector.getText(), project)
                )
        );
        JButton reImportMaven = new JButton("reimportMaven");
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

    private static String getCurrentSdkHomePath(Project project) {
        ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
        Sdk projectSdk = projectRootManager.getProjectSdk();
        if (projectSdk != null && projectSdk.getHomePath() != null) {
            return projectSdk.getHomePath();
        }
        return "";
    }

    private static IdeaPluginAgentServer getIdeaPluginAgentServer() {
        return ApplicationManager.getApplication().getService(IdeaPluginAgentServer.class);
    }

    private static void refreshStatusText(JBLabel kernelStatus) {
        IdeaPluginAgentServer ideaPluginAgentServer = getIdeaPluginAgentServer();
        String stautsText = ideaPluginAgentServer.queryJupyterKernelStatus("");
        kernelStatus.setText(stautsText);
    }
}