package cn.icheny.download;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Demo演示,临时写的Demo,难免有些bug
 *
 * @author Cheny
 */
public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 001;
    TextView tv_file_name1, tv_progress1, tv_file_name2, tv_progress2;
    Button btn_download1, btn_download2, btn_download_all;
    ProgressBar pb_progress1, pb_progress2;

    DownloadManager mDownloadManager;
    //    String wechatUrl = "http://dldir1.qq.com/weixin/android/weixin703android1400.apk";
    String wechatUrl = "http://smbaup.sure56.com:8021/Update/UnitopSure.apk";
    String qqUrl = "https://qd.myapp.com/myapp/qqteam/AndroidQQ/mobileqq_android.apk";
//    String qqUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        initDownloads();
    }

    private void initDownloads() {
        mDownloadManager = DownloadManager.getInstance();
        mDownloadManager.add(wechatUrl, new DownloadListener() {
            @Override
            public void onFinished() {
                Toast.makeText(MainActivity.this, "下载完成!", Toast.LENGTH_SHORT).show();
                btn_download1.setText("下载");
            }

            @Override
            public void onProgress(long progress, long total, int percent) {
                pb_progress1.setProgress(percent);
                tv_progress1.setText(getString(R.string.down_percent, percent));
            }

            @Override
            public void onPause() {
                Toast.makeText(MainActivity.this, "暂停了!", Toast.LENGTH_SHORT).show();
                btn_download1.setEnabled(true);
            }

            @Override
            public void onCancel() {
                tv_progress1.setText("0%");
                pb_progress1.setProgress(0);
                btn_download1.setText("下载");
                Toast.makeText(MainActivity.this, "下载已取消!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFail() {
                tv_progress1.setText("0%");
                pb_progress1.setProgress(0);
                btn_download1.setText("下载");
                Log.d("MainActivity", "onFail 下载失败了");
                Toast.makeText(MainActivity.this, "下载失败了", Toast.LENGTH_SHORT).show();
            }
        });

        mDownloadManager.add(qqUrl, new DownloadListener() {
            @Override
            public void onFinished() {
                Toast.makeText(MainActivity.this, "下载完成!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onProgress(long progress, long total, int percent) {
                pb_progress2.setProgress(percent);
                tv_progress2.setText(percent + "%");
            }

            @Override
            public void onPause() {
                Toast.makeText(MainActivity.this, "暂停了!", Toast.LENGTH_SHORT).show();
                btn_download2.setEnabled(true);

            }

            @Override
            public void onCancel() {
                tv_progress2.setText("0%");
                pb_progress2.setProgress(0);
                btn_download2.setText("下载");
                Toast.makeText(MainActivity.this, "下载已取消!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFail() {

            }
        });
    }

    /**
     * 初始化View控件
     */
    private void initViews() {
        tv_file_name1 = findViewById(R.id.tv_file_name1);
        tv_progress1 = findViewById(R.id.tv_progress1);
        pb_progress1 = findViewById(R.id.pb_progress1);
        btn_download1 = findViewById(R.id.btn_download1);
        tv_file_name1.setText("微信");

        tv_file_name2 = findViewById(R.id.tv_file_name2);
        tv_progress2 = findViewById(R.id.tv_progress2);
        pb_progress2 = findViewById(R.id.pb_progress2);
        btn_download2 = findViewById(R.id.btn_download2);
        tv_file_name2.setText("qq");

        btn_download_all = findViewById(R.id.btn_download_all);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        int thread = sp.getInt("thread", 2);
        DownloadTask.THREAD_COUNT = thread;

        TextView textView = findViewById(R.id.tv_thread);
        textView.setText(String.format(Locale.getDefault(),
                "线程数:%d 重启生效", thread));
        SeekBar seekBar = findViewById(R.id.sb_thread);
        seekBar.setProgress(thread);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 1) {
                    progress = 1;
                }
                sp.edit().putInt("thread", progress).apply();
                textView.setText(String.format(Locale.getDefault(),
                        "线程数:%d 重启生效", progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    /**
     * 下载或暂停下载
     *
     * @param view
     */
    public void downloadOrPause(View view) {
        switch (view.getId()) {
            case R.id.btn_download1:
                if (!mDownloadManager.isDownloading(wechatUrl)) {
                    mDownloadManager.download(wechatUrl);
                    btn_download1.setText("暂停");
                } else {
                    btn_download1.setText("下载");
                    mDownloadManager.pause(wechatUrl);
                    btn_download1.setEnabled(false);
                }
                break;
            case R.id.btn_download2:
                if (!mDownloadManager.isDownloading(qqUrl)) {
                    mDownloadManager.download(qqUrl);
                    btn_download2.setText("暂停");
                } else {
                    btn_download2.setText("下载");
                    mDownloadManager.pause(qqUrl);
                    btn_download2.setEnabled(false);
                }
                break;
            default:
                break;
        }
    }

    public void downloadOrPauseAll(View view) {
        if (!mDownloadManager.isDownloading(wechatUrl, qqUrl)) {
            btn_download1.setText("暂停");
            btn_download2.setText("暂停");
            btn_download_all.setText("全部暂停");
            mDownloadManager.download(wechatUrl, qqUrl);//最好传入个String[]数组进去
        } else {
            mDownloadManager.pause(wechatUrl, qqUrl);
            btn_download1.setText("下载");
            btn_download2.setText("下载");
            btn_download_all.setText("全部下载");
        }
    }

    /**
     * 取消下载
     *
     * @param view
     */
    public void cancel(View view) {
        switch (view.getId()) {
            case R.id.btn_cancel1:
                mDownloadManager.cancel(wechatUrl);
                break;
            case R.id.btn_cancel2:
                mDownloadManager.cancel(qqUrl);
                break;
            default:
                break;
        }
    }

    public void cancelAll(View view) {
        mDownloadManager.cancel(wechatUrl, qqUrl);
        btn_download1.setText("下载");
        btn_download2.setText("下载");
        btn_download_all.setText("全部下载");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;

        if (!checkPermission(permission)) {
            //针对android6.0动态检测申请权限
            if (shouldShowRationale(permission)) {
                showMessage("需要权限跑demo哦...");
            }
            requestPermissions(new String[]{permission}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDownloadManager.pause(qqUrl, wechatUrl);
    }

    /**
     * 显示提示消息
     *
     * @param msg
     */
    private void showMessage(String msg) {
        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * 检测用户权限
     *
     * @param permission
     * @return
     */
    protected boolean checkPermission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * 是否需要显示请求权限的理由
     *
     * @param permission
     * @return
     */
    protected boolean shouldShowRationale(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return shouldShowRequestPermissionRationale(permission);
        }
        return false;
    }
}
