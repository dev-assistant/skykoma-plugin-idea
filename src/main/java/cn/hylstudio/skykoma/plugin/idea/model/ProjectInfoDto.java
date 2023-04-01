package cn.hylstudio.skykoma.plugin.idea.model;

import com.intellij.openapi.project.Project;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ProjectInfoDto {
    private Project project;
    private String key;
    private String name;
    private VCSEntityDto vcsEntityDto;
    private FileDto rootFolder;
    private List<ModuleDto> modules;
    private String scanId;
    private long lastScanTs;

}
