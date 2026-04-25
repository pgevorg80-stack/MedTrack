package com.example.gevorgpetrosyan;

import android.animation.ObjectAnimator;
import android.view.animation.CycleInterpolator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import com.google.firebase.auth.FirebaseAuth;

public class SignUpActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText etEmail, etPassword;
    private View signupCard, signupTitle, logo, loginLink, themeSwitcher;
    private ImageView ivThemeKnob;
    private TextInputLayout tilEmail, tilPassword;
    private MaterialButton btnLangToggle;
    private boolean isRussian = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Hide navigation bar for immersive experience
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars());

        mAuth = FirebaseAuth.getInstance();

        android.content.SharedPreferences prefs = getSharedPreferences("LangPrefs", MODE_PRIVATE);
        isRussian = prefs.getBoolean("IsRussian", false);

        etEmail = findViewById(R.id.et_signup_email);
        etPassword = findViewById(R.id.et_signup_password);
        signupCard = findViewById(R.id.signup_card);
        signupTitle = findViewById(R.id.signup_title);
        logo = findViewById(R.id.imageViewSignup);
        loginLink = findViewById(R.id.tv_login_link);
        tilEmail = findViewById(R.id.til_signup_email);
        tilPassword = findViewById(R.id.til_signup_password);
        btnLangToggle = findViewById(R.id.btn_lang_toggle_signup);
        themeSwitcher = findViewById(R.id.theme_switcher_container_signup);
        ivThemeKnob = findViewById(R.id.iv_theme_icon_knob_signup);

        updateTranslations();
        updateThemeUI(false);

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

        TextView signupSubTitle = findViewById(R.id.signup_subtitle);

        findViewById(R.id.btn_signup).setOnClickListener(v -> {
            animateClick(v);
            registerUser();
        });

        loginLink.setOnClickListener(v -> {
            animateClick(v);
            finish(); // Go back to LoginActivity
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
        TextView signupSubTitle = findViewById(R.id.signup_subtitle);
        MaterialButton btnSignup = findViewById(R.id.btn_signup);

        if (signupSubTitle != null) signupSubTitle.setText(tr("Join our health community", "Присоединяйтесь к нашему сообществу"));
        ((TextView)signupTitle).setText(tr("Create Account", "Создать аккаунт"));
        
        if (tilEmail != null) tilEmail.setHint(tr("Email Address", "Электронная почта"));
        else etEmail.setHint(tr("Email Address", "Электронная почта"));
        
        if (tilPassword != null) tilPassword.setHint(tr("Create Password", "Придумайте пароль"));
        else etPassword.setHint(tr("Create Password", "Придумайте пароль"));

        btnSignup.setText(tr("Create Account", "Создать аккаунт"));
        ((TextView)loginLink).setText(tr("Already have an account? Sign In", "Уже есть аккаунт? Войти"));
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
        signupTitle.setAlpha(0f);
        signupTitle.setTranslationY(-30f);
        signupCard.setAlpha(0f);
        signupCard.setTranslationY(100f);
        loginLink.setAlpha(0f);

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
        signupTitle.animate().alpha(1f).translationY(0f).setDuration(800).setStartDelay(400).start();
        signupCard.animate().alpha(1f).translationY(0f).setDuration(800).setStartDelay(600).start();

        // Top bar animations
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

        loginLink.animate().alpha(1f).setDuration(800).setStartDelay(1100).start();

        // Animate background blobs
        View bgBlob1 = findViewById(R.id.bg_blob_signup);
        View bgBlob2 = findViewById(R.id.bg_blob_signup_secondary);
        if (bgBlob1 != null) animateBlob(bgBlob1, 1.2f, 3500);
        if (bgBlob2 != null) animateBlob(bgBlob2, 1.3f, 4500);
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

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void registerUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || password.length() < 6) {
            Toast.makeText(this, tr("Email required & Password min 6 chars", "Требуется Email и пароль от 6 символов"), Toast.LENGTH_SHORT).show();
            if (TextUtils.isEmpty(email)) shakeView(tilEmail != null ? tilEmail : etEmail);
            if (password.length() < 6) shakeView(tilPassword != null ? tilPassword : etPassword);
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, tr("Account Created Successfully!", "Аккаунт успешно создан!"), Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(SignUpActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this, tr("Registration Failed: ", "Ошибка регистрации: ") + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String tr(String en, String ru) {
        return isRussian ? ru : en;
    }
}
