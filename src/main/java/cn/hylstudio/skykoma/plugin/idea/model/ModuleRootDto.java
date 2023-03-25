package cn.hylstudio.skykoma.plugin.idea.model;

import lombok.Data;

import java.util.List;

@Data
public class ModuleRootDto {
    private String type;//src testSrc resources testResources
    private List<FileDto> folders;

    public ModuleRootDto() {
    }

    public ModuleRootDto(String type, List<FileDto> folders) {
        this.type = type;
        this.folders = folders;
    }
}
