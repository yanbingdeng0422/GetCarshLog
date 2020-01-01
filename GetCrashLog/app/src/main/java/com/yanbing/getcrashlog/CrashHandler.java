package com.yanbing.getcrashlog;

import android.app.Application;
import android.os.Environment;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String LOG_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/crashLog/";


    private Application application;

    private static CrashHandler instance = new CrashHandler();

    private CrashHandler() {//构造方法私有
    }

    public static CrashHandler getInstance() {
        return instance;
    }

    public void init(Application application) {
        this.application = application;
        Thread.setDefaultUncaughtExceptionHandler(instance);//设置该CrashHandler为系统默认的
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        saveInfoToFile(collectCrashInfo(ex));//保存错误信息
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                Toast.makeText(application, "程序开小差了，将会在2秒后退出", Toast.LENGTH_SHORT).show();//使用Toast来显示异常信息
                Looper.loop();
            }
        }.start();
        SystemClock.sleep(2000);//延迟2秒杀进程
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    private String collectCrashInfo(Throwable ex) {
        if (ex == null) return "";

        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        ex.printStackTrace(printWriter);
        Throwable throwable = ex.getCause();
        while (throwable != null) {
            throwable.printStackTrace(printWriter);
            throwable = throwable.getCause();//逐级获取错误信息
        }
        String crashInfo = writer.toString();
        Log.i("bqt", "【错误信息】" + crashInfo);
        printWriter.close();
        return crashInfo;
    }

    private void saveInfoToFile(String crashInfo) {
        try {
            File dir = new File(LOG_PATH);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String date = new SimpleDateFormat("yyyy.MM.dd_HH_mm_ss", Locale.getDefault()).format(new Date());
            String fileName = LOG_PATH + "crash_" + date + ".txt";
            FileWriter writer = new FileWriter(fileName);//如果保存失败，很可能是没有写SD卡权限
            writer.write(crashInfo);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
