package cn.hylstudio.skykoma.plugin.idea.model.payload;

import lombok.Data;

@Data
public class UpdateScanStatusPayload {
    private String scanId;
    private String relativePath;
    private String status;

    public UpdateScanStatusPayload(String scanId, String relativePath, String status) {
        this.scanId = scanId;
        this.status = status;
    }
}
