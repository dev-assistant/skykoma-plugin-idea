package cn.hylstudio.skykoma.plugin.idea.listener;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;

import static cn.hylstudio.skykoma.plugin.idea.util.LogUtils.info;

public class SkykomaVirtualFileListener implements VirtualFileListener {
    private static final Logger LOGGER = Logger.getInstance(SkykomaVirtualFileListener.class);

    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        String propertyName = event.getPropertyName();
        Object oldValue = event.getOldValue();
        Object newValue = event.getNewValue();
        VirtualFile file = event.getFile();
        info(LOGGER, String.format("propertyChanged, path = [%s], propertyName = [%s], oldValue = [%s], newValue = [%s]",
                file.getPath(), propertyName, oldValue, newValue));
    }

    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        long oldModificationStamp = event.getOldModificationStamp();
        long newModificationStamp = event.getNewModificationStamp();
        info(LOGGER, String.format("contentsChanged, path = [%s], oldModificationStamp = [%s], newModificationStamp = [%s]",
                file.getPath(), oldModificationStamp, newModificationStamp));
    }

    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        long oldModificationStamp = event.getOldModificationStamp();
        long newModificationStamp = event.getNewModificationStamp();
        info(LOGGER, String.format("fileCreated, path = [%s], oldModificationStamp = [%s], newModificationStamp = [%s]",
                file.getPath(), oldModificationStamp, newModificationStamp));
    }

    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        long oldModificationStamp = event.getOldModificationStamp();
        long newModificationStamp = event.getNewModificationStamp();
        info(LOGGER, String.format("fileDeleted, path = [%s], oldModificationStamp = [%s], newModificationStamp = [%s]",
                file.getPath(), oldModificationStamp, newModificationStamp));
    }

    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        VirtualFile file = event.getFile();
        VirtualFile oldParent = event.getOldParent();
        VirtualFile newParent = event.getNewParent();
        info(LOGGER, String.format("fileMoved, path = [%s], oldParent = [%s], newParent = [%s]",
                file.getPath(), oldParent.getPath(), newParent.getPath()));
    }

    @Override
    public void fileCopied(@NotNull VirtualFileCopyEvent event) {
        VirtualFile originalFile = event.getOriginalFile();
        VirtualFile file = event.getFile();
        info(LOGGER, String.format("fileCopied, path = [%s], originalFile = [%s]",
                file.getPath(), originalFile.getPath()));
    }
}