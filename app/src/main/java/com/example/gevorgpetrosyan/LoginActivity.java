package com.example.gevorgpetrosyan;

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

import com.google.android.material.button.MaterialButton;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText etEmail, etPassword;
    private View loginCard, loginTitle, logo, registerLink, themeSwitcher;
    private TextView tvLight, tvDark;
    private ImageView ivThemeKnob;
    private TextInputLayout tilEmail, tilPassword;
    private MaterialButton btnLangToggle;
    private boolean isRussian = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

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
        tvLight = findViewById(R.id.tv_light_label);
        tvDark = findViewById(R.id.tv_dark_label);
        ivThemeKnob = findViewById(R.id.iv_theme_icon_knob);

        updateTranslations();
        updateThemeUI(false); // Initial state without animation

        btnLangToggle.setOnClickListener(v -> {
            isRussian = !isRussian;
            getSharedPreferences("LangPrefs", MODE_PRIVATE).edit().putBoolean("IsRussian", isRussian).apply();
            updateTranslations();
        });

        themeSwitcher.setOnClickListener(v -> toggleTheme());

        TextView loginSubTitle = findViewById(R.id.login_subtitle);

        findViewById(R.id.btn_login).setOnClickListener(v -> loginUser());
        findViewById(R.id.btn_guest).setOnClickListener(v -> loginAsGuest());
        registerLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignUpActivity.class);
            startActivity(intent);
        });

        startEntryAnimations();
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

        if (tvLight != null) tvLight.setText(tr("Light", "Светлая"));
        if (tvDark != null) tvDark.setText(tr("Dark", "Темная"));
    }

    private void toggleTheme() {
        int currentMode = AppCompatDelegate.getDefaultNightMode();
        boolean isDark = (currentMode == AppCompatDelegate.MODE_NIGHT_YES);
        
        if (isDark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
        updateThemeUI(true);
    }

    private void updateThemeUI(boolean animate) {
        boolean isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES;
        float targetX = isDark ? 26f : 0f; // Simplified, in real app use dimension or calculate
        
        // Convert dp to px for more accurate translation
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
        
        // Update label opacity
        if (tvLight != null) tvLight.setAlpha(isDark ? 0.5f : 1.0f);
        if (tvDark != null) tvDark.setAlpha(isDark ? 1.0f : 0.5f);
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

        // Inputs initial state for Profile-like animation
        if (tilEmail != null) { tilEmail.setAlpha(0f); tilEmail.setTranslationY(50f); }
        if (tilPassword != null) { tilPassword.setAlpha(0f); tilPassword.setTranslationY(50f); }

        // Animate
        logo.animate().alpha(1f).translationY(0f).setDuration(800).setStartDelay(200).start();
        loginTitle.animate().alpha(1f).translationY(0f).setDuration(800).setStartDelay(400).start();
        loginCard.animate().alpha(1f).translationY(0f).setDuration(800).setStartDelay(600).start();
        
        if (tilEmail != null) {
            tilEmail.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(800).start();
        }
        if (tilPassword != null) {
            tilPassword.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(900).start();
        }
        
        registerLink.animate().alpha(1f).setDuration(800).setStartDelay(1100).start();
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