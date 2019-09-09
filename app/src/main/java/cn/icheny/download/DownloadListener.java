package cn.icheny.download;

/**
 * 下载监听
 *
 * @author Cheny
 */
public interface DownloadListener {

    /**
     * 完成
     */
    void onFinished();
    /**
     * apk下载进度监听
     *
     * @param progress 已经下载或上传字节数
     * @param total    总字节数
     * @param percent  百分比
     */
    void onProgress(long progress, long total, int percent);
    /**
     * 暂停
     */
    void onPause();

    /**
     * 取消
     */
    void onCancel();
    /**
     * 失败
     */
    void onFail();
}
