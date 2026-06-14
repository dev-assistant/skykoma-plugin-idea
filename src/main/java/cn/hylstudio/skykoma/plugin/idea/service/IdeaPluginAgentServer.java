package cn.hylstudio.skykoma.plugin.idea.service;

import java.util.function.Consumer;

public interface IdeaPluginAgentServer {
    void start();

    void stop();

    void restart();

    void registerAsJupyterKernel();

    void registerAsJupyterKernel(Consumer<String> outputConsumer);

    String getKernelJsonPath();

    String genRegisterKernelCmd();

    void startJupyterKernel(String payload);

    void stopJupyterKernel();

    String queryJupyterKernelStatus(String payload);

    boolean isAgentServerRunning();
}
