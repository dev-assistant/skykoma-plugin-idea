package cn.hylstudio.skykoma.plugin.idea.util;

import cn.hylstudio.skykoma.plugin.idea.SkykomaConstants;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;

public class SkykomaNotifier {

    public static void notifyInfo(String content) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(SkykomaConstants.NOTIFICATION_GROUP_ID_DEFAULT)
                .createNotification(content, NotificationType.INFORMATION)
                .notify(null);
    }
    public static void notifyError(String content) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(SkykomaConstants.NOTIFICATION_GROUP_ID_ERROR)
                .createNotification(content, NotificationType.ERROR)
                .notify(null);
    }

}