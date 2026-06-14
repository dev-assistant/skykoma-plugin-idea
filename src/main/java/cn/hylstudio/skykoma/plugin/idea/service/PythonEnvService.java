package cn.hylstudio.skykoma.plugin.idea.service;

import java.util.function.Consumer;

public interface PythonEnvService {

    String findPython312();

    boolean isVenvInitialized(String venvPath);

    String initVenv(String venvPath, String pythonExecutable, String pipPackages, String pipMirror, Consumer<String> outputConsumer);

    String getVenvPythonExecutable(String venvPath);

    Process startJupyterLab(String venvPython, String ip, boolean allowRoot, String token, String workDir, Consumer<String> outputConsumer);
}
