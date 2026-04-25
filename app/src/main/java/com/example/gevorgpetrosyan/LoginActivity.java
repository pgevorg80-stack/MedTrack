package com.example.gevorgpetrosyan;

import android.animation.ObjectAnimator;
import android.view.animation.CycleInterpolator;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText etEmail, etPassword;
    private View loginCard, loginTitle, logo, registerLink, themeSwitcher;
    private ImageView ivThemeKnob;
    private TextInputLayout tilEmail, tilPassword;
    private MaterialButton btnLangToggle;
    private boolean isRussian = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Hide navigation bar for immersive experience
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars());

        requestNotificationPermission();

        mAuth = FirebaseAuth.getInstance();

        SharedPreferences prefs = getSharedPreferences("LangPrefs", MODE_PRIVATE);
        isRussian = prefs.getBoolean("IsRussian", false);

        etEmail = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        loginCard = findViewById(R.id.login_card);
        loginTitle = findViewById(R.id.login_title);
        logo = findViewById(R.id.imageViewLogin);
        registerLink = findViewById(R.id.tv_register_link);
        btnLangToggle = findViewById(R.id.btn_lang_toggle);
        themeSwitcher = findViewById(R.id.theme_switcher_container);
        ivThemeKnob = findViewById(R.id.iv_theme_icon_knob);

        updateTranslations();
        updateThemeUI(false); // Initial state without animation

        btnLangToggle.setOnClickListener(v -> {
            animateClick(v);
            isRussian = !isRussian;
            getSharedPreferences("LangPrefs", MODE_PRIVATE).edit().putBoolean("IsRussian", isRussian).apply();
            updateTranslations();
        });

        themeSwitcher.setOnClickListener(v -> {
            animateClick(v);
            toggleTheme();
        });

        TextView loginSubTitle = findViewById(R.id.login_subtitle);

        findViewById(R.id.btn_login).setOnClickListener(v -> {
            animateClick(v);
            loginUser();
        });
        findViewById(R.id.btn_guest).setOnClickListener(v -> {
            animateClick(v);
            loginAsGuest();
        });
        registerLink.setOnClickListener(v -> {
            animateClick(v);
            Intent intent = new Intent(LoginActivity.this, SignUpActivity.class);
            startActivity(intent);
        });

        logo.setOnClickListener(v -> {
            v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
            }).start();
        });

        startEntryAnimations();
    }

    private void animateClick(View view) {
        view.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                .start();
    }

    private void shakeView(View view) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, "translationX", 0, 10f);
        animator.setDuration(150);
        animator.setInterpolator(new CycleInterpolator(3));
        animator.start();
    }

    private void updateTranslations() {
        TextView loginSubTitle = findViewById(R.id.login_subtitle);
        MaterialButton btnLogin = findViewById(R.id.btn_login);
        MaterialButton btnGuest = findViewById(R.id.btn_guest);

        ((TextView)loginTitle).setText(tr("Welcome Back", "С возвращением"));
        if (loginSubTitle != null) loginSubTitle.setText(tr("Sign in to continue tracking", "Войдите, чтобы продолжить"));
        
        if (tilEmail != null) tilEmail.setHint(tr("Email Address", "Электронная почта"));
        else etEmail.setHint(tr("Email Address", "Электронная почта"));
        
        if (tilPassword != null) tilPassword.setHint(tr("Password", "Пароль"));
        else etPassword.setHint(tr("Password", "Пароль"));

        btnLogin.setText(tr("Sign In", "Войти"));
        btnGuest.setText(tr("Continue as Guest", "Продолжить как гость"));
        ((TextView)registerLink).setText(tr("New here? Create Account", "Впервые здесь? Создать аккаунт"));
        btnLangToggle.setText(isRussian ? "RU" : "EN");
    }

    private void toggleTheme() {
        SharedPreferences pref = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
        boolean isDark = pref.getBoolean("IsDarkMode", false);
        boolean nextDark = !isDark;

        pref.edit().putBoolean("IsDarkMode", nextDark).apply();

        float density = getResources().getDisplayMetrics().density;
        float targetX = nextDark ? 26 * density : 0;

        ivThemeKnob.animate()
                .translationX(targetX)
                .setDuration(300)
                .withEndAction(() -> {
                    AppCompatDelegate.setDefaultNightMode(nextDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
                })
                .start();
    }

    private void updateThemeUI(boolean animate) {
        SharedPreferences pref = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
        boolean isDark = pref.getBoolean("IsDarkMode", false);

        float density = getResources().getDisplayMetrics().density;
        float translationPx = (isDark ? 26 : 0) * density;

        if (animate) {
            ivThemeKnob.animate()
                    .translationX(translationPx)
                    .setDuration(300)
                    .start();
        } else {
            ivThemeKnob.setTranslationX(translationPx);
        }

        ivThemeKnob.setImageResource(isDark ? R.drawable.ic_moon : R.drawable.ic_sun);
        ivThemeKnob.setImageTintList(ColorStateList.valueOf(Color.parseColor("#2196F3")));
    }

    private void applySavedTheme() {
        SharedPreferences pref = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
        AppCompatDelegate.setDefaultNightMode(pref.getBoolean("IsDarkMode", false) ?
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void startEntryAnimations() {
        // Initial state
        logo.setAlpha(0f);
        logo.setTranslationY(-50f);
        loginTitle.setAlpha(0f);
        loginTitle.setTranslationY(-30f);
        loginCard.setAlpha(0f);
        loginCard.setTranslationY(100f);
        registerLink.setAlpha(0f);

        // Top bar items initial state
        themeSwitcher.setAlpha(0f);
        themeSwitcher.setTranslationX(-100f);
        btnLangToggle.setAlpha(0f);
        btnLangToggle.setTranslationX(100f);

        // Inputs initial state
        if (tilEmail != null) { tilEmail.setAlpha(0f); tilEmail.setTranslationY(50f); }
        if (tilPassword != null) { tilPassword.setAlpha(0f); tilPassword.setTranslationY(50f); }

        // Animate
        logo.animate().alpha(1f).translationY(0f).setDuration(800).setStartDelay(200).withEndAction(this::startFloatingAnimation).start();
        loginTitle.animate().alpha(1f).translationY(0f).setDuration(800).setStartDelay(400).start();
        loginCard.animate().alpha(1f).translationY(0f).setDuration(800).setStartDelay(600).start();
        
        // Top bar animations (similar to settings drawer items)
        themeSwitcher.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(600)
                .setStartDelay(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        btnLangToggle.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(600)
                .setStartDelay(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        if (tilEmail != null) {
            tilEmail.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(800).start();
        }
        if (tilPassword != null) {
            tilPassword.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(900).start();
        }
        
        registerLink.animate().alpha(1f).setDuration(800).setStartDelay(1100).start();

        // Animate background blobs
        View bgBlob = findViewById(R.id.bg_blob);
        View bgBlob2 = findViewById(R.id.bg_blob_secondary);
        
        if (bgBlob != null) {
            animateBlob(bgBlob, 1.2f, 3000);
        }
        if (bgBlob2 != null) {
            animateBlob(bgBlob2, 1.3f, 4000);
        }
    }

    private void animateBlob(View blob, float scale, int duration) {
        blob.animate()
                .scaleX(scale)
                .scaleY(scale)
                .rotation(blob.getRotation() + 45f)
                .setDuration(duration)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        blob.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .rotation(blob.getRotation() - 45f)
                                .setDuration(duration)
                                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                                .withEndAction(this)
                                .start();
                    }
                }).start();
    }

    private void startFloatingAnimation() {
        logo.animate()
                .translationY(-20f)
                .setDuration(2000)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    logo.animate()
                            .translationY(20f)
                            .setDuration(2000)
                            .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                            .withEndAction(this::startFloatingAnimation)
                            .start();
                })
                .start();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 102);
            }
        }
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, tr("Please fill all fields", "Пожалуйста, заполните все поля"), Toast.LENGTH_SHORT).show();
            if (TextUtils.isEmpty(email)) shakeView(tilEmail != null ? tilEmail : etEmail);
            if (TextUtils.isEmpty(password)) shakeView(tilPassword != null ? tilPassword : etPassword);
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        goToMain();
                    } else {
                        Toast.makeText(this, tr("Login Failed: ", "Ошибка входа: ") + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loginAsGuest() {
        mAuth.signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        goToMain();
                    } else {
                        Toast.makeText(this, tr("Guest Login Failed: ", "Ошибка гостевого входа: ") + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void goToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private String tr(String en, String ru) {
        return isRussian ? ru : en;
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(currentUser != null){
            goToMain();
        }
    }
}