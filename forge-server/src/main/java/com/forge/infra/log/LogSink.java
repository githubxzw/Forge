package com.forge.infra.log;

/** 任务日志输出接口：同时落盘 + 推送 WS 订阅者 */
public interface LogSink {
    void line(String text);

    LogSink NOOP = text -> {};

    default void info(String text) { line(text); }
    default void close() {}
}
