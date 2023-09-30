package cn.hylstudio.skykoma.plugin.idea.service.impl;

import cn.hylstudio.skykoma.plugin.idea.SkykomaConstants;
import cn.hylstudio.skykoma.plugin.idea.service.IdeaPluginAgentServer;
import cn.hylstudio.skykoma.plugin.idea.service.verticle.DelegateVerticle;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import io.vertx.core.Vertx;
import org.apache.commons.lang.StringUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class IdeaPluginAgentServerImpl implements IdeaPluginAgentServer {
    private static final Vertx vertx = Vertx.vertx();
    private static boolean started = false;
    private static final Logger LOGGER = Logger.getInstance(IdeaPluginAgentServerImpl.class);

    private void start(String listenAddress, int port) {
        if (!started) {
            DelegateVerticle delegateVerticle = new DelegateVerticle(listenAddress, port);
            vertx.deployVerticle(delegateVerticle, result -> {
                boolean succeeded = result.succeeded();
                LOGGER.info(String.format("IDeaPluginAgentServerImpl start, result = %s", succeeded));
            });
//            registerJupyterKernelIdea(listenAddress, port);
            started = true;
        }
        LOGGER.info("IDeaPluginAgentServerImpl already start");
    }

    private void registerJupyterKernelIdea(String listenAddress, int port) {
        LOGGER.info(String.format("registerJupyterKernelIdea, listenAddress = [%s], port = [%s]", listenAddress, port));
        List<String> argv = Lists.newArrayList(
                "python",
                "-m kotlin_kernel",
                "add-kernel",
                "--force",
                "--env SKYKOMA_AGENT_TYPE idea"
        );
        String kernelName = "skykoma-agent-idea";
        String folderName = String.format("kotlin_%s", kernelName);
        argv.add(String.format("--name \"%s\"", kernelName));
        argv.add(String.format("--env SKYKOMA_AGENT_SERVER_API http://%s:%s/startJupyterKernel", listenAddress, port));
        String javaExecutable = detectedJavaExecutable();
        if (!StringUtils.isEmpty(javaExecutable)) {
            argv.add(String.format("--env KOTLIN_JUPYTER_JAVA_EXECUTABLE %s", javaExecutable));
        }
        String cmd = String.join(" ", argv);
        LOGGER.info(String.format("registerJupyterKernelIdea, cmd = [%s]", cmd));
        try {
            Runtime.getRuntime().exec(cmd);
//            python -m kotlin_kernel add-kernel --force --env SKYKOMA_AGENT_TYPE idea --name "skykoma-agent-idea" --env SKYKOMA_AGENT_SERVER_API http://127.0.0.1:2333/startJupyterKernel
//            C:\Users\hyl\AppData\Roaming\jupyter\kernels\kotlin_skykoma-agent-idea\kernel.json
//            String userJupyterPath = getUserJupyterPath(folderName);
//            File kernelJsonFile = new File(userJupyterPath + File.separator + "kernel.json");
//            String kernelJsonStr = FileUtils.readFileToString(kernelJsonFile, Charset.defaultCharset());
//            JsonObject kernelJson = JsonParser.parseString(kernelJsonStr).getAsJsonObject();
//            JsonArray argvArr = kernelJson.get("argv").getAsJsonArray();
        } catch (Exception e) {
            LOGGER.error(String.format("registerJupyterKernelIdea error, e = [%s]", e.getMessage()), e);
        }
    }

    private static String getUserJupyterPath(String folderName) {
        Path path = null;
        String systemCode = System.getProperty("os.name");
        if (systemCode.startsWith("Windows")) {
            path = Path.of(System.getenv("APPDATA"), "jupyter", folderName);
        } else if (systemCode.startsWith("Linux")) {
            path = Path.of(System.getProperty("user.home") + "/.local/share/jupyter", folderName);
        } else if (systemCode.startsWith("Mac")) {
            path = Path.of(System.getProperty("user.home") + "/Library/Jupyter", folderName);
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
    public void start() {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        String defaultAddress = "http://127.0.0.1";
        int defaultPort = 2333;
        String configAddress = propertiesComponent.getValue(SkykomaConstants.AGENT_SERVER_LISTEN_ADDRESS, "");
        if (StringUtils.isEmpty(configAddress)) {
            configAddress = defaultAddress;
        }
        int configPort = propertiesComponent.getInt(SkykomaConstants.AGENT_SERVER_LISTEN_PORT, defaultPort);
        LOGGER.info(String.format("reading config configAddress = %s", configAddress));
        LOGGER.info(String.format("reading config port = %s", configPort));
        if (configAddress.startsWith("http://")) {
            configAddress = configAddress.replace("http://", "");
        }
        start(configAddress, configPort);
    }
}
