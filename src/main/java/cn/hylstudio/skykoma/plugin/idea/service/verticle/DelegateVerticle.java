package cn.hylstudio.skykoma.plugin.idea.service.verticle;

import cn.hylstudio.skykoma.plugin.idea.KotlinReplWrapper;
import cn.hylstudio.skykoma.plugin.idea.SkykomaConstants;
import cn.hylstudio.skykoma.plugin.idea.model.result.JsonResult;
import cn.hylstudio.skykoma.plugin.idea.util.GsonUtils;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.Arrays;

public class DelegateVerticle extends AbstractVerticle {
    private String listenAddress;
    private int port;
    private static final Logger LOGGER = Logger.getInstance(DelegateVerticle.class);

    public DelegateVerticle(String listenAddress, int port) {
        this.listenAddress = listenAddress;
        this.port = port;
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
//        router.route(HttpMethod.POST, "/getOpenedProjects").handler(this::getOpenedProjects);
//        router.route(HttpMethod.POST, "/getCurrentProject").handler(this::getCurrentProject);
//        router.route(HttpMethod.POST, "/closeProject").handler(this::closeProject);
//
//        router.route(HttpMethod.POST, "/editor/openFile").handler(this::openFile);
        // Create the HTTP server
        vertx.createHttpServer()
                // Handle every request using the router
                .requestHandler(router)
                // Start listening
                .listen(port, listenAddress)
                // Print the port
                .onSuccess(server ->
                        LOGGER.info(
                                "HTTP server started on " + listenAddress + ":" + server.actualPort()
                        )
                );
    }

    private void startJupyterKernel(RoutingContext routingContext) {
        String payload = routingContext.body().asString();
        LOGGER.info(String.format("startJupyterKernel, payload = [%s]", payload));
        IdeaPluginDescriptor ideaPluginDescriptor = Arrays.stream(PluginManagerCore.getPlugins())
                .filter(v -> v.getPluginId().getIdString().equals(SkykomaConstants.PLUGIN_ID))
                .findFirst().orElse(null);
        if (ideaPluginDescriptor == null) {
            JsonResult<Object> jsonResult = new JsonResult<>("S00005", "startJupyterKernel failed1", null);
            responseJson(routingContext, jsonResult);
            return;
        }
        ClassLoader pluginClassLoader = ideaPluginDescriptor.getPluginClassLoader();
        if (pluginClassLoader == null) {
            JsonResult<Object> jsonResult = new JsonResult<>("S00005", "startJupyterKernel failed2", null);
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
            LOGGER.error(String.format("startJupyterKernel has error, e = [%s]", e.getMessage()), e);
            JsonResult<Object> jsonResult = new JsonResult<>("S00005", "startJupyterKernel failed3", null);
            responseJson(routingContext, jsonResult);
            return;
        }
        JsonResult<Object> jsonResult = JsonResult.succResult("startJupyterKernel succ", null);
        responseJson(routingContext, jsonResult);
    }

    private static <T> void responseJson(RoutingContext context, T jsonResult) {
        context.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        context.response().end(GsonUtils.GSON.toJson(jsonResult));
    }


}
