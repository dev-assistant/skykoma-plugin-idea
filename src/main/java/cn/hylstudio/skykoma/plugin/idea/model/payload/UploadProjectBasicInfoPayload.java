package cn.hylstudio.skykoma.plugin.idea.model.payload;

import cn.hylstudio.skykoma.plugin.idea.model.ProjectInfoDto;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class UploadProjectBasicInfoPayload {
    private String scanId;
    private ProjectInfoDto projectInfoDto;

    public UploadProjectBasicInfoPayload(String scanId, ProjectInfoDto projectInfoDto) {
        // -------------- generated by skykoma begin --------------
        this.scanId = scanId;
        this.projectInfoDto = projectInfoDto;
    }
}