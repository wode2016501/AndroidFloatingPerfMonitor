package com.example.application3;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class PerfMonitorService extends Service {
    private WindowManager windowManager;
    private TextView textView;
    private WindowManager.LayoutParams params;
    private Handler handler;
    private boolean isRunning = true;

    // CPU 相关
    private long lastTotalCpu = 0;
    private long lastIdleCpu = 0;
    private float cpuUsage = 0;

    // GPU 相关
    private float gpuUsage = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundService();
        createFloatingWindow();
        startMonitoring();
    }

    private void startForegroundService() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Android 8.0+ 需要通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "perf_channel", "性能监控",
                NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+ 使用带 channel ID 的构造函数
            notification = new Notification.Builder(this, "perf_channel")
                .setContentTitle("性能监控运行中")
                .setContentText("点击打开")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .build();
        } else {
            // Android 7.0 及以下（包括 Android 6）使用单参数构造函数
            notification = new Notification.Builder(this)
                .setContentTitle("性能监控运行中")
                .setContentText("点击打开")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .build();
        }

        startForeground(1, notification);
    }

    private void createFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        textView = new TextView(this);
        textView.setText("Loading...");
        textView.setTextColor(Color.parseColor("#00FF00"));
        textView.setBackgroundColor(Color.parseColor("#AA000000"));
        textView.setPadding(25, 15, 25, 15);
        textView.setTextSize(11);
        textView.setTypeface(Typeface.MONOSPACE);

        params = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= 26) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
        params.format = PixelFormat.RGBA_8888;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 10;
        params.y = 100;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;

        windowManager.addView(textView, params);

        textView.setOnTouchListener(new View.OnTouchListener() {
                private int lastX, lastY;
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            lastX = (int) event.getRawX();
                            lastY = (int) event.getRawY();
                            break;
                        case MotionEvent.ACTION_MOVE:
                            int dx = (int) event.getRawX() - lastX;
                            int dy = (int) event.getRawY() - lastY;
                            params.x += dx;
                            params.y += dy;
                            windowManager.updateViewLayout(textView, params);
                            lastX = (int) event.getRawX();
                            lastY = (int) event.getRawY();
                            break;
                    }
                    return true;
                }
            });
    }

    private void startMonitoring() {
        handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
                @Override
                public void run() {
                    if (!isRunning) return;
                    updateAllData();
                    handler.postDelayed(this, 1000);
                }
            });
    }

    
//显示
    private void updateAllData() {
        String fps = readFps();
        updateCpuUsage();

        String info = "FPS: " + fps + 
            "\nCPU: " + String.format("%.1f", cpuUsage) + "%";
        textView.setText(info);
    }
//读取fps
    /*
     shell@xmen:/ $ cat /sys/class/video/fps_info
     input_fps:0x1a output_fps:0x1a drop_fps:0x0
     */
    private String readFps() {
        String content = readFile("/sys/class/video/fps_info");
        if (content == null || content.trim().isEmpty()) {
            return "N/A";
        }

        // 解析 input_fps:0x1a output_fps:0x1a 格式
        try {
            String[] parts = content.split("\\s+");
            for (String part : parts) {
                if (part.startsWith("output_fps:") || part.startsWith("output_fps=")) {
                    String value = part.substring(part.indexOf(":") + 1); // 获取冒号后的部分
                    value = value.trim();

                    int fps;
                    if (value.startsWith("0x") || value.startsWith("0X")) {
                        // 十六进制转换
                        fps = Integer.parseInt(value.substring(2), 16);
                    } else {
                        // 十进制转换
                        fps = Integer.parseInt(value);
                    }

                    // 合理性检查
                    if (fps > 0 && fps < 240) {
                        return String.valueOf(fps);
                    } else {
                        return "0";
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "ERR";
        }
        return "N/A";
    }
    
   
    
    
    private void updateCpuUsage() {
        try {
            String content = readFile("/proc/stat");
            if (content == null) return;

            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.startsWith("cpu ")) {
                    String[] parts = line.trim().split("\\s+");
                    long user = Long.parseLong(parts[1]);
                    long nice = Long.parseLong(parts[2]);
                    long system = Long.parseLong(parts[3]);
                    long idle = Long.parseLong(parts[4]);
                    long iowait = Long.parseLong(parts[5]);
                    long irq = Long.parseLong(parts[6]);
                    long softirq = Long.parseLong(parts[7]);

                    long total = user + nice + system + idle + iowait + irq + softirq;
                    long idleAll = idle + iowait;

                    if (lastTotalCpu != 0) {
                        long totalDiff = total - lastTotalCpu;
                        long idleDiff = idleAll - lastIdleCpu;
                        if (totalDiff > 0) {
                            cpuUsage = (totalDiff - idleDiff) * 100.0f / totalDiff;
                        }
                    }

                    lastTotalCpu = total;
                    lastIdleCpu = idleAll;
                    break;
                }
            }
        } catch (Exception e) {
            cpuUsage = -1;
        }
    }

    private void updateGpuUsage() {
        String content = readFile("/sys/kernel/gpu/gpu_busy");
        if (content == null || content.trim().isEmpty()) {
            gpuUsage = -1;
            return;
        }

        try {
            String value = content.trim().replace("%", "");
            gpuUsage = Float.parseFloat(value);
        } catch (NumberFormatException e) {
            gpuUsage = -1;
        }
    }

    private String readFile(String path) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
            return reader.readLine();
        } catch (Exception e) {
            return null;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException e) {}
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (windowManager != null && textView != null) {
            try {
                windowManager.removeView(textView);
            } catch (Exception e) {}
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
