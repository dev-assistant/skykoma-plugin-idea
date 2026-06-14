package cn.hylstudio.skykoma.plugin.idea.service.impl;

import cn.hylstudio.skykoma.plugin.idea.service.PythonEnvService;
import cn.hylstudio.skykoma.plugin.idea.util.SkykomaNotifier;
import com.intellij.openapi.diagnostic.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PythonEnvServiceImpl implements PythonEnvService {
    private static final Logger LOGGER = Logger.getInstance(PythonEnvServiceImpl.class);

    private static final String[] WIN_SEARCH_PATHS = {
            System.getenv("LOCALAPPDATA") + "\\Programs\\Python\\Python312\\python.exe",
            "C:\\Python312\\python.exe",
            "C:\\Program Files\\Python312\\python.exe"
    };

    private static final String[] LINUX_SEARCH_PATHS = {
            "/usr/bin/python3.12",
            "/usr/local/bin/python3.12"
    };

    private static final String[] MAC_SEARCH_PATHS = {
            "/usr/local/bin/python3.12",
            "/opt/homebrew/bin/python3.12"
    };

    @Override
    public String findPython312() {
        String osName = System.getProperty("os.name").toLowerCase();
        String[] searchPaths;

        if (osName.startsWith("windows")) {
            searchPaths = WIN_SEARCH_PATHS;
        } else if (osName.startsWith("mac")) {
            searchPaths = MAC_SEARCH_PATHS;
        } else {
            searchPaths = LINUX_SEARCH_PATHS;
        }

        for (String path : searchPaths) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                LOGGER.info("Found Python 3.12 at: " + path);
                return path;
            }
        }

        String pythonCmd = osName.startsWith("windows") ? "python" : "python3";
        try {
            Process p = new ProcessBuilder(pythonCmd, "--version")
                    .redirectErrorStream(true)
                    .start();
            p.waitFor(3, TimeUnit.SECONDS);
            String output = new String(p.getInputStream().readAllBytes());
            if (output.contains("3.12")) {
                LOGGER.info("Found Python 3.12 via PATH: " + pythonCmd);
                return pythonCmd;
            }
        } catch (Exception ignored) {
        }

        LOGGER.info("Python 3.12 not found automatically");
        return null;
    }

    @Override
    public boolean isVenvInitialized(String venvPath) {
        Path venvDir = Paths.get(venvPath);
        String osName = System.getProperty("os.name").toLowerCase();
        String pythonBin = osName.startsWith("windows") ? "Scripts\\python.exe" : "bin/python";
        Path pythonExe = venvDir.resolve(pythonBin);
        boolean exists = Files.exists(pythonExe);
        LOGGER.info("Venv check at " + venvPath + ": " + (exists ? "initialized" : "not initialized"));
        return exists;
    }

    @Override
    public String initVenv(String venvPath, String pythonExecutable, String pipPackages, String pipMirror) {
        try {
            Path venvDir = Paths.get(venvPath);
            if (!Files.exists(venvDir)) {
                Files.createDirectories(venvDir);
            }

            SkykomaNotifier.notifyInfo("Creating virtualenv at " + venvPath + " ...");

            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, "-m", "venv", venvPath);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                String msg = "venv creation timed out";
                LOGGER.error(msg);
                SkykomaNotifier.notifyError(msg);
                return null;
            }
            if (p.exitValue() != 0) {
                String output = new String(p.getInputStream().readAllBytes());
                String msg = "venv creation failed: " + output;
                LOGGER.error(msg);
                SkykomaNotifier.notifyError(msg);
                return null;
            }

            LOGGER.info("Virtualenv created successfully at " + venvPath);

            String venvPython = getVenvPythonExecutable(venvPath);
            installPipPackages(venvPython, pipPackages, pipMirror);

            SkykomaNotifier.notifyInfo("Python venv initialized successfully at " + venvPath);
            return venvPython;
        } catch (Exception e) {
            String msg = "initVenv error: " + e.getMessage();
            LOGGER.error(msg, e);
            SkykomaNotifier.notifyError(msg);
            return null;
        }
    }

    private void installPipPackages(String venvPython, String pipPackages, String pipMirror) throws IOException, InterruptedException {
        if (pipPackages == null || pipPackages.trim().isEmpty()) {
            LOGGER.info("No pip packages configured, skipping install");
            return;
        }

        List<String> packages = Arrays.stream(pipPackages.split("\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (packages.isEmpty()) {
            LOGGER.info("No pip packages configured, skipping install");
            return;
        }

        SkykomaNotifier.notifyInfo("Installing pip packages...");

        List<String> cmd = new ArrayList<>();
        cmd.add(venvPython);
        cmd.add("-m");
        cmd.add("pip");
        cmd.add("install");
        if (pipMirror != null && !pipMirror.trim().isEmpty()) {
            cmd.add("-i");
            cmd.add(pipMirror.trim());
        }
        cmd.addAll(packages);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                LOGGER.info("[pip] " + line);
            }
        }

        boolean finished = p.waitFor(300, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            LOGGER.error("pip install timed out");
            SkykomaNotifier.notifyError("pip install timed out");
            return;
        }

        if (p.exitValue() != 0) {
            LOGGER.error("pip install failed: " + output);
            SkykomaNotifier.notifyError("pip install failed, check log for details");
        } else {
            LOGGER.info("pip packages installed successfully");
            SkykomaNotifier.notifyInfo("pip packages installed successfully");
        }
    }

    @Override
    public String getVenvPythonExecutable(String venvPath) {
        String osName = System.getProperty("os.name").toLowerCase();
        String pythonBin = osName.startsWith("windows") ? "Scripts\\python.exe" : "bin/python";
        return Paths.get(venvPath, pythonBin).toString();
    }
}
