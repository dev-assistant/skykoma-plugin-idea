package cn.hylstudio.skykoma.plugin.idea.config;

import cn.hylstudio.skykoma.plugin.idea.SkykomaConstants;
import cn.hylstudio.skykoma.plugin.idea.service.IdeaPluginAgentServer;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class IdeaPluginSettingsDialog implements Configurable {
    private final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();

    private static final String DATA_SERVER_ENABLED = SkykomaConstants.DATA_SERVER_ENABLED;
    private static final String DATA_SERVER_API_HOST = SkykomaConstants.DATA_SERVER_API_HOST;
    private static final String DATA_SERVER_API_KEY = SkykomaConstants.DATA_SERVER_API_KEY;
    private static final String AGENT_SERVER_LISTEN_ADDRESS = SkykomaConstants.AGENT_SERVER_LISTEN_ADDRESS;
    private static final String AGENT_SERVER_LISTEN_PORT = SkykomaConstants.AGENT_SERVER_LISTEN_PORT;
    private static final String JUPYTER_KERNEL_NAME = SkykomaConstants.JUPYTER_KERNEL_NAME;
    private static final String JUPYTER_PYTHON_EXECUTABLE = SkykomaConstants.JUPYTER_PYTHON_EXECUTABLE;

    private JCheckBox dataServerEnabled;
    private JTextField apiHostField;
    private JTextField apiKeyField;
    private JTextField jupyterPythonExecutable;
    private JTextField jupyterKernelName;
    private JTextField agentServerListenAddress;
    private JTextField agentServerListenPort;
    private final JButton btnStartAgentServer = new JButton("start");
    private final JButton btnStopAgentServer= new JButton("stop");
    private final JButton btnRestartAgentServer= new JButton("restart");
    private final JButton btnRegisterKernel= new JButton("register");

    @Nls
    @Override
    public String getDisplayName() {
        return "Skykoma Plugin Config";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JPanel container = new JPanel(new BorderLayout());

        container.add(new JLabel("Data Server Api Config"), BorderLayout.NORTH);

        GridLayout gridLayout = new GridLayout(9, 2);
        JPanel panel = new JPanel(gridLayout);
        container.add(panel, BorderLayout.CENTER);

        panel.add(new JLabel("Enable Data Server"));
        dataServerEnabled = new JCheckBox("Enable Data Server");
        panel.add(dataServerEnabled);
        dataServerEnabled.setSelected(propertiesComponent.getBoolean(DATA_SERVER_ENABLED, SkykomaConstants.DATA_SERVER_ENABLED_DEFAULT));

        panel.add(new JLabel("Api Host"));
        apiHostField = new JTextField();
        panel.add(apiHostField);
        apiHostField.setText(propertiesComponent.getValue(DATA_SERVER_API_HOST, ""));

        panel.add(new JLabel("Api Key"));
        apiKeyField = new JTextField();
        panel.add(apiKeyField);
        apiKeyField.setText(propertiesComponent.getValue(DATA_SERVER_API_KEY, ""));

        panel.add(new JLabel("Agent Server Listen Address"));
        agentServerListenAddress = new JTextField();
        panel.add(agentServerListenAddress);
        agentServerListenAddress.setText(propertiesComponent.getValue(AGENT_SERVER_LISTEN_ADDRESS, SkykomaConstants.AGENT_SERVER_LISTEN_ADDRESS_DEFAULT));

        panel.add(new JLabel("Agent Server Listen Port"));
        agentServerListenPort = new JTextField();
        panel.add(agentServerListenPort);
        agentServerListenPort.setText(String.valueOf(propertiesComponent.getInt(AGENT_SERVER_LISTEN_PORT, SkykomaConstants.AGENT_SERVER_LISTEN_PORT_DEFAULT)));

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
        String registerKernelCmd = ideaPluginAgentServer.genRegisterKernelCmd();
        panel.add(new JLabel("Jupyter Kernel register cmd"));
        panel.add(new TextField(registerKernelCmd));


        panel.add(new JLabel("Jupyter Kernel Name"));
        jupyterKernelName = new JTextField();
        panel.add(jupyterKernelName);
        jupyterKernelName.setText(propertiesComponent.getValue(JUPYTER_KERNEL_NAME, SkykomaConstants.JUPYTER_KERNEL_NAME_DEFAULT));

        panel.add(new JLabel("Jupyter Python Executable"));
        jupyterPythonExecutable = new JTextField();
        panel.add(jupyterPythonExecutable);
        jupyterPythonExecutable.setText(propertiesComponent.getValue(JUPYTER_PYTHON_EXECUTABLE, SkykomaConstants.JUPYTER_PYTHON_EXECUTABLE_DEFAULT));

        return container;
    }

    @Override
    public boolean isModified() {
        return !(Objects.equals(dataServerEnabled.isSelected(), propertiesComponent.getBoolean(DATA_SERVER_ENABLED, false)) &&
                Objects.equals(apiHostField.getText(), propertiesComponent.getValue(DATA_SERVER_API_HOST, "")) &&
                Objects.equals(apiKeyField.getText(), propertiesComponent.getValue(DATA_SERVER_API_KEY, "")) &&
                Objects.equals(agentServerListenAddress.getText(), propertiesComponent.getValue(AGENT_SERVER_LISTEN_ADDRESS, "")) &&
                Objects.equals(agentServerListenPort.getText(), propertiesComponent.getValue(AGENT_SERVER_LISTEN_PORT, "")) &&
                Objects.equals(jupyterKernelName.getText(), propertiesComponent.getValue(JUPYTER_KERNEL_NAME, "")) &&
                Objects.equals(jupyterPythonExecutable.getText(), propertiesComponent.getValue(JUPYTER_PYTHON_EXECUTABLE, ""))
        );
    }

    @Override
    public void apply() throws ConfigurationException {
        propertiesComponent.setValue(DATA_SERVER_ENABLED, dataServerEnabled.isEnabled());
        propertiesComponent.setValue(DATA_SERVER_API_HOST, apiHostField.getText());
        propertiesComponent.setValue(DATA_SERVER_API_KEY, apiKeyField.getText());
        propertiesComponent.setValue(AGENT_SERVER_LISTEN_ADDRESS, agentServerListenAddress.getText());
        propertiesComponent.setValue(AGENT_SERVER_LISTEN_PORT, agentServerListenPort.getText());
        propertiesComponent.setValue(JUPYTER_KERNEL_NAME, jupyterKernelName.getText());
        propertiesComponent.setValue(JUPYTER_PYTHON_EXECUTABLE, jupyterPythonExecutable.getText());
    }
}
