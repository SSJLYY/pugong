package com.gongpu.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第 8 轮：客户端错误不再落进兜底 500——类型不符 400 / 路径不存在 404 / 方法不允许 405。 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void typeMismatchIs400() {
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "slot", null, new IllegalArgumentException("not a number"));
        ResponseEntity<Map<String, Object>> r = handler.handleTypeMismatch(e);
        assertEquals(400, r.getStatusCode().value());
        assertEquals("BAD_REQUEST", r.getBody().get("code"));
        assertEquals(Boolean.FALSE, r.getBody().get("ok"));
    }

    @Test
    void unknownPathIs404() {
        NoResourceFoundException e = new NoResourceFoundException(HttpMethod.GET, "/api/gmae/1/state");
        ResponseEntity<Map<String, Object>> r = handler.handleNoResource(e);
        assertEquals(404, r.getStatusCode().value());
        assertEquals("NOT_FOUND", r.getBody().get("code"));
    }

    @Test
    void wrongMethodIs405() {
        HttpRequestMethodNotSupportedException e =
                new HttpRequestMethodNotSupportedException("DELETE", java.util.List.of("GET", "POST"));
        ResponseEntity<Map<String, Object>> r = handler.handleMethodNotSupported(e);
        assertEquals(405, r.getStatusCode().value());
        assertEquals("METHOD_NOT_ALLOWED", r.getBody().get("code"));
    }
}
