package com.forge.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 错误处理：/api/** 返回 JSON；页面路由（HTML）转发到 index.html 交给前端路由。
 */
@Controller
public class SpaErrorController implements ErrorController {

    @RequestMapping("/error")
    public Object handle(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String accept = request.getHeader("Accept");
        String forwardUri = (String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        boolean isApi = forwardUri != null && forwardUri.startsWith("/api");
        if (!isApi && accept != null && accept.contains("text/html")) {
            response.setStatus(200); // SPA 路由刷新：状态码重置，避免历史 404 残留
            return "forward:/index.html";
        }
        int status = response.getStatus() == 200 ? 404 : response.getStatus();
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter w = response.getWriter()) {
            w.write("{\"code\":" + status + ",\"message\":\"资源不存在或请求错误\",\"data\":null}");
        }
        return null;
    }
}
