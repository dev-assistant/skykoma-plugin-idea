package cn.hylstudio.skykoma.plugin.idea.service;

import com.intellij.execution.ui.ConsoleView;

public interface SkykomaConsoleService {
    ConsoleView initConsole(com.intellij.openapi.project.Project project);
    ConsoleView initLabConsole(com.intellij.openapi.project.Project project);
    ConsoleView getConsole();
    ConsoleView getLabConsole();
    void clear();
    void clearLab();
    void appendInfo(String text);
    void appendError(String text);
    void appendLabInfo(String text);
    void appendLabError(String text);
}
