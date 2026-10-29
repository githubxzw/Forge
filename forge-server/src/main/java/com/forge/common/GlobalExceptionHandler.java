package com.forge.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResult<Void>> handleApi(ApiException e) {
        int status = e.getCode();
        if (status < 400 || status > 599) status = 400; // 业务码透传为 HTTP 状态码（如 429）
        return ResponseEntity.status(status).body(ApiResult.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数校验失败" : fe.getField() + ": " + fe.getDefaultMessage();
        return ResponseEntity.badRequest().body(ApiResult.error(400, msg));
    }

    /**
     * 静态资源未命中（即 SPA 路由，如 /dashboard）：不允许被兜底成 JSON，
     * 重新抛出交给 /error 分发 —— HTML 请求转发 index.html，其余返回 JSON 404。
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNoResource(
            org.springframework.web.servlet.resource.NoResourceFoundException e)
            throws org.springframework.web.servlet.resource.NoResourceFoundException {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleOther(Exception e) {
        log.error("unhandled error", e);
        // 兜底异常不透传内部 message（可能含服务器路径/拓扑信息），详情只进服务端日志
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.error(500, "服务器内部错误，请查看服务端日志"));
    }
}
