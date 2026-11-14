package com.eazilo.yoneticitakip;

import android.content.*;

public class RestoreAlarmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmUtils.rescheduleAll(context);
    }
}
