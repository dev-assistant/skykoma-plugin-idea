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
import java.util.function.Consumer;

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
    public String initVenv(String venvPath, String pythonExecutable, String pipPackages, String pipMirror, Consumer<String> outputConsumer) {
        try {
            Path venvDir = Paths.get(venvPath);
            if (!Files.exists(venvDir)) {
                Files.createDirectories(venvDir);
            }

            String msg = "Creating virtualenv at " + venvPath + " ...";
            SkykomaNotifier.notifyInfo(msg);
            if (outputConsumer != null) outputConsumer.accept(msg);

            boolean venvOk = runVenvCreation(pythonExecutable, venvPath, outputConsumer);
            if (!venvOk) {
                if (outputConsumer != null) outputConsumer.accept("[INFO] venv failed, trying virtualenv...");
                installVirtualenv(pythonExecutable, outputConsumer);
                venvOk = runVirtualenvCreation(pythonExecutable, venvPath, outputConsumer);
            }
            if (!venvOk) {
                String err = "venv creation failed";
                LOGGER.error(err);
                SkykomaNotifier.notifyError(err);
                if (outputConsumer != null) outputConsumer.accept("[ERROR] " + err);
                return null;
            }

            LOGGER.info("Virtualenv created successfully at " + venvPath);
            if (outputConsumer != null) outputConsumer.accept("[OK] Virtualenv created");

            String venvPython = getVenvPythonExecutable(venvPath);
            installPipPackages(venvPython, pipPackages, pipMirror, outputConsumer);

            String done = "Python venv initialized successfully at " + venvPath;
            SkykomaNotifier.notifyInfo(done);
            if (outputConsumer != null) outputConsumer.accept("[OK] " + done);
            return venvPython;
        } catch (Exception e) {
            String err = "initVenv error: " + e.getMessage();
            LOGGER.error(err, e);
            SkykomaNotifier.notifyError(err);
            if (outputConsumer != null) outputConsumer.accept("[ERROR] " + err);
            return null;
        }
    }

    private boolean runVenvCreation(String pythonExecutable, String venvPath, Consumer<String> outputConsumer) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(pythonExecutable, "-m", "venv", venvPath);
        pb.redirectErrorStream(true);
        if (outputConsumer != null) {
            outputConsumer.accept("$ " + String.join(" ", pb.command()));
        }
        return runProcess(pb, 120, outputConsumer);
    }

    private void installVirtualenv(String pythonExecutable, Consumer<String> outputConsumer) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(pythonExecutable, "-m", "pip", "install", "virtualenv");
        pb.redirectErrorStream(true);
        if (outputConsumer != null) {
            outputConsumer.accept("$ " + String.join(" ", pb.command()));
        }
        runProcess(pb, 60, outputConsumer);
    }

    private boolean runVirtualenvCreation(String pythonExecutable, String venvPath, Consumer<String> outputConsumer) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(pythonExecutable, "-m", "virtualenv", venvPath);
        pb.redirectErrorStream(true);
        if (outputConsumer != null) {
            outputConsumer.accept("$ " + String.join(" ", pb.command()));
        }
        return runProcess(pb, 120, outputConsumer);
    }

    private boolean runProcess(ProcessBuilder pb, int timeoutSeconds, Consumer<String> outputConsumer) throws IOException, InterruptedException {
        Process p = pb.start();
        if (outputConsumer != null) {
            readProcessOutput(p, outputConsumer);
        }
        boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            if (outputConsumer != null) outputConsumer.accept("[ERROR] Process timed out");
            return false;
        }
        if (p.exitValue() != 0) {
            if (outputConsumer != null) outputConsumer.accept("[FAIL] Exit code: " + p.exitValue());
            return false;
        }
        return true;
    }

    private void installPipPackages(String venvPython, String pipPackages, String pipMirror, Consumer<String> outputConsumer) throws IOException, InterruptedException {
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

        String msg = "Installing pip packages...";
        SkykomaNotifier.notifyInfo(msg);
        if (outputConsumer != null) outputConsumer.accept(msg);

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

        if (outputConsumer != null) {
            outputConsumer.accept("$ " + String.join(" ", cmd));
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        if (outputConsumer != null) {
            readProcessOutput(p, outputConsumer);
        } else {
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    LOGGER.info("[pip] " + line);
                }
            }
        }

        boolean finished = p.waitFor(300, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            LOGGER.error("pip install timed out");
            SkykomaNotifier.notifyError("pip install timed out");
            if (outputConsumer != null) outputConsumer.accept("[ERROR] pip install timed out");
            return;
        }

        if (p.exitValue() != 0) {
            LOGGER.error("pip install failed");
            SkykomaNotifier.notifyError("pip install failed, check log for details");
            if (outputConsumer != null) outputConsumer.accept("[FAIL] pip install failed");
        } else {
            LOGGER.info("pip packages installed successfully");
            SkykomaNotifier.notifyInfo("pip packages installed successfully");
            if (outputConsumer != null) outputConsumer.accept("[OK] pip packages installed");
        }
    }

    private void readProcessOutput(Process p, Consumer<String> outputConsumer) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputConsumer.accept(line);
                }
            } catch (IOException ignored) {
            }
        }).start();
    }

    @Override
    public Process startJupyterLab(String venvPython, String ip, boolean allowRoot, String token, String workDir, Consumer<String> outputConsumer) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(venvPython);
            cmd.add("-m");
            cmd.add("jupyter");
            cmd.add("lab");
            cmd.add("--ip=" + ip);
            if (allowRoot) {
                cmd.add("--allow-root");
            }
            if (token != null && !token.isEmpty()) {
                cmd.add("--IdentityProvider.token=" + token);
            }

            String cmdStr = "$ " + String.join(" ", cmd);
            outputConsumer.accept(cmdStr);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            if (workDir != null && !workDir.isEmpty()) {
                File dir = new File(workDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                pb.directory(dir);
                outputConsumer.accept("[OK] Working directory: " + dir.getAbsolutePath());
            }
            Process p = pb.start();

            readProcessOutput(p, outputConsumer);
            return p;
        } catch (IOException e) {
            String err = "startJupyterLab error: " + e.getMessage();
            LOGGER.error(err, e);
            outputConsumer.accept("[ERROR] " + err);
            return null;
        }
    }

    @Override
    public String getVenvPythonExecutable(String venvPath) {
        String osName = System.getProperty("os.name").toLowerCase();
        String pythonBin = osName.startsWith("windows") ? "Scripts\\python.exe" : "bin/python";
        return Paths.get(venvPath, pythonBin).toString();
    }
}
