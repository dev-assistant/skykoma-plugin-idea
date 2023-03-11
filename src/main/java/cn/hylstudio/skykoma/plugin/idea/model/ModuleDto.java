package cn.hylstudio.skykoma.plugin.idea.model;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.List;

@Data
@NoArgsConstructor
public class ModuleDto {
    private Module module;
    private String name;
    private List<FileDto> srcRoots;
    private List<FileDto> testSrcRoots;
    private List<FileDto> resRoots;
    private List<FileDto> testResRoots;

    public ModuleDto(Module module) {
        this.module = module;
    }
}
