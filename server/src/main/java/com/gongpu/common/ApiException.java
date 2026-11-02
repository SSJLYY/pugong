package com.gongpu.common;

/**
 * 业务异常：携带 HTTP 语义的错误码，由 {@link GlobalExceptionHandler} 统一转 JSON。
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(String msg) {
        return new ApiException(400, "BAD_REQUEST", msg);
    }

    public static ApiException unauthorized(String msg) {
        return new ApiException(401, "UNAUTHORIZED", msg);
    }

    public static ApiException forbidden(String msg) {
        return new ApiException(403, "FORBIDDEN", msg);
    }

    public static ApiException notFound(String msg) {
        return new ApiException(404, "NOT_FOUND", msg);
    }

    public static ApiException conflict(String msg) {
        return new ApiException(409, "CONFLICT", msg);
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
