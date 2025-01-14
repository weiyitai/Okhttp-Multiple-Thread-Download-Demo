package cn.icheny.download;

/**
 * Created by Cheny on 2017/4/29.
 */

public class FilePoint {

    /**
     * 文件名
     */
    private String fileName;
    /**
     * 下载地址
     */
    private String url;
    /**
     * 下载目录
     */
    private String filePath;

    public FilePoint(String url) {
        this.url = url;
    }

    public FilePoint(String filePath, String url) {
        this.filePath = filePath;
        this.url = url;
    }

    public FilePoint(String url, String filePath, String fileName) {
        this.url = url;
        this.filePath = filePath;
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

}
