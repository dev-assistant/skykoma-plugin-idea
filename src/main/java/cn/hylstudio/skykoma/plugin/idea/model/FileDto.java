package cn.hylstudio.skykoma.plugin.idea.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class FileDto {
    private String name;
    private String type;//file folder
    private String relativePath;
    private List<FileDto> subFiles;

    public FileDto(File file, String rootPath) {
        this.name = file.getName();
        this.type = file.isDirectory() ? "folder" : "file";
        this.relativePath = file.getPath().substring(rootPath.length());
        if (this.relativePath.startsWith(File.separator)) {
            this.relativePath = this.relativePath.substring(1);
        }
        File[] subFiles = file.listFiles();
        if (subFiles != null) {
            this.subFiles = Arrays.stream(subFiles).map(v -> new FileDto(v, rootPath)).collect(Collectors.toList());
        } else {
            this.subFiles = Collections.emptyList();
        }
    }
}
