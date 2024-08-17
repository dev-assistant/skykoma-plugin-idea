package cn.hylstudio.skykoma.plugin.idea.model;

import com.google.common.collect.Sets;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
public class ScanRecordDto {

    public static final String STATUS_INIT = "INIT";
    public static final String STATUS_UPLOADING = "UPLOADING";
    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_SCANNING = "SCANNING";
    public static final String STATUS_SCANNED = "SCANNED";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final Set<String> ALLOW_STATUS = Sets.newHashSet(
            STATUS_INIT,
            STATUS_UPLOADING,
            STATUS_UPLOADED,
            STATUS_SCANNING,
            STATUS_SCANNED,
            STATUS_FINISHED
    );


    private String scanId;
    private String status;
}
