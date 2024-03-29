package cn.hylstudio.skykoma.plugin.idea.model.result;

import lombok.Data;

@Data
public class JsonResult<T> {
    private String code;
    private String msg;
    private T data;

    public JsonResult() {

    }

    public JsonResult(String code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }
    
    public static <T> JsonResult<T> succResult(String msg, T data) {
        return new JsonResult<>("S00000", msg, data);
    }
}
