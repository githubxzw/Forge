package com.forge.build;

import com.forge.common.ApiException;
import com.forge.infra.log.LogSink;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class TarUtils {

    private TarUtils() {}

    /** 将 sourceDir 的内容（不含父目录）打包为 targetFile(.tar.gz) */
    public static Path tarGz(Path sourceDir, Path targetFile, LogSink log) {
        try {
            Files.createDirectories(targetFile.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new ApiException(500, "创建制品目录失败: " + e.getMessage());
        }
        try (OutputStream fos = Files.newOutputStream(targetFile);
             OutputStream bos = new BufferedOutputStream(fos, 1 << 16);
             OutputStream gos = new GzipCompressorOutputStream(bos);
             TarArchiveOutputStream tos = new TarArchiveOutputStream(gos, "UTF-8")) {
            tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            try (Stream<Path> walk = Files.walk(sourceDir)) {
                List<Path> files = walk.sorted().toList();
                for (Path file : files) {
                    String relative = sourceDir.relativize(file).toString().replace('\\', '/');
                    if (relative.isEmpty()) continue;
                    TarArchiveEntry entry = tos.createArchiveEntry(file.toFile(), relative);
                    tos.putArchiveEntry(entry);
                    if (Files.isRegularFile(file)) {
                        try (InputStream in = Files.newInputStream(file)) {
                            in.transferTo(tos);
                        }
                    }
                    tos.closeArchiveEntry();
                }
            }
        } catch (IOException e) {
            throw new ApiException(500, "打包失败: " + e.getMessage());
        }
        return targetFile;
    }

    public static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new ApiException(500, "计算校验和失败: " + e.getMessage());
        }
    }

    /** 递归删除目录 */
    public static void deleteDir(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignore) {}
            });
        } catch (IOException ignore) {}
    }
}
