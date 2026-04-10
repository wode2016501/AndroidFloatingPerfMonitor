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
    private float fps = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundService();
        createFloatingWindow();
        startMonitoring();
    }

    private void startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "perf_channel", "性能监控",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, "perf_channel")
            .setContentTitle("性能监控运行中")
            .setContentText("点击打开")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .build();

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
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
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

    private void updateAllData() {
        updateFps();      // 使用新的 FPS 读取方法
        updateCpuUsage();
        updateGpuUsage();

        String info = String.format("FPS: %.1f", fps) + 
            "\nCPU: " + String.format("%.1f", cpuUsage) + "%" +
            "\nGPU: " + String.format("%.1f", gpuUsage) + "%";
        textView.setText(info);
    }

    // 新增：从 sde-crtc-5 读取 FPS
    private void updateFps() {
        String content = readFile("/sys/class/drm/sde-crtc-0/measured_fps");
        if (content == null || content.trim().isEmpty()) {
            fps = -1;
            return;
        }

        try {
            // 解析格式: "fps: 0.0 duration:1000000 frame_count:0"
            String[] parts = content.trim().split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].startsWith("fps:")) {
                    String fpsValue = parts[i + 1];
                    fps = Float.parseFloat(fpsValue);
                    break;
                }
            }
        } catch (Exception e) {
            fps = -1;
        }
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
        // 尝试多个可能的 GPU 使用率读取路径
        String[] gpuPaths = {
            "/sys/kernel/gpu/gpu_busy",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/misc/mali0/device/utilization"
        };

        String content = null;
        for (String path : gpuPaths) {
            content = readFile(path);
            if (content != null && !content.trim().isEmpty()) {
                break;
            }
        }

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
