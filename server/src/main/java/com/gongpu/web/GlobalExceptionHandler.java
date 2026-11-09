package com.gongpu.web;

import com.gongpu.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** 统一错误响应：{ ok:false, code, message }。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(body(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("参数不合法");
        return ResponseEntity.badRequest().body(body("BAD_REQUEST", msg));
    }

    /** 坏 JSON 请求体:此前落进兜底 500 并打 error 日志,实际是客户端 400。 */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(Exception e) {
        return ResponseEntity.badRequest().body(body("BAD_REQUEST", "请求体不是合法 JSON"));
    }

    /** 缺必填 query/参数:同理 400 而非 500。 */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            org.springframework.web.bind.MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(body("BAD_REQUEST", "缺少参数: " + e.getParameterName()));
    }

    /** 路径/参数类型不符（如槽位传了非数字）:客户端 400,而非落进兜底 500。 */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(body("BAD_REQUEST", "参数类型不合法: " + e.getName()));
    }

    /** 不存在的路径:404,而非 500「服务器开小差」(拼错 API 地址时误导排障)。 */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(
            org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return ResponseEntity.status(404).body(body("NOT_FOUND", "接口不存在"));
    }

    /** 方法不允许（如对只读接口发 POST）:405,而非 500。 */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).body(body("METHOD_NOT_ALLOWED", "请求方法不允许: " + e.getMethod()));
    }

    /** 媒体类型不支持（如表单直发 JSON 接口）：415，而非落进兜底 500。 */
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaType(
            org.springframework.web.HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(415).body(body("UNSUPPORTED_MEDIA_TYPE", "请求需使用 application/json"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e, HttpServletRequest req) {
        // 第二十批·轮10：前端 1 秒轮询，页面关闭/切网时的客户端断连
        // （ClientAbortException / broken pipe）不是服务端故障——降级 debug，
        // 否则挂机长期运行下 500 日志被断连堆栈淹没，真异常信噪比劣化。
        if (isClientAbort(e)) {
            log.debug("客户端断开: {} {}", req.getMethod(), req.getRequestURI());
            return ResponseEntity.internalServerError().body(body("INTERNAL", "服务器开小差了，请稍后再试"));
        }
        // 500 必须带请求上下文:只有堆栈没有 URI,排障时要靠猜
        log.error("未处理异常: {} {}", req.getMethod(), req.getRequestURI(), e);
        return ResponseEntity.internalServerError().body(body("INTERNAL", "服务器开小差了，请稍后再试"));
    }

    /** 沿异常链识别客户端断开（Tomcat ClientAbortException 或 broken pipe 消息）。 */
    private static boolean isClientAbort(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String cn = t.getClass().getName();
            if (cn.endsWith("ClientAbortException")) {
                return true;
            }
            if (t instanceof java.io.IOException) {
                String msg = t.getMessage();
                if (msg != null && (msg.toLowerCase().contains("broken pipe")
                        || msg.contains("远程主机") || msg.contains("你的主机中的软件中止"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<String, Object> body(String code, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("code", code);
        m.put("message", msg);
        return m;
    }
}
