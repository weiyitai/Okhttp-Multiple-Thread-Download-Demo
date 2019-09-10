package cn.icheny.download;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 下载管理器，断点续传
 *
 * @author Cheny
 */
public class DownloadManager {

    private static final String TAG = "DownloadManager";
    /**
     * 默认下载目录
     */
    private String mDefaultFileDir;
    /**
     * 文件下载任务索引，String为url,用来唯一区别并操作下载的文件
     */
    private Map<String, DownloadTask> mDownloadTasks;

    /**
     * 下载文件
     */
    public void download(String... urls) {
        //单任务开启下载或多任务开启下载
        for (String url : urls) {
            DownloadTask task1 = mDownloadTasks.get(url);
            if (task1 != null) {
                task1.start();
            }
        }
    }

    // 获取下载文件的名称
    public String getFileName(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }

    /**
     * 暂停
     */
    public void pause(String... urls) {
        //单任务暂停或多任务暂停下载
        for (String url : urls) {
            DownloadTask task1 = mDownloadTasks.get(url);
            if (task1 != null) {
                task1.pause();
            }
        }
    }

    /**
     * 取消下载
     */
    public void cancel(String... urls) {
        //单任务取消或多任务取消下载
        for (String url : urls) {
            DownloadTask task1 = mDownloadTasks.get(url);
            if (task1 != null) {
                task1.cancel();
            }
        }
    }

    /**
     * 添加下载任务
     */
    public void add(String url, DownloadListener l) {
        add(url, null, null, l);
    }

    /**
     * 添加下载任务
     */
    public void add(String url, String filePath, DownloadListener l) {
        add(url, filePath, null, l);
    }

    /**
     * 添加下载任务
     */
    public void add(String url, String filePath, String fileName, DownloadListener l) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        if (TextUtils.isEmpty(filePath)) {
            //没有指定下载目录,使用默认目录
            filePath = getDefaultDirectory();
        }
        if (TextUtils.isEmpty(fileName)) {
            fileName = getFileName(url);
            filePath += fileName;
        }
        mDownloadTasks.put(url, new DownloadTask(new FilePoint(url, filePath, fileName), l));
    }

    /**
     * 默认下载目录
     *
     * @return
     */
    public String getDefaultDirectory() {
        if (TextUtils.isEmpty(mDefaultFileDir)) {
            mDefaultFileDir = Environment.getExternalStorageDirectory().getAbsolutePath()
                    + File.separator + Environment.DIRECTORY_DOWNLOADS + File.separator
                    + "multi" + File.separator;
        }
        return mDefaultFileDir;
    }

    static class Instant {

        static final DownloadManager MANAGER = new DownloadManager();

    }

    public static DownloadManager getInstance() {
        return Instant.MANAGER;
    }

    public DownloadManager() {
        mDownloadTasks = new HashMap<>();
    }

    /**
     * 取消下载
     */
    public boolean isDownloading(String... urls) {
        //这里传一个url就是判断一个下载任务
        //多个url数组适合下载管理器判断是否作操作全部下载或全部取消下载
        boolean result = false;
        for (String url : urls) {
            DownloadTask task1 = mDownloadTasks.get(url);
            if (task1 != null) {
                result = task1.isDownloading();
            }
        }
        Log.d(TAG, "isDownloading:" + result + " " + Arrays.toString(urls));
        return result;
    }
}
