package cn.hylstudio.skykoma.plugin.idea.model;

import com.intellij.psi.PsiFile;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class FileDto {
    public static final String STATUS_WAIT = "WAIT";
    public static final String STATUS_GSON_FINISHED = "GSON_FINISHED";

    private File file;
    private String name;
    private String type;//file folder
    private String relativePath;
    private String absolutePath;
    private String psiFileJson;
    private PsiFile psiFile;
    private List<FileDto> subFiles;
    private String status;

    public FileDto(File file, String rootPath) {
        this.file = file;
        this.name = file.getName();
        this.type = file.isDirectory() ? "folder" : "file";
        this.absolutePath = file.getPath();
        if (!StringUtils.isEmpty(rootPath)) {
            this.relativePath = file.getPath().substring(rootPath.length());
            if (this.relativePath.startsWith("/") || this.relativePath.startsWith("\\")) {
                this.relativePath = this.relativePath.substring(1);
            }
        } else {
            this.relativePath = "";
        }
        this.subFiles = Collections.emptyList();
    }

    public FileDto fillSubFiles(String rootPath) {
        File[] subFiles = this.file.listFiles();
        if (subFiles != null) {
            this.subFiles = Arrays.stream(subFiles).map(v -> new FileDto(v, rootPath).fillSubFiles(rootPath)).collect(Collectors.toList());
        } else {
            this.subFiles = Collections.emptyList();
        }
        return this;
    }
}
