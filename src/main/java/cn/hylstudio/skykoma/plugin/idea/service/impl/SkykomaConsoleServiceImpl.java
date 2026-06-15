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
    private final List<ConsoleView> labConsoleViews = new CopyOnWriteArrayList<>();
    private final List<String> bufferedLines = new ArrayList<>();
    private final List<String> labBufferedLines = new ArrayList<>();
    private static final int MAX_BUFFERED_LINES = 10000;

    @Override
    public ConsoleView initConsole(Project project) {
        ConsoleView cv = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        consoleViews.add(cv);
        replayBuffer(cv, bufferedLines);
        return cv;
    }

    @Override
    public ConsoleView initLabConsole(Project project) {
        ConsoleView cv = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        labConsoleViews.add(cv);
        replayBuffer(cv, labBufferedLines);
        return cv;
    }

    private void replayBuffer(ConsoleView cv, List<String> buffer) {
        synchronized (buffer) {
            for (String line : buffer) {
                cv.print(line, ConsoleViewContentType.NORMAL_OUTPUT);
            }
        }
    }

    @Override
    public ConsoleView getConsole() {
        return consoleViews.isEmpty() ? null : consoleViews.get(0);
    }

    @Override
    public ConsoleView getLabConsole() {
        return labConsoleViews.isEmpty() ? null : labConsoleViews.get(0);
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
    public void clearLab() {
        synchronized (labBufferedLines) {
            labBufferedLines.clear();
        }
        for (ConsoleView cv : labConsoleViews) {
            cv.clear();
        }
    }

    @Override
    public void appendInfo(String text) {
        String line = text + "\n";
        addToBuffer(line, bufferedLines);
        for (ConsoleView cv : consoleViews) {
            cv.print(line, ConsoleViewContentType.NORMAL_OUTPUT);
        }
    }

    @Override
    public void appendError(String text) {
        String line = text + "\n";
        addToBuffer(line, bufferedLines);
        for (ConsoleView cv : consoleViews) {
            cv.print(line, ConsoleViewContentType.ERROR_OUTPUT);
        }
    }

    @Override
    public void appendLabInfo(String text) {
        String line = text + "\n";
        addToBuffer(line, labBufferedLines);
        for (ConsoleView cv : labConsoleViews) {
            cv.print(line, ConsoleViewContentType.NORMAL_OUTPUT);
        }
    }

    @Override
    public void appendLabError(String text) {
        String line = text + "\n";
        addToBuffer(line, labBufferedLines);
        for (ConsoleView cv : labConsoleViews) {
            cv.print(line, ConsoleViewContentType.ERROR_OUTPUT);
        }
    }

    private void addToBuffer(String line, List<String> buffer) {
        synchronized (buffer) {
            buffer.add(line);
            if (buffer.size() > MAX_BUFFERED_LINES) {
                buffer.subList(0, buffer.size() - MAX_BUFFERED_LINES).clear();
            }
        }
    }
}
