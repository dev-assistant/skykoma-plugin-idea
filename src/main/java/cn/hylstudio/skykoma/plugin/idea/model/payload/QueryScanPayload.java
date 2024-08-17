package cn.hylstudio.skykoma.plugin.idea.model.payload;

import cn.hylstudio.skykoma.plugin.idea.model.ProjectInfoDto;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class QueryScanPayload {
    private String scanId;
    private String relativePath;

    public QueryScanPayload(String scanId) {
        this.scanId = scanId;
    }

    public QueryScanPayload(String scanId, String relativePath) {
        this.scanId = scanId;
        this.relativePath = relativePath;
    }
}
