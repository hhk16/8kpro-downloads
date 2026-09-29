package com.hhk16.push100;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

public class WorkoutReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "push100_rest";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "100 PUSH alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Workout and rest reminders");
            channel.enableVibration(true);
            channel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, null);
            manager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");
        if (title == null || title.isEmpty()) title = "100 PUSH workout";
        if (message == null || message.isEmpty()) message = "Your scheduled push-up session is due.";

        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();

        manager.notify(200, notification);
    }
}
