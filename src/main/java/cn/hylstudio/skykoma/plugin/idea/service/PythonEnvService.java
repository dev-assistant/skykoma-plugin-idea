package cn.hylstudio.skykoma.plugin.idea.service;

public interface PythonEnvService {

    String findPython312();

    boolean isVenvInitialized(String venvPath);

    String initVenv(String venvPath, String pythonExecutable, String pipPackages, String pipMirror);

    String getVenvPythonExecutable(String venvPath);
}
