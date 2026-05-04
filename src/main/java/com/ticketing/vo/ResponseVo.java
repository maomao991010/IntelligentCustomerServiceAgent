package com.ticketing.vo;

import lombok.Data;

@Data
public class ResponseVo {
    private int code;
    private String message;
    private Object data;

    public ResponseVo(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static ResponseVo success(Object data) {
        return new ResponseVo(200, "success", data);
    }

    public static ResponseVo error(int code, String message) {
        return new ResponseVo(code, message, null);
    }

    public static ResponseVo error(String message) {
        return new ResponseVo(500, message, null);
    }
}
