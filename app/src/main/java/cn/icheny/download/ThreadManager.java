package cn.icheny.download;

import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * @author Administrator
 * @Created at 2018/11/25 0025  16:39
 * @des
 */
public class ThreadManager {

    private static final String TAG = "ThreadManager";
    /**
     * 核心线程数
     */
    private static final int CORE_POOL_SIZE = 12;
    private static final int CORE_POOL_SIZE_16 = 16;
    /**
     * 最大线程数
     */
    private static final int MAXIMUM_POOL_SIZE = 32;
    private static final long KEEP_ALIVE_SECONDS = 100L;

    private static volatile ThreadPoolExecutor sExecutorService;
    private static volatile ThreadPoolExecutor sWaitExecutor;
    private static volatile ScheduledThreadPoolExecutor sScheduledExecutorService;
    private static volatile ScheduledThreadPoolExecutor sSingleScheduledService;

    private static ThreadPoolExecutor getExecutor() {
        if (sExecutorService == null) {
            synchronized (ThreadManager.class) {
                if (sExecutorService == null) {
                    ThreadFactory threadFactory = Executors.defaultThreadFactory();
                    sExecutorService = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS,
                            TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>(128),
                            threadFactory);
                }
            }
        }
        int poolSize = sExecutorService.getPoolSize();
        int activeCount = sExecutorService.getActiveCount();
        Log.d(TAG, "getExecutor poolSize:" + poolSize + "  activeCount:" + activeCount);
        return sExecutorService;
    }

    private static ThreadPoolExecutor getWaitExecutor() {
        if (sWaitExecutor == null) {
            synchronized (ThreadManager.class) {
                if (sWaitExecutor == null) {
                    sWaitExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE_16, MAXIMUM_POOL_SIZE, 10L,
                            TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>(128),
                            new WaitThreadFactory());
                }
            }
        }
        int poolSize = sWaitExecutor.getPoolSize();
        int activeCount = sWaitExecutor.getActiveCount();
        Log.d(TAG, "getWaitExecutor poolSize:" + poolSize + "  activeCount:" + activeCount);
        return sWaitExecutor;
    }

    /**
     * 执行有可能会阻塞或等待的线程
     *
     * @param runnable runnable
     */
    public static void runWaitTask(Runnable runnable) {
        getWaitExecutor().execute(runnable);
    }

    public static void runOnBackground(Runnable runnable) {
        getExecutor().execute(runnable);
    }

    public static ScheduledThreadPoolExecutor getSingleScheduledService() {
        if (sSingleScheduledService == null) {
            synchronized (ThreadManager.class) {
                if (sSingleScheduledService == null) {
                    sSingleScheduledService = new ScheduledThreadPoolExecutor(1,
                            new ScheduledThreadFactory());
                }
            }
        }
        return sSingleScheduledService;
    }

    public static ScheduledThreadPoolExecutor getScheduledService() {
        if (sScheduledExecutorService == null) {
            synchronized (ThreadManager.class) {
                if (sScheduledExecutorService == null) {
                    sScheduledExecutorService = new ScheduledThreadPoolExecutor(CORE_POOL_SIZE_16,
                            new ScheduledThreadFactory());
                }
            }
        }
        return sScheduledExecutorService;
    }

    static class ScheduledThreadFactory implements ThreadFactory {

        private final AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Scheduled thread-" + mCount.getAndIncrement());
        }
    }


    static class WaitThreadFactory implements ThreadFactory {

        private final AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Wait thread-" + mCount.getAndIncrement());
        }
    }

}
