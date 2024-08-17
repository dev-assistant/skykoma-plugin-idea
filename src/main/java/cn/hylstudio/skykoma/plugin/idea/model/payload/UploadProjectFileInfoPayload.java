package cn.hylstudio.skykoma.plugin.idea.model.payload;

import cn.hylstudio.skykoma.plugin.idea.model.FileDto;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UploadProjectFileInfoPayload {
    private String scanId;
    private FileDto fileDto;

    public UploadProjectFileInfoPayload(String scanId, FileDto fileDto) {
        // -------------- generated by skykoma begin --------------
        this.scanId = scanId;
        this.fileDto = fileDto;
    }
}