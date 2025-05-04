package cn.hylstudio.skykoma.plugin.idea.util;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenHomeType;
import org.jetbrains.idea.maven.project.MavenInSpecificPath;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Arrays;
import java.util.Optional;

public class ProjectUtils {
    private ProjectUtils() {

    }

    public static void updateProjectMaven(String homePath, Project project) {
        String currentMavenHomePath = getCurrentMavenHomePath(project);
        if (currentMavenHomePath.equals(homePath)) {
            return;
        }
        MavenHomeType mavenHomeType = new MavenInSpecificPath(homePath);
        MavenProjectsManager.getInstance(project).getGeneralSettings().setMavenHomeType(mavenHomeType);
        System.out.println(String.format("skykoma projectMaven update succ, homePath = %s", homePath));
    }

    public static void updateProjectJdk(String homePath, Project project) {
        Sdk jdkFromHomePath = createSdkFromHomePath(homePath);
        if (jdkFromHomePath == null) {
            System.out.println(String.format("skykoma can't detected jdk from %s", homePath));
            return;
        }
        String name = jdkFromHomePath.getName();
        System.out.println(String.format("skykoma detected jdk %s from %s", name, homePath));
        ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
        jdkFromHomePath = tryUpdateJdkTable(jdkTable, jdkFromHomePath);
        if (jdkFromHomePath == null) {
            System.out.println("skykoma tryUpdateJdkTable failed");
            return;
        }
        boolean projectSdkUpdated = tryUpdateProjectSdk(project, jdkFromHomePath);
        if (projectSdkUpdated) {
            System.out.println(String.format("skykoma projectSdk update succ, jdk = %s", jdkFromHomePath));
        }
        tryUpdateProjectLangLevel(project, jdkFromHomePath);
    }

    private static boolean tryUpdateProjectLangLevel(Project project, Sdk jdkFromHomePath) {
        JavaSdkVersion javaSdkVersion = JavaSdk.getInstance().getVersion(jdkFromHomePath);
        if (javaSdkVersion == null) {
            return false;
        }
        LanguageLevel maxLanguageLevel = javaSdkVersion.getMaxLanguageLevel();
        LanguageLevelProjectExtensionImpl instanceImpl = LanguageLevelProjectExtensionImpl.getInstanceImpl(project);
        LanguageLevel currentLevel = instanceImpl.getCurrentLevel();
        if (currentLevel != null && currentLevel.equals(maxLanguageLevel)) {
            return false;
        }
        instanceImpl.setCurrentLevel(maxLanguageLevel);
        instanceImpl.setDefault(true);
        return true;
    }

    /**
     * @param project
     * @param jdkFromHomePath
     * @return project sdk updated
     */
    private static boolean tryUpdateProjectSdk(Project project, Sdk jdkFromHomePath) {
        ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
        Sdk projectSdk = projectRootManager.getProjectSdk();
        if (projectSdk != null && projectSdk.equals(jdkFromHomePath)) {
            return false;
        }
        projectRootManager.setProjectSdk(jdkFromHomePath);
        return true;
    }

    /**
     * @param jdkTable
     * @param newJdk
     * @return changed jdk
     */

    private static Sdk tryUpdateJdkTable(ProjectJdkTable jdkTable, Sdk newJdk) {
        try {
            Sdk[] allJdks = jdkTable.getAllJdks();
            String newJdkHomePath = newJdk.getHomePath();
            Optional<Sdk> firstMatch = Arrays.stream(allJdks).filter(v -> newJdkHomePath != null && newJdkHomePath.equals(v.getHomePath())).findFirst();
            boolean alreadyExists = firstMatch.isPresent();
            if (alreadyExists) {
                System.out.println(String.format("try add jdk %s already exists", newJdkHomePath));
                return firstMatch.get();
            }
            jdkTable.addJdk(newJdk);
            return newJdk;
        } catch (IllegalStateException e) {
            String message = e.getMessage();
            if (message != null &&
                    message.contains("already registered")) {
                System.out.println(message);
            } else {
                System.out.println("try add jdk unknown error");
                e.printStackTrace();
            }
        }
        return null;
    }

    private static Sdk createSdkFromHomePath(String homePath) {
        JavaSdk javaSdk = JavaSdk.getInstance();
        if (homePath == null || !javaSdk.isValidSdkHome(homePath)) return null;
        String suggestedName = JdkUtil.suggestJdkName(javaSdk.getVersionString(homePath));
        if (suggestedName == null) return null;
        return javaSdk.createJdk(String.format("skykoma-auto-%s", suggestedName), homePath, false);
    }

    public static void mavenReImport(Project project) {
        MavenProjectsManager projectsManager = MavenProjectsManager.getInstanceIfCreated(project);
        if (projectsManager == null) {
            return;
        }
        FileDocumentManager.getInstance().saveAllDocuments();
        projectsManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles();
    }

    public static String getCurrentMavenHomePath(Project project) {
        MavenHomeType mavenHomeType = MavenProjectsManager.getInstance(project).getGeneralSettings().getMavenHomeType();
        if (mavenHomeType instanceof MavenInSpecificPath) {
            return ((MavenInSpecificPath) mavenHomeType).getMavenHome();
        }
        String title = mavenHomeType.getTitle();
        return title;
    }
    public static String getCurrentSdkHomePath(Project project) {
        ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
        Sdk projectSdk = projectRootManager.getProjectSdk();
        if (projectSdk != null && projectSdk.getHomePath() != null) {
            return projectSdk.getHomePath();
        }
        return "";
    }

}
