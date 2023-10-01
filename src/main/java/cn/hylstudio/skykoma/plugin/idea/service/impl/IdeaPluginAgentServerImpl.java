package cn.hylstudio.skykoma.plugin.idea.service.impl;

import cn.hylstudio.skykoma.plugin.idea.SkykomaConstants;
import cn.hylstudio.skykoma.plugin.idea.service.IdeaPluginAgentServer;
import cn.hylstudio.skykoma.plugin.idea.service.verticle.DelegateVerticle;
import cn.hylstudio.skykoma.plugin.idea.util.GsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import io.vertx.core.Vertx;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static cn.hylstudio.skykoma.plugin.idea.util.LogUtils.error;
import static cn.hylstudio.skykoma.plugin.idea.util.LogUtils.info;

public class IdeaPluginAgentServerImpl implements IdeaPluginAgentServer {
    private static final Logger LOGGER = Logger.getInstance(IdeaPluginAgentServerImpl.class);
    private static final Vertx vertx = Vertx.vertx();
    private static boolean started = false;

    private static String configAddress;
    private static Integer configPort;

    private static DelegateVerticle delegateVerticle;

    @Override
    public void start() {
        reloadConfig();
        String listenAddress = configAddress;
        int port = configPort;
        if (!started) {
            delegateVerticle = new DelegateVerticle(listenAddress, port);
            vertx.deployVerticle(delegateVerticle, result -> {
                boolean succeeded = result.succeeded();
                info(LOGGER, String.format("IdeaPluginAgentServer start, result = %s", succeeded));
            });
            registerAsJupyterKernel();
            started = true;
        }
        info(LOGGER, "IdeaPluginAgentServer already start");
    }

    @Override
    public void registerAsJupyterKernel() {
        String cmd = genRegisterKernelCmd();
        info(LOGGER, String.format("registerAsJupyterKernel, cmd = [%s]", cmd));
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor(5, TimeUnit.SECONDS);
            info(LOGGER, String.format("registerAsJupyterKernel, execute finished, cmd = [%s]", cmd));
            String pythonExecutable = getPythonExecutable();
            String kernelName = getKernelName();
            String folderName = String.format("kotlin_%s", kernelName);
            String userJupyterPath = getUserJupyterPath(folderName);
            File kernelJsonFile = new File(userJupyterPath + File.separator + "kernel.json");
            String kernelJsonStr = FileUtils.readFileToString(kernelJsonFile, Charset.defaultCharset());
            JsonObject kernelJson = JsonParser.parseString(kernelJsonStr).getAsJsonObject();
            JsonArray argvArr = kernelJson.get("argv").getAsJsonArray();
            argvArr.set(0, new JsonPrimitive(pythonExecutable));
            FileUtils.writeStringToFile(kernelJsonFile, GsonUtils.JUPYTER_KERNEL_JSON.toJson(kernelJson), Charset.defaultCharset());
        } catch (Exception e) {
            error(LOGGER, String.format("registerAsJupyterKernel error, e = [%s]", e.getMessage()), e);
        }
    }

    @NotNull
    private static String getKernelName() {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        return SkykomaConstants.JUPYTER_KERNEL_NAME_PREFIX + propertiesComponent.getValue(SkykomaConstants.JUPYTER_KERNEL_NAME, SkykomaConstants.JUPYTER_KERNEL_NAME_DEFAULT);
    }

    @NotNull
    private static String getPythonExecutable() {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        return propertiesComponent.getValue(SkykomaConstants.JUPYTER_PYTHON_EXECUTABLE, SkykomaConstants.JUPYTER_PYTHON_EXECUTABLE_DEFAULT);
    }

    @Override
    public String genRegisterKernelCmd() {
        reloadConfig();
        String listenAddress = configAddress;
        int port = configPort;
        info(LOGGER, String.format("genRegisterKernelCmd, listenAddress = [%s], port = [%s]", listenAddress, port));
        String pythonExecutable = getPythonExecutable();
        String kernelName = getKernelName();
        List<String> argv = new ArrayList<>();
        argv.add(pythonExecutable);
        argv.add("-m kotlin_kernel");
        argv.add("add-kernel");
        argv.add("--force");
        argv.add(String.format("--name \"%s\"", kernelName));
        argv.add(String.format("--env SKYKOMA_AGENT_SERVER_API %s:%s/startJupyterKernel", listenAddress, port));
        argv.add("--env SKYKOMA_AGENT_TYPE idea");
//        String javaExecutable = detectedJavaExecutable();
//        if (!StringUtils.isEmpty(javaExecutable)) {
//            argv.add(String.format("--env KOTLIN_JUPYTER_JAVA_EXECUTABLE %s", javaExecutable));
//        }
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
            delegateVerticle.stop();
        } catch (Exception e) {
            error(LOGGER, "close agent server error", e);
        }
        started = false;
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
        int configPort = propertiesComponent.getInt(SkykomaConstants.AGENT_SERVER_LISTEN_PORT, SkykomaConstants.AGENT_SERVER_LISTEN_PORT_DEFAULT);
        info(LOGGER, String.format("IdeaPluginAgentServer reading config configAddress = %s", configAddress));
        info(LOGGER, String.format("IdeaPluginAgentServer reading config port = %s", configPort));
        IdeaPluginAgentServerImpl.configAddress = configAddress;
        IdeaPluginAgentServerImpl.configPort = configPort;
    }

}
