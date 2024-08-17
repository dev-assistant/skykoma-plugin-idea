package cn.hylstudio.skykoma.plugin.idea.model.payload;

import cn.hylstudio.skykoma.plugin.idea.model.ProjectInfoDto;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BeginScanPayload {
    private String scanId;
    private ProjectInfoDto projectInfoDto;

    public BeginScanPayload(String scanId, ProjectInfoDto projectInfoDto) {
        this.scanId = scanId;
        this.projectInfoDto = projectInfoDto;
    }
}
