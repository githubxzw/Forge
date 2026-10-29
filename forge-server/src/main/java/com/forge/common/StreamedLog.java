package com.forge.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 日志文件下载响应：流式拷贝，不把整个文件读进内存（docker build 日志可达上百 MB） */
public final class StreamedLog {

    private StreamedLog() {}

    public static ResponseEntity<StreamingResponseBody> of(Path path, String filename) throws IOException {
        boolean exists = Files.exists(path);
        StreamingResponseBody body = out -> {
            if (!exists) {
                out.write("(无日志)\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            try (var in = Files.newInputStream(path)) {
                in.transferTo(out);
            }
        };
        var builder = ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + filename);
        if (exists) builder.contentLength(Files.size(path));
        return builder.body(body);
    }
}
