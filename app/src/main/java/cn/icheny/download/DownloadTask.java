package cn.icheny.download;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
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

public class DownloadTask extends Handler {

    public static final boolean DEBUG = true;
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
    private AtomicInteger childCancelCount = new AtomicInteger(0);
    /**
     * 子线程暂停数量
     */
    private AtomicInteger childPauseCount = new AtomicInteger(0);
    /**
     * 子线程完成数量
     */
    private AtomicInteger childFinishCount = new AtomicInteger(0);
    /**
     * 子线程失败数量
     */
    private AtomicInteger childFailCount = new AtomicInteger(0);
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
    private DownloadListener mListener;

    /**
     * 任务管理器初始化数据
     *
     * @param point
     * @param l
     */
    DownloadTask(FilePoint point, DownloadListener l) {
        this.mPoint = point;
        this.mListener = l;
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
        if (null == mListener) {
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
                    mListener.onProgress(progress, mFileLength, nowPercent);
                    mPrePercent = nowPercent;
                    if (DEBUG) {
                        Log.d(TAG, "progress:" + progress + " nowPercent:" + nowPercent);
                    }
                }
                break;
            case MSG_PAUSE:
                //暂停
                Log.d(TAG, "confirmStatus MSG_PAUSE:" + childPauseCount.get());
                if (confirmStatus(childPauseCount)) {
                    return;
                }
                Log.d(TAG, "MSG_PAUSE 暂停了");
                resetStatus();
                mListener.onPause();
                break;
            case MSG_FINISH:
                // 下载完成,一个线程暂停
                childPauseCount.incrementAndGet();
                //完成
                Log.d(TAG, "confirmStatus MSG_FINISH:" + childFinishCount.get());
                if (confirmStatus(childFinishCount)) {
                    return;
                }
                Log.d(TAG, "MSG_FINISH 下载完毕");
                //下载完毕后，重命名目标文件名
                resetStatus();
                reSetProgress();
                postDelayed(() -> {
                    // 将删除文件延后 50ms ,因为写进度是在 finally 代码块里删的太早可能那里又会把文件写入
                    cleanFile(mCacheFiles);
                }, 50);
                mListener.onFinished();
                break;
            case MSG_CANCEL:
                //取消
                if (confirmStatus(childCancelCount)) {
                    return;
                }
                resetStatus();
                mProgress = new long[THREAD_COUNT];
                mListener.onCancel();
                break;
            case MSG_FAIL:
                if (confirmStatus(childFailCount)) {
                    return;
                }
                resetStatus();
                mListener.onFail();
                break;
            default:
                break;
        }
    }

    private static final String TAG = "DownloadTask";

    public synchronized void start() {
        pause = false;
        cancel = false;
        Log.d(TAG, "start isDownloading:" + isDownloading + " Url:" + mPoint.getUrl());
        if (isDownloading) {
            return;
        }
        childFinishCount.set(0);
        childPauseCount.set(0);
        childCancelCount.set(0);
        isDownloading = true;
        ThreadManager.runOnBackground(() -> {
            try {
                File fileDes = new File(mPoint.getFilePath());
                if (fileDes.exists()) {
                    mFileLength = fileDes.length();
                } else {
                    File parentFile = fileDes.getParentFile();
                    if (!parentFile.exists()) {
                        boolean mkdirs = parentFile.mkdirs();
                    }
                    mFileLength = mHttpUtil.getContentLength(mPoint.getUrl());
                    // 在本地创建一个与资源同样大小的文件来占位
                    RandomAccessFile tmpAccessFile = new RandomAccessFile(fileDes, "rw");
                    tmpAccessFile.setLength(mFileLength);
                }
                splitThread();
            } catch (IOException e) {
                e.printStackTrace();
                sendEmptyMessage(MSG_FAIL);
            }
        });
    }

    /**
     * 分线程下载
     */
    private void splitThread() {
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
        if (DEBUG) {
            Log.d(TAG, "download:" + "startIndex:" + startIndex + " endIndex:" + endIndex + " threadId:" + threadId);
        }
        // 记录本次下载文件的位置
        long progress = startIndex;
        // dSize 这个线程下载的大小  range 这个线程需要下载多少长度
        long dSize = 0, range = endIndex - startIndex;
        File cacheFile = new File(mPoint.getFilePath() + "thread" + threadId + ".cache");
        try {
            final long finalStartIndex = Math.max(startIndex, readProgress(cacheFile));
            if (DEBUG) {
                Log.d(TAG, "finalStartIndex:" + finalStartIndex + " endIndex:"
                        + endIndex + " threadId:" + threadId + " from begin:" + (startIndex == finalStartIndex));
            }
            progress = finalStartIndex;
            if (finalStartIndex >= endIndex) {
                // 这个线程已经完成,不需要在 finally 代码块再写入进度
                sendEmptyMessage(MSG_FINISH);
                return;
            }
            Response response = mHttpUtil.downloadFileByRange(mPoint.getUrl(), finalStartIndex, endIndex);
            if (DEBUG) {
                Log.d(TAG, "threadId:" + threadId + " " + response);
            }
            ResponseBody body = response.body();
            if (response.code() != 206 || body == null) {
                // 206：请求部分资源成功码
                resetStatus();
                sendEmptyMessage(MSG_FAIL);
                return;
            }
            // 获取前面已创建的文件.
            RandomAccessFile tmpAccessFile = new RandomAccessFile(mPoint.getFilePath(), "rw");
            // 文件写入的开始位置.
            tmpAccessFile.seek(finalStartIndex);
            byte[] buffer = new byte[1024 * 4];
            int length, total = 0;
            long preProgress = 0;
            if (DEBUG) {
                Log.d(TAG, "准备开始写文件 threadId:" + threadId + " contentLength:"
                        + body.contentLength() + " " + cancel + " " + pause);
            }
            InputStream is = body.byteStream();
            while ((length = is.read(buffer)) != -1) {
                if (cancel) {
                    close(tmpAccessFile, is, response.body());
                    cleanFile(cacheFile);
                    sendEmptyMessage(MSG_CANCEL);
                    return;
                }
                if (pause) {
                    close(tmpAccessFile, is, response.body());
                    sendEmptyMessage(MSG_PAUSE);
                    return;
                }
                tmpAccessFile.write(buffer, 0, length);

                total += length;
                progress = finalStartIndex + total;

                // 发送进度消息,为避免消息发送太过频繁,限制每个线程完成 1% 发送一次进度
                dSize = progress - startIndex;
                long nowProgress = dSize * 100 / range;
                if (nowProgress != preProgress || progress > endIndex) {
                    mProgress[threadId] = dSize;
                    sendEmptyMessage(MSG_PROGRESS);
                    preProgress = nowProgress;
                }
            }
            if (DEBUG) {
                Log.d(TAG, "pro:" + dSize + " range:" + range + " threadId:" + threadId);
            }
            close(tmpAccessFile, is, response.body());
            // 删除临时文件,不能在这里删除,假如两个线程,一个完成了,一个没有,这时候暂停,完成的线程把文件删除了
            // 等一下恢复下载又开两个线程,那个已经完成的线程又会重新开始下载
//            cleanFile(cacheFile);
            //发送完成消息
            sendEmptyMessage(MSG_FINISH);
        } catch (Exception e) {
            e.printStackTrace();
            sendEmptyMessage(MSG_FAIL);
            Log.e(TAG, Log.getStackTraceString(e));
        } finally {
            writeProgress(threadId, progress, dSize, range, cacheFile);
        }
    }

    /**
     * 读取进度,读取之后就关掉,避免同时打开多个文件
     *
     * @param cacheFile
     * @return
     */
    private long readProgress(File cacheFile) {
        if (cacheFile == null || !cacheFile.exists()) {
            return 0;
        }
        RandomAccessFile cacheAccessFile = null;
        try {
            cacheAccessFile = new RandomAccessFile(cacheFile, "rwd");
            String s = cacheAccessFile.readLine();
            if (s != null) {
                return Long.parseLong(s);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(cacheAccessFile);
        }
        return 0;
    }

    /**
     * 在 finally 代码块保存进度性能更好
     *
     * @param threadId  线程id
     * @param progress  下载到的位置
     * @param dSize     此线程已经下载的大小
     * @param range     下载范围
     * @param cacheFile 保存进度的文件
     */
    private void writeProgress(int threadId, long progress,
                               long dSize, long range, File cacheFile) {
        RandomAccessFile cacheAccessFile = null;
        try {
            String msg = "finally:";
            //将当前现在到的位置保存到文件中
            if (dSize != range + 1) {
                msg += "write progress:" + progress + " threadId:" + threadId;
            } else {
                msg += "download complete write progress:" + progress
                        + " threadId:" + threadId;
            }
            cacheAccessFile = new RandomAccessFile(cacheFile, "rwd");
            cacheAccessFile.seek(0);
            cacheAccessFile.write((progress + "").getBytes(StandardCharsets.UTF_8));
            cacheAccessFile.close();
            if (DEBUG) {
                Log.d(TAG, msg);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, Log.getStackTraceString(e));
            close(cacheAccessFile);
        }
    }

    /**
     * 关闭资源
     *
     * @param closeables closeables
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

    public void reSetProgress() {
        if (mProgress != null) {
            for (int i = 0; i < mProgress.length; i++) {
                mProgress[i] = 0;
            }
        }
    }

    /**
     * 取消
     */
    public void cancel() {
        cancel = true;
        if (!isDownloading) {
            if (mListener != null) {
                cleanFile(mCacheFiles);
                resetStatus();
                reSetProgress();
                mListener.onCancel();
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
     * @param count count
     * @return
     */
    private boolean confirmStatus(AtomicInteger count) {
        return count.incrementAndGet() % THREAD_COUNT != 0;
    }

    public boolean isDownloading() {
        return isDownloading;
    }
}
