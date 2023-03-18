package cn.hylstudio.skykoma.plugin.idea.model.result;

import lombok.Getter;

@Getter
public enum BizCode {
    SUCC("C000000", "succ"),
    WRONG_PARAMS("C000001", "wrong param"),
    NOT_FOUND("C000004", "wrong param"),
    SYSTEM_ERROR("C00005", "system error");
    private String code;
    private String msg;

    private BizCode(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

}
