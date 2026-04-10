package com.example.application3;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Button button = new Button(this);
        button.setText("启动悬浮窗");
        button.setTextSize(18);
        button.setPadding(50, 30, 50, 30);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkPermissionAndStart();
                }
            });

        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.addView(button);
        setContentView(layout);
    }

    private void checkPermissionAndStart() {
        // Android 6.0+ 需要悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                startService();
            } else {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                           Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "请授予悬浮窗权限后再次点击", Toast.LENGTH_LONG).show();
            }
        } else {
            startService();
        }
    }

    private void startService() {
        startService(new Intent(this, PerfMonitorService.class));
        Toast.makeText(this, "性能监控已启动", Toast.LENGTH_SHORT).show();
        finish();
    }
}
