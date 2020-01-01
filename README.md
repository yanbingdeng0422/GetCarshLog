# GetCarshLog
# Android 异常捕获和log保存

标签： 异常捕获和log保存 UncaughtExceptionHandler 

---

## Android系统内Carch出现原因

系统Thread类内部有一个静态变量,它能监测到进程当前进程内，某个线程出现异常而没有被捕获的情况，然后对其进行善后处理：
```
private static volatile UncaughtExceptionHandler defaultUncaughtExceptionHandler;
```
android 系统中，在RuntimeInit类中，该类是新启动进程，每个进程初始化的时候都会走RuntimeInit.main方法；
```
    public static final void main(String[] argv) {
        enableDdms();
        if (argv.length == 2 && argv[1].equals("application")) {
            if (DEBUG) Slog.d(TAG, "RuntimeInit: Starting application");
            redirectLogStreams();
        } else {
            if (DEBUG) Slog.d(TAG, "RuntimeInit: Starting tool");
        }

        commonInit();

        /*
         * Now that we're running in interpreted code, call back into native code
         * to run the system.
         */
        nativeFinishInit();

        if (DEBUG) Slog.d(TAG, "Leaving RuntimeInit!");
    }
```
里面commonInit（）方法
```

    protected static final void commonInit() {
        if (DEBUG) Slog.d(TAG, "Entered RuntimeInit!");

        /*
         * set handlers; these apply to all threads in the VM. Apps can replace
         * the default handler, but not the pre handler.
         */
        Thread.setUncaughtExceptionPreHandler(new LoggingHandler());
        Thread.setDefaultUncaughtExceptionHandler(new KillApplicationHandler());
        ...
    }
```
 Thread.setUncaughtExceptionPreHandler对象是LoggingHandler；
 主要作用：是打出异常log信息，将异常信息添加到log系统中；
 Thread.setDefaultUncaughtExceptionHandler对象是KillApplicationHandler;
 主要作用:1、弹出carsh的对话框；2、carsh日志输出；3、杀死该进程
 ```
     /**
     * Handle application death from an uncaught exception.  The framework
     * catches these for the main threads, so this should only matter for
     * threads created by applications.  Before this method runs,
     * {@link LoggingHandler} will already have logged details.
     */
    private static class KillApplicationHandler implements Thread.UncaughtExceptionHandler {
        public void uncaughtException(Thread t, Throwable e) {
            try {
                // Don't re-enter -- avoid infinite loops if crash-reporting crashes.
                if (mCrashing) return;
                mCrashing = true;

                // Try to end profiling. If a profiler is running at this point, and we kill the
                // process (below), the in-memory buffer will be lost. So try to stop, which will
                // flush the buffer. (This makes method trace profiling useful to debug crashes.)
                if (ActivityThread.currentActivityThread() != null) {
                    ActivityThread.currentActivityThread().stopProfiling();
                }

                // Bring up crash dialog, wait for it to be dismissed
                ActivityManager.getService().handleApplicationCrash(
                        mApplicationObject, new ApplicationErrorReport.ParcelableCrashInfo(e));
            } catch (Throwable t2) {
                if (t2 instanceof DeadObjectException) {
                    // System process is dead; ignore
                } else {
                    try {
                        Clog_e(TAG, "Error reporting crash", t2);
                    } catch (Throwable t3) {
                        // Even Clog_e() fails!  Oh well.
                    }
                }
            } finally {
                // Try everything to make sure this process goes away.
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        }
    }
 ```
以上是系统异常日志获取和输出及异常处理；那在应用开发时，怎么在自己的APP中获取全局的异常log呢?

## Android APP内Carch捕获和log保存
主要是Thread.UncaughtExceptionHandler;当 Thread 因未捕获的异常而突然终止时，调用处理程序的接口
当某一线程因未捕获的异常而即将终止时，Java 虚拟机将使用 Thread.getUncaughtExceptionHandler() 查询该线程以获得其 UncaughtExceptionHandler 的线程，并调用处理程序的 uncaughtException 方法，将线程和异常作为参数传递。
如果某一线程没有明确设置其 UncaughtExceptionHandler，则将它的 ThreadGroup 对象作为其 UncaughtExceptionHandler。如果 ThreadGroup 对象对处理异常没有什么特殊要求，那么它可以将调用转发给默认的未捕获异常处理程序。

代码如下
```
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
```
异常获取log如下：
```
1970-03-03 17:37:17.234 6593-6593/com.yanbing.getcrashlog I/bqt: 【错误信息】java.lang.RuntimeException: Unable to start activity ComponentInfo{com.yanbing.getcrashlog/com.yanbing.getcrashlog.MainActivity}: java.lang.NullPointerException: Attempt to invoke virtual method 'boolean java.lang.String.equals(java.lang.Object)' on a null object reference
        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:2782)
        at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:2863)
        at android.app.ActivityThread.-wrap11(Unknown Source:0)
        at android.app.ActivityThread$H.handleMessage(ActivityThread.java:1589)
        at android.os.Handler.dispatchMessage(Handler.java:106)
        at android.os.Looper.loop(Looper.java:164)
        at android.app.ActivityThread.main(ActivityThread.java:6502)
        at java.lang.reflect.Method.invoke(Native Method)
        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:438)
        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:807)
     Caused by: java.lang.NullPointerException: Attempt to invoke virtual method 'boolean java.lang.String.equals(java.lang.Object)' on a null object reference
        at com.yanbing.getcrashlog.MainActivity.onCreate(MainActivity.java:18)
        at android.app.Activity.performCreate(Activity.java:7014)
        at android.app.Activity.performCreate(Activity.java:7005)
        at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1217)
        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:2733)
        at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:2863) 
        at android.app.ActivityThread.-wrap11(Unknown Source:0) 
        at android.app.ActivityThread$H.handleMessage(ActivityThread.java:1589) 
        at android.os.Handler.dispatchMessage(Handler.java:106) 
        at android.os.Looper.loop(Looper.java:164) 
        at android.app.ActivityThread.main(ActivityThread.java:6502) 
        at java.lang.reflect.Method.invoke(Native Method) 
        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:438) 
        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:807) 
    java.lang.NullPointerException: Attempt to invoke virtual method 'boolean java.lang.String.equals(java.lang.Object)' on a null object reference
        at com.yanbing.getcrashlog.MainActivity.onCreate(MainActivity.java:18)
        at android.app.Activity.performCreate(Activity.java:7014)
        at android.app.Activity.performCreate(Activity.java:7005)
        at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1217)
        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:2733)
        at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:2863)
        at android.app.ActivityThread.-wrap11(Unknown Source:0)
        at android.app.ActivityThread$H.handleMessage(ActivityThread.java:1589)
        at android.os.Handler.dispatchMessage(Handler.java:106)
        at android.os.Looper.loop(Looper.java:164)
        at android.app.ActivityThread.main(ActivityThread.java:6502)
        at java.lang.reflect.Method.invoke(Native Method)
        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:438)
        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:807)
```
