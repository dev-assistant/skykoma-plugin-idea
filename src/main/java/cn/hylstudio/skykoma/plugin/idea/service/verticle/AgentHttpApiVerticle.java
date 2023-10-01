package cn.hylstudio.skykoma.plugin.idea.service.verticle;

import cn.hylstudio.skykoma.plugin.idea.KotlinReplWrapper;
import cn.hylstudio.skykoma.plugin.idea.SkykomaConstants;
import cn.hylstudio.skykoma.plugin.idea.model.result.JsonResult;
import cn.hylstudio.skykoma.plugin.idea.util.GsonUtils;
import cn.hylstudio.skykoma.plugin.idea.util.SkykomaNotifier;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.diagnostic.Logger;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.Arrays;

import static cn.hylstudio.skykoma.plugin.idea.util.LogUtils.error;
import static cn.hylstudio.skykoma.plugin.idea.util.LogUtils.info;

public class AgentHttpApiVerticle extends AbstractVerticle {
    private HttpServer httpServer;
    private String listenAddress;
    private int port;
    private static final Logger LOGGER = Logger.getInstance(AgentHttpApiVerticle.class);
    private boolean started = false;

    public AgentHttpApiVerticle(String listenAddress, int port) {
        if (listenAddress.startsWith("http://")) {
            listenAddress = listenAddress.replace("http://", "");
        }
        this.listenAddress = listenAddress;
        this.port = port;
    }

    public boolean isStarted() {
        return started;
    }

    @Override
    public void start() throws Exception {
        // Create a Router
        Router router = Router.router(vertx);

        // Mount the handler for all incoming requests at every path and HTTP method
        router.route().handler(CorsHandler.create());
        router.route().handler(BodyHandler.create());
//        router.route().handler(this::checkAuth);
        router.route(HttpMethod.POST, "/startJupyterKernel").handler(this::startJupyterKernel);
        // Create the HTTP server
        vertx.createHttpServer()
                // Handle every request using the router
                .requestHandler(router)
                // Start listening
                .listen(port, listenAddress)
                // Print the port
                .onSuccess(server -> {
                    this.httpServer = server;
                    String startSucc = "agentHttpApiServer started on " + listenAddress + ":" + server.actualPort();
                    info(LOGGER, startSucc);
                    SkykomaNotifier.notifyInfo(startSucc);
                    started = true;
                }).onFailure((e) -> {
                    String startFailed = String.format("agentHttpApiServer started failed, e = [%s]", e.getMessage());
                    error(LOGGER, startFailed, e);
                    SkykomaNotifier.notifyError(startFailed);
                });
    }

    private void startJupyterKernel(RoutingContext routingContext) {
        String payload = routingContext.body().asString();
        info(LOGGER, String.format("startJupyterKernel, payload = [%s]", payload));
        IdeaPluginDescriptor ideaPluginDescriptor = Arrays.stream(PluginManagerCore.getPlugins())
                .filter(v -> v.getPluginId().getIdString().equals(SkykomaConstants.PLUGIN_ID))
                .findFirst().orElse(null);
        if (ideaPluginDescriptor == null) {
            String startJupyterKernelErrorMsg = "startJupyterKernel failed, can't find ideaPluginDescriptor";
            JsonResult<Object> jsonResult = new JsonResult<>("S00005", startJupyterKernelErrorMsg, null);
            SkykomaNotifier.notifyError(startJupyterKernelErrorMsg);
            responseJson(routingContext, jsonResult);
            return;
        }
        ClassLoader pluginClassLoader = ideaPluginDescriptor.getPluginClassLoader();
        if (pluginClassLoader == null) {
            String startJupyterKernelErrorMsg = "startJupyterKernel failed, can't find pluginClassLoader";
            JsonResult<Object> jsonResult = new JsonResult<>("S00005", startJupyterKernelErrorMsg, null);
            SkykomaNotifier.notifyError(startJupyterKernelErrorMsg);
            responseJson(routingContext, jsonResult);
            return;
        }
        try {
            Thread thread = new Thread(() -> {
                KotlinReplWrapper wrapper = KotlinReplWrapper.getInstance(pluginClassLoader);
                wrapper.makeEmbeddedRepl(payload);
            });
            thread.setContextClassLoader(pluginClassLoader);
            thread.start();
        } catch (Exception e) {
            String startJupyterKernelErrorMsg = String.format("startJupyterKernel has error, e = [%s]", e.getMessage());
            error(LOGGER, startJupyterKernelErrorMsg, e);
            SkykomaNotifier.notifyError(startJupyterKernelErrorMsg);
            JsonResult<Object> jsonResult = new JsonResult<>("S00005", "startJupyterKernel failed3", null);
            responseJson(routingContext, jsonResult);
            return;
        }
        String startJupyterKernelSucc = "startJupyterKernel succ";
        JsonResult<Object> jsonResult = JsonResult.succResult(startJupyterKernelSucc, null);
        SkykomaNotifier.notifyInfo(startJupyterKernelSucc);
        responseJson(routingContext, jsonResult);
    }

    private static <T> void responseJson(RoutingContext context, T jsonResult) {
        context.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        context.response().end(GsonUtils.GSON.toJson(jsonResult));
    }

    @Override
    public void stop() throws Exception {
        this.started = false;
        if (httpServer != null) {
            httpServer.close().onSuccess((v) -> {
                String agentHttpApiServerStopped = "agentHttpApiServer stopped";
                info(LOGGER, agentHttpApiServerStopped);
                SkykomaNotifier.notifyInfo(agentHttpApiServerStopped);
            });
        }
        super.stop();
    }

    public void showInfo() {
        HttpServer server = this.httpServer;
        if (server == null) {
            return;
        }
        String startSucc = "agentHttpApiServer started on " + listenAddress + ":" + server.actualPort();
        info(LOGGER, startSucc);
        SkykomaNotifier.notifyInfo(startSucc);
    }
}
