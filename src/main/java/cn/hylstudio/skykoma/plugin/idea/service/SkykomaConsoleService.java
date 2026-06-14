package cn.hylstudio.skykoma.plugin.idea.service;

import com.intellij.execution.ui.ConsoleView;

public interface SkykomaConsoleService {
    ConsoleView initConsole(com.intellij.openapi.project.Project project);
    ConsoleView getConsole();
    void clear();
    void appendInfo(String text);
    void appendError(String text);
}
