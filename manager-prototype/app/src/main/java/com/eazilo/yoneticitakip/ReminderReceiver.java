package com.eazilo.yoneticitakip;

import android.app.*;
import android.content.*;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");
        int id = intent.getIntExtra("id", (int)(System.currentTimeMillis()%100000));
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, "takip_hatirlatmalari")
                : new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
         .setContentTitle(title == null ? "Takip hatırlatması" : title)
         .setContentText(body == null ? "Kontrol zamanı yaklaşıyor." : body)
         .setStyle(new Notification.BigTextStyle().bigText(body))
         .setAutoCancel(true)
         .setContentIntent(pi)
         .setPriority(Notification.PRIORITY_HIGH);
        ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, b.build());
    }
}
