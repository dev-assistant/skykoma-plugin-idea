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

public class IdeaPluginCodeGenSettingsDialog implements Configurable {
    private final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();

    private JCheckBox generateCurrentMethodName;
    private JTextField logVariableName;

    @Nls
    @Override
    public String getDisplayName() {
        return "Skykoma Plugin CodeGen Config";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JPanel container = new JPanel(new BorderLayout());

        container.add(new JLabel("Skykoma CodeGen Config"), BorderLayout.NORTH);

        GridLayout gridLayout = new GridLayout(2, 2);//row,column
        JPanel panel = new JPanel(gridLayout);
        container.add(panel, BorderLayout.CENTER);

        panel.add(new JLabel("Logger Generate Current Method Name"));
        generateCurrentMethodName = new JCheckBox("Enabled");
        panel.add(generateCurrentMethodName);
        generateCurrentMethodName.setSelected(propertiesComponent.getBoolean(GENERATE_CURRENT_METHOD_NAME_ENABLED, GENERATE_CURRENT_METHOD_NAME_ENABLED_DEFAULT));

        panel.add(new JLabel("Logger Variable Name"));
        logVariableName = new JTextField();
        panel.add(logVariableName);
        logVariableName.setText(propertiesComponent.getValue(GENERATE_LOG_VARIABLE_NAME, GENERATE_LOG_VARIABLE_NAME_DEFAULT));
        return container;
    }

    @Override
    public boolean isModified() {
        return !(Objects.equals(String.valueOf(generateCurrentMethodName.isSelected()), String.valueOf(propertiesComponent.getBoolean(GENERATE_CURRENT_METHOD_NAME_ENABLED, GENERATE_CURRENT_METHOD_NAME_ENABLED_DEFAULT))) &&
                Objects.equals(logVariableName.getText(), propertiesComponent.getValue(GENERATE_LOG_VARIABLE_NAME, GENERATE_LOG_VARIABLE_NAME_DEFAULT))
        );
    }

    @Override
    public void apply() throws ConfigurationException {
        propertiesComponent.setValue(GENERATE_CURRENT_METHOD_NAME_ENABLED, String.valueOf(generateCurrentMethodName.isSelected()));
        propertiesComponent.setValue(GENERATE_LOG_VARIABLE_NAME, logVariableName.getText());
    }
}
