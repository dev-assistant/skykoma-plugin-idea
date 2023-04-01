package cn.hylstudio.skykoma.plugin.idea.util;

import com.intellij.openapi.diagnostic.Logger;

public class LogUtils {
    private static final Logger LOGGER = Logger.getInstance(LogUtils.class);

    private LogUtils() {

    }

    public static void info(Logger logger, String msg) {
        System.out.println(msg);
        logger.info(msg);
    }
    public static void info(String msg) {
        System.out.println(msg);
        LOGGER.info(msg);
    }
}
