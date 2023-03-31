package cn.hylstudio.skykoma.plugin.idea.model.payload;

import cn.hylstudio.skykoma.plugin.idea.model.ProjectInfoDto;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class ProjectInfoQueryPayload {
    private String key;
    private String name;
    private Boolean createIfNotExists;

    public ProjectInfoQueryPayload() {
    }

    public ProjectInfoQueryPayload(ProjectInfoDto projectInfoDto) {
        // -------------- generated by skykoma begin --------------
        this.key = projectInfoDto.getKey();
        this.name = projectInfoDto.getName();
        // -------------- generated by skykoma end --------------

    }
}