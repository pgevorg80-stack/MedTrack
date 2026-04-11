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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;
import androidx.viewpager2.widget.ViewPager2;

import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import com.bumptech.glide.Glide;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.File;
import java.io.OutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private final ExecutorService diskExecutor = Executors.newFixedThreadPool(4);
    private AppDatabase db;
    private ViewPager2 viewPager;
    private DrawerLayout drawerLayout;
    private final List<String> tempTimes = new ArrayList<>();
    private final List<Medicine> tempMedsToSchedule = new ArrayList<>();
    private final String BLUE_COLOR = "#2196F3";
    private String tempExpiryInternal = "Not Set";
    private String tempImagePath = null;
    private SpeechRecognizer speechRecognizer;
    private ImageView micIconRef;
    
    // OCR components
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private EditText ocrTargetEditText;

    // Image picking for Medicine
    private ActivityResultLauncher<Uri> medicinePhotoLauncher;
    private Medicine photoTargetMed;
    private Uri photoUri;

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
        
        db = AppDatabase.getInstance(this);
        // Sync from Firestore on startup
        FirestoreHelper.syncFromFirestore(currentUserId, db, () -> runOnUiThread(this::refreshCurrentTab));
        
        setContentView(R.layout.activity_main);

        requestNotificationPermission();

        medicinePhotoLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && photoUri != null) {
                diskExecutor.execute(() -> {
                    try (InputStream is = getContentResolver().openInputStream(photoUri)) {
                        Bitmap bitmap = BitmapFactory.decodeStream(is);
                        if (bitmap != null) {
                            Bitmap rotated = rotateBitmapIfRequired(bitmap, photoUri);
                            if (photoTargetMed != null) {
                                saveMedicineImage(photoTargetMed, rotated);
                            } else {
                                saveTempMedicineImage(rotated);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && photoUri != null) {
                diskExecutor.execute(() -> {
                    try (InputStream is = getContentResolver().openInputStream(photoUri)) {
                        Bitmap bitmap = BitmapFactory.decodeStream(is);
                        if (bitmap != null) {
                            Bitmap rotated = rotateBitmapIfRequired(bitmap, photoUri);
                            if (ocrTargetEditText != null) {
                                runOnUiThread(() -> recognizeText(rotated));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } else if (!success) {
                Toast.makeText(this, tr("Camera cancelled or failed", "Камера отменена или произошла ошибка"), Toast.LENGTH_SHORT).show();
            }
        });
        

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
        findViewById(R.id.nav_add).setOnClickListener(v -> {
            v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                showRegisterMedicineDialog();
            }).start();
        });
        findViewById(R.id.nav_inventory).setOnClickListener(v -> viewPager.setCurrentItem(2));
        findViewById(R.id.nav_dashboard).setOnClickListener(v -> viewPager.setCurrentItem(3));

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateBottomNavSelection(position);
            }
        });

        // --- Settings Drawer Animation ---
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            private boolean hasAnimated = false;
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
                if (slideOffset > 0.05f && !hasAnimated) {
                    animateSettingsItems();
                    hasAnimated = true;
                }
            }
            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                hasAnimated = false;
                // Reset items for next time
                View[] items = {
                    findViewById(R.id.tv_settings_header),
                    findViewById(R.id.btn_profile),
                    findViewById(R.id.theme_switcher_container),
                    findViewById(R.id.btn_language),
                    findViewById(R.id.btn_add_widget),
                    findViewById(R.id.btn_logout)
                };
                for (View v : items) if (v != null) v.setAlpha(0);
            }
        });

        // Initialize first selection
        viewPager.post(() -> updateBottomNavSelection(viewPager.getCurrentItem()));

        // --- Settings Drawer Listeners ---
        findViewById(R.id.btn_profile).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            startActivity(new Intent(this, ProfileActivity.class));
        });

        findViewById(R.id.theme_switcher_container).setOnClickListener(v -> {
            SharedPreferences pref = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
            boolean isDark = pref.getBoolean("IsDarkMode", false);
            boolean nextDark = !isDark;
            
            pref.edit().putBoolean("IsDarkMode", nextDark).apply();
            
            // Animate knob and then recreate
            View knob = findViewById(R.id.iv_theme_icon_knob);
            if (knob != null) {
                float targetX = nextDark ? 26 * getResources().getDisplayMetrics().density : 0;
                knob.animate()
                    .translationX(targetX)
                    .setDuration(300)
                    .withEndAction(() -> {
                        AppCompatDelegate.setDefaultNightMode(nextDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
                    })
                    .start();
            } else {
                AppCompatDelegate.setDefaultNightMode(nextDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            }
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
        ((TextView) findViewById(R.id.tv_nav_inv)).setText(tr("Stock", "Склад"));
        ((TextView) findViewById(R.id.tv_nav_dash)).setText(tr("Home", "Панель"));

        ((TextView) findViewById(R.id.tv_settings_header)).setText(tr("SETTINGS", "НАСТРОЙКИ"));
        ((MaterialButton) findViewById(R.id.btn_profile)).setText(tr("My Profile", "Мой профиль"));
        ((TextView) findViewById(R.id.tv_light_label)).setText(tr("Light", "Светлая"));
        ((TextView) findViewById(R.id.tv_dark_label)).setText(tr("Dark", "Темная"));
        ((MaterialButton) findViewById(R.id.btn_language)).setText(tr("Language", "Язык (RU)"));
        ((MaterialButton) findViewById(R.id.btn_add_widget)).setText(tr("Add widget", "Добавить виджет"));
        ((MaterialButton) findViewById(R.id.btn_logout)).setText(tr("Log Out", "Выйти"));

        // Theme Switcher Sync
        boolean isDark = getSharedPreferences("ThemePrefs", MODE_PRIVATE).getBoolean("IsDarkMode", false);
        View knob = findViewById(R.id.iv_theme_icon_knob);
        if (knob != null) {
            float density = getResources().getDisplayMetrics().density;
            knob.setTranslationX(isDark ? 26 * density : 0);
            ((ImageView) knob).setImageResource(isDark ? R.drawable.ic_moon : R.drawable.ic_sun);
            ((ImageView) knob).setImageTintList(ColorStateList.valueOf(Color.parseColor("#2196F3")));
        }
        View lightLabel = findViewById(R.id.tv_light_label);
        View darkLabel = findViewById(R.id.tv_dark_label);
        if (lightLabel != null) lightLabel.setAlpha(isDark ? 0.4f : 1.0f);
        if (darkLabel != null) darkLabel.setAlpha(isDark ? 1.0f : 0.4f);
        
        if (tvSuccessMsg != null) tvSuccessMsg.setText(tr("Logged!", "Отмечено!"));
    }

    private void updateBottomNavSelection(int position) {
        int[] ids = {R.id.nav_list, R.id.nav_stats, 0, R.id.nav_inventory, R.id.nav_dashboard};
        int activeIndex = (position < 2 ? position : position + 1);

        View indicator = findViewById(R.id.nav_indicator);
        int navBg = getThemeColor(R.attr.navBarBackground);
        
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == 0) continue;
            View v = findViewById(ids[i]);
            boolean isSelected = (i == activeIndex);

            int activeColor;
            int inactiveColor;

            if (navBg == Color.parseColor(BLUE_COLOR)) { // Light Mode
                activeColor = Color.parseColor(BLUE_COLOR); // Blue icon
                inactiveColor = Color.argb(180, 255, 255, 255); // White icon (semi-trans)
            } else { // Dark Mode
                activeColor = Color.WHITE; // White icon
                inactiveColor = Color.argb(160, 255, 255, 255); // White-ish icon
            }

            if (isSelected && indicator != null) {
                indicator.setVisibility(View.VISIBLE);
                indicator.animate()
                        .x(v.getX() + (v.getWidth() - indicator.getWidth()) / 2f)
                        .alpha(1f)
                        .setDuration(400)
                        .setInterpolator(new OvershootInterpolator(1.4f))
                        .start();
            }

            float targetScale = isSelected ? 1.12f : 1.0f;
            float targetTranslationY = isSelected ? -10f : 0f;
            
            v.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .translationY(targetTranslationY)
                    .setDuration(300)
                    .setInterpolator(new OvershootInterpolator(1.5f))
                    .start();

            if (v instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) v;
                for (int j = 0; j < group.getChildCount(); j++) {
                    View child = group.getChildAt(j);
                    if (child instanceof ImageView) {
                        ((ImageView) child).setImageTintList(ColorStateList.valueOf(isSelected ? activeColor : inactiveColor));
                    } else if (child instanceof TextView) {
                        ((TextView) child).setTextColor(isSelected ? activeColor : inactiveColor);
                        ((TextView) child).setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
                        child.animate().alpha(isSelected ? 1.0f : 0.7f).setDuration(300).start();
                    }
                }
            }
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

    private void recognizeText(android.graphics.Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String resultText = visionText.getText();
                    if (resultText != null && !resultText.isEmpty() && ocrTargetEditText != null) {
                        String[] lines = resultText.split("\n");
                        String bestMatch = "";
                        for (String line : lines) {
                            String trimmed = line.trim();
                            if (trimmed.length() > bestMatch.length()) {
                                bestMatch = trimmed;
                            }
                        }
                        ocrTargetEditText.setText(bestMatch.isEmpty() ? lines[0].trim() : bestMatch);
                    } else {
                        Toast.makeText(this, tr("No text detected", "Текст не найден"), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, tr("OCR Failed", "Ошибка распознавания"), Toast.LENGTH_SHORT).show();
                });
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
        animateViewIn(btnAdd, 100);

        boolean hasSchedules = false;
        int delay = 200;
        for (Medicine m : meds) {
            if (m.times != null && !m.times.isEmpty()) {
                View card = createExpandableMedCard(m);
                layout.addView(card);
                animateViewIn(card, delay);
                delay += 100;
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
            animateViewIn(empty, 200);
        }
    }

    private void populateStats(LinearLayout layout, List<Medicine> meds) {
        layout.addView(createHeaderWithMenu(tr("Usage History", "История использования")));
        
        MaterialButton btnPdf = createActionButton(tr("+ Add PDF", "+ Создать PDF"));
        LinearLayout.LayoutParams pdfLp = new LinearLayout.LayoutParams(-1, -2);
        pdfLp.setMargins(0, 0, 0, 30);
        btnPdf.setLayoutParams(pdfLp);
        btnPdf.setOnClickListener(v -> showPdfDatePicker(meds));
        layout.addView(btnPdf);
        animateViewIn(btnPdf, 100);

        boolean hasHistory = false;
        int delay = 200;
        for (Medicine m : meds) {
            int taken = 0;
            if (m.history != null && !m.history.isEmpty()) {
                taken = m.history.split(",").length / 2;
            }
            
            if (taken > 0) {
                View card = createStatsCard(m, taken);
                layout.addView(card);
                animateViewIn(card, delay);
                delay += 100;
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
            animateViewIn(empty, 200);
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
        animateViewIn(btnAdd, 100);

        if (meds.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(tr("Your warehouse is empty.", "Ваш склад пуст."));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 100, 0, 0);
            empty.setAlpha(0.6f);
            empty.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
            layout.addView(empty);
            animateViewIn(empty, 200);
        } else {
            int delay = 200;
            for (Medicine m : meds) {
                View card = createInventoryCard(m);
                layout.addView(card);
                animateViewIn(card, delay);
                delay += 100;
            }
        }
    }

    private void launchCamera() {
        File photoFile = new File(getExternalFilesDir(null), "camera_temp.jpg");
        photoUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
        medicinePhotoLauncher.launch(photoUri);
    }

    private void launchOCRCamera() {
        File photoFile = new File(getExternalFilesDir(null), "ocr_temp.jpg");
        photoUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
        takePictureLauncher.launch(photoUri);
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

        // Refill Prediction
        if (total > 0 && m.dosage > 0) {
            int dailyDose = 0;
            if (m.times != null && !m.times.isEmpty()) {
                dailyDose = m.times.split(",").length * m.dosage;
            }
            if (dailyDose > 0) {
                int daysLeft = total / dailyDose;
                TextView tvPredict = new TextView(this);
                String predText = tr("Lasts ~", "Хватит на ~") + daysLeft + tr(" days", " дн.");
                if (daysLeft <= 3) {
                    tvPredict.setTextColor(Color.parseColor("#F44336"));
                    predText += " " + tr("(Refill Soon!)", "(Срочно!)");
                } else {
                    tvPredict.setTextColor(Color.parseColor("#4CAF50"));
                }
                tvPredict.setText(predText);
                tvPredict.setTextSize(12);
                tvPredict.setTypeface(null, Typeface.BOLD);
                textInfo.addView(tvPredict);
            }
        }
        
        if (isExpired) {
            TextView tvExp = new TextView(this);
            tvExp.setText(tr("Some batches expired!", "Есть просроченные партии!"));
            tvExp.setTextSize(12);
            tvExp.setTextColor(Color.parseColor("#F44336"));
            tvExp.setTypeface(null, Typeface.ITALIC);
            textInfo.addView(tvExp);
        }

        card.addView(textInfo);

        // Medicine Photo and Camera Button
        LinearLayout photoBox = new LinearLayout(this);
        photoBox.setOrientation(LinearLayout.HORIZONTAL);
        photoBox.setGravity(Gravity.CENTER_VERTICAL);
        
        if (m.imagePath != null && !m.imagePath.isEmpty() && new File(m.imagePath).exists()) {
            com.google.android.material.card.MaterialCardView imgCard = new com.google.android.material.card.MaterialCardView(this);
            imgCard.setRadius(15);
            imgCard.setCardElevation(0);
            imgCard.setStrokeWidth(2);
            imgCard.setStrokeColor(Color.parseColor("#15000000"));
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(100, 100);
            imgLp.setMargins(10, 0, 5, 0);
            imgCard.setLayoutParams(imgLp);

            ImageView ivMedPhoto = new ImageView(this);
            ivMedPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(new File(m.imagePath)).into(ivMedPhoto);
            ivMedPhoto.setOnClickListener(v -> showExpandedImage(m));
            imgCard.addView(ivMedPhoto);
            photoBox.addView(imgCard);
        }

        ImageView ivCamera = new ImageView(this);
        ivCamera.setImageResource(android.R.drawable.ic_menu_camera);
        ivCamera.setColorFilter(getThemeColor(R.attr.secondaryTextColor));
        ivCamera.setPadding(12, 12, 12, 12);
        TypedValue outVal = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outVal, true);
        ivCamera.setBackgroundResource(outVal.resourceId);
        ivCamera.setOnClickListener(v -> {
            photoTargetMed = m;
            launchCamera();
        });
        photoBox.addView(ivCamera);
        
        card.addView(photoBox);

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

        // Greeting based on time
        TextView tvGreeting = new TextView(this);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) greeting = tr("Good Morning", "Доброе утро");
        else if (hour < 18) greeting = tr("Good Afternoon", "Добрый день");
        else greeting = tr("Good Evening", "Добрый вечер");

        tvGreeting.setText(greeting + "!");
        tvGreeting.setTextSize(24);
        tvGreeting.setTypeface(null, Typeface.BOLD);
        tvGreeting.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
        tvGreeting.setPadding(10, 0, 0, 20);
        layout.addView(tvGreeting);
        animateViewIn(tvGreeting, 100);

        com.google.android.material.card.MaterialCardView dashCard = new com.google.android.material.card.MaterialCardView(this);
        dashCard.setRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics()));
        dashCard.setCardElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
        dashCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor(BLUE_COLOR)));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, 10, 0, 40);
        dashCard.setLayoutParams(cardParams);

        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.HORIZONTAL);
        cardContent.setPadding(50, 60, 50, 60);
        dashCard.addView(cardContent);

        LinearLayout leftPart = new LinearLayout(this);
        leftPart.setOrientation(LinearLayout.VERTICAL);
        leftPart.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1.8f));
        leftPart.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTime = new TextView(this);
        tvTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        tvTime.setTextSize(54);
        tvTime.setTextColor(Color.WHITE);
        tvTime.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        leftPart.addView(tvTime);

        LinearLayout weekGrid = new LinearLayout(this);
        weekGrid.setOrientation(LinearLayout.HORIZONTAL);
        weekGrid.setGravity(Gravity.CENTER);
        weekGrid.setPadding(0, 20, 0, 0);
        String[] dayLettersEn = {"S", "M", "T", "W", "T", "F", "S"};
        String[] dayLettersRu = {"В", "П", "В", "С", "Ч", "П", "С"};
        String[] dayLetters = isRussian ? dayLettersRu : dayLettersEn;
        int currentDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);

        // Pre-calculate which days have intakes in the current week
        Set<String> intakeDays = new HashSet<>();
        SimpleDateFormat sdfIntake = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (Medicine m : meds) {
            if (m.history != null && !m.history.isEmpty()) {
                for (String entry : m.history.split(",")) {
                    try { intakeDays.add(entry.split(" ")[0]); } catch (Exception ignored) {}
                }
            }
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);

        for (int i = 1; i <= 7; i++) {
            String dayKey = sdfIntake.format(cal.getTime());
            boolean hasIntake = intakeDays.contains(dayKey);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

            TextView tvLetter = new TextView(this);
            tvLetter.setText(dayLetters[i - 1]);
            tvLetter.setTextSize(11);
            tvLetter.setGravity(Gravity.CENTER);
            tvLetter.setTextColor(i == currentDay ? Color.WHITE : Color.parseColor("#B0FFFFFF"));
            tvLetter.setTypeface(null, i == currentDay ? Typeface.BOLD : Typeface.NORMAL);
            col.addView(tvLetter);

            com.google.android.material.card.MaterialCardView dot = new com.google.android.material.card.MaterialCardView(this);
            int width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
            int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, i == currentDay ? 18 : 8, getResources().getDisplayMetrics());
            
            LinearLayout.LayoutParams dotP = new LinearLayout.LayoutParams(width, height);
            dotP.setMargins(0, 10, 0, 0);
            dot.setLayoutParams(dotP);
            dot.setRadius(100);
            dot.setStrokeWidth(0);

            if (i == currentDay) {
                dot.setCardBackgroundColor(Color.WHITE);
                dot.setCardElevation(6);
            } else if (i < currentDay) {
                if (hasIntake) {
                    dot.setCardBackgroundColor(Color.WHITE); // Solid for taken
                    dot.setAlpha(0.9f);
                } else {
                    dot.setStrokeWidth(3);
                    dot.setStrokeColor(Color.parseColor("#60FFFFFF")); // Ring for missed
                    dot.setCardBackgroundColor(Color.TRANSPARENT);
                    dot.setCardElevation(0);
                }
            } else {
                dot.setCardBackgroundColor(Color.parseColor("#40FFFFFF")); // Future
                dot.setCardElevation(0);
            }
            
            col.addView(dot);
            weekGrid.addView(col);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        leftPart.addView(weekGrid);

        LinearLayout rightPart = new LinearLayout(this);
        rightPart.setOrientation(LinearLayout.VERTICAL);
        rightPart.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
        rightPart.setGravity(Gravity.CENTER);

        String globalNext = getNextUpcomingDose(meds);

        TextView tvNextLabel = new TextView(this);
        tvNextLabel.setText(tr("Next Dose", "След. доза"));
        tvNextLabel.setTextColor(Color.parseColor("#CCFFFFFF"));
        tvNextLabel.setTextSize(12);
        tvNextLabel.setGravity(Gravity.CENTER);
        rightPart.addView(tvNextLabel);

        TextView tvNextDose = new TextView(this);
        tvNextDose.setText(globalNext);
        tvNextDose.setTextColor(Color.WHITE);
        tvNextDose.setTextSize(28);
        tvNextDose.setTypeface(null, Typeface.BOLD);
        tvNextDose.setGravity(Gravity.CENTER);
        rightPart.addView(tvNextDose);

        com.google.android.material.card.MaterialCardView micCard = new com.google.android.material.card.MaterialCardView(this);
        micCard.setRadius(100);
        micCard.setCardBackgroundColor(Color.parseColor("#40FFFFFF"));
        micCard.setStrokeWidth(0);
        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(110, 110);
        micLp.setMargins(0, 30, 0, 0);
        micCard.setLayoutParams(micLp);
        
        ImageView mic = new ImageView(this);
        mic.setImageResource(android.R.drawable.ic_btn_speak_now);
        mic.setColorFilter(Color.WHITE);
        mic.setPadding(25, 25, 25, 25);
        micCard.addView(mic);
        micCard.setOnClickListener(v -> startVoiceRecognition(mic));
        rightPart.addView(micCard);

        cardContent.addView(leftPart);
        cardContent.addView(rightPart);
        layout.addView(dashCard);
        animateViewIn(dashCard, 200);

        TextView tvSectionTitle = new TextView(this);
        tvSectionTitle.setText(tr("Today's Schedule", "Расписание на сегодня"));
        tvSectionTitle.setTextSize(18);
        tvSectionTitle.setTypeface(null, Typeface.BOLD);
        tvSectionTitle.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
        tvSectionTitle.setPadding(10, 20, 0, 20);
        layout.addView(tvSectionTitle);
        animateViewIn(tvSectionTitle, 300);

        int delay = 400;
        boolean hasMeds = false;
        for (Medicine m : meds) {
            if (m.times != null && !m.times.isEmpty()) {
                hasMeds = true;
                String medNext = getNextDoseForMed(m);
                
                com.google.android.material.card.MaterialCardView itemCard = new com.google.android.material.card.MaterialCardView(this);
                itemCard.setRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics()));
                itemCard.setCardElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()));
                
                boolean isNearest = medNext.equals(globalNext) && !globalNext.equals("--:--");
                if (isNearest) {
                    itemCard.setStrokeWidth(4);
                    itemCard.setStrokeColor(Color.parseColor(BLUE_COLOR));
                } else {
                    itemCard.setStrokeWidth(2);
                    itemCard.setStrokeColor(getThemeColor(R.attr.cardStrokeColor));
                }
                itemCard.setCardBackgroundColor(getThemeColor(R.attr.cardBackgroundColor));
                
                LinearLayout itemContent = new LinearLayout(this);
                itemContent.setOrientation(LinearLayout.HORIZONTAL);
                itemContent.setGravity(Gravity.CENTER_VERTICAL);
                itemContent.setPadding(40, 35, 40, 35);
                
                com.google.android.material.card.MaterialCardView imgCard = new com.google.android.material.card.MaterialCardView(this);
                imgCard.setRadius(25);
                imgCard.setCardElevation(0);
                imgCard.setStrokeWidth(0);
                LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(110, 110);
                imgCard.setLayoutParams(imgLp);
                
                ImageView icon = new ImageView(this);
                icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (m.imagePath != null) {
                    Glide.with(this).load(new File(m.imagePath)).into(icon);
                } else {
                    icon.setImageResource(android.R.drawable.ic_menu_today);
                    icon.setColorFilter(isNearest ? Color.parseColor(BLUE_COLOR) : getThemeColor(android.R.attr.textColorSecondary));
                    icon.setPadding(20, 20, 20, 20);
                }
                imgCard.addView(icon);
                itemContent.addView(imgCard);

                LinearLayout textInfo = new LinearLayout(this);
                textInfo.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, -2, 1f);
                textLp.setMargins(30, 0, 0, 0);
                textInfo.setLayoutParams(textLp);

                TextView tvName = new TextView(this);
                tvName.setText(m.name);
                tvName.setTextSize(17);
                tvName.setTypeface(null, Typeface.BOLD);
                tvName.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
                textInfo.addView(tvName);

                TextView tvTimeInfo = new TextView(this);
                tvTimeInfo.setText(tr("Next dose at ", "След. доза в ") + medNext);
                tvTimeInfo.setTextSize(13);
                tvTimeInfo.setTextColor(getThemeColor(android.R.attr.textColorSecondary));
                textInfo.addView(tvTimeInfo);
                
                itemContent.addView(textInfo);

                ImageView arrow = new ImageView(this);
                arrow.setImageResource(android.R.drawable.ic_media_play);
                arrow.setColorFilter(getThemeColor(android.R.attr.textColorSecondary));
                arrow.setAlpha(0.5f);
                itemContent.addView(arrow);

                itemCard.addView(itemContent);
                itemCard.setOnClickListener(v -> logManualIntake(m));
                
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
                p.setMargins(0, 10, 0, 15);
                itemCard.setLayoutParams(p);
                
                layout.addView(itemCard);
                animateViewIn(itemCard, delay);
                delay += 100;
            }
        }
        
        if (!hasMeds) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText(tr("No medicines scheduled for today", "Нет запланированных лекарств на сегодня"));
            tvEmpty.setTextSize(15);
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, 100, 0, 0);
            tvEmpty.setAlpha(0.6f);
            layout.addView(tvEmpty);
        }
    }

    private void animateSettingsItems() {
        View[] items = {
            findViewById(R.id.tv_settings_header),
            findViewById(R.id.btn_profile),
            findViewById(R.id.theme_switcher_container),
            findViewById(R.id.btn_language),
            findViewById(R.id.btn_add_widget),
            findViewById(R.id.btn_logout)
        };
        int delay = 50;
        for (View v : items) {
            if (v != null) {
                animateViewInFromLeft(v, delay);
                delay += 70;
            }
        }
    }

    private void animateViewInFromLeft(View view, int delay) {
        view.setAlpha(0f);
        view.setTranslationX(-150f);
        view.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(500)
                .setStartDelay(delay)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void animateViewIn(View view, int delay) {
        view.setAlpha(0f);
        view.setTranslationY(60f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(delay)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
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
                // Removed restartListening from here to avoid double-triggering with onResults/onError
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
                if (matches != null && !matches.isEmpty()) {
                    String topMatch = matches.get(0);
                        processVoiceCommand(matches);
                }
                if (voiceDialogShowing) restartListening();
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partialMatches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partialMatches != null && !partialMatches.isEmpty()) {
                    String partial = partialMatches.get(0);
                    }
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
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isRussian ? "ru-RU" : "en-US");
                intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"ru-RU", "en-US"});
                intent.putExtra("android.speech.extra.LANGUAGE_SWITCH_ALLOWED", true);
                intent.putExtra("android.speech.extra.BILINGUAL_MODE", true);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10);
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
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
        View v = getLayoutInflater().inflate(R.layout.dialog_register_medicine, null);
        TextView tvTitle = v.findViewById(R.id.tv_dialog_title);
        TextView tvStockLabel = v.findViewById(R.id.tv_stock_label);
        TextView tvWarnLabel = v.findViewById(R.id.tv_warn_label);
        EditText nameIn = v.findViewById(R.id.dialog_name);
        MaterialButton ocrBtn = v.findViewById(R.id.btn_scan_ocr);
        EditText stockIn = v.findViewById(R.id.dialog_stock);
        EditText warnIn = v.findViewById(R.id.dialog_warn);
        MaterialButton expBtn = v.findViewById(R.id.dialog_btn_expiry);
        MaterialButton photoBtn = v.findViewById(R.id.btn_med_photo);
        MaterialButton btnCancel = v.findViewById(R.id.btn_cancel);
        MaterialButton btnSave = v.findViewById(R.id.btn_save);

        if (tvTitle != null) tvTitle.setText(tr("Register New Medicine", "Регистрация лекарства"));
        if (tvStockLabel != null) tvStockLabel.setText(tr("INITIAL STOCK", "НАЧАЛЬНЫЙ ЗАПАС"));
        if (tvWarnLabel != null) tvWarnLabel.setText(tr("WARNING DAYS (EXPIRY)", "ДНЕЙ ДО ИСТЕЧЕНИЯ"));

        nameIn.setHint(tr("Medicine Name", "Название лекарства"));
        ocrBtn.setText(tr("Scan Name with Camera", "Сканировать название"));
        stockIn.setHint(tr("Initial Stock", "Начальный запас"));
        warnIn.setHint(tr("Warning Days (Expiry)", "Дней до конца срока"));
        expBtn.setText(tr("Set Expiry Date", "Установить срок годности"));
        photoBtn.setText(tr("Add Medicine Photo", "Добавить фото"));
        btnCancel.setText(tr("Cancel", "Отмена"));
        btnSave.setText(tr("Add to Inventory", "Добавить"));

        ocrBtn.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ocrTargetEditText = nameIn;
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 104);
            } else {
                ocrTargetEditText = nameIn;
                launchOCRCamera();
            }
        });

        photoBtn.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                photoTargetMed = null; // Signal it's for new medicine
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 101);
            } else {
                photoTargetMed = null;
                launchCamera();
            }
        });

        expBtn.setOnClickListener(view -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view1, y, m, d) -> {
                tempExpiryInternal = d + "/" + (m + 1) + "/" + y;
                expBtn.setText(tr("Exp: ", "Срок: ") + tempExpiryInternal);
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(v)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(view -> dialog.dismiss());
        btnSave.setOnClickListener(view -> {
            String name = nameIn.getText().toString().trim();
            String stockStr = stockIn.getText().toString();
            String warnStr = warnIn.getText().toString();
            int stock = Integer.parseInt(stockStr.isEmpty() ? "0" : stockStr);
            int warn = Integer.parseInt(warnStr.isEmpty() ? "0" : warnStr);
            if (!name.isEmpty()) {
                saveToWarehouse(name, stock, tempExpiryInternal, warn, tempImagePath);
                tempImagePath = null; // reset
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void saveToWarehouse(String name, int stock, String exp, int warn, String imagePath) {
        Executors.newSingleThreadExecutor().execute(() -> {
            Medicine existing = db.medicineDao().getByNameAndUserId(name, currentUserId);
            String batch = stock + " pills (Exp: " + exp + ")";
            if (existing != null) {
                existing.batches = (existing.batches == null || existing.batches.isEmpty()) ? batch : existing.batches + "|" + batch;
                existing.expiryWarningDays = warn;
                if (imagePath != null) existing.imagePath = imagePath;
                existing.lastUpdated = System.currentTimeMillis();
                db.medicineDao().update(existing);
                FirestoreHelper.uploadMedicine(existing);
            } else {
                Medicine m = new Medicine(currentUserId, name, "");
                m.batches = batch; m.expiryWarningDays = warn;
                m.imagePath = imagePath;
                m.lastUpdated = System.currentTimeMillis();
                db.medicineDao().insert(m);
                FirestoreHelper.uploadMedicine(m);
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
        
        if (voiceDialog.getWindow() != null) {
            voiceDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        voiceDialog.show();
        
        initSpeechRecognizer();
        
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isRussian ? "ru-RU" : "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"ru-RU", "en-US"});
        intent.putExtra("android.speech.extra.LANGUAGE_SWITCH_ALLOWED", true);
        intent.putExtra("android.speech.extra.BILINGUAL_MODE", true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                speechRecognizer.startListening(intent);
            } catch (Exception e) {
                isListening = false;
            }
        }, 100);
    }

    private void processVoiceCommand(List<String> matches) {
        if (matches == null || matches.isEmpty()) return;
        
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Medicine> meds = db.medicineDao().getAllByUserId(currentUserId);
            boolean addedNew = false;

            // Use only the top match to avoid over-logging from similar-sounding alternatives
            String topResult = matches.get(0);
            // Pad with spaces for whole-phrase matching
            String rClean = " " + normalizeForVoice(topResult) + " ";

            for (Medicine m : meds) {
                String mClean = normalizeForVoice(m.name);
                if (mClean.length() < 2) continue;

                // Match if the recognized speech contains the medicine name as a whole phrase/word
                if (rClean.contains(" " + mClean + " ")) {
                    if (!detectedMeds.contains(m)) {
                        detectedMeds.add(m);
                        addedNew = true;
                    }
                }
            }

            if (addedNew) {
                runOnUiThread(this::updateDetectedMedsUI);
            }
        });
    }

    private String normalizeForVoice(String s) {
        if (s == null) return "";
        // Lowercase and replace punctuation/symbols with spaces
        s = s.toLowerCase().replaceAll("[^a-zа-я0-9]", " ").trim();
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == 'а') sb.append('a');
            else if (c == 'б') sb.append('b');
            else if (c == 'в') sb.append('v');
            else if (c == 'г') sb.append('g');
            else if (c == 'д') sb.append('d');
            else if (c == 'е' || c == 'ё' || c == 'э') sb.append('e');
            else if (c == 'ж') sb.append('j');
            else if (c == 'з') sb.append('z');
            else if (c == 'и' || c == 'й' || c == 'ы') sb.append('i');
            else if (c == 'к') sb.append('k');
            else if (c == 'л') sb.append('l');
            else if (c == 'м') sb.append('m');
            else if (c == 'н') sb.append('n');
            else if (c == 'о') sb.append('o');
            else if (c == 'п') sb.append('p');
            else if (c == 'р') sb.append('r');
            else if (c == 'с') sb.append('s');
            else if (c == 'т') sb.append('t');
            else if (c == 'у') sb.append('u');
            else if (c == 'ф') sb.append('f');
            else if (c == 'х') sb.append('h');
            else if (c == 'ц') sb.append('c');
            else if (c == 'ч') sb.append("ch");
            else if (c == 'ш') sb.append("sh");
            else if (c == 'ю') sb.append("yu");
            else if (c == 'я') sb.append("ya");
            else sb.append(c);
        }
        // Normalize multiple spaces to a single space for consistent phrase matching
        return sb.toString().replaceAll("\\s+", " ").trim();
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

        com.google.android.material.card.MaterialCardView imgCard = new com.google.android.material.card.MaterialCardView(this);
        imgCard.setRadius(20);
        imgCard.setCardElevation(0);
        imgCard.setStrokeWidth(2);
        imgCard.setStrokeColor(Color.parseColor("#15000000"));
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(90, 90);
        imgLp.setMargins(0, 0, 25, 0);
        imgCard.setLayoutParams(imgLp);
        
        ImageView ivMed = new ImageView(this);
        ivMed.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (m.imagePath != null && !m.imagePath.isEmpty() && new File(m.imagePath).exists()) {
            Glide.with(this).load(new File(m.imagePath)).into(ivMed);
            imgCard.setOnClickListener(v -> showExpandedImage(m));
        } else {
            ivMed.setImageResource(android.R.drawable.ic_dialog_info);
            ivMed.setPadding(20, 20, 20, 20);
            ivMed.setColorFilter(Color.parseColor(BLUE_COLOR));
            // Don't set click listener if no image
        }
        imgCard.addView(ivMed);

        ImageView ivArrow = new ImageView(this);
        ivArrow.setImageResource(androidx.appcompat.R.drawable.abc_ic_arrow_drop_right_black_24dp);
        ivArrow.setColorFilter(getThemeColor(R.attr.secondaryTextColor));

        header.addView(imgCard);
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
            FirestoreHelper.uploadMedicine(m);
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

    private Bitmap rotateBitmapIfRequired(Bitmap img, Uri selectedImage) throws IOException {
        InputStream input = getContentResolver().openInputStream(selectedImage);
        ExifInterface ei;
        if (Build.VERSION.SDK_INT > 23)
            ei = new ExifInterface(input);
        else
            ei = new ExifInterface(selectedImage.getPath());

        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(img, 270);
            default:
                return img;
        }
    }

    private Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    private void saveMedicineImage(Medicine m, Bitmap bitmap) {
        diskExecutor.execute(() -> {
            File dir = new File(getExternalFilesDir(null), "med_photos");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "med_" + m.id + "_" + System.currentTimeMillis() + ".jpg");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
                m.imagePath = file.getAbsolutePath();
                m.lastUpdated = System.currentTimeMillis();
                db.medicineDao().update(m);
                FirestoreHelper.uploadMedicine(m);
                bitmap.recycle(); // Free memory
                runOnUiThread(this::refreshCurrentTab);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void saveTempMedicineImage(Bitmap bitmap) {
        diskExecutor.execute(() -> {
            File dir = new File(getExternalFilesDir(null), "med_photos");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "new_med_" + System.currentTimeMillis() + ".jpg");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
                tempImagePath = file.getAbsolutePath();
                bitmap.recycle(); // Free memory
                runOnUiThread(() -> Toast.makeText(this, tr("Photo attached", "Фото прикреплено"), Toast.LENGTH_SHORT).show());
            } catch (Exception e) { e.printStackTrace(); }
        });
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
                        FirestoreHelper.uploadMedicine(m);
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
                FirestoreHelper.uploadMedicine(m);
                setupAlarms(m.name, tempTimes);
            }
            runOnUiThread(() -> { 
                refreshCurrentTab(); 
                updateWidget();
                Toast.makeText(this, tr("Schedule Updated", "Расписание обновлено"), Toast.LENGTH_SHORT).show(); 
            });
        });
    }

    private void showExpandedImage(Medicine m) {
        if (m.imagePath == null || m.imagePath.isEmpty()) {
            Toast.makeText(this, tr("No photo available", "Нет фото"), Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(m.imagePath);
        if (!file.exists()) {
            Toast.makeText(this, tr("Photo file not found", "Файл фото не найден"), Toast.LENGTH_SHORT).show();
            return;
        }

        com.google.android.material.card.MaterialCardView root = new com.google.android.material.card.MaterialCardView(this);
        root.setRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics()));
        root.setCardElevation(0);
        root.setStrokeWidth(0);
        root.setBackgroundTintList(ColorStateList.valueOf(getThemeColor(android.R.attr.windowBackground)));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);
        layout.setGravity(Gravity.CENTER);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(m.name);
        tvTitle.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, 0, 0, 40);
        tvTitle.setLayoutParams(titleLp);
        layout.addView(tvTitle);

        com.google.android.material.card.MaterialCardView imgCard = new com.google.android.material.card.MaterialCardView(this);
        imgCard.setRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics()));
        imgCard.setCardElevation(0);
        imgCard.setStrokeWidth(0);
        imgCard.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0A000000")));
        LinearLayout.LayoutParams imgCardLp = new LinearLayout.LayoutParams(-1, -2);
        imgCardLp.setMargins(0, 0, 0, 60);
        imgCard.setLayoutParams(imgCardLp);

        ImageView iv = new ImageView(this);
        Glide.with(this)
                .load(file)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(iv);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imgCard.addView(iv);
        layout.addView(imgCard);

        MaterialButton btnClose = new MaterialButton(this);
        btnClose.setText(tr("Close", "Закрыть"));
        btnClose.setAllCaps(false);
        btnClose.setCornerRadius((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, getResources().getDisplayMetrics()));
        btnClose.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(BLUE_COLOR)));
        btnClose.setTextColor(Color.WHITE);
        btnClose.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics()));
        btnClose.setLayoutParams(btnLp);
        
        layout.addView(btnClose);

        MaterialButton btnDeleteImg = new MaterialButton(this);
        btnDeleteImg.setText(tr("Delete Photo", "Удалить фото"));
        btnDeleteImg.setAllCaps(false);
        btnDeleteImg.setCornerRadius((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, getResources().getDisplayMetrics()));
        btnDeleteImg.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F44336")));
        btnDeleteImg.setTextColor(Color.WHITE);
        btnDeleteImg.setIconResource(android.R.drawable.ic_menu_delete);
        btnDeleteImg.setIconTint(ColorStateList.valueOf(Color.WHITE));
        btnDeleteImg.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-1, (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics()));
        delLp.setMargins(0, 20, 0, 0);
        btnDeleteImg.setLayoutParams(delLp);

        layout.addView(btnDeleteImg);
        root.addView(layout);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(root)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnDeleteImg.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle(tr("Delete Photo", "Удалить фото"))
                .setMessage(tr("Are you sure you want to delete this medicine photo?", "Вы уверены, что хотите удалить фото этого лекарства?"))
                .setPositiveButton(tr("Delete", "Удалить"), (d, w) -> {
                    diskExecutor.execute(() -> {
                        if (file.exists()) file.delete();
                        m.imagePath = null;
                        m.lastUpdated = System.currentTimeMillis();
                        db.medicineDao().update(m);
                        FirestoreHelper.uploadMedicine(m);
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            refreshCurrentTab();
                            Toast.makeText(this, tr("Photo deleted", "Фото удалено"), Toast.LENGTH_SHORT).show();
                        });
                    });
                })
                .setNegativeButton(tr("Cancel", "Отмена"), null)
                .show();
        });
        dialog.show();
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
        btnDeleteMed.setCornerRadius(32);
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
                .setMessage(tr("Are you sure you want to delete ", "Вы уверены, что хотите удалить ") + m.name + tr(" и всю историю?", " and all its history?"))
                .setPositiveButton(tr("Delete", "Удалить"), (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> { 
                        db.medicineDao().delete(m); 
                        FirestoreHelper.deleteMedicine(m);
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
                FirestoreHelper.uploadMedicine(m);
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
                FirestoreHelper.uploadMedicine(m);
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
            btnDate.setCornerRadius(32);
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
                    m.lastUpdated = System.currentTimeMillis();
                    Executors.newSingleThreadExecutor().execute(() -> { 
                        db.medicineDao().update(m); 
                        FirestoreHelper.uploadMedicine(m);
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
            btn.setText(t); btn.setAllCaps(false); btn.setCornerRadius(32);
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
            btn.setAllCaps(false); btn.setCornerRadius(32);
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

    private void showPdfDatePicker(List<Medicine> meds) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar selectedDate = Calendar.getInstance();
            selectedDate.set(year, month, dayOfMonth);
            generatePdf(meds, selectedDate);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void generatePdf(List<Medicine> meds, Calendar selectedDate) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 103);
                return;
            }
        }

        PdfDocument document = new PdfDocument();
        int pageNum = 1;
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNum).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        String startStr = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(selectedDate.getTime());
        String endStr = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date());

        paint.setTextSize(22);
        paint.setFakeBoldText(true);
        canvas.drawText(tr("Medicine Usage Report", "Отчет об использовании лекарств"), 50, 60, paint);
        
        paint.setTextSize(14);
        paint.setFakeBoldText(false);
        canvas.drawText(tr("From: ", "С: ") + startStr + tr(" To: ", " По: ") + endStr, 50, 95, paint);

        paint.setStrokeWidth(1);
        canvas.drawLine(50, 110, 545, 110, paint);

        int y = 140;
        boolean foundAny = false;

        for (Medicine m : meds) {
            if (m.history == null || m.history.isEmpty()) continue;
            
            Map<String, List<String>> rangeData = new LinkedHashMap<>();
            String[] parts = m.history.split(",");
            for (int i = 0; i < parts.length - 1; i += 2) {
                String datePart = parts[i].trim();
                String timeAndStatus = parts[i + 1].trim();
                
                if (isDateAfterOrEqual(datePart, selectedDate)) {
                    if (!rangeData.containsKey(datePart)) rangeData.put(datePart, new ArrayList<>());
                    rangeData.get(datePart).add(timeAndStatus);
                }
            }

            if (!rangeData.isEmpty()) {
                foundAny = true;
                if (y > 720) {
                    document.finishPage(page);
                    pageNum++;
                    pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNum).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 60;
                }
                
                paint.setTextSize(16);
                paint.setFakeBoldText(true);
                canvas.drawText(m.name + ":", 50, y, paint);
                y += 25;
                
                paint.setTextSize(14);
                paint.setFakeBoldText(false);
                for (String dKey : rangeData.keySet()) {
                    if (y > 780) {
                        document.finishPage(page);
                        pageNum++;
                        pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNum).create();
                        page = document.startPage(pageInfo);
                        canvas = page.getCanvas();
                        y = 60;
                    }
                    paint.setFakeBoldText(true);
                    canvas.drawText(dKey + ":", 60, y, paint);
                    paint.setFakeBoldText(false);
                    y += 20;
                    
                    for (String status : rangeData.get(dKey)) {
                        if (y > 800) {
                            document.finishPage(page);
                            pageNum++;
                            pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNum).create();
                            page = document.startPage(pageInfo);
                            canvas = page.getCanvas();
                            y = 60;
                        }
                        canvas.drawText(" • " + status, 80, y, paint);
                        y += 20;
                    }
                    y += 5;
                }
                y += 15;
            }
        }

        if (!foundAny) {
            paint.setTextSize(14);
            canvas.drawText(tr("No data for this range.", "Нет данных за этот период."), 50, y, paint);
        }

        document.finishPage(page);

        String fileName = "Medicine_Report_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(selectedDate.getTime()) + ".pdf";
        
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/");
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        } else {
            collection = MediaStore.Files.getContentUri("external");
        }

        Uri uri = getContentResolver().insert(collection, contentValues);
        if (uri != null) {
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                document.writeTo(outputStream);
                Toast.makeText(this, tr("PDF saved to Downloads", "PDF сохранен в Загрузки"), Toast.LENGTH_LONG).show();
                
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/pdf");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, tr("Open PDF", "Открыть PDF")));
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        document.close();
    }

    private boolean isDateAfterOrEqual(String historyDateStr, Calendar selectedDate) {
        try {
            // historyDateStr might be "MMM dd" (Apr 01) or "MMM dd, HH:mm" (Apr 01, 10:30)
            String cleanDate = historyDateStr.split(",")[0].trim();
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.US);
            Date hDateParsed = sdf.parse(cleanDate);
            if (hDateParsed == null) return false;

            Calendar hCal = Calendar.getInstance();
            Calendar sCal = (Calendar) selectedDate.clone();
            Calendar now = Calendar.getInstance();

            hCal.setTime(hDateParsed);
            hCal.set(Calendar.YEAR, now.get(Calendar.YEAR));
            hCal.set(Calendar.HOUR_OF_DAY, 0); hCal.set(Calendar.MINUTE, 0); hCal.set(Calendar.SECOND, 0); hCal.set(Calendar.MILLISECOND, 0);
            
            sCal.set(Calendar.HOUR_OF_DAY, 0); sCal.set(Calendar.MINUTE, 0); sCal.set(Calendar.SECOND, 0); sCal.set(Calendar.MILLISECOND, 0);

            if (hCal.after(now)) {
                hCal.add(Calendar.YEAR, -1);
            }

            return !hCal.before(sCal);
        } catch (Exception e) {
            return false;
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
        b.setCornerRadius(32);
        b.setPadding(40, 30, 40, 30); 
        return b; 
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 104) { // Camera
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ocrTargetEditText != null) {
                    try {
                        takePictureLauncher.launch(null);
                    } catch (Exception e) {
                        Toast.makeText(this, "Error launching camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, tr("Camera permission denied", "Доступ к камере отклонен"), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 101 || requestCode == 103) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, tr("Permission granted. Please try again.", "Разрешение получено. Попробуйте еще раз."), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }
}
