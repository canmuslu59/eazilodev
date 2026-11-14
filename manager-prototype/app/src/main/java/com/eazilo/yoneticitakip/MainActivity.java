package com.eazilo.yoneticitakip;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final String PREFS = "yonetici_takip_data";
    private static final String CHANNEL_ID = "takip_hatirlatmalari";
    private static final int REQ_CAMERA = 40;
    private static final int REQ_NOTIF = 41;

    private int BG, CARD, TEXT, MUTED, BORDER, DARK, GREEN, RED, AMBER, BLUE, SAFETY, SAFETY_DARK;
    private int SURFACE_ALT, INPUT_BG, HINT, PRESSED_BG, CHIP_BG, CHIP_PRESSED, WARNING_BG, WARNING_BORDER;
    private boolean darkMode;

    private SharedPreferences prefs;
    private JSONArray tasks = new JSONArray();
    private JSONArray fieldNotes = new JSONArray();
    private JSONArray controlCards = new JSONArray();
    private LinearLayout content;
    private String currentPage = "home";
    private String pendingCardBranch;
    private long pendingCardNextCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        applyPalette();
        setTheme(darkMode ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(savedInstanceState);
        updateSystemBars();

        createNotificationChannel();
        requestNotificationPermissionIfNeeded();
        loadData();
        purgeLegacyDemoData();
        buildShell();
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && am.canScheduleExactAlarms()) AlarmUtils.rescheduleAll(this);
        }
        if (content != null && "home".equals(currentPage)) showHome();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            int uiFlags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if(!darkMode){
                uiFlags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if(Build.VERSION.SDK_INT>=26) uiFlags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(uiFlags);
        }

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(12), dp(12), dp(10));
        header.setBackgroundColor(BG);

        TextView brand = tv("Önce Güvenlik", 22, TEXT, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        TextView settings = chip("⚙", SAFETY, Color.WHITE);
        settings.setGravity(Gravity.CENTER);
        settings.setTextSize(18);
        settings.setContentDescription("Ayarlar");
        settings.setOnClickListener(v -> showSettingsDialog());
        header.addView(settings, new LinearLayout.LayoutParams(dp(44), dp(40)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int side = isNarrowScreen() ? 14 : 18;
        content.setPadding(dp(side), dp(8), dp(side), dp(28));
        scroller.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(6), dp(8), dp(8));
        nav.setBackgroundColor(CARD);
        nav.addView(navButton("Bugün", "home"), new LinearLayout.LayoutParams(0, dp(52), 1));
        nav.addView(navButton("İşler", "tasks"), new LinearLayout.LayoutParams(0, dp(52), 1));
        nav.addView(navButton("Saha", "field"), new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(nav);

        applySystemBarInsets(root, header, nav);
        setContentView(root);
    }

    private void applySystemBarInsets(View root, View header, View nav) {
        final int headerLeft = dp(18), headerTop = dp(12), headerRight = dp(12), headerBottom = dp(10);
        final int navLeft = dp(8), navTop = dp(6), navRight = dp(8), navBottom = dp(8);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            header.setPadding(headerLeft + left, headerTop + top, headerRight + right, headerBottom);
            nav.setPadding(navLeft + left, navTop, navRight + right, navBottom + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private TextView navButton(String label, String page) {
        TextView v = tv(label, 13, TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(pressStateBg(Color.TRANSPARENT, PRESSED_BG, 0, 16));
        makePressable(v, false);
        v.setOnClickListener(x -> {
            currentPage = page;
            if ("home".equals(page)) showHome();
            else if ("tasks".equals(page)) showTasks();
            else showField();
        });
        return v;
    }

    private void showHome() {
        currentPage = "home";
        content.removeAllViews();

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.BOTTOM);
        TextView title = tv("Bugün", isNarrowScreen() ? 30 : 34, TEXT, true);
        hero.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView date = tv(new SimpleDateFormat("d MMM", new Locale("tr","TR")).format(new Date()), 13, MUTED, true);
        hero.addView(date);
        content.addView(hero);

        addGap(14);

        if (!notificationSetupComplete()) {
            LinearLayout warn = roundedCard(WARNING_BG, WARNING_BORDER, 18);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = tv("Bildirim güvencesi", 14, TEXT, true);
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            TextView setup = chip("Ayarla", SAFETY, Color.WHITE);
            setup.setOnClickListener(v -> openNotificationReliabilitySetup());
            row.addView(setup);
            warn.addView(row);
            addCard(warn);
        }

        int overdue = 0, dueSoon = 0, fieldDue = 0;
        long now = System.currentTimeMillis();
        long week = now + 7L * 24 * 60 * 60 * 1000;
        for (int i=0;i<tasks.length();i++) {
            JSONObject o=tasks.optJSONObject(i);
            if (o==null || o.optBoolean("done")) continue;
            long due=o.optLong("due");
            if (due < now) overdue++;
            else if (due <= week) dueSoon++;
        }
        for (int i=0;i<fieldNotes.length();i++) {
            JSONObject o=fieldNotes.optJSONObject(i);
            if (o==null || o.optBoolean("done")) continue;
            if (o.optLong("checkAt") <= week) fieldDue++;
        }

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statCard("Geciken", overdue, overdue>0?RED:TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        Space a=new Space(this); stats.addView(a,new LinearLayout.LayoutParams(dp(8),1));
        stats.addView(statCard("7 gün", dueSoon, BLUE), new LinearLayout.LayoutParams(0, -2, 1));
        Space b=new Space(this); stats.addView(b,new LinearLayout.LayoutParams(dp(8),1));
        stats.addView(statCard("Saha", fieldDue, AMBER), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(stats);

        addGap(18);
        Button add = primaryButton("+  Yeni iş", v -> showTaskWizard());
        content.addView(add, new LinearLayout.LayoutParams(-1, dp(52)));

        sectionTitle("Takipte");
        ArrayList<JSONObject> open = new ArrayList<>();
        for (int i=0;i<tasks.length();i++) {
            JSONObject o=tasks.optJSONObject(i);
            if (o!=null && !o.optBoolean("done")) open.add(o);
        }
        open.sort(Comparator.comparingLong(o -> o.optLong("due")));
        if (open.isEmpty()) content.addView(emptyState("Açık iş yok"));
        for (int i=0;i<Math.min(6,open.size());i++) content.addView(taskCard(open.get(i), true));

        sectionTitle("Saha");
        ArrayList<JSONObject> notes = new ArrayList<>();
        for (int i=0;i<fieldNotes.length();i++) {
            JSONObject o=fieldNotes.optJSONObject(i);
            if (o!=null && !o.optBoolean("done")) notes.add(o);
        }
        notes.sort(Comparator.comparingLong(o -> o.optLong("checkAt")));
        if (notes.isEmpty()) content.addView(emptyState("Kontrol bekleyen saha notu yok"));
        for (int i=0;i<Math.min(3,notes.size());i++) content.addView(fieldCard(notes.get(i), true));
    }

    private void showTasks() {
        currentPage = "tasks";
        content.removeAllViews();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(tv("İşler", 30, TEXT, true), new LinearLayout.LayoutParams(0,-2,1));
        TextView add = chip("+ Yeni", DARK, Color.WHITE);
        add.setOnClickListener(v -> showTaskWizard());
        top.addView(add);
        content.addView(top);

        addGap(14);

        ArrayList<JSONObject> list = new ArrayList<>();
        for (int i=0;i<tasks.length();i++) {
            JSONObject o=tasks.optJSONObject(i);
            if (o!=null) list.add(o);
        }
        list.sort((x,y) -> {
            if (x.optBoolean("done") != y.optBoolean("done")) return x.optBoolean("done") ? 1 : -1;
            return Long.compare(x.optLong("due"), y.optLong("due"));
        });

        if (list.isEmpty()) content.addView(emptyState("Henüz iş yok"));
        for (JSONObject o:list) content.addView(taskCard(o,false));
    }

    private void showField() {
        currentPage = "field";
        content.removeAllViews();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(tv("Saha", 30, TEXT, true), new LinearLayout.LayoutParams(0,-2,1));
        content.addView(top);

        addGap(12);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(isNarrowScreen()?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        Button note = primaryButton("+ Saha notu", v -> showAddFieldDialog());
        Button photo = secondaryButton("Kontrol kartı  ◫", v -> showControlCardDialog());
        if (isNarrowScreen()) {
            buttons.addView(note,new LinearLayout.LayoutParams(-1,dp(50)));
            addGap(buttons,8);
            buttons.addView(photo,new LinearLayout.LayoutParams(-1,dp(50)));
        } else {
            buttons.addView(note,new LinearLayout.LayoutParams(0,dp(50),1));
            Space s=new Space(this);buttons.addView(s,new LinearLayout.LayoutParams(dp(8),1));
            buttons.addView(photo,new LinearLayout.LayoutParams(0,dp(50),1));
        }
        content.addView(buttons);

        sectionTitle("Kontrol bekleyen");
        ArrayList<JSONObject> notes=new ArrayList<>();
        for(int i=0;i<fieldNotes.length();i++){
            JSONObject o=fieldNotes.optJSONObject(i);
            if(o!=null) notes.add(o);
        }
        notes.sort((x,y)->{
            if(x.optBoolean("done")!=y.optBoolean("done")) return x.optBoolean("done")?1:-1;
            return Long.compare(x.optLong("checkAt"),y.optLong("checkAt"));
        });
        if(notes.isEmpty()) content.addView(emptyState("Saha notu yok"));
        for(JSONObject o:notes) content.addView(fieldCard(o,false));

        sectionTitle("Kontrol kartları");
        if(controlCards.length()==0) content.addView(emptyState("Kontrol kartı yok"));
        for(int i=controlCards.length()-1;i>=0;i--) content.addView(controlCardView(controlCards.optJSONObject(i)));
    }

    private void showTaskWizard() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18),dp(18),dp(18),dp(18));
        shell.setBackgroundColor(BG);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=tv("Yeni iş",24,TEXT,true);
        head.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=chip("×",SURFACE_ALT,TEXT);
        close.setTextSize(19);
        close.setOnClickListener(v->dialog.dismiss());
        head.addView(close,new LinearLayout.LayoutParams(dp(40),dp(40)));
        shell.addView(head);

        TextView progress=tv("1 / 5",12,MUTED,true);
        progress.setPadding(0,dp(10),0,dp(14));
        shell.addView(progress);

        LinearLayout cardHost = new LinearLayout(this);
        cardHost.setOrientation(LinearLayout.VERTICAL);
        shell.addView(cardHost,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(0,dp(12),0,0);
        Button back=secondaryButton("Geri",v->{});
        Button next=primaryButton("Devam",v->{});
        foot.addView(back,new LinearLayout.LayoutParams(0,dp(50),1));
        Space sp=new Space(this);foot.addView(sp,new LinearLayout.LayoutParams(dp(8),1));
        foot.addView(next,new LinearLayout.LayoutParams(0,dp(50),1));
        shell.addView(foot);

        final int[] step={0};
        final String[] title={""}, steps={""}, person={""};
        final long[] due={System.currentTimeMillis()+3L*24*60*60*1000};
        final int[] remindDays={1};
        final EditText[] currentInput={null};

        Runnable render = new Runnable() {
            @Override public void run() {
                cardHost.removeAllViews();
                progress.setText((step[0]+1)+" / 5");
                back.setVisibility(step[0]==0?View.INVISIBLE:View.VISIBLE);
                next.setText(step[0]==4?"Kaydet":"Devam");

                LinearLayout card=roundedCard(CARD,BORDER,22);
                card.setPadding(dp(20),dp(22),dp(20),dp(20));

                if(step[0]==0){
                    card.addView(tv("İş başlığı",20,TEXT,true));
                    EditText e=wizardInput("Kısa ve net yaz");
                    e.setSingleLine(true);
                    e.setText(title[0]);
                    e.setSelection(e.length());
                    currentInput[0]=e;
                    addGap(card,16); card.addView(e);
                    e.requestFocus();
                } else if(step[0]==1){
                    card.addView(tv("İş adımları",20,TEXT,true));
                    EditText e=wizardInput("Yapılacak adımları sırayla yaz");
                    e.setMinLines(5);
                    e.setGravity(Gravity.TOP);
                    e.setText(steps[0]);
                    e.setSelection(e.length());
                    currentInput[0]=e;
                    addGap(card,16); card.addView(e,new LinearLayout.LayoutParams(-1,dp(170)));
                    e.requestFocus();
                } else if(step[0]==2){
                    card.addView(tv("Görev verilen kişi",20,TEXT,true));
                    EditText e=wizardInput("Ad soyad");
                    e.setSingleLine(true);
                    e.setText(person[0]);
                    e.setSelection(e.length());
                    currentInput[0]=e;
                    addGap(card,16); card.addView(e);
                    e.requestFocus();
                } else if(step[0]==3){
                    currentInput[0]=null;
                    card.addView(tv("Geri dönüş zamanı",20,TEXT,true));
                    TextView chosen=tv(formatDateLong(due[0]),28,TEXT,true);
                    chosen.setPadding(0,dp(18),0,dp(18));
                    card.addView(chosen);
                    Button choose=secondaryButton("Tarih / saat seç",v->pickDateTime(due,chosen,""));
                    card.addView(choose,new LinearLayout.LayoutParams(-1,dp(50)));
                } else {
                    currentInput[0]=null;
                    card.addView(tv("Kaç gün önce hatırlatsın?",20,TEXT,true));
                    addGap(card,18);
                    int[] vals={1,2,3,7,15};
                    LinearLayout chips=new LinearLayout(MainActivity.this);
                    chips.setOrientation(LinearLayout.HORIZONTAL);
                    chips.setGravity(Gravity.CENTER);
                    for(int val:vals){
                        TextView c=reminderChip(val,remindDays[0]==val);
                        c.setOnClickListener(v->{remindDays[0]=val;run();});
                        chips.addView(c,new LinearLayout.LayoutParams(0,dp(52),1));
                        if(val!=15){Space q=new Space(MainActivity.this);chips.addView(q,new LinearLayout.LayoutParams(dp(6),1));}
                    }
                    card.addView(chips);
                }
                cardHost.addView(card,new LinearLayout.LayoutParams(-1,-1));
            }
        };

        back.setOnClickListener(v->{
            if(step[0]>0){storeWizardInput(step[0],currentInput[0],title,steps,person);step[0]--;render.run();}
        });

        next.setOnClickListener(v->{
            if(step[0]<=2){
                String val=currentInput[0]==null?"":currentInput[0].getText().toString().trim();
                if(val.isEmpty()){toast("Bu alan boş bırakılamaz.");return;}
            }
            storeWizardInput(step[0],currentInput[0],title,steps,person);
            hideKeyboard();
            if(step[0]<4){step[0]++;render.run();return;}
            try{
                int id=nextId();
                JSONObject o=new JSONObject();
                o.put("id",id);o.put("title",title[0]);o.put("steps",steps[0]);o.put("person",person[0]);
                o.put("due",due[0]);o.put("remindDays",remindDays[0]);o.put("done",false);o.put("created",System.currentTimeMillis());
                tasks.put(o);saveData();
                scheduleTaskAlarms(o);
                dialog.dismiss();refreshCurrent();
            }catch(Exception e){toast("Kaydedilemedi.");}
        });

        dialog.setContentView(shell);
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnShowListener(x->{Window ww=dialog.getWindow();if(ww!=null)ww.setLayout(-1,-1);});
        render.run();
        dialog.show();
    }

    private void storeWizardInput(int step, EditText input, String[] title, String[] steps, String[] person){
        if(input==null)return;
        String v=input.getText().toString().trim();
        if(step==0)title[0]=v;
        else if(step==1)steps[0]=v;
        else if(step==2)person[0]=v;
    }

    private TextView reminderChip(int day, boolean selected){
        TextView c=tv(String.valueOf(day),18,selected?Color.WHITE:TEXT,true);
        c.setGravity(Gravity.CENTER);
        int normal = selected ? SAFETY : CHIP_BG;
        int pressed = selected ? SAFETY_DARK : CHIP_PRESSED;
        c.setBackground(pressStateBg(normal, pressed, selected ? SAFETY_DARK : BORDER, 16));
        makePressable(c, true);
        return c;
    }

    private void showAddFieldDialog(){
        final long[] checkAt={System.currentTimeMillis()+3L*24*60*60*1000};
        LinearLayout form=dialogForm();
        EditText branch=wizardInput("Şube adı");
        EditText note=wizardInput("Saha notu");
        note.setMinLines(3); note.setGravity(Gravity.TOP);
        EditText person=wizardInput("Görev verilen kişi");
        form.addView(branch);addGap(form,10);form.addView(note,new LinearLayout.LayoutParams(-1,dp(120)));addGap(form,10);form.addView(person);addGap(form,14);
        TextView chosen=tv(formatDateLong(checkAt[0]),18,TEXT,true);
        form.addView(chosen);addGap(form,8);
        form.addView(secondaryButton("Kontrol tarihini seç",v->pickDateTime(checkAt,chosen,"")),new LinearLayout.LayoutParams(-1,dp(48)));

        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Saha notu").setView(wrapForm(form)).setNegativeButton("Vazgeç",null).setPositiveButton("Kaydet",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String b=branch.getText().toString().trim(),n=note.getText().toString().trim(),p=person.getText().toString().trim();
            if(b.isEmpty()||n.isEmpty()||p.isEmpty()){toast("Tüm alanları doldur.");return;}
            try{
                int id=nextId();JSONObject o=new JSONObject();
                o.put("id",id);o.put("branch",b);o.put("note",n);o.put("person",p);o.put("checkAt",checkAt[0]);o.put("done",false);o.put("created",System.currentTimeMillis());
                fieldNotes.put(o);saveData();scheduleFieldAlarms(o);dlg.dismiss();refreshCurrent();
            }catch(Exception e){toast("Kaydedilemedi.");}
        }));
        dlg.show();
    }

    private void showControlCardDialog(){
        final long[] next={System.currentTimeMillis()+14L*24*60*60*1000};
        LinearLayout form=dialogForm();
        EditText branch=wizardInput("Şube adı");
        form.addView(branch);addGap(form,14);
        TextView chosen=tv(formatDateLong(next[0]),18,TEXT,true);
        form.addView(chosen);addGap(form,8);
        form.addView(secondaryButton("Sonraki kontrol tarihini seç",v->pickDateTime(next,chosen,"")),new LinearLayout.LayoutParams(-1,dp(48)));

        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Kontrol kartı").setView(wrapForm(form)).setNegativeButton("Vazgeç",null).setPositiveButton("Fotoğraf çek",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String b=branch.getText().toString().trim();
            if(b.isEmpty()){toast("Şube adı gerekli.");return;}
            pendingCardBranch=b;pendingCardNextCheck=next[0];dlg.dismiss();launchCamera();
        }));
        dlg.show();
    }

    private LinearLayout taskCard(JSONObject o, boolean compact){
        LinearLayout card=roundedCard(CARD,BORDER,20);
        boolean done=o.optBoolean("done");
        long due=o.optLong("due");
        boolean late=!done && due<System.currentTimeMillis();

        LinearLayout tagRow=new LinearLayout(this);tagRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag=smallTag(done?"TAMAMLANDI":late?"GECİKTİ":"DELEGE",done?GREEN:late?RED:BLUE);
        tagRow.addView(tag);
        Space flex=new Space(this);tagRow.addView(flex,new LinearLayout.LayoutParams(0,1,1));
        tagRow.addView(tv(formatDue(due),12,late?RED:MUTED,true));
        card.addView(tagRow);

        TextView title=tv(o.optString("title"),18,done?MUTED:TEXT,true);
        title.setPadding(0,dp(10),0,dp(5));card.addView(title);
        card.addView(tv(o.optString("person"),13,MUTED,true));

        if(!compact){
            String steps=o.optString("steps","");
            if(!steps.isEmpty()){
                TextView st=tv(steps,13,MUTED,false);st.setPadding(0,dp(10),0,0);card.addView(st);
            }
            if(!done){
                LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(14),0,0);
                Button ok=smallButton("Tamamlandı",GREEN,v->markTaskDone(o.optInt("id")));
                Button plus=smallButton("+1 gün",BLUE,v->postponeTask(o.optInt("id")));
                actions.addView(ok,new LinearLayout.LayoutParams(0,dp(42),1));
                Space s=new Space(this);actions.addView(s,new LinearLayout.LayoutParams(dp(8),1));
                actions.addView(plus,new LinearLayout.LayoutParams(0,dp(42),1));
                card.addView(actions);
            }
        }
        addMargins(card);return card;
    }

    private LinearLayout fieldCard(JSONObject o, boolean compact){
        LinearLayout card=roundedCard(CARD,BORDER,20);
        boolean done=o.optBoolean("done");
        long due=o.optLong("checkAt");
        boolean late=!done && due<System.currentTimeMillis();

        LinearLayout tagRow=new LinearLayout(this);tagRow.setGravity(Gravity.CENTER_VERTICAL);
        tagRow.addView(smallTag(done?"KAPANDI":late?"KONTROL GECİKTİ":"SAHA",done?GREEN:late?RED:AMBER));
        Space flex=new Space(this);tagRow.addView(flex,new LinearLayout.LayoutParams(0,1,1));
        tagRow.addView(tv(formatDue(due),12,late?RED:MUTED,true));
        card.addView(tagRow);

        TextView branch=tv(o.optString("branch"),12,MUTED,true);branch.setPadding(0,dp(10),0,dp(3));card.addView(branch);
        card.addView(tv(o.optString("note"),17,done?MUTED:TEXT,true));
        TextView assignee=tv(o.optString("person"),13,MUTED,true);assignee.setPadding(0,dp(6),0,0);card.addView(assignee);

        if(!compact && !done){
            LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(14),0,0);
            actions.addView(smallButton("Kontrol edildi",GREEN,v->markFieldDone(o.optInt("id"))),new LinearLayout.LayoutParams(0,dp(42),1));
            Space s=new Space(this);actions.addView(s,new LinearLayout.LayoutParams(dp(8),1));
            actions.addView(smallButton("Yarın",AMBER,v->postponeField(o.optInt("id"))),new LinearLayout.LayoutParams(0,dp(42),1));
            card.addView(actions);
        }
        addMargins(card);return card;
    }

    private LinearLayout controlCardView(JSONObject o){
        LinearLayout card=roundedCard(CARD,BORDER,20);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        String path=o.optString("photoPath");
        if(!path.isEmpty()){
            Bitmap b=BitmapFactory.decodeFile(path);
            if(b!=null){
                ImageView img=new ImageView(this);img.setImageBitmap(b);img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                img.setBackground(roundRect(SURFACE_ALT,14));img.setClipToOutline(true);
                row.addView(img,new LinearLayout.LayoutParams(dp(78),dp(78)));
                Space s=new Space(this);row.addView(s,new LinearLayout.LayoutParams(dp(12),1));
            }
        }
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
        info.addView(smallTag("KONTROL KARTI",BLUE));
        TextView bname=tv(o.optString("branch"),17,TEXT,true);bname.setPadding(0,dp(8),0,dp(4));info.addView(bname);
        info.addView(tv(formatDateLong(o.optLong("nextCheckAt")),13,MUTED,true));
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(row);addMargins(card);return card;
    }

    private void launchCamera(){
        Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if(i.resolveActivity(getPackageManager())!=null)startActivityForResult(i,REQ_CAMERA);
        else toast("Kamera uygulaması bulunamadı.");
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_CAMERA && resultCode==RESULT_OK && data!=null && data.getExtras()!=null){
            Object raw=data.getExtras().get("data");
            if(!(raw instanceof Bitmap)){toast("Fotoğraf alınamadı.");return;}
            try{
                Bitmap bmp=(Bitmap)raw;
                File f=new File(getFilesDir(),"control_"+System.currentTimeMillis()+".jpg");
                try(FileOutputStream fos=new FileOutputStream(f)){bmp.compress(Bitmap.CompressFormat.JPEG,92,fos);}
                int id=nextId();JSONObject o=new JSONObject();
                o.put("id",id);o.put("branch",pendingCardBranch);o.put("capturedAt",System.currentTimeMillis());
                o.put("nextCheckAt",pendingCardNextCheck);o.put("photoPath",f.getAbsolutePath());
                controlCards.put(o);saveData();
                AlarmUtils.schedule(this,id,pendingCardNextCheck-24L*60*60*1000,"Kontrol kartı","Yarın • "+pendingCardBranch);
                AlarmUtils.schedule(this,id+300000,pendingCardNextCheck,"Kontrol zamanı",pendingCardBranch);
                refreshCurrent();
            }catch(Exception e){toast("Fotoğraf kaydedilemedi.");}
        }
    }

    private void scheduleTaskAlarms(JSONObject o){
        int id=o.optInt("id");
        long due=o.optLong("due");
        int days=o.optInt("remindDays",1);
        AlarmUtils.schedule(this,id,due-days*24L*60*60*1000,"Dönüş yaklaşıyor",o.optString("title")+" • "+o.optString("person"));
        AlarmUtils.schedule(this,id+100000,due,"Geri dönüş zamanı",o.optString("title")+" • "+o.optString("person"));
    }

    private void scheduleFieldAlarms(JSONObject o){
        int id=o.optInt("id");
        long at=o.optLong("checkAt");
        AlarmUtils.schedule(this,id,at-24L*60*60*1000,"Saha kontrolü yarın",o.optString("branch")+" • "+o.optString("note"));
        AlarmUtils.schedule(this,id+200000,at,"Saha kontrol zamanı",o.optString("branch")+" • "+o.optString("note"));
    }

    private void markTaskDone(int id){
        for(int i=0;i<tasks.length();i++){JSONObject o=tasks.optJSONObject(i);if(o!=null&&o.optInt("id")==id){try{o.put("done",true);}catch(Exception ignored){}break;}}
        AlarmUtils.cancel(this,id);AlarmUtils.cancel(this,id+100000);saveData();refreshCurrent();
    }

    private void postponeTask(int id){
        for(int i=0;i<tasks.length();i++){
            JSONObject o=tasks.optJSONObject(i);
            if(o!=null&&o.optInt("id")==id){
                try{o.put("due",o.optLong("due")+24L*60*60*1000);scheduleTaskAlarms(o);}catch(Exception ignored){}
                break;
            }
        }
        saveData();refreshCurrent();
    }

    private void markFieldDone(int id){
        for(int i=0;i<fieldNotes.length();i++){JSONObject o=fieldNotes.optJSONObject(i);if(o!=null&&o.optInt("id")==id){try{o.put("done",true);}catch(Exception ignored){}break;}}
        AlarmUtils.cancel(this,id);AlarmUtils.cancel(this,id+200000);saveData();refreshCurrent();
    }

    private void postponeField(int id){
        for(int i=0;i<fieldNotes.length();i++){
            JSONObject o=fieldNotes.optJSONObject(i);
            if(o!=null&&o.optInt("id")==id){
                try{o.put("checkAt",System.currentTimeMillis()+24L*60*60*1000);scheduleFieldAlarms(o);}catch(Exception ignored){}
                break;
            }
        }
        saveData();refreshCurrent();
    }

    private void refreshCurrent(){
        if("home".equals(currentPage))showHome();
        else if("tasks".equals(currentPage))showTasks();
        else showField();
    }

    private void pickDateTime(long[] target,TextView label,String prefix){
        Calendar c=Calendar.getInstance();c.setTimeInMillis(target[0]);
        new DatePickerDialog(this,(v,y,m,d)->{
            Calendar cc=Calendar.getInstance();cc.setTimeInMillis(target[0]);cc.set(y,m,d);
            new TimePickerDialog(this,(tv,h,min)->{
                cc.set(Calendar.HOUR_OF_DAY,h);cc.set(Calendar.MINUTE,min);cc.set(Calendar.SECOND,0);target[0]=cc.getTimeInMillis();
                label.setText(prefix+formatDateLong(target[0]));
            },cc.get(Calendar.HOUR_OF_DAY),cc.get(Calendar.MINUTE),true).show();
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showSettingsDialog(){
        final Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell=roundedCard(CARD,BORDER,24);
        shell.setPadding(dp(20),dp(20),dp(20),dp(20));

        LinearLayout head=new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(tv("Ayarlar",24,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView close=chip("×",SURFACE_ALT,TEXT);
        close.setTextSize(19);
        close.setOnClickListener(v->dialog.dismiss());
        head.addView(close,new LinearLayout.LayoutParams(dp(40),dp(40)));
        shell.addView(head);

        TextView appearance=tv("Görünüm",14,MUTED,true);
        appearance.setPadding(0,dp(22),0,dp(10));
        shell.addView(appearance);

        String selected=prefs.getString("theme_mode","system");
        LinearLayout themes=new LinearLayout(this);
        themes.setOrientation(LinearLayout.HORIZONTAL);
        themes.setGravity(Gravity.CENTER);
        String[] labels={"Sistem","Açık","Karanlık"};
        String[] modes={"system","light","dark"};
        for(int i=0;i<modes.length;i++){
            final String mode=modes[i];
            boolean active=mode.equals(selected);
            TextView choice=themeChoice(labels[i],active);
            choice.setOnClickListener(v->{
                prefs.edit().putString("theme_mode",mode).apply();
                dialog.dismiss();
                recreate();
            });
            themes.addView(choice,new LinearLayout.LayoutParams(0,dp(48),1));
            if(i<modes.length-1){
                Space gap=new Space(this);
                themes.addView(gap,new LinearLayout.LayoutParams(dp(7),1));
            }
        }
        shell.addView(themes);

        TextView reminders=tv("Bildirimler ve hatırlatmalar",14,MUTED,true);
        reminders.setPadding(0,dp(24),0,dp(10));
        shell.addView(reminders);

        LinearLayout notifyCard=roundedCard(SURFACE_ALT,BORDER,18);
        notifyCard.setPadding(dp(14),dp(14),dp(14),dp(14));
        LinearLayout statusRow=new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.addView(tv("Hatırlatma sistemi",15,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
        boolean ready=notificationSetupComplete();
        TextView status=smallTag(ready?"HAZIR":"AYAR GEREKİYOR",ready?GREEN:AMBER);
        statusRow.addView(status);
        notifyCard.addView(statusRow);

        Button notify=primaryButton("Bildirim ve hatırlatma ayarları",v->{
            dialog.dismiss();
            openNotificationReliabilitySetup();
        });
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,dp(50));
        nlp.setMargins(0,dp(14),0,0);
        notifyCard.addView(notify,nlp);
        shell.addView(notifyCard);

        dialog.setContentView(shell);
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setDimAmount(0.48f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.setOnShowListener(x->{
            Window ww=dialog.getWindow();
            if(ww!=null){
                WindowManager.LayoutParams lp=ww.getAttributes();
                lp.width=(int)(getResources().getDisplayMetrics().widthPixels*0.92f);
                lp.height=WindowManager.LayoutParams.WRAP_CONTENT;
                ww.setAttributes(lp);
            }
        });
        dialog.show();
    }

    private TextView themeChoice(String label,boolean selected){
        TextView t=tv(label,13,selected?Color.WHITE:TEXT,true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(pressStateBg(
                selected?SAFETY:SURFACE_ALT,
                selected?SAFETY_DARK:PRESSED_BG,
                selected?SAFETY_DARK:BORDER,
                15));
        makePressable(t,true);
        return t;
    }

    private void applyPalette(){
        String mode=prefs==null?"system":prefs.getString("theme_mode","system");
        boolean systemDark=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        darkMode="dark".equals(mode) || ("system".equals(mode) && systemDark);

        if(darkMode){
            BG=Color.rgb(17,19,23);
            CARD=Color.rgb(27,30,36);
            TEXT=Color.rgb(245,247,250);
            MUTED=Color.rgb(183,190,201);
            BORDER=Color.rgb(58,64,75);
            DARK=Color.rgb(38,91,174);
            GREEN=Color.rgb(88,205,143);
            RED=Color.rgb(255,116,108);
            AMBER=Color.rgb(245,184,80);
            BLUE=Color.rgb(116,148,255);
            SAFETY=Color.rgb(71,123,220);
            SAFETY_DARK=Color.rgb(45,91,178);
            SURFACE_ALT=Color.rgb(36,40,48);
            INPUT_BG=Color.rgb(22,25,31);
            HINT=Color.rgb(139,147,159);
            PRESSED_BG=Color.rgb(51,59,73);
            CHIP_BG=Color.rgb(44,49,58);
            CHIP_PRESSED=Color.rgb(59,67,82);
            WARNING_BG=Color.rgb(51,43,25);
            WARNING_BORDER=Color.rgb(114,91,38);
        }else{
            BG=Color.rgb(247,247,244);
            CARD=Color.WHITE;
            TEXT=Color.rgb(24,24,22);
            MUTED=Color.rgb(105,105,99);
            BORDER=Color.rgb(222,224,228);
            DARK=Color.rgb(25,25,23);
            GREEN=Color.rgb(38,137,91);
            RED=Color.rgb(196,64,57);
            AMBER=Color.rgb(174,111,31);
            BLUE=Color.rgb(69,95,184);
            SAFETY=Color.rgb(38,91,174);
            SAFETY_DARK=Color.rgb(27,67,132);
            SURFACE_ALT=Color.rgb(239,240,242);
            INPUT_BG=Color.rgb(250,250,247);
            HINT=Color.rgb(139,139,132);
            PRESSED_BG=Color.rgb(228,235,248);
            CHIP_BG=Color.rgb(239,239,234);
            CHIP_PRESSED=Color.rgb(220,226,238);
            WARNING_BG=Color.rgb(252,248,235);
            WARNING_BORDER=Color.rgb(238,220,170);
        }
    }

    private void updateSystemBars(){
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController c=getWindow().getInsetsController();
            if(c!=null){
                int appearance=darkMode?0:
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(
                        appearance,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        }else if(Build.VERSION.SDK_INT>=26){
            int flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if(!darkMode) flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }else if(Build.VERSION.SDK_INT>=23){
            int flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if(!darkMode) flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void createNotificationChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"Takip hatırlatmaları",NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("İş ve saha hatırlatmaları");
            ch.enableVibration(true);
            ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private void requestNotificationPermissionIfNeeded(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIF);
    }

    private boolean notificationSetupComplete(){
        boolean post=Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
        boolean exact=true;
        if(Build.VERSION.SDK_INT>=31){
            AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
            exact=am!=null && am.canScheduleExactAlarms();
        }
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        boolean battery=pm==null || pm.isIgnoringBatteryOptimizations(getPackageName());
        return post && exact && battery;
    }

    private void openNotificationReliabilitySetup(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIF);
            return;
        }
        if(Build.VERSION.SDK_INT>=31){
            AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
            if(am!=null && !am.canScheduleExactAlarms()){
                try{
                    Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+getPackageName()));
                    startActivity(i);return;
                }catch(Exception ignored){}
            }
        }
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        if(pm!=null && !pm.isIgnoringBatteryOptimizations(getPackageName())){
            try{
                Intent i=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName()));
                startActivity(i);return;
            }catch(Exception ignored){}
        }
        toast("Bildirim izinleri hazır.");
        AlarmUtils.rescheduleAll(this);
    }

    private void loadData(){
        tasks=parseArray(prefs.getString("tasks","[]"));
        fieldNotes=parseArray(prefs.getString("fieldNotes","[]"));
        controlCards=parseArray(prefs.getString("controlCards","[]"));
    }

    private JSONArray parseArray(String s){try{return new JSONArray(s);}catch(Exception e){return new JSONArray();}}
    private void saveData(){prefs.edit().putString("tasks",tasks.toString()).putString("fieldNotes",fieldNotes.toString()).putString("controlCards",controlCards.toString()).apply();}
    private int nextId(){return (int)(System.currentTimeMillis()%900000000L);}

    private void purgeLegacyDemoData(){
        if(!prefs.getBoolean("seeded",false)) return;
        tasks=new JSONArray();fieldNotes=new JSONArray();controlCards=new JSONArray();saveData();
        prefs.edit().remove("seeded").apply();
    }

    private LinearLayout statCard(String label,int value,int color){
        LinearLayout c=roundedCard(CARD,BORDER,18);
        c.setPadding(dp(12),dp(14),dp(12),dp(12));
        c.addView(tv(String.valueOf(value),26,color,true));
        TextView l=tv(label,11,MUTED,true);l.setPadding(0,dp(3),0,0);c.addView(l);
        return c;
    }

    private LinearLayout roundedCard(int fill,int stroke,int radius){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16),dp(16),dp(16),dp(16));
        GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);c.setBackground(g);
        return c;
    }

    private void addCard(View v){addMargins(v);content.addView(v);}
    private void addMargins(View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));v.setLayoutParams(lp);}

    private TextView chip(String text,int bg,int fg){
        TextView t=tv(text,13,fg,true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(13),dp(8),dp(13),dp(8));
        t.setBackground(pressStateBg(bg, darken(bg,0.80f), darken(bg,0.72f), 18));
        makePressable(t, true);
        return t;
    }

    private TextView smallTag(String text,int color){
        TextView t=tv(text,10,color,true);t.setPadding(dp(9),dp(5),dp(9),dp(5));t.setBackground(roundRect(withAlpha(color,0.10f),12));return t;
    }

    private GradientDrawable roundRect(int color,int radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
    }

    private int withAlpha(int color,float a){return Color.argb(Math.round(255*a),Color.red(color),Color.green(color),Color.blue(color));}

    private Button primaryButton(String text,View.OnClickListener l){
        Button b=new Button(this);
        b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(15); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(pressStateBg(SAFETY, SAFETY_DARK, SAFETY_DARK, 16));
        b.setElevation(dp(4));
        b.setOnClickListener(l);
        makePressable(b, true);
        return b;
    }

    private Button secondaryButton(String text,View.OnClickListener l){
        Button b=new Button(this);
        b.setText(text); b.setTextColor(SAFETY_DARK); b.setTextSize(14); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(pressStateBg(CARD, PRESSED_BG, SAFETY, 16));
        b.setElevation(dp(2));
        b.setOnClickListener(l);
        makePressable(b, true);
        return b;
    }

    private Button smallButton(String text,int color,View.OnClickListener l){
        Button b=new Button(this);
        b.setText(text); b.setTextColor(color); b.setTextSize(12); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(pressStateBg(CARD, withAlpha(color, darkMode ? 0.24f : 0.12f), withAlpha(color,0.65f), 14));
        b.setElevation(dp(2));
        b.setOnClickListener(l);
        makePressable(b, true);
        return b;
    }

    private StateListDrawable pressStateBg(int normalColor,int pressedColor,int strokeColor,int radius){
        StateListDrawable state=new StateListDrawable();
        GradientDrawable pressed=new GradientDrawable();
        pressed.setColor(pressedColor);
        pressed.setCornerRadius(dp(radius));
        if(strokeColor!=0) pressed.setStroke(dp(2),strokeColor);

        GradientDrawable normal=new GradientDrawable();
        normal.setColor(normalColor);
        normal.setCornerRadius(dp(radius));
        if(strokeColor!=0) normal.setStroke(dp(2),strokeColor);

        state.addState(new int[]{android.R.attr.state_pressed},pressed);
        state.addState(new int[]{},normal);
        return state;
    }

    private int darken(int color,float factor){
        if(color==Color.TRANSPARENT) return PRESSED_BG;
        return Color.rgb(
                Math.max(0,Math.min(255,Math.round(Color.red(color)*factor))),
                Math.max(0,Math.min(255,Math.round(Color.green(color)*factor))),
                Math.max(0,Math.min(255,Math.round(Color.blue(color)*factor))));
    }

    private void makePressable(View v, boolean elevate){
        v.setClickable(true);
        if(elevate && Build.VERSION.SDK_INT>=21) v.setElevation(dp(2));
        v.setOnTouchListener((view,event)->{
            if(event.getAction()==MotionEvent.ACTION_DOWN){
                view.animate().scaleX(0.965f).scaleY(0.965f).alpha(0.88f).setDuration(65).start();
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }else if(event.getAction()==MotionEvent.ACTION_UP || event.getAction()==MotionEvent.ACTION_CANCEL){
                view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(95).start();
            }
            return false;
        });
    }

    private EditText wizardInput(String hint){
        EditText e=new EditText(this);e.setHint(hint);e.setTextSize(16);e.setTextColor(TEXT);e.setHintTextColor(HINT);
        e.setPadding(dp(14),dp(13),dp(14),dp(13));
        GradientDrawable g=new GradientDrawable();g.setColor(INPUT_BG);g.setCornerRadius(dp(16));g.setStroke(dp(1),BORDER);e.setBackground(g);return e;
    }

    private LinearLayout dialogForm(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(4),dp(4),dp(4),dp(8));return l;}
    private ScrollView wrapForm(LinearLayout form){ScrollView s=new ScrollView(this);s.setPadding(dp(16),0,dp(16),0);s.addView(form);return s;}

    private void sectionTitle(String s){TextView t=tv(s,15,TEXT,true);t.setPadding(0,dp(22),0,dp(10));content.addView(t);}

    private TextView emptyState(String s){
        TextView t=tv(s,14,MUTED,false);t.setGravity(Gravity.CENTER);t.setPadding(dp(16),dp(24),dp(16),dp(24));
        GradientDrawable g=new GradientDrawable();g.setColor(CARD);g.setCornerRadius(dp(20));g.setStroke(dp(1),BORDER);t.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));t.setLayoutParams(lp);return t;
    }

    private TextView tv(String s,float size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setLineSpacing(0,1.08f);return t;
    }

    private void addGap(int d){Space s=new Space(this);content.addView(s,new LinearLayout.LayoutParams(1,dp(d)));}
    private void addGap(LinearLayout parent,int d){Space s=new Space(this);parent.addView(s,new LinearLayout.LayoutParams(1,dp(d)));}

    private String formatDateLong(long ms){return new SimpleDateFormat("d MMMM EEEE  •  HH:mm",new Locale("tr","TR")).format(new Date(ms));}
    private String formatDue(long ms){
        long diff=ms-System.currentTimeMillis();
        if(diff<0)return "gecikti";
        long days=(diff+24L*60*60*1000-1)/(24L*60*60*1000);
        if(days<=1)return new SimpleDateFormat("bugün HH:mm",new Locale("tr","TR")).format(new Date(ms));
        return days+" gün";
    }

    private boolean isNarrowScreen(){return getResources().getConfiguration().screenWidthDp<380;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void hideKeyboard(){View v=getCurrentFocus();if(v!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);}
}
