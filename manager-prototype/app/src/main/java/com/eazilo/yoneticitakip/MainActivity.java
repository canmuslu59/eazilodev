package com.eazilo.yoneticitakip;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
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

    private final int BG = Color.rgb(246, 248, 252);
    private final int CARD = Color.WHITE;
    private final int TEXT = Color.rgb(27, 35, 52);
    private final int MUTED = Color.rgb(108, 119, 140);
    private final int ACCENT = Color.rgb(83, 102, 245);
    private final int GREEN = Color.rgb(45, 158, 105);
    private final int RED = Color.rgb(213, 72, 72);
    private final int ORANGE = Color.rgb(222, 139, 41);

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
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(28, 37, 56));
        getWindow().setNavigationBarColor(Color.WHITE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();
        loadData();
        purgeLegacyDemoData();
        buildShell();
        showHome();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setPadding(dp(18), dp(14), dp(18), dp(12));
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.rgb(28, 37, 56));
        TextView brand = tv("Yönetici Takip", 20, Color.WHITE, true);
        TextView privacy = tv("  •  yerel", 12, Color.rgb(171, 184, 206), false);
        header.addView(brand);
        header.addView(privacy);
        header.setMinimumHeight(dp(58));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int side = isNarrowScreen() ? 12 : 16;
        content.setPadding(dp(side), dp(16), dp(side), dp(28));
        scroller.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(4), dp(5), dp(4), dp(7));
        nav.setBackgroundColor(Color.WHITE);
        nav.setGravity(Gravity.CENTER);
        nav.setMinimumHeight(dp(62));
        nav.addView(navButton("⌂\nÖzet", "home"), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(navButton("✓\nİşler", "tasks"), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(navButton("⌖\nSaha", "field"), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(navButton("▦\nŞubeler", "branches"), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(nav);
        setContentView(root);
    }

    private TextView navButton(String label, String page) {
        TextView v = tv(label, 12, MUTED, false);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(3), dp(4), dp(3), dp(2));
        v.setOnClickListener(x -> {
            currentPage = page;
            if (page.equals("home")) showHome();
            else if (page.equals("tasks")) showTasks();
            else if (page.equals("field")) showField();
            else showBranches();
        });
        return v;
    }

    private void showHome() {
        currentPage = "home";
        content.removeAllViews();
        content.addView(tv("Bugün neye bakmalıyım?", 25, TEXT, true));
        TextView sub = tv("Açık kalan işleri, dönüşleri ve saha kontrollerini tek yerde gör.", 14, MUTED, false);
        sub.setPadding(0, dp(4), 0, dp(14));
        content.addView(sub);

        int overdueTasks = 0, todayTasks = 0, waitingField = 0, todayField = 0;
        long now = System.currentTimeMillis();
        long end = now + 24L * 60 * 60 * 1000;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject o = tasks.optJSONObject(i);
            if (o == null || o.optBoolean("done")) continue;
            long due = o.optLong("due");
            if (due < now) overdueTasks++;
            else if (due < end) todayTasks++;
        }
        for (int i = 0; i < fieldNotes.length(); i++) {
            JSONObject o = fieldNotes.optJSONObject(i);
            if (o == null || o.optBoolean("done")) continue;
            waitingField++;
            long due = o.optLong("checkAt");
            if (due <= end) todayField++;
        }

        LinearLayout row1 = new LinearLayout(this); row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(statCard("Geciken", String.valueOf(overdueTasks), RED), new LinearLayout.LayoutParams(0, -2, 1));
        Space s1 = new Space(this); row1.addView(s1, new LinearLayout.LayoutParams(dp(10), 1));
        row1.addView(statCard("Bugün dönüş", String.valueOf(todayTasks), ACCENT), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(row1);
        Space vs = new Space(this); content.addView(vs, new LinearLayout.LayoutParams(1, dp(10)));
        LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(statCard("Saha açık", String.valueOf(waitingField), ORANGE), new LinearLayout.LayoutParams(0, -2, 1));
        Space s2 = new Space(this); row2.addView(s2, new LinearLayout.LayoutParams(dp(10), 1));
        row2.addView(statCard("Bugün kontrol", String.valueOf(todayField), GREEN), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(row2);

        Space gap = new Space(this); content.addView(gap, new LinearLayout.LayoutParams(1, dp(20)));
        LinearLayout quick = new LinearLayout(this); quick.setOrientation(isNarrowScreen() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        Button quickTask = primaryButton("+ Yeni iş", v -> showAddTaskDialog());
        Button quickField = outlineButton("+ Saha notu", v -> showAddFieldDialog());
        if (isNarrowScreen()) {
            quick.addView(quickTask, new LinearLayout.LayoutParams(-1, dp(48)));
            Space qs = new Space(this); quick.addView(qs, new LinearLayout.LayoutParams(1, dp(8)));
            quick.addView(quickField, new LinearLayout.LayoutParams(-1, dp(48)));
        } else {
            quick.addView(quickTask, new LinearLayout.LayoutParams(0, dp(48), 1));
            Space qs = new Space(this); quick.addView(qs, new LinearLayout.LayoutParams(dp(10), 1));
            quick.addView(quickField, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        content.addView(quick);

        sectionTitle("Öncelikli takipler");
        ArrayList<JSONObject> upcoming = new ArrayList<>();
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject o = tasks.optJSONObject(i);
            if (o != null && !o.optBoolean("done")) upcoming.add(o);
        }
        upcoming.sort(Comparator.comparingLong(o -> o.optLong("due")));
        if (upcoming.isEmpty()) content.addView(emptyState("Açık iş yok."));
        for (int i = 0; i < Math.min(5, upcoming.size()); i++) content.addView(taskCard(upcoming.get(i), true));

        sectionTitle("Yaklaşan saha kontrolleri");
        ArrayList<JSONObject> notes = new ArrayList<>();
        for (int i = 0; i < fieldNotes.length(); i++) {
            JSONObject o = fieldNotes.optJSONObject(i);
            if (o != null && !o.optBoolean("done")) notes.add(o);
        }
        notes.sort(Comparator.comparingLong(o -> o.optLong("checkAt")));
        if (notes.isEmpty()) content.addView(emptyState("Kontrol bekleyen saha notu yok."));
        for (int i = 0; i < Math.min(3, notes.size()); i++) content.addView(fieldCard(notes.get(i), true));
    }

    private void showTasks() {
        currentPage = "tasks";
        content.removeAllViews();
        LinearLayout top = titleRow("İşler & Delegasyon", "+ Yeni iş", v -> showAddTaskDialog());
        content.addView(top);
        TextView hint = tv("Kime verdiğini, iş adımlarını ve geri dönüş zamanını kaydet. Yaklaşınca uygulama hatırlatır.", 13, MUTED, false);
        hint.setPadding(0, dp(4), 0, dp(12)); content.addView(hint);

        int active = 0;
        for (int i=0;i<tasks.length();i++) if (!tasks.optJSONObject(i).optBoolean("done")) active++;
        if (active == 0) content.addView(emptyState("Henüz açık iş yok. Yeni iş ekleyebilirsin."));

        ArrayList<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < tasks.length(); i++) list.add(tasks.optJSONObject(i));
        list.sort((a,b) -> Boolean.compare(a.optBoolean("done"), b.optBoolean("done")) != 0 ? Boolean.compare(a.optBoolean("done"), b.optBoolean("done")) : Long.compare(a.optLong("due"), b.optLong("due")));
        for (JSONObject o : list) content.addView(taskCard(o, false));
    }

    private void showField() {
        currentPage = "field";
        content.removeAllViews();
        content.addView(tv("Saha", 25, TEXT, true));
        TextView hint = tv("Şubede görülen konu → görev verilen kişi → yeniden kontrol zamanı.", 13, MUTED, false);
        hint.setPadding(0, dp(4), 0, dp(12)); content.addView(hint);

        LinearLayout row = new LinearLayout(this); row.setOrientation(isNarrowScreen() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        Button addField = primaryButton("+ Saha notu", v -> showAddFieldDialog());
        Button addCard = outlineButton("📷 Kontrol kartı", v -> showControlCardDialog());
        if (isNarrowScreen()) {
            row.addView(addField, new LinearLayout.LayoutParams(-1, dp(48)));
            Space sp = new Space(this); row.addView(sp, new LinearLayout.LayoutParams(1, dp(8)));
            row.addView(addCard, new LinearLayout.LayoutParams(-1, dp(48)));
        } else {
            row.addView(addField, new LinearLayout.LayoutParams(0, dp(48), 1));
            Space sp = new Space(this); row.addView(sp, new LinearLayout.LayoutParams(dp(10), 1));
            row.addView(addCard, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        content.addView(row);

        sectionTitle("Kontrol bekleyen saha notları");
        ArrayList<JSONObject> notes = new ArrayList<>();
        for (int i = 0; i < fieldNotes.length(); i++) {
            JSONObject o = fieldNotes.optJSONObject(i);
            if (o != null) notes.add(o);
        }
        notes.sort((a,b) -> Boolean.compare(a.optBoolean("done"), b.optBoolean("done")) != 0 ? Boolean.compare(a.optBoolean("done"), b.optBoolean("done")) : Long.compare(a.optLong("checkAt"), b.optLong("checkAt")));
        if (notes.isEmpty()) content.addView(emptyState("Saha notu yok."));
        for (JSONObject o : notes) content.addView(fieldCard(o, false));

        sectionTitle("Son kontrol kartları");
        if (controlCards.length() == 0) content.addView(emptyState("Henüz kontrol kartı fotoğraflanmadı."));
        for (int i = controlCards.length() - 1; i >= 0 && i >= controlCards.length()-4; i--) content.addView(controlCardView(controlCards.optJSONObject(i)));
    }

    private void showBranches() {
        currentPage = "branches";
        content.removeAllViews();
        content.addView(tv("Şubeler", 25, TEXT, true));
        TextView hint = tv("Her şubenin açık saha maddelerini ve fotoğraflanan kontrol kartlarını gör.", 13, MUTED, false);
        hint.setPadding(0, dp(4), 0, dp(14)); content.addView(hint);

        LinkedHashSet<String> branches = new LinkedHashSet<>();
        for (int i=0;i<fieldNotes.length();i++) branches.add(fieldNotes.optJSONObject(i).optString("branch"));
        for (int i=0;i<controlCards.length();i++) branches.add(controlCards.optJSONObject(i).optString("branch"));
        branches.remove("");
        if (branches.isEmpty()) content.addView(emptyState("Henüz şube kaydı yok. Saha notu veya kontrol kartı ekleyerek başlayabilirsin."));

        for (String branch : branches) {
            int open = 0, cards = 0; long next = Long.MAX_VALUE;
            for (int i=0;i<fieldNotes.length();i++) {
                JSONObject n = fieldNotes.optJSONObject(i);
                if (branch.equals(n.optString("branch")) && !n.optBoolean("done")) { open++; next = Math.min(next, n.optLong("checkAt")); }
            }
            for (int i=0;i<controlCards.length();i++) {
                JSONObject c = controlCards.optJSONObject(i);
                if (branch.equals(c.optString("branch"))) { cards++; next = Math.min(next, c.optLong("nextCheckAt", Long.MAX_VALUE)); }
            }
            LinearLayout card = baseCard();
            TextView t = tv(branch, 18, TEXT, true); card.addView(t);
            TextView m = tv(open + " açık saha maddesi  •  " + cards + " kontrol kartı", 13, MUTED, false); m.setPadding(0, dp(5),0,dp(3)); card.addView(m);
            if (next != Long.MAX_VALUE) card.addView(tv("Sonraki kontrol: " + formatDate(next), 13, next < System.currentTimeMillis() ? RED : ACCENT, true));
            TextView openBtn = tv("Şube detayını aç  ›", 13, ACCENT, true); openBtn.setPadding(0, dp(12), 0, 0); card.addView(openBtn);
            card.setOnClickListener(v -> showBranchDetail(branch));
            addCard(card);
        }
    }

    private LinearLayout taskCard(JSONObject o, boolean compact) {
        LinearLayout card = baseCard();
        boolean done = o.optBoolean("done");
        long due = o.optLong("due");
        boolean late = !done && due < System.currentTimeMillis();
        String status = done ? "TAMAMLANDI" : late ? "GECİKTİ" : "BEKLENİYOR";
        int statusColor = done ? GREEN : late ? RED : ACCENT;
        card.addView(badge(status, statusColor));
        TextView title = tv(o.optString("title"), compact ? 16 : 17, done ? MUTED : TEXT, true); title.setPadding(0,dp(7),0,dp(4)); card.addView(title);
        card.addView(tv("↳ " + o.optString("person") + "  •  " + formatDate(due), 13, late ? RED : MUTED, false));
        String steps = o.optString("steps", "").trim();
        if (!compact && !steps.isEmpty()) { TextView st=tv("İş adımları: " + steps, 13, MUTED, false); st.setPadding(0,dp(7),0,0); card.addView(st); }
        if (!compact && !done) {
            LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setPadding(0,dp(10),0,0);
            actions.addView(smallButton("Tamamlandı", GREEN, v -> markTaskDone(o.optInt("id"))), new LinearLayout.LayoutParams(0,dp(40),1));
            Space sp = new Space(this); actions.addView(sp,new LinearLayout.LayoutParams(dp(8),1));
            actions.addView(smallButton("+1 gün", ACCENT, v -> postponeTask(o.optInt("id"))), new LinearLayout.LayoutParams(0,dp(40),1));
            card.addView(actions);
        }
        addMargins(card);
        return card;
    }

    private LinearLayout fieldCard(JSONObject o, boolean compact) {
        LinearLayout card = baseCard();
        boolean done = o.optBoolean("done");
        long due = o.optLong("checkAt");
        boolean late = !done && due < System.currentTimeMillis();
        card.addView(badge(done ? "KAPANDI" : late ? "KONTROL GECİKTİ" : "KONTROL BEKLİYOR", done ? GREEN : late ? RED : ORANGE));
        TextView title=tv(o.optString("branch") + " • " + o.optString("note"), compact?15:16, done?MUTED:TEXT,true); title.setPadding(0,dp(7),0,dp(4)); card.addView(title);
        card.addView(tv("Görev: " + o.optString("person") + "  •  Kontrol: " + formatDate(due),13, late?RED:MUTED,false));
        if (!compact && !done) {
            LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setPadding(0,dp(10),0,0);
            actions.addView(smallButton("Kontrol edildi", GREEN, v -> markFieldDone(o.optInt("id"))), new LinearLayout.LayoutParams(0,dp(40),1));
            Space sp=new Space(this); actions.addView(sp,new LinearLayout.LayoutParams(dp(8),1));
            actions.addView(smallButton("Yarın bak", ORANGE, v -> postponeField(o.optInt("id"))), new LinearLayout.LayoutParams(0,dp(40),1));
            card.addView(actions);
        }
        addMargins(card); return card;
    }

    private LinearLayout controlCardView(JSONObject o) {
        LinearLayout card = baseCard();
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        String path = o.optString("photoPath");
        if (!path.isEmpty()) {
            Bitmap b = BitmapFactory.decodeFile(path);
            if (b != null) {
                ImageView img = new ImageView(this); img.setImageBitmap(b); img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                row.addView(img, new LinearLayout.LayoutParams(dp(82),dp(82)));
                Space s=new Space(this); row.addView(s,new LinearLayout.LayoutParams(dp(12),1));
            }
        }
        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL);
        info.addView(tv(o.optString("branch"),17,TEXT,true));
        TextView shot=tv("Fotoğraf: " + formatDate(o.optLong("capturedAt")),12,MUTED,false); shot.setPadding(0,dp(4),0,dp(2)); info.addView(shot);
        long next=o.optLong("nextCheckAt"); info.addView(tv("Sonraki kontrol: " + formatDate(next),13,next<System.currentTimeMillis()?RED:ACCENT,true));
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1)); card.addView(row); addMargins(card); return card;
    }

    private void showAddTaskDialog() {
        final long[] due = {System.currentTimeMillis() + 24L*60*60*1000};
        LinearLayout form = dialogForm();
        EditText title = input("İş başlığı");
        EditText steps = input("İş adımları (örn. sayım → rapor → fotoğraf)"); steps.setMinLines(2);
        EditText person = input("Görev verilen kişi");
        form.addView(title); form.addView(space8()); form.addView(steps); form.addView(space8()); form.addView(person); form.addView(space8());
        TextView dateLabel = formLabel("Geri dönüş zamanı: " + formatDate(due[0])); form.addView(dateLabel);
        Button dateBtn = outlineButton("Tarih / saat seç", v -> pickDateTime(due, dateLabel, "Geri dönüş zamanı: "));
        form.addView(dateBtn); form.addView(space8());

        TextView remL = formLabel("Ne kadar önce hatırlatsın?"); form.addView(remL);
        Spinner reminder = new Spinner(this); String[] rLabels={"15 dakika","30 dakika","1 saat","3 saat","1 gün"}; int[] rVals={15,30,60,180,1440}; reminder.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,rLabels)); reminder.setSelection(3); form.addView(reminder);
        TextView pL=formLabel("Öncelik"); pL.setPadding(0,dp(9),0,0); form.addView(pL);
        Spinner priority = new Spinner(this); String[] pr={"Normal","Önemli","Kritik"}; priority.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,pr)); priority.setSelection(1); form.addView(priority);

        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Yeni iş / delegasyon").setView(wrapForm(form)).setNegativeButton("Vazgeç",null)
                .setPositiveButton("Kaydet",null).create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (title.getText().toString().trim().isEmpty() || person.getText().toString().trim().isEmpty()) { toast("İş başlığı ve kişi gerekli."); return; }
            try {
                int id = nextId(); JSONObject o=new JSONObject();
                o.put("id",id); o.put("title",title.getText().toString().trim()); o.put("steps",steps.getText().toString().trim()); o.put("person",person.getText().toString().trim());
                o.put("due",due[0]); o.put("remindMin",rVals[reminder.getSelectedItemPosition()]); o.put("priority",pr[priority.getSelectedItemPosition()]); o.put("done",false); o.put("created",System.currentTimeMillis());
                tasks.put(o); saveData();
                scheduleNotification(id, due[0] - rVals[reminder.getSelectedItemPosition()]*60_000L, "Dönüş yaklaşıyor", title.getText().toString().trim() + " • " + person.getText().toString().trim());
                scheduleNotification(id+100000, due[0], "Geri dönüş zamanı geldi", title.getText().toString().trim() + " • " + person.getText().toString().trim());
                dlg.dismiss(); toast("İş kaydedildi ve hatırlatma kuruldu."); refreshCurrent();
            } catch (Exception e) { toast("Kaydedilemedi."); }
        }));
        dlg.show();
    }

    private void showAddFieldDialog() {
        final long[] checkAt = {System.currentTimeMillis()+24L*60*60*1000};
        LinearLayout form=dialogForm();
        EditText branch=input("Şube adı");
        EditText note=input("Sahada alınan not / görülen konu"); note.setMinLines(2);
        EditText person=input("Görev verilen kişi");
        form.addView(formLabel("Şube")); form.addView(branch); form.addView(space8()); form.addView(note); form.addView(space8()); form.addView(person); form.addView(space8());
        TextView dateLabel=formLabel("Yeniden kontrol: "+formatDate(checkAt[0])); form.addView(dateLabel);
        form.addView(outlineButton("Kontrol tarihini seç",v->pickDateTime(checkAt,dateLabel,"Yeniden kontrol: ")));
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Yeni saha notu").setView(wrapForm(form)).setNegativeButton("Vazgeç",null).setPositiveButton("Kaydet",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String branchName=branch.getText().toString().trim();
            if(branchName.isEmpty()||note.getText().toString().trim().isEmpty()||person.getText().toString().trim().isEmpty()){toast("Şube, not ve görev verilen kişi gerekli.");return;}
            try { int id=nextId(); JSONObject o=new JSONObject(); o.put("id",id);o.put("branch",branchName);o.put("note",note.getText().toString().trim());o.put("person",person.getText().toString().trim());o.put("checkAt",checkAt[0]);o.put("done",false);o.put("created",System.currentTimeMillis());fieldNotes.put(o);saveData();
                scheduleNotification(id,Math.max(System.currentTimeMillis()+60_000L,checkAt[0]-3L*60*60*1000),"Saha kontrolü yaklaşıyor",branchName+" • "+note.getText().toString().trim());
                scheduleNotification(id+200000,checkAt[0],"Saha kontrol zamanı",branchName+" • "+note.getText().toString().trim()); dlg.dismiss();toast("Saha notu kaydedildi.");refreshCurrent();
            }catch(Exception e){toast("Kaydedilemedi.");}
        })); dlg.show();
    }

    private void showControlCardDialog() {
        final long[] next = {System.currentTimeMillis()+14L*24*60*60*1000};
        LinearLayout form=dialogForm(); EditText branch=input("Şube adı");
        form.addView(formLabel("Şube"));form.addView(branch);form.addView(space8());TextView d=formLabel("Sonraki kontrol: "+formatDate(next[0]));form.addView(d);form.addView(outlineButton("Sonraki kontrol tarihini seç",v->pickDateTime(next,d,"Sonraki kontrol: ")));
        TextView info=tv("Devam edince kamera açılır. Çekilen kontrol kartı fotoğrafı yalnızca uygulamanın yerel alanına kaydedilir.",13,MUTED,false);info.setPadding(0,dp(12),0,0);form.addView(info);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Kontrol kartını fotoğrafla").setView(wrapForm(form)).setNegativeButton("Vazgeç",null).setPositiveButton("Kamerayı aç",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String branchName=branch.getText().toString().trim();
            if(branchName.isEmpty()){toast("Şube adı gerekli.");return;}
            pendingCardBranch=branchName;pendingCardNextCheck=next[0];dlg.dismiss();launchCamera();
        }));
        dlg.show();
    }

    private void launchCamera() {
        Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if(intent.resolveActivity(getPackageManager())!=null) startActivityForResult(intent,REQ_CAMERA); else toast("Kamera uygulaması bulunamadı.");
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_CAMERA && resultCode==RESULT_OK && data!=null && data.getExtras()!=null){
            Object raw=data.getExtras().get("data"); if(!(raw instanceof Bitmap)){toast("Fotoğraf alınamadı.");return;}
            Bitmap bmp=(Bitmap)raw; try{
                File f=new File(getFilesDir(),"control_"+System.currentTimeMillis()+".jpg"); FileOutputStream fos=new FileOutputStream(f); bmp.compress(Bitmap.CompressFormat.JPEG,90,fos);fos.close();
                int id=nextId();JSONObject o=new JSONObject();o.put("id",id);o.put("branch",pendingCardBranch);o.put("capturedAt",System.currentTimeMillis());o.put("nextCheckAt",pendingCardNextCheck);o.put("photoPath",f.getAbsolutePath());controlCards.put(o);saveData();
                scheduleNotification(id,Math.max(System.currentTimeMillis()+60_000L,pendingCardNextCheck-24L*60*60*1000),"Kontrol kartı yenileme zamanı yaklaşıyor",pendingCardBranch+" • yarın kontrol");
                toast("Kontrol kartı kaydedildi.");refreshCurrent();
            }catch(Exception e){toast("Fotoğraf kaydedilemedi.");}
        }
    }

    private void showBranchDetail(String branch) {
        LinearLayout box=dialogForm(); box.addView(tv(branch,22,TEXT,true));
        int count=0;
        for(int i=0;i<fieldNotes.length();i++){JSONObject n=fieldNotes.optJSONObject(i);if(branch.equals(n.optString("branch"))){TextView x=tv((n.optBoolean("done")?"✓ ":"• ")+n.optString("note")+"\n"+n.optString("person")+" • "+formatDate(n.optLong("checkAt")),14,n.optBoolean("done")?MUTED:TEXT,false);x.setPadding(0,dp(10),0,dp(8));box.addView(x);count++;}}
        for(int i=controlCards.length()-1;i>=0;i--){JSONObject c=controlCards.optJSONObject(i);if(branch.equals(c.optString("branch"))){box.addView(controlCardView(c));count++;}}
        if(count==0)box.addView(emptyState("Bu şubede kayıt yok."));
        new AlertDialog.Builder(this).setView(wrapForm(box)).setPositiveButton("Kapat",null).show();
    }

    private void markTaskDone(int id){for(int i=0;i<tasks.length();i++){JSONObject o=tasks.optJSONObject(i);if(o.optInt("id")==id){try{o.put("done",true);}catch(Exception ignored){}break;}}saveData();refreshCurrent();}
    private void postponeTask(int id){for(int i=0;i<tasks.length();i++){JSONObject o=tasks.optJSONObject(i);if(o.optInt("id")==id){try{long d=o.optLong("due")+24L*60*60*1000;o.put("due",d);scheduleNotification(id,d-3L*60*60*1000,"Dönüş yaklaşıyor",o.optString("title")+" • "+o.optString("person"));}catch(Exception ignored){}break;}}saveData();refreshCurrent();}
    private void markFieldDone(int id){for(int i=0;i<fieldNotes.length();i++){JSONObject o=fieldNotes.optJSONObject(i);if(o.optInt("id")==id){try{o.put("done",true);}catch(Exception ignored){}break;}}saveData();refreshCurrent();}
    private void postponeField(int id){for(int i=0;i<fieldNotes.length();i++){JSONObject o=fieldNotes.optJSONObject(i);if(o.optInt("id")==id){try{long d=System.currentTimeMillis()+24L*60*60*1000;o.put("checkAt",d);scheduleNotification(id,d-3L*60*60*1000,"Saha kontrolü yaklaşıyor",o.optString("branch")+" • "+o.optString("note"));}catch(Exception ignored){}break;}}saveData();refreshCurrent();}

    private void refreshCurrent(){ if(currentPage.equals("home"))showHome();else if(currentPage.equals("tasks"))showTasks();else if(currentPage.equals("field"))showField();else showBranches(); }

    private void pickDateTime(long[] target, TextView label, String prefix){
        Calendar c=Calendar.getInstance();c.setTimeInMillis(target[0]);
        new DatePickerDialog(this,(view,y,m,d)->{Calendar cc=Calendar.getInstance();cc.setTimeInMillis(target[0]);cc.set(y,m,d);new TimePickerDialog(this,(tv,h,min)->{cc.set(Calendar.HOUR_OF_DAY,h);cc.set(Calendar.MINUTE,min);cc.set(Calendar.SECOND,0);target[0]=cc.getTimeInMillis();label.setText(prefix+formatDate(target[0]));},cc.get(Calendar.HOUR_OF_DAY),cc.get(Calendar.MINUTE),true).show();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void createNotificationChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"Takip hatırlatmaları",NotificationManager.IMPORTANCE_HIGH);ch.setDescription("İş dönüşü ve saha kontrol hatırlatmaları");getSystemService(NotificationManager.class).createNotificationChannel(ch);}}
    private void requestNotificationPermissionIfNeeded(){if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIF);}
    private void scheduleNotification(int id,long when,String title,String body){if(when<=System.currentTimeMillis())when=System.currentTimeMillis()+5_000L;Intent i=new Intent(this,ReminderReceiver.class);i.putExtra("title",title);i.putExtra("body",body);i.putExtra("id",id);PendingIntent pi=PendingIntent.getBroadcast(this,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);}

    private void loadData(){tasks=parseArray(prefs.getString("tasks","[]"));fieldNotes=parseArray(prefs.getString("fieldNotes","[]"));controlCards=parseArray(prefs.getString("controlCards","[]"));}
    private JSONArray parseArray(String s){try{return new JSONArray(s);}catch(Exception e){return new JSONArray();}}
    private void saveData(){prefs.edit().putString("tasks",tasks.toString()).putString("fieldNotes",fieldNotes.toString()).putString("controlCards",controlCards.toString()).apply();}
    private int nextId(){return (int)(System.currentTimeMillis()%1000000000L);}

    private void purgeLegacyDemoData(){
        boolean changed=false;
        for(int i=tasks.length()-1;i>=0;i--){
            JSONObject o=tasks.optJSONObject(i); if(o==null) continue;
            String title=o.optString("title");
            if(title.equals("Haftalık stok fark raporu")||title.equals("Vitrin uygulama teyidi")){ tasks.remove(i); changed=true; }
        }
        for(int i=fieldNotes.length()-1;i>=0;i--){
            JSONObject o=fieldNotes.optJSONObject(i); if(o==null) continue;
            if(o.optString("branch").equals("Kanyon") && o.optString("note").equals("Depo düzeni yeniden kontrol edilecek")){ fieldNotes.remove(i); changed=true; }
        }
        if(changed) saveData();
        prefs.edit().remove("seeded").apply();
    }

    private LinearLayout statCard(String label,String value,int color){LinearLayout c=baseCard();c.setGravity(Gravity.CENTER_VERTICAL);c.setMinimumHeight(dp(96));TextView v=tv(value,isNarrowScreen()?27:31,color,true);c.addView(v);TextView l=tv(label,isNarrowScreen()?12:13,MUTED,true);l.setPadding(0,dp(3),0,0);c.addView(l);return c;}
    private LinearLayout baseCard(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(14),dp(15),dp(14));c.setBackground(roundRect(CARD,16));return c;}
    private void addCard(View v){addMargins(v);content.addView(v);}
    private void addMargins(View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));v.setLayoutParams(lp);}
    private TextView badge(String s,int color){TextView b=tv(s,10,color,true);b.setPadding(dp(9),dp(5),dp(9),dp(5));b.setBackground(roundRect(adjustAlpha(color,0.12f),12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);b.setLayoutParams(lp);return b;}
    private int adjustAlpha(int color,float factor){return Color.argb(Math.round(255*factor),Color.red(color),Color.green(color),Color.blue(color));}
    private GradientDrawable roundRect(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp((int)radius));return g;}
    private GradientDrawable outlineBg(int stroke){GradientDrawable g=new GradientDrawable();g.setColor(Color.WHITE);g.setCornerRadius(dp(13));g.setStroke(dp(1),stroke);return g;}
    private Button primaryButton(String text,View.OnClickListener l){Button b=new Button(this);b.setText(text);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setAllCaps(false);b.setBackground(roundRect(ACCENT,13));b.setOnClickListener(l);return b;}
    private Button outlineButton(String text,View.OnClickListener l){Button b=new Button(this);b.setText(text);b.setTextColor(ACCENT);b.setTextSize(13);b.setAllCaps(false);b.setBackground(outlineBg(Color.rgb(211,216,232)));b.setOnClickListener(l);return b;}
    private Button smallButton(String text,int color,View.OnClickListener l){Button b=new Button(this);b.setText(text);b.setTextColor(color);b.setTextSize(12);b.setAllCaps(false);b.setBackground(outlineBg(adjustAlpha(color,0.55f)));b.setOnClickListener(l);return b;}
    private LinearLayout titleRow(String title,String button,View.OnClickListener listener){LinearLayout row=new LinearLayout(this);row.setOrientation(isNarrowScreen()?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);row.setGravity(isNarrowScreen()?Gravity.START:Gravity.CENTER_VERTICAL);TextView t=tv(title,isNarrowScreen()?22:25,TEXT,true);Button b=primaryButton(button,listener);if(isNarrowScreen()){row.addView(t,new LinearLayout.LayoutParams(-1,-2));Space s=new Space(this);row.addView(s,new LinearLayout.LayoutParams(1,dp(8)));row.addView(b,new LinearLayout.LayoutParams(-1,dp(44)));}else{row.addView(t,new LinearLayout.LayoutParams(0,-2,1));row.addView(b,new LinearLayout.LayoutParams(-2,dp(44)));}return row;}
    private void sectionTitle(String s){TextView t=tv(s,16,TEXT,true);t.setPadding(0,dp(20),0,dp(9));content.addView(t);}
    private TextView emptyState(String s){TextView t=tv(s,14,MUTED,false);t.setGravity(Gravity.CENTER);t.setPadding(dp(15),dp(18),dp(15),dp(18));t.setBackground(roundRect(Color.WHITE,15));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));t.setLayoutParams(lp);return t;}
    private LinearLayout dialogForm(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(4),dp(4),dp(4),dp(6));return l;}
    private ScrollView wrapForm(LinearLayout form){ScrollView s=new ScrollView(this);s.setPadding(dp(16),0,dp(16),0);s.addView(form);return s;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(14);e.setTextColor(TEXT);e.setHintTextColor(Color.rgb(150,158,174));e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(outlineBg(Color.rgb(215,220,232)));return e;}
    private TextView formLabel(String s){TextView t=tv(s,13,TEXT,true);t.setPadding(0,0,0,dp(6));return t;}
    private Space space8(){Space s=new Space(this);s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(8)));return s;}
    private TextView tv(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setLineSpacing(0,1.05f);return t;}
    private String formatDate(long ms){SimpleDateFormat f=new SimpleDateFormat("dd MMM, HH:mm",new Locale("tr","TR"));return f.format(new Date(ms));}
    private boolean isNarrowScreen(){return getResources().getConfiguration().screenWidthDp < 380;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
