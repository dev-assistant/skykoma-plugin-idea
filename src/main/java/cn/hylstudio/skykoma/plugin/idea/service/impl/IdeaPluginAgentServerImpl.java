package cn.hylstudio.skykoma.plugin.idea.service.impl;

import cn.hylstudio.skykoma.plugin.idea.SkykomaConstants;
import cn.hylstudio.skykoma.plugin.idea.service.IdeaPluginAgentServer;
import cn.hylstudio.skykoma.plugin.idea.service.verticle.DelegateVerticle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import io.vertx.core.Vertx;
import org.apache.commons.lang.StringUtils;

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
            started = true;
        }
        LOGGER.info("IDeaPluginAgentServerImpl already start");
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
