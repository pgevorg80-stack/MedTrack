package com.example.gevorgpetrosyan;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class SignUpActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText etEmail, etPassword;
    private View signupCard, signupTitle, logo, loginLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();

        etEmail = findViewById(R.id.et_signup_email);
        etPassword = findViewById(R.id.et_signup_password);
        signupCard = findViewById(R.id.signup_card);
        signupTitle = findViewById(R.id.signup_title);
        logo = findViewById(R.id.imageViewSignup);
        loginLink = findViewById(R.id.tv_login_link);

        findViewById(R.id.btn_signup).setOnClickListener(v -> registerUser());

        loginLink.setOnClickListener(v -> {
            finish(); // Go back to LoginActivity
        });

        startEntryAnimations();
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

        // Animate
        logo.animate().alpha(1f).translationY(0f).setDuration(800).setStartDelay(200).start();
        signupTitle.animate().alpha(1f).translationY(0f).setDuration(800).setStartDelay(400).start();
        signupCard.animate().alpha(1f).translationY(0f).setDuration(800).setStartDelay(600).start();
        loginLink.animate().alpha(1f).setDuration(800).setStartDelay(1000).start();
    }

    private void registerUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || password.length() < 6) {
            Toast.makeText(this, "Email required & Password min 6 chars", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Account Created Successfully!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(SignUpActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this, "Registration Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}