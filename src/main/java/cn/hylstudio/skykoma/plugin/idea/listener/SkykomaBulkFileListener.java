package cn.hylstudio.skykoma.plugin.idea.listener;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SkykomaBulkFileListener implements BulkFileListener {
    private static final Logger LOGGER = Logger.getInstance(SkykomaBulkFileListener.class);

    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        SkykomaVirtualFileListener skykomaVirtualFileListener = new SkykomaVirtualFileListener();
        BulkVirtualFileListenerAdapter bulkVirtualFileListenerAdapter = new BulkVirtualFileListenerAdapter(skykomaVirtualFileListener);
        bulkVirtualFileListenerAdapter.after(events);
    }
}