package cn.hylstudio.skykoma.plugin.idea.service;

public interface IdeaPluginAgentServer {
    void start();

    void stop();

    void restart();

    void registerAsJupyterKernel();

    String genRegisterKernelCmd();

    void startJupyterKernel(String payload);

    void stopJupyterKernel();

    String queryJupyterKernelStatus(String payload);
}
