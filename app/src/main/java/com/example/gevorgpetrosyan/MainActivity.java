package com.example.gevorgpetrosyan;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.firebase.auth.FirebaseAuth; 
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private AppDatabase db;
    private ViewPager2 viewPager;
    private DrawerLayout drawerLayout;
    private final List<String> tempTimes = new ArrayList<>();
    private final List<Medicine> tempMedsToSchedule = new ArrayList<>();
    private final String BLUE_COLOR = "#2196F3";
    private String tempExpiryInternal = "Not Set";
    private SpeechRecognizer speechRecognizer;
    private ImageView micIconRef;
    
    // Success Animation UI
    private FrameLayout successOverlay;
    private View successContent;
    private ImageView successCheckmark;
    private TextView tvSuccessMsg;
    
    // Voice Logging Dialog UI components
    private AlertDialog voiceDialog;
    private VoiceVisualizerView visualizer;
    private LinearLayout detectedMedsContainer;
    private final Set<Medicine> detectedMeds = new HashSet<>();
    private boolean isListening = false;
    private boolean voiceDialogShowing = false;
    private boolean isRussian = false;

    private SectionsPagerAdapter sectionsPagerAdapter;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();
        loadLanguagePreference();
        super.onCreate(savedInstanceState);
        
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        currentUserId = user.getUid();
        
        setContentView(R.layout.activity_main);

        requestNotificationPermission();

        db = AppDatabase.getInstance(this);
        viewPager = findViewById(R.id.view_pager);
        drawerLayout = findViewById(R.id.drawer_layout);
        
        // Init Success Overlay
        successOverlay = findViewById(R.id.success_overlay);
        successContent = findViewById(R.id.success_content);
        successCheckmark = findViewById(R.id.success_checkmark);
        tvSuccessMsg = findViewById(R.id.tv_success_msg);

        sectionsPagerAdapter = new SectionsPagerAdapter();
        viewPager.setAdapter(sectionsPagerAdapter);

        updateStaticUI();

        // Check if triggered from widget
        if (getIntent().getBooleanExtra("trigger_voice", false)) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // We need to wait a bit for UI to be ready
                startVoiceRecognition(null);
            }, 500);
        }

        // --- Bottom Navigation Listeners ---
        findViewById(R.id.nav_list).setOnClickListener(v -> viewPager.setCurrentItem(0));
        findViewById(R.id.nav_stats).setOnClickListener(v -> viewPager.setCurrentItem(1));
        findViewById(R.id.nav_add).setOnClickListener(v -> showRegisterMedicineDialog());
        findViewById(R.id.nav_inventory).setOnClickListener(v -> viewPager.setCurrentItem(2));
        findViewById(R.id.nav_dashboard).setOnClickListener(v -> viewPager.setCurrentItem(3));

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateBottomNavSelection(position);
            }
        });

        // --- Settings Drawer Listeners ---
        findViewById(R.id.btn_profile).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            startActivity(new Intent(this, ProfileActivity.class));
        });

        findViewById(R.id.btn_theme_toggle).setOnClickListener(v -> {
            SharedPreferences pref = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
            boolean isDark = pref.getBoolean("IsDarkMode", false);
            AppCompatDelegate.setDefaultNightMode(isDark ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
            pref.edit().putBoolean("IsDarkMode", !isDark).apply();
            drawerLayout.closeDrawers();
        });

        findViewById(R.id.btn_language).setOnClickListener(v -> {
            isRussian = !isRussian;
            getSharedPreferences("LangPrefs", MODE_PRIVATE).edit().putBoolean("IsRussian", isRussian).apply();
            updateStaticUI();
            refreshCurrentTab();
            drawerLayout.closeDrawers();
        });

        findViewById(R.id.btn_add_widget).setOnClickListener(v -> {
            Intent intent = new Intent(this, WidgetConfigActivity.class);
            startActivity(intent);
            drawerLayout.closeDrawers();
        });

        findViewById(R.id.btn_logout).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 102);
            }
        }
    }

    private void loadLanguagePreference() {
        isRussian = getSharedPreferences("LangPrefs", MODE_PRIVATE).getBoolean("IsRussian", false);
    }

    private String tr(String en, String ru) {
        return isRussian ? ru : en;
    }

    private void updateStaticUI() {
        ((TextView) findViewById(R.id.tv_nav_list)).setText(tr("List", "Список"));
        ((TextView) findViewById(R.id.tv_nav_stats)).setText(tr("Stats", "История"));
        ((TextView) findViewById(R.id.tv_nav_inv)).setText(tr("Inv", "Склад"));
        ((TextView) findViewById(R.id.tv_nav_dash)).setText(tr("Dash", "Панель"));

        ((TextView) findViewById(R.id.tv_settings_header)).setText(tr("SETTINGS", "НАСТРОЙКИ"));
        ((MaterialButton) findViewById(R.id.btn_profile)).setText(tr("My Profile", "Мой профиль"));
        ((MaterialButton) findViewById(R.id.btn_theme_toggle)).setText(tr("Switch Light/Dark Mode", "Сменить тему"));
        ((MaterialButton) findViewById(R.id.btn_language)).setText(tr("Language", "Язык (RU)"));
        ((MaterialButton) findViewById(R.id.btn_add_widget)).setText(tr("Add widget", "Добавить виджет"));
        ((MaterialButton) findViewById(R.id.btn_logout)).setText(tr("Log Out", "Выйти"));
        
        if (tvSuccessMsg != null) tvSuccessMsg.setText(tr("Logged!", "Отмечено!"));
    }

    private void updateBottomNavSelection(int position) {
        int[] ids = {R.id.nav_list, R.id.nav_stats, 0, R.id.nav_inventory, R.id.nav_dashboard};
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == 0) continue;
            View v = findViewById(ids[i]);
            float alpha = (i == (position < 2 ? position : position + 1)) ? 1.0f : 0.5f;
            v.setAlpha(alpha);
        }
    }

    private class SectionsPagerAdapter extends RecyclerView.Adapter<SectionsPagerAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ScrollView scrollView = new ScrollView(parent.getContext());
            scrollView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            scrollView.setFillViewport(true);
            return new ViewHolder(scrollView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            refreshPageContent((ScrollView) holder.itemView, position);
        }

        @Override
        public int getItemCount() {
            return 4; // List, Stats, Inventory, Dashboard
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    private void refreshPageContent(ScrollView scrollView, int position) {
        scrollView.removeAllViews();
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Medicine> meds = db.medicineDao().getAllByUserId(currentUserId);
            runOnUiThread(() -> {
                LinearLayout layout = createBaseLayout();
                switch (position) {
                    case 0: populateList(layout, meds); break;
                    case 1: populateStats(layout, meds); break;
                    case 2: populateInventory(layout, meds); break;
                    case 3: populateDashboard(layout, meds); break;
                }
                scrollView.addView(layout);
            });
        });
    }

    private void populateList(LinearLayout layout, List<Medicine> meds) {
        layout.addView(createHeaderWithMenu(tr("Medicine Reminders", "График приема")));
        
        MaterialButton btnAdd = createActionButton(tr("+ Schedule New Dose", "+ Добавить прием"));
        btnAdd.setOnClickListener(v -> { tempTimes.clear(); tempMedsToSchedule.clear(); showScheduleDoseDialog(); });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, -2);
        btnLp.setMargins(0, 0, 0, 40);
        btnAdd.setLayoutParams(btnLp);
        layout.addView(btnAdd);

        boolean hasSchedules = false;
        for (Medicine m : meds) {
            if (m.times != null && !m.times.isEmpty()) {
                layout.addView(createExpandableMedCard(m));
                hasSchedules = true;
            }
        }

        if (!hasSchedules) {
            TextView empty = new TextView(this);
            empty.setText(tr("No reminders set yet.", "Напоминания еще не установлены."));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 100, 0, 0);
            empty.setAlpha(0.6f);
            empty.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
            layout.addView(empty);
        }
    }

    private void populateStats(LinearLayout layout, List<Medicine> meds) {
        layout.addView(createHeaderWithMenu(tr("Usage History", "История использования")));
        
        boolean hasHistory = false;
        for (Medicine m : meds) {
            int taken = 0;
            if (m.history != null && !m.history.isEmpty()) {
                taken = m.history.split(",").length / 2;
            }
            
            if (taken > 0) {
                View card = createStatsCard(m, taken);
                layout.addView(card);
                hasHistory = true;
            }
        }

        if (!hasHistory) {
            TextView empty = new TextView(this);
            empty.setText(tr("No usage history available.", "История использования отсутствует."));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 100, 0, 0);
            empty.setAlpha(0.6f);
            empty.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
            layout.addView(empty);
        }
    }

    private void populateInventory(LinearLayout layout, List<Medicine> meds) {
        layout.addView(createHeaderWithMenu(tr("Warehouse Stock", "Запас лекарств")));
        
        MaterialButton btnAdd = createActionButton(tr("+ Register Medicine", "+ Добавить лекарство"));
        btnAdd.setOnClickListener(v -> showRegisterMedicineDialog());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, -2);
        btnLp.setMargins(0, 0, 0, 40);
        btnAdd.setLayoutParams(btnLp);
        layout.addView(btnAdd);

        if (meds.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(tr("Your warehouse is empty.", "Ваш склад пуст."));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 100, 0, 0);
            empty.setAlpha(0.6f);
            empty.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
            layout.addView(empty);
        } else {
            for (Medicine m : meds) {
                layout.addView(createInventoryCard(m));
            }
        }
    }

    private View createInventoryCard(Medicine m) {
        int total = calculateTotalStock(m.batches);
        boolean isExpired = isMedicineExpired(m.batches);
        
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.card_background);
        card.setPadding(45, 40, 45, 40);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 10, 0, 20);
        card.setLayoutParams(lp);
        card.setClickable(true);
        card.setFocusable(true);
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            card.setForeground(getDrawable(outValue.resourceId));
        }

        // Status Icon
        ImageView ivStatus = new ImageView(this);
        int iconRes = android.R.drawable.ic_dialog_info;
        int iconColor = Color.parseColor(BLUE_COLOR);
        
        if (total <= 0) {
            iconRes = android.R.drawable.ic_dialog_alert;
            iconColor = Color.parseColor("#F44336");
        } else if (isExpired) {
            iconRes = android.R.drawable.ic_menu_today;
            iconColor = Color.parseColor("#FF9800");
        }
        
        ivStatus.setImageResource(iconRes);
        ivStatus.setColorFilter(iconColor);
        LinearLayout.LayoutParams iconP = new LinearLayout.LayoutParams(70, 70);
        iconP.setMargins(0, 0, 35, 0);
        ivStatus.setLayoutParams(iconP);
        card.addView(ivStatus);

        // Text Info
        LinearLayout textInfo = new LinearLayout(this);
        textInfo.setOrientation(LinearLayout.VERTICAL);
        textInfo.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView tvName = new TextView(this);
        tvName.setText(m.name);
        tvName.setTextSize(18);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
        textInfo.addView(tvName);

        TextView tvStock = new TextView(this);
        tvStock.setText(tr("Stock: ", "Запас: ") + total + " " + tr("pills", "таб."));
        tvStock.setTextSize(14);
        tvStock.setTextColor(getThemeColor(R.attr.secondaryTextColor));
        textInfo.addView(tvStock);
        
        if (isExpired) {
            TextView tvExp = new TextView(this);
            tvExp.setText(tr("Some batches expired!", "Есть просроченные партии!"));
            tvExp.setTextSize(12);
            tvExp.setTextColor(Color.parseColor("#F44336"));
            tvExp.setTypeface(null, Typeface.ITALIC);
            textInfo.addView(tvExp);
        }

        card.addView(textInfo);

        // Arrow
        ImageView ivArrow = new ImageView(this);
        ivArrow.setImageResource(androidx.appcompat.R.drawable.abc_ic_arrow_drop_right_black_24dp);
        ivArrow.setColorFilter(getThemeColor(R.attr.secondaryTextColor));
        card.addView(ivArrow);

        card.setOnClickListener(v -> showBatchEditMenu(m));
        return card;
    }

    private void populateDashboard(LinearLayout layout, List<Medicine> meds) {
        layout.addView(createHeaderWithMenu(tr("Dashboard", "Панель")));

        LinearLayout dashCard = new LinearLayout(this);
        dashCard.setOrientation(LinearLayout.HORIZONTAL);
        dashCard.setBackgroundResource(R.drawable.banner_background);
        dashCard.setPadding(40, 60, 40, 60);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, 0, 0, 40);
        dashCard.setLayoutParams(cardParams);

        LinearLayout leftPart = new LinearLayout(this);
        leftPart.setOrientation(LinearLayout.VERTICAL);
        leftPart.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1.8f));
        leftPart.setGravity(Gravity.CENTER);

        TextView tvTime = new TextView(this);
        tvTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        tvTime.setTextSize(60);
        tvTime.setTextColor(Color.WHITE);
        tvTime.setTypeface(null, Typeface.BOLD);
        leftPart.addView(tvTime);

        LinearLayout weekGrid = new LinearLayout(this);
        weekGrid.setOrientation(LinearLayout.HORIZONTAL);
        weekGrid.setGravity(Gravity.CENTER);
        String[] dayLettersEn = {"S", "M", "T", "W", "T", "F", "S"};
        String[] dayLettersRu = {"В", "П", "В", "С", "Ч", "П", "С"};
        String[] dayLetters = isRussian ? dayLettersRu : dayLettersEn;
        int currentDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);

        for (int i = 1; i <= 7; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

            TextView tvLetter = new TextView(this);
            tvLetter.setText(dayLetters[i - 1]);
            tvLetter.setTextSize(12);
            tvLetter.setGravity(Gravity.CENTER);
            tvLetter.setTextColor(i == currentDay ? Color.WHITE : Color.parseColor("#A0FFFFFF"));
            tvLetter.setTypeface(null, i == currentDay ? Typeface.BOLD : Typeface.NORMAL);
            col.addView(tvLetter);

            View dot = new View(this);
            int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
            LinearLayout.LayoutParams dotP = new LinearLayout.LayoutParams(size, size);
            dotP.setMargins(0, 10, 0, 0);
            dot.setLayoutParams(dotP);
            dot.setBackgroundResource(R.drawable.ok_background);
            if (i == currentDay) dot.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            else dot.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#40FFFFFF")));
            col.addView(dot);

            weekGrid.addView(col);
        }
        leftPart.addView(weekGrid);

        LinearLayout rightPart = new LinearLayout(this);
        rightPart.setOrientation(LinearLayout.VERTICAL);
        rightPart.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));

        String globalNext = getNextUpcomingDose(meds);

        FrameLayout topBox = new FrameLayout(this);
        topBox.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1.2f));
        topBox.setBackgroundResource(R.drawable.nav_pill_background);
        topBox.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#40FFFFFF")));
        TextView tvNextDose = new TextView(this);
        tvNextDose.setText(globalNext);
        tvNextDose.setTextColor(Color.WHITE);
        tvNextDose.setTextSize(22);
        tvNextDose.setTypeface(null, Typeface.BOLD);
        tvNextDose.setGravity(Gravity.CENTER);
        topBox.addView(tvNextDose);
        rightPart.addView(topBox);

        FrameLayout botBox = new FrameLayout(this);
        botBox.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        botBox.setClickable(true);
        botBox.setFocusable(true);
        ImageView mic = new ImageView(this);
        mic.setImageResource(android.R.drawable.ic_btn_speak_now);
        mic.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams micP = new FrameLayout.LayoutParams(80, 80);
        micP.gravity = Gravity.CENTER;
        mic.setLayoutParams(micP);
        botBox.addView(mic);
        botBox.setOnClickListener(v -> startVoiceRecognition(mic));
        rightPart.addView(botBox);

        dashCard.addView(leftPart);
        dashCard.addView(rightPart);
        layout.addView(dashCard);

        for (Medicine m : meds) {
            if (m.times != null && !m.times.isEmpty()) {
                String medNext = getNextDoseForMed(m);
                MaterialButton btn = new MaterialButton(this);
                btn.setText(m.name + " (" + medNext + ") ▶");
                btn.setAllCaps(false);
                btn.setCornerRadius(20);
                
                boolean isNearest = medNext.equals(globalNext) && !globalNext.equals("--:--");
                if (isNearest) {
                    btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(BLUE_COLOR)));
                    btn.setTextColor(Color.WHITE);
                } else {
                    btn.setBackgroundTintList(ColorStateList.valueOf(getThemeColor(R.attr.cardBackgroundColor)));
                    btn.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
                    btn.setStrokeWidth(2);
                    btn.setStrokeColor(ColorStateList.valueOf(getThemeColor(R.attr.cardStrokeColor)));
                }
                
                btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                btn.setPadding(40, 30, 40, 30);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
                p.setMargins(0, 10, 0, 10);
                btn.setLayoutParams(p);
                btn.setOnClickListener(v -> logManualIntake(m));
                layout.addView(btn);
            }
        }
    }

    private int getThemeColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private void initSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                isListening = true;
                if (micIconRef != null) {
                    AlphaAnimation pulse = new AlphaAnimation(1.0f, 0.2f);
                    pulse.setDuration(400); pulse.setRepeatCount(Animation.INFINITE);
                    pulse.setRepeatMode(Animation.REVERSE); micIconRef.startAnimation(pulse);
                }
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {
                if (visualizer != null) {
                    runOnUiThread(() -> visualizer.updateRms(rmsdB));
                }
            }
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { 
                isListening = false;
                if (micIconRef != null) micIconRef.clearAnimation();
                if (voiceDialogShowing) {
                    restartListening();
                }
            }
            @Override public void onError(int error) { 
                isListening = false;
                if (micIconRef != null) micIconRef.clearAnimation();
                if (voiceDialogShowing && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == 5)) {
                    restartListening();
                } else if (error != 5) {
                   // Toast.makeText(MainActivity.this, "Voice error: " + error, Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) processVoiceCommand(matches);
                if (voiceDialogShowing) restartListening();
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) processVoiceCommand(matches);
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void restartListening() {
        if (!voiceDialogShowing) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isRussian ? "ru-RU" : Locale.getDefault().toString());
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                speechRecognizer.startListening(intent);
            } catch (Exception ignored) {}
        }, 100);
    }

    private void applySavedTheme() {
        SharedPreferences pref = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
        AppCompatDelegate.setDefaultNightMode(pref.getBoolean("IsDarkMode", false) ?
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void showRegisterMedicineDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(tr("Register New Medicine", "Регистрация лекарства"));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 20);

        final EditText nameIn = new EditText(this); nameIn.setHint(tr("Medicine Name", "Название лекарства"));
        final EditText stockIn = new EditText(this); stockIn.setHint(tr("Initial Stock", "Начальный запас")); stockIn.setInputType(2);
        final EditText warnIn = new EditText(this); warnIn.setHint(tr("Warning Days (Expiry)", "Предупредить за (дней до конца срока)")); warnIn.setInputType(2);

        final MaterialButton expBtn = createActionButton(tr("Set Expiry Date", "Установить срок годности"));
        expBtn.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, y, m, d) -> {
                tempExpiryInternal = d + "/" + (m + 1) + "/" + y;
                expBtn.setText(tr("Exp: ", "Срок: ") + tempExpiryInternal);
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 20, 0, 20);
        nameIn.setLayoutParams(lp); stockIn.setLayoutParams(lp); warnIn.setLayoutParams(lp); expBtn.setLayoutParams(lp);

        layout.addView(nameIn); layout.addView(stockIn); layout.addView(warnIn); layout.addView(expBtn);
        builder.setView(layout);
        builder.setPositiveButton(tr("Add to Inventory", "Добавить"), (d, w) -> {
            String name = nameIn.getText().toString().trim();
            String stockStr = stockIn.getText().toString();
            String warnStr = warnIn.getText().toString();
            int stock = Integer.parseInt(stockStr.isEmpty() ? "0" : stockStr);
            int warn = Integer.parseInt(warnStr.isEmpty() ? "0" : warnStr);
            if (!name.isEmpty()) saveToWarehouse(name, stock, tempExpiryInternal, warn);
        });
        builder.setNegativeButton(tr("Cancel", "Отмена"), null).show();
    }

    private void saveToWarehouse(String name, int stock, String exp, int warn) {
        Executors.newSingleThreadExecutor().execute(() -> {
            Medicine existing = db.medicineDao().getByNameAndUserId(name, currentUserId);
            String batch = stock + " pills (Exp: " + exp + ")";
            if (existing != null) {
                existing.batches = (existing.batches == null || existing.batches.isEmpty()) ? batch : existing.batches + "|" + batch;
                existing.expiryWarningDays = warn;
                existing.lastUpdated = System.currentTimeMillis();
                db.medicineDao().update(existing);
            } else {
                Medicine m = new Medicine(currentUserId, name, "");
                m.batches = batch; m.expiryWarningDays = warn;
                db.medicineDao().insert(m);
            }
            runOnUiThread(() -> { 
                refreshCurrentTab(); 
                updateWidget();
                Toast.makeText(this, tr("Warehouse Updated", "Склад обновлен"), Toast.LENGTH_SHORT).show(); 
            });
        });
    }

    private String getNextDoseForMed(Medicine m) {
        if (m.times == null || m.times.isEmpty()) return "--:--";
        Calendar now = Calendar.getInstance();
        int nowTotal = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        String closest = "--:--";
        int minDiff = Integer.MAX_VALUE;
        for (String t : m.times.split(",")) {
            try {
                String[] p = t.trim().split(":");
                int total = Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
                int diff = total - nowTotal;
                if (diff < 0) diff += 1440;
                if (diff < minDiff) { minDiff = diff; closest = t.trim(); }
            } catch (Exception ignored) {}
        }
        return closest;
    }

    private void startVoiceRecognition(ImageView micIcon) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                return;
            }
        }
        
        if (isListening) return;

        this.micIconRef = micIcon;
        detectedMeds.clear();
        voiceDialogShowing = true;
        
        // Setup Dialog
        View v = getLayoutInflater().inflate(R.layout.dialog_voice_log, null);
        visualizer = v.findViewById(R.id.voice_visualizer);
        detectedMedsContainer = v.findViewById(R.id.detected_meds_container);
        MaterialButton btnCancel = v.findViewById(R.id.btn_voice_cancel);
        MaterialButton btnOk = v.findViewById(R.id.btn_voice_ok);
        TextView tvStatus = v.findViewById(R.id.tv_voice_status);
        TextView tvDetectedTitle = v.findViewById(R.id.tv_detected_title);
        
        if (tvStatus != null) tvStatus.setText(tr("Listening for Medicine Names...", "Слушаю названия лекарств..."));
        if (tvDetectedTitle != null) tvDetectedTitle.setText(tr("Detected Medicines:", "Обнаруженные лекарства:"));
        btnCancel.setText(tr("Cancel", "Отмена"));
        btnOk.setText(tr("OK", "Готово"));

        voiceDialog = new AlertDialog.Builder(this)
                .setView(v)
                .setCancelable(false)
                .create();
        
        btnCancel.setOnClickListener(view -> {
            voiceDialogShowing = false;
            if (speechRecognizer != null) speechRecognizer.cancel();
            isListening = false;
            voiceDialog.dismiss();
        });
        
        btnOk.setOnClickListener(view -> {
            voiceDialogShowing = false;
            if (speechRecognizer != null) speechRecognizer.stopListening();
            isListening = false;
            for (Medicine med : detectedMeds) {
                logManualIntake(med);
            }
            voiceDialog.dismiss();
        });
        
        voiceDialog.show();
        
        initSpeechRecognizer();
        
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isRussian ? "ru-RU" : Locale.getDefault().toString());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                speechRecognizer.startListening(intent);
            } catch (Exception e) {
                isListening = false;
            }
        }, 100);
    }

    private void processVoiceCommand(List<String> matches) {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Medicine> meds = db.medicineDao().getAllByUserId(currentUserId);
            boolean addedNew = false;

            for (String result : matches) {
                String resultLower = result.toLowerCase();
                for (Medicine m : meds) {
                    String medNameLower = m.name.toLowerCase();
                    if (resultLower.contains(medNameLower)) {
                        if (!detectedMeds.contains(m)) {
                            detectedMeds.add(m);
                            addedNew = true;
                        }
                    }
                }
            }

            if (addedNew) {
                runOnUiThread(this::updateDetectedMedsUI);
            }
        });
    }
    
    private void updateDetectedMedsUI() {
        if (detectedMedsContainer == null) return;
        detectedMedsContainer.removeAllViews();
        for (Medicine m : detectedMeds) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(35, 25, 35, 25);
            row.setBackgroundResource(R.drawable.nav_pill_background);
            row.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F5F5F5")));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 10, 0, 10);
            row.setLayoutParams(lp);

            TextView tv = new TextView(this);
            tv.setText(m.name);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            tv.setTextColor(Color.BLACK);
            tv.setTextSize(17);
            tv.setTypeface(null, Typeface.BOLD);
            
            ImageView ivRemove = new ImageView(this);
            ivRemove.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            ivRemove.setColorFilter(Color.RED);
            ivRemove.setPadding(10, 10, 10, 10);
            ivRemove.setOnClickListener(v -> {
                detectedMeds.remove(m);
                updateDetectedMedsUI();
            });

            row.addView(tv);
            row.addView(ivRemove);
            detectedMedsContainer.addView(row);
            
            AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
            anim.setDuration(400);
            row.startAnimation(anim);
        }
    }

    private String getNextUpcomingDose(List<Medicine> meds) {
        Calendar now = Calendar.getInstance();
        int nowTotal = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        String closest = "--:--";
        int minDiff = Integer.MAX_VALUE;
        for (Medicine m : meds) {
            if (m.times == null) continue;
            for (String t : m.times.split(",")) {
                try {
                    String[] p = t.trim().split(":");
                    int total = Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
                    int diff = total - nowTotal;
                    if (diff < 0) diff += 1440;
                    if (diff < minDiff) { minDiff = diff; closest = t.trim(); }
                } catch (Exception ignored) {}
            }
        }
        return closest;
    }

    private View createHeaderWithMenu(String text) {
        FrameLayout header = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 10, 0, 40); header.setLayoutParams(lp);
        header.setBackgroundResource(R.drawable.banner_background);
        TextView tvTitle = new TextView(this);
        tvTitle.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tvTitle.setText(text.toUpperCase(Locale.getDefault())); tvTitle.setTextSize(16);
        tvTitle.setTypeface(null, Typeface.BOLD); tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(20, 30, 20, 30); tvTitle.setTextColor(Color.WHITE);
        header.addView(tvTitle);
        TextView tvMenu = new TextView(this);
        tvMenu.setText("≡"); tvMenu.setTextColor(Color.WHITE); tvMenu.setTextSize(30);
        tvMenu.setTypeface(null, Typeface.BOLD); tvMenu.setGravity(Gravity.CENTER);
        tvMenu.setPadding(45, 0, 45, 0);
        FrameLayout.LayoutParams menuLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        menuLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL; tvMenu.setLayoutParams(menuLp);
        tvMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        tvMenu.setBackgroundResource(outValue.resourceId);
        header.addView(tvMenu);
        return header;
    }

    private View createExpandableMedCard(Medicine m) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.card_background);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(-1, -2);
        containerLp.setMargins(0, 10, 0, 20);
        container.setLayoutParams(containerLp);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(45, 45, 45, 45);
        header.setClickable(true);
        header.setFocusable(true);
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        header.setBackgroundResource(outValue.resourceId);

        TextView tvName = new TextView(this);
        tvName.setText(m.name);
        tvName.setTextSize(18);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView ivArrow = new ImageView(this);
        ivArrow.setImageResource(androidx.appcompat.R.drawable.abc_ic_arrow_drop_right_black_24dp);
        ivArrow.setColorFilter(getThemeColor(R.attr.secondaryTextColor));

        header.addView(tvName);
        header.addView(ivArrow);

        LinearLayout expandedView = new LinearLayout(this);
        expandedView.setOrientation(LinearLayout.VERTICAL);
        expandedView.setVisibility(View.GONE);
        expandedView.setPadding(45, 0, 45, 30);

        String[] timesArray = m.times.split(",");
        Arrays.sort(timesArray);
        for (String t : timesArray) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 15, 0, 15);

            TextView tvTime = new TextView(this);
            tvTime.setText(t);
            tvTime.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            tvTime.setTextSize(16);
            tvTime.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
            
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, getResources().getDisplayMetrics()));
            btnLp.setMargins(10, 0, 0, 0);

            MaterialButton btnTake = new MaterialButton(this);
            btnTake.setText(tr("Log", "Принять"));
            btnTake.setTextSize(10);
            btnTake.setCornerRadius(18);
            btnTake.setPadding(25, 0, 25, 0);
            btnTake.setAllCaps(false);
            btnTake.setIcon(getDrawable(android.R.drawable.ic_input_add));
            btnTake.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            btnTake.setIconPadding(4);
            btnTake.setLayoutParams(btnLp);
            btnTake.setOnClickListener(v -> logManualIntake(m));
            
            MaterialButton btnDel = new MaterialButton(this);
            btnDel.setText(tr("Delete", "Удалить"));
            btnDel.setTextSize(10);
            btnDel.setCornerRadius(18);
            btnDel.setPadding(25, 0, 25, 0);
            btnDel.setAllCaps(false);
            btnDel.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF5252")));
            btnDel.setIcon(getDrawable(android.R.drawable.ic_menu_delete));
            btnDel.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            btnDel.setIconPadding(4);
            btnDel.setLayoutParams(btnLp);
            btnDel.setOnClickListener(v -> deleteSpecific_dose(m, t));
            
            row.addView(tvTime);
            row.addView(btnTake);
            row.addView(btnDel);
            expandedView.addView(row);
        }

        header.setOnClickListener(v -> {
            if (expandedView.getVisibility() == View.GONE) {
                expandedView.setVisibility(View.VISIBLE);
                ivArrow.setImageResource(androidx.appcompat.R.drawable.abc_spinner_mtrl_am_alpha);
            } else {
                expandedView.setVisibility(View.GONE);
                ivArrow.setImageResource(androidx.appcompat.R.drawable.abc_ic_arrow_drop_right_black_24dp);
            }
        });

        container.addView(header);
        container.addView(expandedView);
        return container;
    }

    private View createStatsCard(Medicine m, int takenCount) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_background);
        card.setPadding(45, 45, 45, 45);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 10, 0, 20);
        card.setLayoutParams(lp);
        card.setClickable(true);
        card.setFocusable(true);
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        card.setBackgroundResource(outValue.resourceId);
        // Important: reset background after setting selectable background
        card.setBackground(getDrawable(R.drawable.card_background));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            card.setForeground(getDrawable(outValue.resourceId));
        }

        TextView tvName = new TextView(this);
        tvName.setText(m.name);
        tvName.setTextSize(18);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
        card.addView(tvName);

        TextView tvStats = new TextView(this);
        tvStats.setText(tr("Total intakes: ", "Всего приемов: ") + takenCount);
        tvStats.setTextSize(14);
        tvStats.setTextColor(getThemeColor(R.attr.secondaryTextColor));
        tvStats.setPadding(0, 5, 0, 0);
        card.addView(tvStats);

        card.setOnClickListener(v -> showDetailedHistory(m));
        return card;
    }

    private void logManualIntake(Medicine m) {
        Executors.newSingleThreadExecutor().execute(() -> {
            String dateStamp = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(new Date());
            m.batches = subtractFromBatches(m.batches, m.dosage);
            if (m.history == null) m.history = "";
            m.history += dateStamp + (isRussian ? " - Принято," : " - Taken,");
            m.lastUpdated = System.currentTimeMillis();
            int totalStock = calculateTotalStock(m.batches);
            if (totalStock <= 0) sendStatusNotification(m.name, tr("OUT OF STOCK!", "НЕТ В НАЛИЧИИ!"));
            if (isMedicineExpired(m.batches)) sendStatusNotification(m.name, tr("EXPIRED!", "ПРОСРОЧЕНО!"));
            db.medicineDao().update(m);
            runOnUiThread(() -> { 
                showSuccessAnimation();
                refreshCurrentTab();
                updateWidget();
            });
        });
    }

    private void showSuccessAnimation() {
        showStatusAnimation(R.drawable.ic_check_mark, tr("Logged", "Отмечено"));
    }

    private void showDeleteAnimation() {
        showStatusAnimation(R.drawable.ic_delete_anim, tr("Deleted", "Удалено"));
    }

    private void showStatusAnimation(int iconRes, String message) {
        if (successOverlay == null) return;
        
        successCheckmark.setImageResource(iconRes);
        tvSuccessMsg.setText(message);
        
        successOverlay.setVisibility(View.VISIBLE);
        successOverlay.setAlpha(0f);
        successOverlay.animate().alpha(1f).setDuration(200).start();

        successContent.setScaleX(0.5f);
        successContent.setScaleY(0.5f);
        successContent.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setInterpolator(new OvershootInterpolator())
                .start();

        // If it's an AnimatedVectorDrawable, start it
        Drawable d = successCheckmark.getDrawable();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && d instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) d).start();
        } else if (d instanceof AnimatedVectorDrawableCompat) {
            ((AnimatedVectorDrawableCompat) d).start();
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            successOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> successOverlay.setVisibility(View.GONE))
                    .start();
        }, 1200);
    }

    private void sendStatusNotification(String medName, String status) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String CHANNEL_ID = "med_reminders";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Alerts", NotificationManager.IMPORTANCE_HIGH);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(tr("Medicine Alert", "Предупреждение о лекарстве"))
                .setContentText(medName + " " + status)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        if (manager != null) manager.notify((int)System.currentTimeMillis(), b.build());
    }

    private boolean isMedicineExpired(String batchString) {
        if (batchString == null || batchString.isEmpty()) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
            Date today = new Date();
            for (String batch : batchString.split("\\|")) {
                if (batch.contains("(Exp: ")) {
                    String dateStr = batch.substring(batch.indexOf("(Exp: ") + 6, batch.length() - 1);
                    Date expiryDate = sdf.parse(dateStr);
                    if (expiryDate != null && expiryDate.before(today)) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void deleteSpecific_dose(Medicine m, String timeToDelete) {
        new AlertDialog.Builder(this).setTitle(tr("Delete Dose", "Удалить прием"))
                .setMessage(tr("Remove the ", "Удалить прием в ") + timeToDelete + tr(" dose for ", " для ") + m.name + "?")
                .setPositiveButton(tr("Delete", "Удалить"), (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        List<String> tList = new ArrayList<>(Arrays.asList(m.times.split(",")));
                        tList.remove(timeToDelete);
                        m.times = android.text.TextUtils.join(",", tList);
                        m.lastUpdated = System.currentTimeMillis();
                        db.medicineDao().update(m);
                        runOnUiThread(() -> {
                            showDeleteAnimation();
                            refreshCurrentTab();
                            updateWidget();
                        });
                    });
                }).setNegativeButton(tr("Cancel", "Отмена"), null).show();
    }

    private void showScheduleDoseDialog() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Medicine> warehouseMeds = db.medicineDao().getAllByUserId(currentUserId);
            runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(tr("Schedule New Dose", "Новое назначение"));
                LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 40, 50, 20);
                
                layout.addView(createSectionLabel(tr("1. Intake Times", "1. Время приема")));
                LinearLayout timePreview = new LinearLayout(this); timePreview.setOrientation(LinearLayout.VERTICAL);
                layout.addView(timePreview); refreshTimePreview(timePreview);
                MaterialButton btnTime = createActionButton(tr("+ Add Time", "+ Добавить время"));
                btnTime.setOnClickListener(v -> {
                    Calendar c = Calendar.getInstance();
                    new TimePickerDialog(this, (view, h, m) -> {
                        String t = String.format(Locale.getDefault(), "%02d:%02d", h, m);
                        if(!tempTimes.contains(t)) { tempTimes.add(t); refreshTimePreview(timePreview); }
                    }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
                });
                layout.addView(btnTime);
                
                layout.addView(createSectionLabel("\n" + tr("2. Select Pills", "2. Выбор лекарств")));
                LinearLayout medPreview = new LinearLayout(this); medPreview.setOrientation(LinearLayout.VERTICAL);
                layout.addView(medPreview); refreshMedPreview(medPreview);
                MaterialButton btnPick = createActionButton(tr("+ Pick from Warehouse", "+ Выбрать со склада"));
                btnPick.setOnClickListener(v -> {
                    String[] names = new String[warehouseMeds.size()];
                    for(int i=0; i<warehouseMeds.size(); i++) names[i] = warehouseMeds.get(i).name;
                    new AlertDialog.Builder(this).setItems(names, (d, which) -> showDoseConfig(warehouseMeds.get(which), medPreview)).show();
                });
                layout.addView(btnPick);
                
                ScrollView sc = new ScrollView(this); sc.addView(layout);
                builder.setView(sc);
                builder.setPositiveButton(tr("Save All", "Сохранить все"), (d, w) -> saveScheduledDoses());
                builder.setNegativeButton(tr("Cancel", "Отмена"), null).show();
            });
        });
    }

    private void showDoseConfig(Medicine m, LinearLayout container) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(tr("Dose for ", "Дозировка для ") + m.name);
        EditText doseIn = new EditText(this); doseIn.setHint(tr("Dosage (e.g. 2)", "Дозировка (напр. 2)")); doseIn.setInputType(2);
        b.setView(doseIn);
        b.setPositiveButton(tr("Add", "Добавить"), (d, w) -> {
            String dStr = doseIn.getText().toString();
            m.dosage = Integer.parseInt(dStr.isEmpty() ? "1" : dStr);
            boolean exists = false;
            for(Medicine tm : tempMedsToSchedule) if(tm.id == m.id) { exists = true; break; }
            if(!exists) tempMedsToSchedule.add(m);
            refreshMedPreview(container);
        });
        b.show();
    }

    private void saveScheduledDoses() {
        Executors.newSingleThreadExecutor().execute(() -> {
            for (Medicine m : tempMedsToSchedule) {
                List<String> combined = new ArrayList<>();
                if (m.times != null && !m.times.isEmpty()) combined.addAll(Arrays.asList(m.times.split(",")));
                for (String nt : tempTimes) if (!combined.contains(nt)) combined.add(nt);
                m.times = android.text.TextUtils.join(",", combined);
                m.lastUpdated = System.currentTimeMillis();
                db.medicineDao().update(m);
                setupAlarms(m.name, tempTimes);
            }
            runOnUiThread(() -> { 
                refreshCurrentTab(); 
                updateWidget();
                Toast.makeText(this, tr("Schedule Updated", "Расписание обновлено"), Toast.LENGTH_SHORT).show(); 
            });
        });
    }

    private void showBatchEditMenu(Medicine m) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(m.name + " " + tr("Batches", "Партии"));
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(40, 40, 40, 40);
        if (m.batches != null && !m.batches.isEmpty()) {
            String[] arr = m.batches.split("\\|");
            for (int i = 0; i < arr.length; i++) {
                final int index = i; MaterialButton btn = new MaterialButton(this);
                btn.setText(arr[index]); btn.setAllCaps(false);
                btn.setOnClickListener(v -> showSpecificBatchEdit(m, index));
                l.addView(btn);
            }
        }
        
        MaterialButton btnDeleteMed = new MaterialButton(this);
        btnDeleteMed.setText(tr("Delete Medicine Entirely", "Полностью удалить лекарство"));
        btnDeleteMed.setAllCaps(false);
        btnDeleteMed.setCornerRadius(30);
        btnDeleteMed.setTextColor(Color.WHITE);
        btnDeleteMed.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F44336")));
        btnDeleteMed.setIconResource(android.R.drawable.ic_menu_delete);
        btnDeleteMed.setIconTint(ColorStateList.valueOf(Color.WHITE));
        btnDeleteMed.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btnDeleteMed.setPadding(40, 30, 40, 30);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 40, 0, 0);
        btnDeleteMed.setLayoutParams(p);

        final AlertDialog parentDialog = b.create();

        btnDeleteMed.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle(tr("Delete Medicine", "Удалить лекарство"))
                .setMessage(tr("Are you sure you want to delete ", "Вы уверены, что хотите удалить ") + m.name + tr(" and all its history?", " и всю историю?"))
                .setPositiveButton(tr("Delete", "Удалить"), (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> { 
                        db.medicineDao().delete(m); 
                        runOnUiThread(() -> {
                            showDeleteAnimation();
                            refreshCurrentTab();
                            updateWidget();
                            parentDialog.dismiss();
                        }); 
                    });
                })
                .setNegativeButton(tr("Cancel", "Отмена"), null)
                .show();
        });
        l.addView(btnDeleteMed);

        parentDialog.setView(l);
        parentDialog.setButton(AlertDialog.BUTTON_NEGATIVE, tr("Close", "Закрыть"), (d, w) -> parentDialog.dismiss());
        parentDialog.show();
    }

    private void showSpecificBatchEdit(Medicine m, int index) {
        String[] arr = m.batches.split("\\|");
        String count = arr[index].split(" ")[0];
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        LinearLayout lay = new LinearLayout(this); lay.setOrientation(LinearLayout.VERTICAL); lay.setPadding(40,40,40,40);
        EditText in = new EditText(this); in.setInputType(2); in.setText(count); lay.addView(in);
        b.setView(lay);
        b.setPositiveButton(tr("Save", "Сохранить"), (d, w) -> {
            arr[index] = in.getText().toString() + " pills" + arr[index].substring(arr[index].indexOf(" (Exp:"));
            m.batches = android.text.TextUtils.join("|", arr);
            m.lastUpdated = System.currentTimeMillis();
            Executors.newSingleThreadExecutor().execute(() -> { 
                db.medicineDao().update(m); 
                runOnUiThread(() -> {
                    refreshCurrentTab();
                    updateWidget();
                }); 
            });
        });
        b.setNegativeButton(tr("Delete Batch", "Удалить партию"), (d, w) -> {
            ArrayList<String> list = new ArrayList<>(Arrays.asList(arr)); list.remove(index);
            m.batches = android.text.TextUtils.join("|", list);
            m.lastUpdated = System.currentTimeMillis();
            Executors.newSingleThreadExecutor().execute(() -> { 
                db.medicineDao().update(m); 
                runOnUiThread(() -> {
                    showDeleteAnimation();
                    refreshCurrentTab();
                    updateWidget();
                }); 
            });
        });
        b.show();
    }

    private void showDetailedHistory(Medicine m) {
        if (m.history == null || m.history.isEmpty()) {
            new AlertDialog.Builder(this).setTitle(m.name)
                    .setMessage(tr("No intake records yet.", "Записи приемов отсутствуют."))
                    .setPositiveButton(tr("Close", "Закрыть"), null).show();
            return;
        }

        Map<String, List<String>> dateToTimes = new LinkedHashMap<>();
        String[] parts = m.history.split(",");
        for (int i = 0; i < parts.length - 1; i += 2) {
            String date = parts[i].trim();
            String timeAndStatus = parts[i+1].trim();
            if (!dateToTimes.containsKey(date)) dateToTimes.put(date, new ArrayList<>());
            dateToTimes.get(date).add(timeAndStatus);
        }

        showHistoryDatesDialog(m, dateToTimes);
    }

    private void showHistoryDatesDialog(Medicine m, Map<String, List<String>> dateToTimes) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(m.name + " - " + tr("Select Date", "Выберите дату"));

        ScrollView scroll = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 30, 40, 30);

        List<String> sortedDates = new ArrayList<>(dateToTimes.keySet());
        Collections.reverse(sortedDates);

        for (String date : sortedDates) {
            MaterialButton btnDate = new MaterialButton(this);
            btnDate.setText(date + " (" + dateToTimes.get(date).size() + ")");
            btnDate.setAllCaps(false);
            btnDate.setCornerRadius(25);
            btnDate.setBackgroundTintList(ColorStateList.valueOf(getThemeColor(R.attr.cardBackgroundColor)));
            btnDate.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
            btnDate.setStrokeWidth(2);
            btnDate.setStrokeColor(ColorStateList.valueOf(getThemeColor(R.attr.cardStrokeColor)));
            btnDate.setOnClickListener(v -> showHistoryTimesDialog(m, date, dateToTimes.get(date)));
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 15, 0, 15);
            btnDate.setLayoutParams(lp);
            container.addView(btnDate);
        }

        scroll.addView(container);
        builder.setView(scroll);
        builder.setPositiveButton(tr("Close", "Закрыть"), null);
        builder.setNeutralButton(tr("Clear History", "Очистить всё"), (d, w) -> {
            new AlertDialog.Builder(this).setMessage(tr("Clear all history?", "Очистить всю историю?"))
                .setPositiveButton(tr("Yes", "Да"), (d2, w2) -> {
                    m.history = "";
                    Executors.newSingleThreadExecutor().execute(() -> { 
                        db.medicineDao().update(m); 
                        runOnUiThread(() -> {
                            refreshCurrentTab();
                            updateWidget();
                        }); 
                    });
                }).setNegativeButton(tr("No", "Нет"), null).show();
        });
        builder.show();
    }

    private void showHistoryTimesDialog(Medicine m, String date, List<String> times) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(m.name + " - " + date);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 30, 40, 30);

        for (String time : times) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(35, 30, 35, 30);
            item.setBackgroundResource(R.drawable.card_background);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 10, 0, 10);
            item.setLayoutParams(lp);

            ImageView icon = new ImageView(this);
            icon.setImageResource(android.R.drawable.checkbox_on_background);
            icon.setColorFilter(Color.parseColor(BLUE_COLOR));
            item.addView(icon);

            TextView tvTime = new TextView(this);
            tvTime.setText(time);
            tvTime.setPadding(30, 0, 0, 0);
            tvTime.setTextSize(16);
            tvTime.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
            item.addView(tvTime);

            container.addView(item);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(container);
        builder.setView(scroll);
        builder.setPositiveButton(tr("Back", "Назад"), (d, w) -> showDetailedHistory(m));
        builder.show();
    }

    private void updateWidget() {
        Intent updateIntent = new Intent(this, MedWidget.class);
        updateIntent.setAction(MedWidget.ACTION_AUTO_UPDATE);
        sendBroadcast(updateIntent);
    }

    private String subtractFromBatches(String batchString, int pillsToTake) {
        if (batchString == null || batchString.isEmpty()) return "";
        String[] batches = batchString.split("\\|");
        StringBuilder newBatchBuilder = new StringBuilder();
        int remaining = pillsToTake;
        for (String batch : batches) {
            if (batch.trim().isEmpty()) continue;
            if (remaining > 0) {
                try {
                    int firstSpace = batch.indexOf(" ");
                    int currentAmount = Integer.parseInt(batch.substring(0, firstSpace));
                    String details = batch.substring(firstSpace);
                    if (currentAmount <= remaining) { remaining -= currentAmount; }
                    else {
                        int newAmt = currentAmount - remaining; remaining = 0;
                        if (newBatchBuilder.length() > 0) newBatchBuilder.append("|");
                        newBatchBuilder.append(newAmt).append(details);
                    }
                } catch (Exception e) {
                    if (newBatchBuilder.length() > 0) newBatchBuilder.append("|");
                    newBatchBuilder.append(batch);
                }
            } else {
                if (newBatchBuilder.length() > 0) newBatchBuilder.append("|");
                newBatchBuilder.append(batch);
            }
        }
        return newBatchBuilder.toString();
    }

    private int calculateTotalStock(String batches) {
        if (batches == null || batches.isEmpty()) return 0;
        int total = 0;
        for (String b : batches.split("\\|")) {
            try { total += Integer.parseInt(b.split(" ")[0]); } catch (Exception ignored) {}
        }
        return total;
    }

    private void setupAlarms(String name, List<String> times) {
        for (String t : times) {
            String[] p = t.split(":");
            setAlarm(name, Integer.parseInt(p[0]), Integer.parseInt(p[1]), t);
        }
    }

    private void setAlarm(String name, int h, int m, String timeStr) {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra("med_name", name); intent.putExtra("med_time", timeStr);
        PendingIntent pi = PendingIntent.getBroadcast(this, (name + timeStr).hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar cal = Calendar.getInstance(); cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m); cal.set(Calendar.SECOND, 0);
        if (cal.before(Calendar.getInstance())) cal.add(Calendar.DAY_OF_MONTH, 1);
        if (am != null) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
    }

    private MaterialButton createCardButton(String text, String colorHex) {
        MaterialButton btn = new MaterialButton(this);
        btn.setText(text); btn.setAllCaps(false); btn.setCornerRadius(38);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(colorHex)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 15, 0, 15); btn.setLayoutParams(p);
        return btn;
    }

    private void refreshTimePreview(LinearLayout container) {
        container.removeAllViews();
        for (String t : tempTimes) {
            MaterialButton btn = new MaterialButton(this);
            btn.setText(t); btn.setAllCaps(false); btn.setCornerRadius(25);
            btn.setBackgroundTintList(ColorStateList.valueOf(getThemeColor(R.attr.cardBackgroundColor)));
            btn.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
            btn.setStrokeWidth(2);
            btn.setStrokeColor(ColorStateList.valueOf(getThemeColor(R.attr.cardStrokeColor)));
            btn.setOnClickListener(v -> { tempTimes.remove(t); refreshTimePreview(container); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 5, 0, 5);
            btn.setLayoutParams(lp);
            container.addView(btn);
        }
    }

    private void refreshMedPreview(LinearLayout container) {
        container.removeAllViews();
        for (Medicine m : tempMedsToSchedule) {
            MaterialButton btn = new MaterialButton(this);
            String label = m.name + " (" + m.dosage + " " + tr("pills", "таб.") + ")";
            btn.setText(label);
            btn.setAllCaps(false); btn.setCornerRadius(25);
            btn.setBackgroundTintList(ColorStateList.valueOf(getThemeColor(R.attr.cardBackgroundColor)));
            btn.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
            btn.setStrokeWidth(2);
            btn.setStrokeColor(ColorStateList.valueOf(getThemeColor(R.attr.cardStrokeColor)));
            btn.setOnClickListener(v -> { tempMedsToSchedule.remove(m); refreshMedPreview(container); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 5, 0, 5);
            btn.setLayoutParams(lp);
            container.addView(btn);
        }
    }

    private void refreshCurrentTab() {
        if (sectionsPagerAdapter != null && viewPager != null) {
            sectionsPagerAdapter.notifyDataSetChanged();
        }
    }
    private LinearLayout createBaseLayout() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(30, 20, 30, 20); return l; }
    private TextView createSectionLabel(String txt) { 
        TextView tv = new TextView(this); 
        tv.setText(txt); 
        tv.setTypeface(null, Typeface.BOLD); 
        tv.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
        return tv; 
    }
    private MaterialButton createActionButton(String text) { 
        MaterialButton b = new MaterialButton(this); 
        b.setText(text); 
        b.setAllCaps(false); 
        b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(BLUE_COLOR))); 
        b.setTextColor(Color.WHITE); 
        b.setCornerRadius(30); 
        b.setPadding(40, 30, 40, 30); 
        return b; 
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }
}
