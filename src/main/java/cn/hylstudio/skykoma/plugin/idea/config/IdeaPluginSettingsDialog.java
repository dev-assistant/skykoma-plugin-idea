package cn.hylstudio.skykoma.plugin.idea.config;

import cn.hylstudio.skykoma.plugin.idea.service.IdeaPluginAgentServer;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Objects;

import static cn.hylstudio.skykoma.plugin.idea.SkykomaConstants.*;

public class IdeaPluginSettingsDialog implements Configurable {
    private final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();

    private JCheckBox dataServerEnabled;
    private JTextField apiHostField;
    private JTextField apiKeyField;
    private JTextField threadsField;
    private JTextField jupyterPythonExecutable;
    private JTextField jupyterKernelName;
    private JTextField agentServerListenAddress;
    private JTextField agentServerListenPort;
    private JTextField jupyterKernelHbPort;
    private JTextField jupyterKernelShellPort;
    private JTextField jupyterKernelIopubPort;
    private JTextField jupyterKernelStdinPort;
    private JTextField jupyterKernelControlPort;
    private final JButton btnStartAgentServer = new JButton("start");
    private final JButton btnStopAgentServer = new JButton("stop");
    private final JButton btnRestartAgentServer = new JButton("restart");
    private final JButton btnRegisterKernel = new JButton("register");

    @Nls
    @Override
    public String getDisplayName() {
        return "Skykoma Plugin Config";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JPanel container = new JPanel(new BorderLayout());

        container.add(new JLabel("Skykoma Config"), BorderLayout.NORTH);

        GridLayout gridLayout = new GridLayout(15, 2);
        JPanel panel = new JPanel(gridLayout);
        container.add(panel, BorderLayout.CENTER);

        panel.add(new JLabel("Data Server Switch"));
        dataServerEnabled = new JCheckBox("Enable Data Server");
        panel.add(dataServerEnabled);
        dataServerEnabled.setSelected(propertiesComponent.getBoolean(DATA_SERVER_ENABLED, DATA_SERVER_ENABLED_DEFAULT));

        panel.add(new JLabel("Data Server Api Host"));
        apiHostField = new JTextField();
        panel.add(apiHostField);
        apiHostField.setText(propertiesComponent.getValue(DATA_SERVER_API_HOST, ""));

        panel.add(new JLabel("Data Server Api Key"));
        apiKeyField = new JTextField();
        panel.add(apiKeyField);
        apiKeyField.setText(propertiesComponent.getValue(DATA_SERVER_API_KEY, ""));

        panel.add(new JLabel("Data Server Upload Threads"));
        threadsField = new JTextField();
        panel.add(threadsField);
        threadsField.setText(propertiesComponent.getValue(DATA_SERVER_UPLOAD_THREADS, ""));

        panel.add(new JLabel("Agent Server Listen Address"));
        agentServerListenAddress = new JTextField();
        panel.add(agentServerListenAddress);
        agentServerListenAddress.setText(propertiesComponent.getValue(AGENT_SERVER_LISTEN_ADDRESS, AGENT_SERVER_LISTEN_ADDRESS_DEFAULT));

        panel.add(new JLabel("Agent Server Listen Port"));
        agentServerListenPort = new JTextField();
        panel.add(agentServerListenPort);
        agentServerListenPort.setText(String.valueOf(propertiesComponent.getInt(AGENT_SERVER_LISTEN_PORT, AGENT_SERVER_LISTEN_PORT_DEFAULT)));

        IdeaPluginAgentServer ideaPluginAgentServer =
                ApplicationManager.getApplication().getService(IdeaPluginAgentServer.class);
        panel.add(new JLabel("Agent Server Control"));
        JPanel agentServerControlPanel = new JPanel();
        panel.add(agentServerControlPanel);
        agentServerControlPanel.add(btnStartAgentServer);
        agentServerControlPanel.add(btnStopAgentServer);
        agentServerControlPanel.add(btnRestartAgentServer);
        agentServerControlPanel.add(btnRegisterKernel);
        btnStartAgentServer.addActionListener(e -> ideaPluginAgentServer.start());
        btnStopAgentServer.addActionListener(e -> ideaPluginAgentServer.stop());
        btnRestartAgentServer.addActionListener(e -> ideaPluginAgentServer.restart());
        btnRegisterKernel.addActionListener(e -> ideaPluginAgentServer.registerAsJupyterKernel());

        panel.add(new JLabel("Jupyter Kernel Name"));
        jupyterKernelName = new JTextField();
        panel.add(jupyterKernelName);
        jupyterKernelName.setText(propertiesComponent.getValue(JUPYTER_KERNEL_NAME, JUPYTER_KERNEL_NAME_DEFAULT));

        panel.add(new JLabel("Jupyter Python Executable"));
        jupyterPythonExecutable = new JTextField();
        panel.add(jupyterPythonExecutable);
        jupyterPythonExecutable.setText(propertiesComponent.getValue(JUPYTER_PYTHON_EXECUTABLE, JUPYTER_PYTHON_EXECUTABLE_DEFAULT));

        panel.add(new JLabel("Jupyter Kernel HB Port"));
        jupyterKernelHbPort = new JTextField();
        panel.add(jupyterKernelHbPort);
        jupyterKernelHbPort.setText(String.valueOf(propertiesComponent.getInt(JUPYTER_SERVER_HB_PORT, JUPYTER_SERVER_HB_PORT_DEFAULT)));

        panel.add(new JLabel("Jupyter Kernel Shell Port"));
        jupyterKernelShellPort = new JTextField();
        panel.add(jupyterKernelShellPort);
        jupyterKernelShellPort.setText(String.valueOf(propertiesComponent.getInt(JUPYTER_SERVER_SHELL_PORT, JUPYTER_SERVER_SHELL_PORT_DEFAULT)));

        panel.add(new JLabel("Jupyter Kernel IOPUB Port"));
        jupyterKernelIopubPort = new JTextField();
        panel.add(jupyterKernelIopubPort);
        jupyterKernelIopubPort.setText(String.valueOf(propertiesComponent.getInt(JUPYTER_SERVER_IOPUB_PORT, JUPYTER_SERVER_IOPUB_PORT_DEFAULT)));

        panel.add(new JLabel("Jupyter Kernel Stdin Port"));
        jupyterKernelStdinPort = new JTextField();
        panel.add(jupyterKernelStdinPort);
        jupyterKernelStdinPort.setText(String.valueOf(propertiesComponent.getInt(JUPYTER_SERVER_STDIN_PORT, JUPYTER_SERVER_STDIN_PORT_DEFAULT)));

        panel.add(new JLabel("Jupyter Kernel Control Port"));
        jupyterKernelControlPort = new JTextField();
        panel.add(jupyterKernelControlPort);
        jupyterKernelControlPort.setText(String.valueOf(propertiesComponent.getInt(JUPYTER_SERVER_CONTROL_PORT, JUPYTER_SERVER_CONTROL_PORT_DEFAULT)));

        String registerKernelCmd = ideaPluginAgentServer.genRegisterKernelCmd();
        panel.add(new JLabel("Jupyter Kernel register cmd"));
        JTextField txtRegisterKernelCmd = new JTextField(registerKernelCmd);
        txtRegisterKernelCmd.setColumns(30);
        panel.add(txtRegisterKernelCmd);
        txtRegisterKernelCmd.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                txtRegisterKernelCmd.setText(ideaPluginAgentServer.genRegisterKernelCmd());
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

        return container;
    }

    @Override
    public boolean isModified() {
        return !(Objects.equals(dataServerEnabled.isSelected(), propertiesComponent.getBoolean(DATA_SERVER_ENABLED, false)) &&
                Objects.equals(apiHostField.getText(), propertiesComponent.getValue(DATA_SERVER_API_HOST, "")) &&
                Objects.equals(apiKeyField.getText(), propertiesComponent.getValue(DATA_SERVER_API_KEY, "")) &&
                Objects.equals(threadsField.getText(), propertiesComponent.getValue(DATA_SERVER_UPLOAD_THREADS, "")) &&
                Objects.equals(agentServerListenAddress.getText(), propertiesComponent.getValue(AGENT_SERVER_LISTEN_ADDRESS, "")) &&
                Objects.equals(agentServerListenPort.getText(), propertiesComponent.getValue(AGENT_SERVER_LISTEN_PORT, "")) &&
                Objects.equals(jupyterKernelName.getText(), propertiesComponent.getValue(JUPYTER_KERNEL_NAME, "")) &&
                Objects.equals(jupyterPythonExecutable.getText(), propertiesComponent.getValue(JUPYTER_PYTHON_EXECUTABLE, "")) &&
                Objects.equals(jupyterKernelHbPort.getText(), propertiesComponent.getValue(JUPYTER_SERVER_HB_PORT, "")) &&
                Objects.equals(jupyterKernelShellPort.getText(), propertiesComponent.getValue(JUPYTER_SERVER_SHELL_PORT, "")) &&
                Objects.equals(jupyterKernelIopubPort.getText(), propertiesComponent.getValue(JUPYTER_SERVER_IOPUB_PORT, "")) &&
                Objects.equals(jupyterKernelStdinPort.getText(), propertiesComponent.getValue(JUPYTER_SERVER_STDIN_PORT, "")) &&
                Objects.equals(jupyterKernelControlPort.getText(), propertiesComponent.getValue(JUPYTER_SERVER_CONTROL_PORT, ""))
        );
    }

    @Override
    public void apply() throws ConfigurationException {
        propertiesComponent.setValue(DATA_SERVER_ENABLED, dataServerEnabled.isSelected());
        propertiesComponent.setValue(DATA_SERVER_API_HOST, apiHostField.getText());
        propertiesComponent.setValue(DATA_SERVER_API_KEY, apiKeyField.getText());
        propertiesComponent.setValue(DATA_SERVER_UPLOAD_THREADS, threadsField.getText());
        propertiesComponent.setValue(AGENT_SERVER_LISTEN_ADDRESS, agentServerListenAddress.getText());
        propertiesComponent.setValue(AGENT_SERVER_LISTEN_PORT, agentServerListenPort.getText());
        propertiesComponent.setValue(JUPYTER_KERNEL_NAME, jupyterKernelName.getText());
        propertiesComponent.setValue(JUPYTER_PYTHON_EXECUTABLE, jupyterPythonExecutable.getText());
        propertiesComponent.setValue(JUPYTER_SERVER_HB_PORT, jupyterKernelHbPort.getText());
        propertiesComponent.setValue(JUPYTER_SERVER_SHELL_PORT, jupyterKernelShellPort.getText());
        propertiesComponent.setValue(JUPYTER_SERVER_IOPUB_PORT, jupyterKernelIopubPort.getText());
        propertiesComponent.setValue(JUPYTER_SERVER_STDIN_PORT, jupyterKernelStdinPort.getText());
        propertiesComponent.setValue(JUPYTER_SERVER_CONTROL_PORT, jupyterKernelControlPort.getText());
    }
}
