package com.forge.infra.process;

import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 运行中任务的句柄登记表，支持取消（关闭进程/SSH 会话等） */
@Component
public class TaskRegistry {
    private final Map<String, Closeable> handles = new ConcurrentHashMap<>();

    public void register(String id, Closeable handle) { handles.put(id, handle); }

    public void unregister(String id) { handles.remove(id); }

    public boolean cancel(String id) {
        Closeable h = handles.get(id);
        if (h == null) return false;
        try { h.close(); } catch (IOException ignore) {}
        return true;
    }
}
