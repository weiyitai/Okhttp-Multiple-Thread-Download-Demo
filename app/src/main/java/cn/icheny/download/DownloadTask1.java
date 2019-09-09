package cn.icheny.download;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by Cheny on 2017/4/29.
 */

public class DownloadTask1 extends Handler {

    public static int THREAD_COUNT = 2;
    private FilePoint mPoint;
    private volatile long mFileLength;
    /**
     * 之前下载暂停的百分比
     */
    private volatile int mPrePercent;

    private volatile boolean isDownloading = false;
    /**
     * 子线程取消数量
     */
    private AtomicInteger childCanleCount = new AtomicInteger(0);
    /**
     * 子线程暂停数量
     */
    private AtomicInteger childPauseCount = new AtomicInteger(0);
    /**
     * 子线程完成数量
     */
    private AtomicInteger childFinishCount = new AtomicInteger(0);
    private HttpUtil mHttpUtil;
    private long[] mProgress;
    private File[] mCacheFiles;
    /**
     * 是否暂停
     */
    private volatile boolean pause;
    /**
     * 是否取消下载
     */
    private volatile boolean cancel;
    /**
     * 正在下载
     */
    private static final int MSG_PROGRESS = 1;
    /**
     * 完成下载
     */
    private static final int MSG_FINISH = 2;
    /**
     * 暂停
     */
    private static final int MSG_PAUSE = 3;
    /**
     * 取消
     */
    private static final int MSG_CANCEL = 4;
    /**
     * 失败
     */
    private static final int MSG_FAIL = 5;
    /**
     * 下载回调监听
     */
    private DownloadListener mListner;
    /**
     * 正在下载的线程数,用于判断是否正在下载
     */
    private volatile int mDownloadingThread = 0;

    /**
     * 任务管理器初始化数据
     *
     * @param point
     * @param l
     */
    DownloadTask1(FilePoint point, DownloadListener l) {
        this.mPoint = point;
        this.mListner = l;
        this.mProgress = new long[THREAD_COUNT];
        this.mCacheFiles = new File[THREAD_COUNT];
        this.mHttpUtil = HttpUtil.getInstance();
    }

    /**
     * 任务回调消息
     *
     * @param msg
     */
    @Override
    public void handleMessage(Message msg) {
        if (null == mListner) {
            return;
        }
        switch (msg.what) {
            case MSG_PROGRESS:
                //进度
                long progress = 0;
                for (long mProgress : mProgress) {
                    progress += mProgress;
                }
                int nowPercent = (int) (progress * 100 / mFileLength);
                if (nowPercent != mPrePercent) {
                    mListner.onProgress(progress, mFileLength, nowPercent);
                    mPrePercent = nowPercent;
                    Log.d(TAG, "progress:" + progress + " nowPercent:" + nowPercent);
                }
                break;
            case MSG_PAUSE:
                //暂停
                if (confirmStatus(childPauseCount)) {
                    return;
                }
                resetStatus();
                mListner.onPause();
                break;
            case MSG_FINISH:
                //完成
                Log.d(TAG, "confirmStatus MSG_FINISH:" + childFinishCount.get());
                if (confirmStatus(childFinishCount)) {
                    return;
                }
                Log.d(TAG, "MSG_FINISH 下载完毕");
                //下载完毕后，重命名目标文件名
                resetStatus();
                cleanFile(mCacheFiles);
                mListner.onFinished();
                break;
            case MSG_CANCEL:
                //取消
                if (confirmStatus(childCanleCount)) {
                    return;
                }
                resetStatus();
                mProgress = new long[THREAD_COUNT];
                mListner.onCancel();
                break;
            default:
                break;
        }
    }

    private static final String TAG = "DownloadTask";

    public synchronized void start() {
        pause = false;
        cancel = false;
        try {
            Log.d(TAG, "start isDownloading:" + isDownloading + " Url:" + mPoint.getUrl());
            if (isDownloading) {
                return;
            }
            isDownloading = true;
            ThreadManager.runOnBackground(() -> {
                try {
                    File file = new File(mPoint.getFilePath());
                    if (file.exists()) {
                        mFileLength = file.length();
                    } else {
                        File parentFile = file.getParentFile();
                        if (!parentFile.exists()) {
                            boolean mkdirs = parentFile.mkdirs();
                        }
                        mFileLength = mHttpUtil.getContentLength(mPoint.getUrl());
                        // 在本地创建一个与资源同样大小的文件来占位
                        File fileDes = new File(mPoint.getFilePath());
                        if (!fileDes.getParentFile().exists()) {
                            boolean mkdirs = fileDes.getParentFile().mkdirs();
                        }
                        RandomAccessFile tmpAccessFile = new RandomAccessFile(fileDes, "rw");
                        tmpAccessFile.setLength(mFileLength);
                    }
                    splitThread();
                } catch (IOException e) {
                    e.printStackTrace();
                    if (mListner != null) {
                        mListner.onFail();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            resetStatus();
        }
    }

    /**
     * 分线程下载
     */
    private void splitThread() {
        childFinishCount.set(0);
        /*将下载任务分配给每个线程*/
        long blockSize = mFileLength / THREAD_COUNT;
        // 计算每个线程理论上下载的数量.

        /*为每个线程配置并分配任务*/
        for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
            long startIndex = threadId * blockSize;
            // 线程开始下载的位置
            long endIndex = (threadId + 1) * blockSize - 1;
            // 线程结束下载的位置
            if (threadId == (THREAD_COUNT - 1)) {
                // 如果是最后一个线程,将剩下的文件全部交给这个线程完成
                endIndex = mFileLength - 1;
            }
            // 开启线程下载
            long finalEndIndex = endIndex;
            int finalThreadId = threadId;
            ThreadManager.runOnBackground(() ->
                    download(startIndex, finalEndIndex, finalThreadId));
        }
    }

    private void download(long startIndex, final long endIndex, final int threadId) {
        Log.d(TAG, "download:" + "startIndex:" + startIndex + " endIndex:" + endIndex + " threadId:" + threadId);
        // 记录本次下载文件的大小
        long progress = 0;
        RandomAccessFile cacheAccessFile = null;
        // 分段请求网络连接,分段将文件保存到本地.
        // 加载下载位置缓存文件
        long pro = 0, range = 0;
        final File cacheFile = new File(mPoint.getFilePath() + "thread" + threadId + ".cache");
        try {
            long newStartIndex = startIndex;
            mCacheFiles[threadId] = cacheFile;
            cacheAccessFile = new RandomAccessFile(cacheFile, "rwd");
            // 如果文件存在
            if (cacheFile.exists()) {
                long l = Util.parseLong(cacheAccessFile.readLine());
                if (l != 0) {
                    newStartIndex = l;
                }
            }
            final long finalStartIndex = newStartIndex;
            Log.d(TAG, "finalStartIndex:" + finalStartIndex + " endIndex:" + endIndex + " threadId:" + threadId);
            if (finalStartIndex >= endIndex) {
                progress = finalStartIndex;
                Log.d(TAG, threadId + " mProgress[threadId]:" + mProgress[threadId]);
                sendEmptyMessage(MSG_FINISH);
                return;
            }
            Response response = mHttpUtil.downloadFileByRange(mPoint.getUrl(), finalStartIndex, endIndex);
            Log.d(TAG, "threadId:" + threadId + " " + response);
            ResponseBody body = response.body();
            if (response.code() != 206 || body == null) {
                // 206：请求部分资源成功码
                resetStatus();
                mListner.onFail();
                return;
            }
            InputStream is = body.byteStream();
            RandomAccessFile tmpAccessFile = new RandomAccessFile(mPoint.getFilePath(), "rw");
            // 获取前面已创建的文件.
            tmpAccessFile.seek(finalStartIndex);
            // 文件写入的开始位置.
            byte[] buffer = new byte[1024 << 2];
            int length = -1;
            int total = 0;
            long preProgress = 0;
            range = endIndex - startIndex;
            Log.d(TAG, "准备开始写文件 threadId:" + threadId + " contentLength:"
                    + body.contentLength() + " " + cancel + " " + pause);
            while ((length = is.read(buffer)) != -1) {
                if (cancel) {
                    //关闭资源
                    close(cacheAccessFile, tmpAccessFile, is, response.body());
                    cleanFile(cacheFile);
                    sendEmptyMessage(MSG_CANCEL);
                    return;
                }
                if (pause) {
                    //关闭资源
                    close(cacheAccessFile, tmpAccessFile, is, response.body());
                    //发送暂停消息
                    sendEmptyMessage(MSG_PAUSE);
                    return;
                }
                tmpAccessFile.write(buffer, 0, length);

                total += length;
                progress = finalStartIndex + total;

                //发送进度消息
                pro = progress - startIndex;
                long nowPro = pro * 100 / range;
                if (nowPro != preProgress || progress > endIndex) {
                    mProgress[threadId] = pro;
                    sendEmptyMessage(MSG_PROGRESS);
                    preProgress = nowPro;
                }
            }
            Log.d(TAG, "pro:" + pro + " range:" + range + " threadId:" + threadId);
            //关闭资源
            close(cacheAccessFile, tmpAccessFile, is, response.body());
            // 删除临时文件,不能在这里删除,假如两个线程,一个完成了,一个没有,这时候暂停,完成的线程把文件删除了
            // 等一下恢复下载又开两个线程,那个已经完成的线程又会重新开始下载
//            cleanFile(cacheFile);
            //发送完成消息
            sendEmptyMessage(MSG_FINISH);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, Log.getStackTraceString(e));
        } finally {
            try {
                String msg = "finally:";
                //将当前现在到的位置保存到文件中
                if (pro != range + 1) {
                    msg += "write progress:" + progress + " threadId:" + threadId;
                } else {
                    msg += "download complete write progress:" + progress
                            + " threadId:" + threadId;
                }
                cacheAccessFile = new RandomAccessFile(cacheFile, "rwd");
                cacheAccessFile.seek(0);
                cacheAccessFile.write((progress + "").getBytes(StandardCharsets.UTF_8));
                cacheAccessFile.close();
                Log.d(TAG, msg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 关闭资源
     *
     * @param closeables
     */
    private void close(Closeable... closeables) {
        if (closeables == null) {
            return;
        }
        int length = closeables.length;
        try {
            for (Closeable closeable : closeables) {
                if (closeable != null) {
                    closeable.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            for (int i = 0; i < length; i++) {
                closeables[i] = null;
            }
        }
    }

    /**
     * 删除临时文件
     */
    private void cleanFile(File... files) {
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file != null) {
                boolean delete = file.delete();
                Log.d(TAG, file.getName() + " delete:" + delete);
            }
        }
    }

    /**
     * 暂停
     */
    public void pause() {
        pause = true;
        isDownloading = false;
    }

    /**
     * 取消
     */
    public void cancel() {
        cancel = true;
        if (!isDownloading) {
            if (mListner != null) {
                cleanFile(mCacheFiles);
                resetStatus();
                mListner.onCancel();
            }
        }
        isDownloading = false;
        mPrePercent = 0;
    }

    /**
     * 重置下载状态
     */
    private void resetStatus() {
        pause = false;
        cancel = false;
        isDownloading = false;
    }

    /**
     * 确认下载状态
     *
     * @param count
     * @return
     */
    private boolean confirmStatus(AtomicInteger count) {
        return count.incrementAndGet() % THREAD_COUNT != 0;
    }

    public boolean isDownloading() {
        return isDownloading;
    }
}
