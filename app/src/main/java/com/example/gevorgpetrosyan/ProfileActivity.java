package com.example.gevorgpetrosyan;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvUserEmail;
    private EditText etNewPassword, etConfirmPassword;
    private MaterialButton btnSavePassword;
    private FirebaseAuth mAuth;
    private boolean isRussian = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        loadLanguagePreference();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        tvUserEmail = findViewById(R.id.tv_profile_email);
        etNewPassword = findViewById(R.id.edit_password);
        etConfirmPassword = findViewById(R.id.edit_password_confirm);
        btnSavePassword = findViewById(R.id.btn_save_profile);

        updateUI();

        if (user != null) {
            tvUserEmail.setText(user.getEmail());
        } else {
            tvUserEmail.setText(tr("No User Logged In", "Пользователь не авторизован"));
        }

        btnSavePassword.setOnClickListener(v -> {
            String newPassword = etNewPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            if (TextUtils.isEmpty(newPassword) || newPassword.length() < 6) {
                Toast.makeText(this, tr("Password must be at least 6 characters", "Пароль должен быть не менее 6 символов"), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(this, tr("Passwords do not match", "Пароли не совпадают"), Toast.LENGTH_SHORT).show();
                return;
            }

            if (user != null) {
                user.updatePassword(newPassword)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(this, tr("Password Updated!", "Пароль обновлен!"), Toast.LENGTH_SHORT).show();
                                etNewPassword.setText("");
                                etConfirmPassword.setText("");
                            } else {
                                String error = task.getException() != null ? task.getException().getMessage() : tr("Update failed", "Ошибка обновления");
                                Toast.makeText(this, tr("Error: ", "Ошибка: ") + error, Toast.LENGTH_LONG).show();
                            }
                        });
            }
        });
    }

    private void loadLanguagePreference() {
        isRussian = getSharedPreferences("LangPrefs", MODE_PRIVATE).getBoolean("IsRussian", false);
    }

    private String tr(String en, String ru) {
        return isRussian ? ru : en;
    }

    private void updateUI() {
        etNewPassword.setHint(tr("New Password", "Новый пароль"));
        etConfirmPassword.setHint(tr("Confirm New Password", "Подтвердите пароль"));
        btnSavePassword.setText(tr("Save Changes", "Сохранить изменения"));
    }
}