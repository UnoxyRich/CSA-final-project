package com.csa.minecraft.engine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background thread pool for pure-CPU world work — terrain generation and mesh
 * vertex building. The game loop stays single-threaded for everything that
 * touches OpenGL or mutates shared state; only self-contained compute is handed
 * off here so all CPU cores get used and that work no longer stalls the frame.
 *
 * <p>Sized to leave the render/main thread a core of its own. Threads are
 * daemons (they never block JVM shutdown) and run a notch below normal priority
 * so the OS favours the thread that is actually drawing.
 */
public final class ChunkWorkerPool {
    private final ExecutorService exec;
    private final int threads;

    public ChunkWorkerPool() {
        // Leave one core for the GL/main thread; never drop below 2 workers.
        this.threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        AtomicInteger seq = new AtomicInteger();
        this.exec = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "chunk-worker-" + seq.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    /** Hands a CPU task to the pool. Must not touch OpenGL or unsynchronized shared state. */
    public void submit(Runnable task) {
        exec.execute(task);
    }

    public int threadCount() { return threads; }

    public void shutdown() {
        exec.shutdownNow();
    }
}
