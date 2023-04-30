package cn.hylstudio.skykoma.plugin.idea.config;

import com.intellij.openapi.options.Configurable;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.ConfigurationException;

import cn.hylstudio.skykoma.plugin.idea.SkykomaConstants;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.*;
import java.util.Objects;

public class IdeaPluginSettingsDialog implements Configurable {
    private final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();

    public static final String DATA_SERVER_ENABLED = SkykomaConstants.DATA_SERVER_ENABLED;
    public static final String DATA_SERVER_API_HOST = SkykomaConstants.DATA_SERVER_API_HOST;
    public static final String DATA_SERVER_API_KEY = SkykomaConstants.DATA_SERVER_API_KEY;
    public static final String AGENT_SERVER_LISTEN_ADDRESS = SkykomaConstants.AGENT_SERVER_LISTEN_ADDRESS;
    public static final String AGENT_SERVER_LISTEN_PORT = SkykomaConstants.AGENT_SERVER_LISTEN_PORT;

    private JTextField apiHostField;
    private JTextField apiKeyField;
    private JTextField agentServerListenAddress;
    private JTextField agentServerListenPort;

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

        GridLayout gridLayout = new GridLayout(4, 2);
        JPanel panel = new JPanel(gridLayout);
        container.add(panel, BorderLayout.CENTER);

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
        agentServerListenAddress.setText(propertiesComponent.getValue(AGENT_SERVER_LISTEN_ADDRESS, "http://127.0.0.1"));

        panel.add(new JLabel("Agent Server Listen Port"));
        agentServerListenPort = new JTextField();
        panel.add(agentServerListenPort);
        agentServerListenPort.setText(propertiesComponent.getValue(AGENT_SERVER_LISTEN_PORT, "2333"));
        return container;
    }

    @Override
    public boolean isModified() {
        return !(Objects.equals(apiHostField.getText(), propertiesComponent.getValue(DATA_SERVER_API_HOST, "")) &&
                Objects.equals(apiKeyField.getText(), propertiesComponent.getValue(DATA_SERVER_API_KEY, "")) &&
                Objects.equals(agentServerListenAddress.getText(), propertiesComponent.getValue(AGENT_SERVER_LISTEN_ADDRESS, "")) &&
                Objects.equals(agentServerListenPort.getText(), propertiesComponent.getValue(AGENT_SERVER_LISTEN_PORT, ""))
        );
    }

    @Override
    public void apply() throws ConfigurationException {
        propertiesComponent.setValue(DATA_SERVER_API_HOST, apiHostField.getText());
        propertiesComponent.setValue(DATA_SERVER_API_KEY, apiKeyField.getText());
        propertiesComponent.setValue(AGENT_SERVER_LISTEN_ADDRESS, agentServerListenAddress.getText());
        propertiesComponent.setValue(AGENT_SERVER_LISTEN_PORT, agentServerListenPort.getText());
    }
}
