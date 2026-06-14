package cn.hylstudio.skykoma.plugin.idea.service.impl;

import cn.hylstudio.skykoma.plugin.idea.service.SkykomaConsoleService;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SkykomaConsoleServiceImpl implements SkykomaConsoleService {

    private final List<ConsoleView> consoleViews = new CopyOnWriteArrayList<>();
    private final List<String> bufferedLines = new ArrayList<>();
    private static final int MAX_BUFFERED_LINES = 10000;

    @Override
    public ConsoleView initConsole(Project project) {
        ConsoleView cv = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        consoleViews.add(cv);
        replayBuffer(cv);
        return cv;
    }

    private void replayBuffer(ConsoleView cv) {
        synchronized (bufferedLines) {
            for (String line : bufferedLines) {
                cv.print(line, ConsoleViewContentType.NORMAL_OUTPUT);
            }
        }
    }

    @Override
    public ConsoleView getConsole() {
        return consoleViews.isEmpty() ? null : consoleViews.get(0);
    }

    @Override
    public void clear() {
        synchronized (bufferedLines) {
            bufferedLines.clear();
        }
        for (ConsoleView cv : consoleViews) {
            cv.clear();
        }
    }

    @Override
    public void appendInfo(String text) {
        String line = text + "\n";
        addToBuffer(line);
        for (ConsoleView cv : consoleViews) {
            cv.print(line, ConsoleViewContentType.NORMAL_OUTPUT);
        }
    }

    @Override
    public void appendError(String text) {
        String line = text + "\n";
        addToBuffer(line);
        for (ConsoleView cv : consoleViews) {
            cv.print(line, ConsoleViewContentType.ERROR_OUTPUT);
        }
    }

    private void addToBuffer(String line) {
        synchronized (bufferedLines) {
            bufferedLines.add(line);
            if (bufferedLines.size() > MAX_BUFFERED_LINES) {
                bufferedLines.subList(0, bufferedLines.size() - MAX_BUFFERED_LINES).clear();
            }
        }
    }
}
