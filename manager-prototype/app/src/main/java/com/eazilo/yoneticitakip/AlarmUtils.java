package com.eazilo.yoneticitakip;

import android.app.*;
import android.content.*;
import android.os.Build;

import org.json.*;

public final class AlarmUtils {
    private static final String PREFS = "yonetici_takip_data";

    private AlarmUtils(){}

    public static void schedule(Context context,int id,long when,String title,String body){
        if(when<=System.currentTimeMillis()) when=System.currentTimeMillis()+5000L;

        Intent i=new Intent(context,ReminderReceiver.class);
        i.putExtra("title",title);
        i.putExtra("body",body);
        i.putExtra("id",id);

        PendingIntent pi=PendingIntent.getBroadcast(
                context,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;

        try{
            if(Build.VERSION.SDK_INT>=31){
                if(am.canScheduleExactAlarms()){
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
                }else{
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
                }
            }else if(Build.VERSION.SDK_INT>=23){
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            }else{
                am.setExact(AlarmManager.RTC_WAKEUP,when,pi);
            }
        }catch(SecurityException e){
            if(Build.VERSION.SDK_INT>=23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            else am.set(AlarmManager.RTC_WAKEUP,when,pi);
        }
    }

    public static void cancel(Context context,int id){
        Intent i=new Intent(context,ReminderReceiver.class);
        PendingIntent pi=PendingIntent.getBroadcast(
                context,id,i,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(am!=null && pi!=null)am.cancel(pi);
    }

    public static void rescheduleAll(Context context){
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        JSONArray tasks=parse(prefs.getString("tasks","[]"));
        JSONArray fields=parse(prefs.getString("fieldNotes","[]"));
        JSONArray cards=parse(prefs.getString("controlCards","[]"));

        for(int idx=0;idx<tasks.length();idx++){
            JSONObject o=tasks.optJSONObject(idx);
            if(o==null || o.optBoolean("done"))continue;
            int id=o.optInt("id");
            long due=o.optLong("due");
            int days=o.has("remindDays")?o.optInt("remindDays",1):1;
            schedule(context,id,due-days*24L*60*60*1000,
                    "Dönüş yaklaşıyor",o.optString("title")+" • "+o.optString("person"));
            schedule(context,id+100000,due,
                    "Geri dönüş zamanı",o.optString("title")+" • "+o.optString("person"));
        }

        for(int idx=0;idx<fields.length();idx++){
            JSONObject o=fields.optJSONObject(idx);
            if(o==null || o.optBoolean("done"))continue;
            int id=o.optInt("id");
            long at=o.optLong("checkAt");
            schedule(context,id,at-24L*60*60*1000,
                    "Saha kontrolü yarın",o.optString("branch")+" • "+o.optString("note"));
            schedule(context,id+200000,at,
                    "Saha kontrol zamanı",o.optString("branch")+" • "+o.optString("note"));
        }

        for(int idx=0;idx<cards.length();idx++){
            JSONObject o=cards.optJSONObject(idx);
            if(o==null)continue;
            int id=o.optInt("id");
            long at=o.optLong("nextCheckAt");
            schedule(context,id,at-24L*60*60*1000,
                    "Kontrol kartı","Yarın • "+o.optString("branch"));
            schedule(context,id+300000,at,
                    "Kontrol zamanı",o.optString("branch"));
        }
    }

    private static JSONArray parse(String s){
        try{return new JSONArray(s);}catch(Exception e){return new JSONArray();}
    }
}
