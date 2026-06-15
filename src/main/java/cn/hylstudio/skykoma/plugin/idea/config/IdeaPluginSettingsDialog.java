package cn.hylstudio.skykoma.plugin.idea.config;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.hylstudio.skykoma.plugin.idea.SkykomaConstants.*;

public class IdeaPluginSettingsDialog implements Configurable {
    private final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();

    private JCheckBox dataServerEnabled;
    private JTextField apiHostField;
    private JTextField apiKeyField;
    private JTextField threadsField;
    private TextFieldWithBrowseButton python312Executable;
    private TextFieldWithBrowseButton jupyterPythonExecutable;
    private JTextField jupyterKernelName;
    private ListTableModel<String> jupyterExtraClasspathModel;
    private JTextField agentServerListenAddress;
    private JTextField agentServerListenPort;
    private JTextField jupyterKernelHbPort;
    private JTextField jupyterKernelShellPort;
    private JTextField jupyterKernelIopubPort;
    private JTextField jupyterKernelStdinPort;
    private JTextField jupyterKernelControlPort;
    private TextFieldWithBrowseButton pythonVenvPath;
    private JTextField pythonDownloadUrl;
    private JTextArea pythonPipPackages;
    private JTextField pythonPipMirror;
    private JTextField jupyterLabIp;
    private JCheckBox jupyterLabAllowRoot;
    private JTextField jupyterLabToken;
    private TextFieldWithBrowseButton jupyterLabWorkdir;

    @Nls
    @Override
    public String getDisplayName() {
        return "Skykoma Plugin Config";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JPanel container = new JPanel(new BorderLayout());
        JPanel mainPanel = new JPanel(new BorderLayout());
        VerticalBox mainVBox = new VerticalBox();
        mainPanel.add(mainVBox, BorderLayout.NORTH);
        mainVBox.add(new TitledSeparator("Data Server"));

        JPanel dataServerConfigPanel = new JPanel();
        dataServerConfigPanel.setLayout(new BoxLayout(dataServerConfigPanel, BoxLayout.Y_AXIS));

        JPanel dataServerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        dataServerEnabled = new JCheckBox("Enable");
        dataServerEnabled.setSelected(propertiesComponent.getBoolean(DATA_SERVER_ENABLED, DATA_SERVER_ENABLED_DEFAULT));
        dataServerPanel.add(dataServerEnabled);
        dataServerConfigPanel.add(dataServerPanel);

        apiHostField = new JBTextField();
        apiHostField.setText(propertiesComponent.getValue(DATA_SERVER_API_HOST, ""));
        appendField(dataServerConfigPanel, "Api Host:", apiHostField);

        apiKeyField = new JBTextField();
        apiKeyField.setText(propertiesComponent.getValue(DATA_SERVER_API_KEY, ""));
        appendField(dataServerConfigPanel, "Api Key:", apiKeyField);

        threadsField = new JBTextField();
        threadsField.setText(propertiesComponent.getValue(DATA_SERVER_UPLOAD_THREADS, ""));
        appendField(dataServerConfigPanel, "Upload Threads:", threadsField);

        mainVBox.add(dataServerConfigPanel);
        mainVBox.add(Box.createVerticalStrut(5));

        mainVBox.add(new TitledSeparator("Agent Server"));
        JPanel agentServerPanel = new JPanel();
        agentServerPanel.setLayout(new BoxLayout(agentServerPanel, BoxLayout.Y_AXIS));

        agentServerListenAddress = new JBTextField();
        agentServerListenAddress.setText(propertiesComponent.getValue(AGENT_SERVER_LISTEN_ADDRESS, AGENT_SERVER_LISTEN_ADDRESS_DEFAULT));
        appendField(agentServerPanel, "Listen Address:", agentServerListenAddress);

        agentServerListenPort = new JBTextField();
        agentServerListenPort.setText(propertiesComponent.getValue(AGENT_SERVER_LISTEN_PORT, AGENT_SERVER_LISTEN_PORT_DEFAULT+""));
        appendField(agentServerPanel, "Listen Port:", agentServerListenPort);

        mainVBox.add(agentServerPanel);
        mainVBox.add(Box.createVerticalStrut(5));

        mainVBox.add(new TitledSeparator("Python Environment"));
        JPanel pythonEnvPanel = new JPanel();
        pythonEnvPanel.setLayout(new BoxLayout(pythonEnvPanel, BoxLayout.Y_AXIS));

        python312Executable = new TextFieldWithBrowseButton();
        FileChooserDescriptor singleFile = new FileChooserDescriptor(true, false, false, false, false, false);
        python312Executable.addBrowseFolderListener(new TextBrowseFolderListener(singleFile.withTitle("Select Python 3.12 Executable"), null));
        python312Executable.setText(propertiesComponent.getValue(PYTHON312_EXECUTABLE, PYTHON312_EXECUTABLE_DEFAULT));
        appendField(pythonEnvPanel, "Python 3.12:", python312Executable);

        pythonVenvPath = new TextFieldWithBrowseButton();
        FileChooserDescriptor singleDir = new FileChooserDescriptor(false, true, false, false, false, false);
        pythonVenvPath.addBrowseFolderListener(new TextBrowseFolderListener(singleDir.withTitle("Select Virtualenv Path"), null));
        pythonVenvPath.setText(propertiesComponent.getValue(PYTHON_VENV_PATH, PYTHON_VENV_PATH_DEFAULT));
        appendField(pythonEnvPanel, "Venv Path:", pythonVenvPath);

        pythonDownloadUrl = new JBTextField();
        pythonDownloadUrl.setText(propertiesComponent.getValue(PYTHON_DOWNLOAD_URL, PYTHON_DOWNLOAD_URL_DEFAULT));
        appendField(pythonEnvPanel, "Python 3.12 Download:", pythonDownloadUrl);

        mainVBox.add(pythonEnvPanel);
        mainVBox.add(Box.createVerticalStrut(5));

        mainVBox.add(new TitledSeparator("Jupyter"));
        JPanel jupyterPanel = new JPanel();
        jupyterPanel.setLayout(new BoxLayout(jupyterPanel, BoxLayout.Y_AXIS));

        jupyterKernelName = new JBTextField();
        jupyterKernelName.setText(propertiesComponent.getValue(JUPYTER_KERNEL_NAME, JUPYTER_KERNEL_NAME_DEFAULT));
        appendField(jupyterPanel, "Kernel Name:", jupyterKernelName);

        jupyterPythonExecutable = new TextFieldWithBrowseButton();
        jupyterPythonExecutable.addBrowseFolderListener(new TextBrowseFolderListener(singleFile.withTitle("Select Kernel Python Executable"), null));
        jupyterPythonExecutable.setText(propertiesComponent.getValue(JUPYTER_PYTHON_EXECUTABLE, JUPYTER_PYTHON_EXECUTABLE_DEFAULT));
        appendField(jupyterPanel, "Kernel Python:", jupyterPythonExecutable);

        JPanel pipPackagesPanel = new JPanel();
        pipPackagesPanel.setLayout(new BoxLayout(pipPackagesPanel, BoxLayout.X_AXIS));
        pipPackagesPanel.add(new JLabel("Pip Packages:"));
        pythonPipPackages = new JBTextArea();
        pythonPipPackages.setText(propertiesComponent.getValue(PYTHON_PIP_PACKAGES, PYTHON_PIP_PACKAGES_DEFAULT));
        pythonPipPackages.setRows(8);
        JScrollPane pipScrollPane = new JBScrollPane(pythonPipPackages);
        pipScrollPane.setPreferredSize(new Dimension(400, 150));
        pipPackagesPanel.add(pipScrollPane);
        jupyterPanel.add(pipPackagesPanel);

        pythonPipMirror = new JBTextField();
        pythonPipMirror.setText(propertiesComponent.getValue(PYTHON_PIP_MIRROR, PYTHON_PIP_MIRROR_DEFAULT));
        appendField(jupyterPanel, "Pip Mirror:", pythonPipMirror);

        JPanel pipResetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnResetPipPackages = new JButton("Reset Pip to Default");
        btnResetPipPackages.addActionListener(e -> {
            pythonPipPackages.setText(PYTHON_PIP_PACKAGES_DEFAULT);
            pythonPipMirror.setText(PYTHON_PIP_MIRROR_DEFAULT);
        });
        pipResetPanel.add(btnResetPipPackages);
        jupyterPanel.add(pipResetPanel);

        String savedClasspath = propertiesComponent.getValue(JUPYTER_EXTRA_CLASSPATH, JUPYTER_EXTRA_CLASSPATH_DEFAULT);
        java.util.List<String> classpathItems = new java.util.ArrayList<>();
        if (savedClasspath != null && !savedClasspath.isEmpty()) {
            for (String item : savedClasspath.split(File.pathSeparator)) {
                if (!item.trim().isEmpty()) {
                    classpathItems.add(item.trim());
                }
            }
        }
        ColumnInfo<String, String> pathColumn = new ColumnInfo<String, String>("Path") {
            @Override
            public String valueOf(String s) {
                return s;
            }
        };
        jupyterExtraClasspathModel = new ListTableModel<>(pathColumn);
        jupyterExtraClasspathModel.setItems(classpathItems);
        JBTable classpathTable = new JBTable(jupyterExtraClasspathModel);
        classpathTable.getEmptyText().setText("No classpath entries");
        FileChooserDescriptor jarFile = new FileChooserDescriptor(true, false, true, true, false, false)
                .withTitle("Select JAR File");
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(classpathTable)
                .setAddAction(button -> {
                    VirtualFile[] files = FileChooser.chooseFiles(jarFile, null, null);
                    for (VirtualFile file : files) {
                        jupyterExtraClasspathModel.addRow(file.getPath());
                    }
                })
                .setEditAction(button -> {
                    int row = classpathTable.getSelectedRow();
                    if (row >= 0) {
                        String current = jupyterExtraClasspathModel.getItem(row);
                        String newValue = JOptionPane.showInputDialog(classpathTable, "Edit path:", current);
                        if (newValue != null && !newValue.trim().isEmpty()) {
                            jupyterExtraClasspathModel.setItem(row, newValue.trim());
                        }
                    }
                })
                .disableUpDownActions();
        JPanel classpathPanel = decorator.createPanel();
        classpathPanel.setPreferredSize(new Dimension(500, 120));
        jupyterPanel.add(new JLabel("Kernel Extra Classpath:"));
        jupyterPanel.add(classpathPanel);

        jupyterKernelHbPort = new JBTextField();
        jupyterKernelHbPort.setText(String.valueOf(propertiesComponent.getInt(JUPYTER_SERVER_HB_PORT, JUPYTER_SERVER_HB_PORT_DEFAULT)));
        appendField(jupyterPanel, "HB Port:", jupyterKernelHbPort);

        jupyterKernelShellPort = new JBTextField();
        jupyterKernelShellPort.setText(String.valueOf(propertiesComponent.getInt(JUPYTER_SERVER_SHELL_PORT, JUPYTER_SERVER_SHELL_PORT_DEFAULT)));
        appendField(jupyterPanel, "Shell Port:", jupyterKernelShellPort);

        jupyterKernelIopubPort = new JBTextField();
        jupyterKernelIopubPort.setText(String.valueOf(propertiesComponent.getInt(JUPYTER_SERVER_IOPUB_PORT, JUPYTER_SERVER_IOPUB_PORT_DEFAULT)));
        appendField(jupyterPanel, "IOPUB Port:", jupyterKernelIopubPort);

        jupyterKernelStdinPort = new JBTextField();
        jupyterKernelStdinPort.setText(String.valueOf(propertiesComponent.getInt(JUPYTER_SERVER_STDIN_PORT, JUPYTER_SERVER_STDIN_PORT_DEFAULT)));
        appendField(jupyterPanel, "Stdin Port:", jupyterKernelStdinPort);

        jupyterKernelControlPort = new JBTextField();
        jupyterKernelControlPort.setText(String.valueOf(propertiesComponent.getInt(JUPYTER_SERVER_CONTROL_PORT, JUPYTER_SERVER_CONTROL_PORT_DEFAULT)));
        appendField(jupyterPanel, "Control Port:", jupyterKernelControlPort);

        mainVBox.add(jupyterPanel);
        mainVBox.add(Box.createVerticalStrut(5));

        mainVBox.add(new TitledSeparator("Jupyter Lab"));
        JPanel jupyterLabPanel = new JPanel();
        jupyterLabPanel.setLayout(new BoxLayout(jupyterLabPanel, BoxLayout.Y_AXIS));

        jupyterLabIp = new JBTextField();
        jupyterLabIp.setText(propertiesComponent.getValue(JUPYTER_LAB_IP, JUPYTER_LAB_IP_DEFAULT));
        appendField(jupyterLabPanel, "Listen IP:", jupyterLabIp);

        jupyterLabAllowRoot = new JCheckBox("Allow Root");
        jupyterLabAllowRoot.setSelected(propertiesComponent.getBoolean(JUPYTER_LAB_ALLOW_ROOT, JUPYTER_LAB_ALLOW_ROOT_DEFAULT));
        JPanel allowRootPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        allowRootPanel.add(jupyterLabAllowRoot);
        jupyterLabPanel.add(allowRootPanel);

        jupyterLabToken = new JBTextField();
        jupyterLabToken.setText(propertiesComponent.getValue(JUPYTER_LAB_TOKEN, JUPYTER_LAB_TOKEN_DEFAULT));
        appendField(jupyterLabPanel, "Token:", jupyterLabToken);

        jupyterLabWorkdir = new TextFieldWithBrowseButton();
        FileChooserDescriptor singleDirWorkdir = new FileChooserDescriptor(false, true, false, false, false, false);
        jupyterLabWorkdir.addBrowseFolderListener(new TextBrowseFolderListener(singleDirWorkdir.withTitle("Select Work Directory"), null));
        jupyterLabWorkdir.setText(propertiesComponent.getValue(JUPYTER_LAB_WORKDIR, JUPYTER_LAB_WORKDIR_DEFAULT));
        appendField(jupyterLabPanel, "Work Dir:", jupyterLabWorkdir);

        mainVBox.add(jupyterLabPanel);
        mainVBox.add(Box.createVerticalStrut(5));

        JBScrollPane scrollPane = new JBScrollPane(mainPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setPreferredSize(new Dimension(600, 600));
        container.add(scrollPane, BorderLayout.CENTER);
        return container;
    }

    private void appendField(JPanel parentPanel, String label, JComponent jComponent) {
        JPanel result = new JPanel();
        result.setLayout(new BoxLayout(result, BoxLayout.X_AXIS));
        result.add(new JLabel(label));
        result.add(jComponent);
        parentPanel.add(result);
    }

    @Override
    public boolean isModified() {
        return !(Objects.equals(dataServerEnabled.isSelected(), propertiesComponent.getBoolean(DATA_SERVER_ENABLED, DATA_SERVER_ENABLED_DEFAULT)) &&
                Objects.equals(apiHostField.getText(), propertiesComponent.getValue(DATA_SERVER_API_HOST, "")) &&
                Objects.equals(apiKeyField.getText(), propertiesComponent.getValue(DATA_SERVER_API_KEY, "")) &&
                Objects.equals(threadsField.getText(), propertiesComponent.getValue(DATA_SERVER_UPLOAD_THREADS, "")) &&
                Objects.equals(agentServerListenAddress.getText(), propertiesComponent.getValue(AGENT_SERVER_LISTEN_ADDRESS, AGENT_SERVER_LISTEN_ADDRESS_DEFAULT)) &&
                Objects.equals(agentServerListenPort.getText(), propertiesComponent.getValue(AGENT_SERVER_LISTEN_PORT, AGENT_SERVER_LISTEN_PORT_DEFAULT + "")) &&
                Objects.equals(python312Executable.getText(), propertiesComponent.getValue(PYTHON312_EXECUTABLE, PYTHON312_EXECUTABLE_DEFAULT)) &&
                Objects.equals(pythonVenvPath.getText(), propertiesComponent.getValue(PYTHON_VENV_PATH, PYTHON_VENV_PATH_DEFAULT)) &&
                Objects.equals(pythonDownloadUrl.getText(), propertiesComponent.getValue(PYTHON_DOWNLOAD_URL, PYTHON_DOWNLOAD_URL_DEFAULT)) &&
                Objects.equals(pythonPipPackages.getText(), propertiesComponent.getValue(PYTHON_PIP_PACKAGES, PYTHON_PIP_PACKAGES_DEFAULT)) &&
                Objects.equals(pythonPipMirror.getText(), propertiesComponent.getValue(PYTHON_PIP_MIRROR, PYTHON_PIP_MIRROR_DEFAULT)) &&
                Objects.equals(jupyterKernelName.getText(), propertiesComponent.getValue(JUPYTER_KERNEL_NAME, JUPYTER_KERNEL_NAME_DEFAULT)) &&
                Objects.equals(jupyterPythonExecutable.getText(), propertiesComponent.getValue(JUPYTER_PYTHON_EXECUTABLE, JUPYTER_PYTHON_EXECUTABLE_DEFAULT)) &&
                Objects.equals(classpathModelToString(), propertiesComponent.getValue(JUPYTER_EXTRA_CLASSPATH, JUPYTER_EXTRA_CLASSPATH_DEFAULT)) &&
                Objects.equals(jupyterKernelHbPort.getText(), propertiesComponent.getValue(JUPYTER_SERVER_HB_PORT, JUPYTER_SERVER_HB_PORT_DEFAULT + "")) &&
                Objects.equals(jupyterKernelShellPort.getText(), propertiesComponent.getValue(JUPYTER_SERVER_SHELL_PORT, JUPYTER_SERVER_SHELL_PORT_DEFAULT + "")) &&
                Objects.equals(jupyterKernelIopubPort.getText(), propertiesComponent.getValue(JUPYTER_SERVER_IOPUB_PORT, JUPYTER_SERVER_IOPUB_PORT_DEFAULT + "")) &&
                Objects.equals(jupyterKernelStdinPort.getText(), propertiesComponent.getValue(JUPYTER_SERVER_STDIN_PORT, JUPYTER_SERVER_STDIN_PORT_DEFAULT + "")) &&
                Objects.equals(jupyterKernelControlPort.getText(), propertiesComponent.getValue(JUPYTER_SERVER_CONTROL_PORT, JUPYTER_SERVER_CONTROL_PORT_DEFAULT + "")) &&
                Objects.equals(jupyterLabIp.getText(), propertiesComponent.getValue(JUPYTER_LAB_IP, JUPYTER_LAB_IP_DEFAULT)) &&
                Objects.equals(jupyterLabAllowRoot.isSelected(), propertiesComponent.getBoolean(JUPYTER_LAB_ALLOW_ROOT, JUPYTER_LAB_ALLOW_ROOT_DEFAULT)) &&
                Objects.equals(jupyterLabToken.getText(), propertiesComponent.getValue(JUPYTER_LAB_TOKEN, JUPYTER_LAB_TOKEN_DEFAULT)) &&
                Objects.equals(jupyterLabWorkdir.getText(), propertiesComponent.getValue(JUPYTER_LAB_WORKDIR, JUPYTER_LAB_WORKDIR_DEFAULT))
        );
    }

    private String classpathModelToString() {
        return jupyterExtraClasspathModel.getItems().stream()
                .filter(s -> !s.trim().isEmpty())
                .collect(Collectors.joining(File.pathSeparator));
    }

    @Override
    public void apply() throws ConfigurationException {
        propertiesComponent.setValue(DATA_SERVER_ENABLED, dataServerEnabled.isSelected());
        propertiesComponent.setValue(DATA_SERVER_API_HOST, apiHostField.getText());
        propertiesComponent.setValue(DATA_SERVER_API_KEY, apiKeyField.getText());
        propertiesComponent.setValue(DATA_SERVER_UPLOAD_THREADS, threadsField.getText());
        propertiesComponent.setValue(AGENT_SERVER_LISTEN_ADDRESS, agentServerListenAddress.getText());
        propertiesComponent.setValue(AGENT_SERVER_LISTEN_PORT, agentServerListenPort.getText());
        propertiesComponent.setValue(PYTHON312_EXECUTABLE, python312Executable.getText());
        propertiesComponent.setValue(PYTHON_VENV_PATH, pythonVenvPath.getText());
        propertiesComponent.setValue(PYTHON_DOWNLOAD_URL, pythonDownloadUrl.getText());
        propertiesComponent.setValue(PYTHON_PIP_PACKAGES, pythonPipPackages.getText());
        propertiesComponent.setValue(PYTHON_PIP_MIRROR, pythonPipMirror.getText());
        propertiesComponent.setValue(JUPYTER_KERNEL_NAME, jupyterKernelName.getText());
        propertiesComponent.setValue(JUPYTER_PYTHON_EXECUTABLE, jupyterPythonExecutable.getText());
        propertiesComponent.setValue(JUPYTER_EXTRA_CLASSPATH, classpathModelToString());
        propertiesComponent.setValue(JUPYTER_SERVER_HB_PORT, jupyterKernelHbPort.getText());
        propertiesComponent.setValue(JUPYTER_SERVER_SHELL_PORT, jupyterKernelShellPort.getText());
        propertiesComponent.setValue(JUPYTER_SERVER_IOPUB_PORT, jupyterKernelIopubPort.getText());
        propertiesComponent.setValue(JUPYTER_SERVER_STDIN_PORT, jupyterKernelStdinPort.getText());
        propertiesComponent.setValue(JUPYTER_SERVER_CONTROL_PORT, jupyterKernelControlPort.getText());
        propertiesComponent.setValue(JUPYTER_LAB_IP, jupyterLabIp.getText());
        propertiesComponent.setValue(JUPYTER_LAB_ALLOW_ROOT, jupyterLabAllowRoot.isSelected());
        propertiesComponent.setValue(JUPYTER_LAB_TOKEN, jupyterLabToken.getText());
        propertiesComponent.setValue(JUPYTER_LAB_WORKDIR, jupyterLabWorkdir.getText());
    }
}
