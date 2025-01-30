package cn.hylstudio.skykoma.plugin.idea.service.impl;

import cn.hylstudio.skykoma.plugin.idea.KotlinReplWrapper;
import cn.hylstudio.skykoma.plugin.idea.SkykomaConstants;
import cn.hylstudio.skykoma.plugin.idea.service.IdeaPluginAgentServer;
import cn.hylstudio.skykoma.plugin.idea.service.verticle.AgentHttpApiVerticle;
import cn.hylstudio.skykoma.plugin.idea.util.GsonUtils;
import cn.hylstudio.skykoma.plugin.idea.util.SkykomaNotifier;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import io.vertx.core.Vertx;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static cn.hylstudio.skykoma.plugin.idea.util.LogUtils.error;
import static cn.hylstudio.skykoma.plugin.idea.util.LogUtils.info;

public class IdeaPluginAgentServerImpl implements IdeaPluginAgentServer {
    private static final Logger LOGGER = Logger.getInstance(IdeaPluginAgentServerImpl.class);
    private static final Vertx vertx = Vertx.vertx();
    private static String configAddress;
    private static Integer configPort;
    private static Thread jupyterServerThread = null;
    private static AgentHttpApiVerticle agentHttpApiVerticle;

    @Override
    public void start() {
        reloadConfig();
        String listenAddress = configAddress;
        int port = configPort;
        if (agentHttpApiVerticle == null || !agentHttpApiVerticle.isStarted()) {
            agentHttpApiVerticle = new AgentHttpApiVerticle(listenAddress, port);
            vertx.deployVerticle(agentHttpApiVerticle, result -> {
                boolean succeeded = result.succeeded();
                info(LOGGER, String.format("IdeaPluginAgentServer start, result = %s", succeeded));
            });
            registerAsJupyterKernel();
        } else {
            agentHttpApiVerticle.showInfo();
        }
    }

    @Override
    public void registerAsJupyterKernel() {
        String pythonExecutable = getPythonExecutable();
        String cmd = genRegisterKernelCmd(pythonExecutable);
        info(LOGGER, String.format("registerAsJupyterKernel, cmd = [%s]", cmd));
        try {
            boolean registerSucc = registerCustomKernel(cmd);
            if (registerSucc) {
                String kernelJsonPath = getKernelJsonPath();
                updateKernelJson(kernelJsonPath, pythonExecutable);
            }
        } catch (Exception e) {
            String registerError = String.format("registerAsJupyterKernel error, e = [%s]", e.getMessage());
            error(LOGGER, registerError, e);
            SkykomaNotifier.notifyError(registerError);
        }
    }

    private static boolean registerCustomKernel(String cmd) throws IOException, InterruptedException {
        boolean succ = false;
        Process process = Runtime.getRuntime().exec(cmd);
        boolean exited = process.waitFor(5, TimeUnit.SECONDS);
        if (exited) {
            int exitValue = process.exitValue();
            if (exitValue == 0) {
                succ = true;
                SkykomaNotifier.notifyInfo("registerAsJupyterKernel succ");
            } else {
                String msg = String.format("registerAsJupyterKernel failed, exitValue = [%s], cmd = [%s]", exitValue, cmd);
                info(LOGGER, msg);
                SkykomaNotifier.notifyInfo(msg);
            }
        } else {
            info(LOGGER, String.format("registerAsJupyterKernel timeout, cmd = [%s]", cmd));
        }
        return succ;
    }

    @Override
    public String getKernelJsonPath() {
        String kernelName = getKernelName();
        String folderName = String.format("kotlin_%s", kernelName);
        String userJupyterPath = getUserJupyterPath(folderName);
        return userJupyterPath + File.separator + "kernel.json";
    }

    private static void updateKernelJson(String kernelJsonPath, String pythonExecutable) throws IOException {
        File kernelJsonFile = new File(kernelJsonPath);
        if (!kernelJsonFile.exists()) {
            String registerFinished = String.format("updateKernelJson error, kernel.json not exist, path = " + kernelJsonFile.getAbsolutePath());
            info(LOGGER, registerFinished);
            return;
        }
        String kernelJsonStr = FileUtils.readFileToString(kernelJsonFile, Charset.defaultCharset());
        JsonObject kernelJson = JsonParser.parseString(kernelJsonStr).getAsJsonObject();
        JsonArray argvArr = kernelJson.get("argv").getAsJsonArray();
        // python -> absPath
        argvArr.set(0, new JsonPrimitive(pythonExecutable));
        // TODO replace custom run_kernel cmd
        FileUtils.writeStringToFile(kernelJsonFile, GsonUtils.JUPYTER_KERNEL_JSON.toJson(kernelJson),
                Charset.defaultCharset());
    }

    @NotNull
    private static String getKernelName() {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        return SkykomaConstants.JUPYTER_KERNEL_NAME_PREFIX + propertiesComponent
                .getValue(SkykomaConstants.JUPYTER_KERNEL_NAME, SkykomaConstants.JUPYTER_KERNEL_NAME_DEFAULT);
    }

    @NotNull
    private static String getPythonExecutable() {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        return propertiesComponent.getValue(SkykomaConstants.JUPYTER_PYTHON_EXECUTABLE,
                SkykomaConstants.JUPYTER_PYTHON_EXECUTABLE_DEFAULT);
    }

    @Override
    public String genRegisterKernelCmd() {
        return genRegisterKernelCmd(getPythonExecutable());
    }

    private String genRegisterKernelCmd(String pythonExecutable) {
        reloadConfig();
        String listenAddress = configAddress;
        int port = configPort;
        info(LOGGER, String.format("genRegisterKernelCmd, listenAddress = [%s], port = [%s]", listenAddress, port));
        String kernelName = getKernelName();
        List<String> argv = new ArrayList<>();
        argv.add(pythonExecutable);
        argv.add("-m kotlin_kernel");
        argv.add("add-kernel");
        argv.add("--force");
        argv.add(String.format("--name %s", kernelName));
        argv.add(String.format("--env SKYKOMA_AGENT_SERVER_API %s:%s", listenAddress, port));
        argv.add("--env SKYKOMA_AGENT_TYPE idea");
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        int jupyterServerHbPort = propertiesComponent.getInt(SkykomaConstants.JUPYTER_SERVER_HB_PORT, SkykomaConstants.JUPYTER_SERVER_HB_PORT_DEFAULT);
        int jupyterShellPort = propertiesComponent.getInt(SkykomaConstants.JUPYTER_SERVER_SHELL_PORT, SkykomaConstants.JUPYTER_SERVER_SHELL_PORT_DEFAULT);
        int jupyterIopubPort = propertiesComponent.getInt(SkykomaConstants.JUPYTER_SERVER_IOPUB_PORT, SkykomaConstants.JUPYTER_SERVER_IOPUB_PORT_DEFAULT);
        int jupyterStdinPort = propertiesComponent.getInt(SkykomaConstants.JUPYTER_SERVER_STDIN_PORT, SkykomaConstants.JUPYTER_SERVER_STDIN_PORT_DEFAULT);
        int jupyterControlPort = propertiesComponent.getInt(SkykomaConstants.JUPYTER_SERVER_CONTROL_PORT, SkykomaConstants.JUPYTER_SERVER_CONTROL_PORT_DEFAULT);
        argv.add(String.format("--env JUPYTER_SERVER_HB_PORT %s", jupyterServerHbPort));
        argv.add(String.format("--env JUPYTER_SERVER_SHELL_PORT %s", jupyterShellPort));
        argv.add(String.format("--env JUPYTER_SERVER_IOPUB_PORT %s", jupyterIopubPort));
        argv.add(String.format("--env JUPYTER_SERVER_STDIN_PORT %s", jupyterStdinPort));
        argv.add(String.format("--env JUPYTER_SERVER_CONTROL_PORT %s", jupyterControlPort));
        // String javaExecutable = detectedJavaExecutable();
        // if (!StringUtils.isEmpty(javaExecutable)) {
        // argv.add(String.format("--env KOTLIN_JUPYTER_JAVA_EXECUTABLE %s",
        // javaExecutable));
        // }
        String cmd = String.join(" ", argv);
        return cmd;
    }

    private static String getUserJupyterPath(String folderName) {
        Path path = null;
        String systemCode = System.getProperty("os.name");
        if (systemCode.startsWith("Windows")) {
            path = Path.of(System.getenv("APPDATA"), "jupyter", "kernels", folderName);
        } else if (systemCode.startsWith("Linux")) {
            path = Path.of(System.getProperty("user.home") + "/.local/share/jupyter", "kernels", folderName);
        } else if (systemCode.startsWith("Mac")) {
            path = Path.of(System.getProperty("user.home") + "/Library/Jupyter", "kernels", folderName);
        } else {
            throw new RuntimeException("Unknown platform: " + systemCode);
        }
        return path.toAbsolutePath().toString();
    }

    private static String detectedJavaExecutable() {
        ProcessHandle current = ProcessHandle.current();
        ProcessHandle tmp = current;
        while (true) {
            ProcessHandle.Info info = tmp.info();
            String cmd = info.command().orElse("");
            if (cmd.contains("jbr")) {
                return cmd;
            }
            Optional<ProcessHandle> parentProcess = current.parent();
            if (parentProcess.isEmpty()) {
                break;
            }
            current = current.parent().orElse(null);
        }
        return "";
    }

    @Override
    public void stop() {
        try {
            if (agentHttpApiVerticle != null) {
                agentHttpApiVerticle.stop();
            }
        } catch (Exception e) {
            error(LOGGER, "close agent server error", e);
        }
    }

    @Override
    public void restart() {
        stop();
        start();
    }

    private static void reloadConfig() {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        String configAddress = propertiesComponent.getValue(SkykomaConstants.AGENT_SERVER_LISTEN_ADDRESS, "");
        if (StringUtils.isEmpty(configAddress)) {
            configAddress = SkykomaConstants.AGENT_SERVER_LISTEN_ADDRESS_DEFAULT;
        }
        int configPort = propertiesComponent.getInt(SkykomaConstants.AGENT_SERVER_LISTEN_PORT,
                SkykomaConstants.AGENT_SERVER_LISTEN_PORT_DEFAULT);
        info(LOGGER, String.format("IdeaPluginAgentServer reading config configAddress = %s", configAddress));
        info(LOGGER, String.format("IdeaPluginAgentServer reading config port = %s", configPort));
        IdeaPluginAgentServerImpl.configAddress = configAddress;
        IdeaPluginAgentServerImpl.configPort = configPort;
    }


    @Override
    public String queryJupyterKernelStatus(String payload) {
        if (jupyterServerThread != null && jupyterServerThread.isAlive()) {
            return "RUNNING";
        }
        return "STOPPED";
    }

    @Override
    public void startJupyterKernel(String payload) {
        stopJupyterKernel();
        IdeaPluginDescriptor ideaPluginDescriptor = Arrays.stream(PluginManagerCore.getPlugins())
                .filter(v -> v.getPluginId().getIdString().equals(SkykomaConstants.PLUGIN_ID))
                .findFirst().orElse(null);
        if (ideaPluginDescriptor == null) {
            SkykomaNotifier.notifyError("startJupyterKernel failed, can't find ideaPluginDescriptor");
            return;
        }
        ClassLoader pluginClassLoader = ideaPluginDescriptor.getPluginClassLoader();
        if (pluginClassLoader == null) {
            SkykomaNotifier.notifyError("startJupyterKernel failed, can't find pluginClassLoader");
            return;
        }
        try {
            jupyterServerThread = new Thread(() -> {
                KotlinReplWrapper wrapper = KotlinReplWrapper.getInstance(pluginClassLoader);
                wrapper.makeEmbeddedRepl(payload);
            });
            jupyterServerThread.setContextClassLoader(pluginClassLoader);
            jupyterServerThread.start();
            SkykomaNotifier.notifyInfo("startJupyterKernel succ");
        } catch (Exception e) {
            String startJupyterKernelErrorMsg = String.format("startJupyterKernel has error, e = [%s]", e.getMessage());
            error(LOGGER, startJupyterKernelErrorMsg, e);
            SkykomaNotifier.notifyError(startJupyterKernelErrorMsg);
        }
    }

    @Override
    public void stopJupyterKernel() {
        if (jupyterServerThread == null) {
            return;
        }
        int n = 5;
        while (n-- > 0) {
            LOGGER.info(String.format("stopJupyterKernel, try times remains = [%s]", n));
            try {
                if (jupyterServerThread.isInterrupted()) {
                    break;
                } else {
                    jupyterServerThread.interrupt();
                }
            } catch (Exception ignored) {
            } finally {
                jupyterServerThread = null;
            }
        }
        LOGGER.info("stopJupyterKernel finished");
    }
}
