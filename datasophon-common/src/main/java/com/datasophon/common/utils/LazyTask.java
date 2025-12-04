package com.datasophon.common.utils;


public class LazyTask {


    private volatile Runnable task;

    private volatile boolean hasRun = false;

    private final Object lock = new Object();

    private LazyTask(Runnable task) {
        this.task = task;
    }

    public static LazyTask of(Runnable task) {
        return new LazyTask(task);
    }

    public boolean hasExec() {
        return task == null;
    }
    public void exec() {
        if (!hasRun) {
            synchronized (lock) {
                if (!hasRun) {
                    if (task != null) {
                        task.run();
                        task = null; // 帮助GC回收，避免内存泄漏
                    }
                    hasRun = true;
                }
            }
        }
    }
}