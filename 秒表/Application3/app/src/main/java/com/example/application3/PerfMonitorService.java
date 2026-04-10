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

public class PerfMonitorService extends Service {
    private WindowManager windowManager;
    private TextView textView;
    private WindowManager.LayoutParams params;
    private Handler handler;
    private boolean isRunning = true;

    // 秒表相关
    private long startTime = 0;
    private boolean isRunning_ = false;
    private long pausedTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundService();
        createFloatingWindow();
        startStopwatch();
    }

    private void startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "perf_channel", "秒表",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, "perf_channel")
            .setContentTitle("秒表运行中")
            .setContentText("点击打开")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .build();

        startForeground(1, notification);
    }

    private void createFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        textView = new TextView(this);
        textView.setText("00:00:00.000");
        textView.setTextColor(Color.parseColor("#00FF00"));
        textView.setBackgroundColor(Color.parseColor("#AA000000"));
        textView.setPadding(35, 20, 35, 20);
        textView.setTextSize(18);
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

        // 点击暂停/继续，长按重置
        textView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleStopwatch();
                }
            });

        textView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    resetStopwatch();
                    return true;
                }
            });

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

    private void startStopwatch() {
        handler = new Handler(Looper.getMainLooper());
        startTime = System.currentTimeMillis();
        isRunning_ = true;

        // 约60帧刷新（16.6ms一帧）
        handler.post(new Runnable() {
                @Override
                public void run() {
                    if (!isRunning) return;

                    if (isRunning_) {
                        updateDisplay();
                    }

                    // 每秒约60次刷新（约16.6毫秒）
                    handler.postDelayed(this, 16);
                }
            });
    }

    private void toggleStopwatch() {
        if (isRunning_) {
            // 暂停
            pausedTime = System.currentTimeMillis() - startTime;
            isRunning_ = false;
            textView.setBackgroundColor(Color.parseColor("#AA333333"));
        } else {
            // 继续
            startTime = System.currentTimeMillis() - pausedTime;
            isRunning_ = true;
            textView.setBackgroundColor(Color.parseColor("#AA000000"));
        }
    }

    private void resetStopwatch() {
        if (isRunning_) {
            startTime = System.currentTimeMillis();
            pausedTime = 0;
        } else {
            startTime = 0;
            pausedTime = 0;
            updateDisplay();
        }
        textView.setBackgroundColor(Color.parseColor("#AA000000"));
    }

    private void updateDisplay() {
        long elapsed;
        if (isRunning_) {
            elapsed = System.currentTimeMillis() - startTime;
        } else {
            elapsed = pausedTime;
        }

        int hours = (int) (elapsed / 3600000);
        int minutes = (int) ((elapsed % 3600000) / 60000);
        int seconds = (int) ((elapsed % 60000) / 1000);
        int milliseconds = (int) (elapsed % 1000);

        String time = String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds);
        textView.setText(time);
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
