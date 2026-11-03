package com.gongpu.web;

import com.gongpu.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第六批第 66 轮：全局异常映射补 415，并锁定既有映射不回归。 */
class ExceptionMappingTest {

    private final GlobalExceptionHandler h = new GlobalExceptionHandler();

    @Test
    void apiExceptionKeepsStatus() {
        ResponseEntity<Map<String, Object>> r = h.handleApi(ApiException.tooManyRequests("慢点"));
        assertEquals(429, r.getStatusCode().value());
        assertEquals("TOO_MANY_REQUESTS", r.getBody().get("code"));
    }

    @Test
    void mediaTypeIs415() {
        ResponseEntity<Map<String, Object>> r = h.handleMediaType(
                new HttpMediaTypeNotSupportedException(org.springframework.http.MediaType.TEXT_PLAIN,
                        java.util.List.of(org.springframework.http.MediaType.APPLICATION_JSON),
                        org.springframework.http.HttpMethod.POST));
        assertEquals(415, r.getStatusCode().value());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", r.getBody().get("code"));
    }
}
