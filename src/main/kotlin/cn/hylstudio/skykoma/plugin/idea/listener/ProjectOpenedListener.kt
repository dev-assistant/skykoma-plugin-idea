package cn.hylstudio.skykoma.plugin.idea.listener

import cn.hylstudio.skykoma.plugin.idea.service.IProjectInfoService
import cn.hylstudio.skykoma.plugin.idea.service.IdeaPluginAgentServer
import cn.hylstudio.skykoma.plugin.idea.util.LogUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class ProjectOpenedListener : ProjectActivity {
    private val LOGGER = Logger.getInstance(ProjectOpenCloseListener::class.java)

    override suspend fun execute(project: Project) {
        // Ensure this isn't part of testing
        val application = ApplicationManager.getApplication()
        if (application == null) {
            LOGGER.info("listener projectOpened, application is null")
            return
        }
        if (application.isUnitTestMode) {
            return
        }
        LOGGER.info(String.format("listener projectOpened, name = %s", project.name))
        val ideaPluginAgentServer = ApplicationManager.getApplication().getService(IdeaPluginAgentServer::class.java)
        val projectService = project.getService(IProjectInfoService::class.java)
        val dumbService = DumbService.getInstance(project)
        dumbService.runWhenSmart {
            ideaPluginAgentServer.start()
            val name = project.name
            LogUtils.info(LOGGER, String.format("background task trigger succ, project = [%s]", name))
            projectService.setCurrentProject(project)
        }
    }

}