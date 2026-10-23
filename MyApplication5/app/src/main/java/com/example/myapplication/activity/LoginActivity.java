package com.example.myapplication.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.remote.ApiResponse;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.SessionManager;
import com.example.myapplication.remote.dto.LoginRequest;
import com.example.myapplication.remote.dto.LoginResponse;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilUsername, tilPassword;
    private TextInputEditText et_username, et_password;
    private MaterialButton login_btn;
    private TextView tvToRegister;
    private SharedPreferences mSharedPreferences;
    private CheckBox checkBox;
    private boolean is_login = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        RetrofitClient.init(this);

        tilUsername = findViewById(R.id.til_username);
        tilPassword = findViewById(R.id.til_password);
        et_username = findViewById(R.id.et_username);
        et_password = findViewById(R.id.et_password);
        login_btn = findViewById(R.id.login_btn);
        tvToRegister = findViewById(R.id.tv_to_register);
        checkBox = findViewById(R.id.remember_password);
        mSharedPreferences = getSharedPreferences("user", MODE_PRIVATE);

        is_login = mSharedPreferences.getBoolean("is_login", false);
        if (is_login) {
            String username = mSharedPreferences.getString("username", null);
            String password = mSharedPreferences.getString("password", null);
            et_username.setText(username);
            et_password.setText(password);
            checkBox.setChecked(true);
        }

        checkBox.setOnCheckedChangeListener((compoundButton, b) -> is_login = b);

        tvToRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));

        login_btn.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String username = et_username.getText().toString().trim();
        String password = et_password.getText().toString().trim();

        // 清空旧错误
        tilUsername.setError(null);
        tilPassword.setError(null);

        boolean valid = true;
        if (TextUtils.isEmpty(username)) {
            tilUsername.setError("请输入用户名");
            valid = false;
        } else if (username.length() < 2) {
            tilUsername.setError("用户名至少2个字符");
            valid = false;
        }
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("请输入密码");
            valid = false;
        } else if (password.length() < 6) {
            tilPassword.setError("密码至少6个字符");
            valid = false;
        }
        if (!valid) return;

        // 异步调用后端登录接口
        login_btn.setEnabled(false);
        RetrofitClient.api().login(new LoginRequest(username, password))
                .enqueue(new Callback<ApiResponse<LoginResponse>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<LoginResponse>> call,
                                           Response<ApiResponse<LoginResponse>> response) {
                        login_btn.setEnabled(true);
                        ApiResponse<LoginResponse> body = response.body();
                        if (body != null && body.isSuccess() && body.data != null) {
                            SessionManager.save(body.data.token, body.data.user.id);

                            SharedPreferences.Editor edit = mSharedPreferences.edit();
                            edit.putBoolean("is_login", is_login);
                            edit.putString("username", username);
                            edit.putString("password", password);
                            edit.putInt("user_id", (int) body.data.user.id);
                            edit.apply();

                            Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            String msg = (body != null && body.message != null)
                                    ? body.message : "登录失败";
                            Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<LoginResponse>> call, Throwable t) {
                        login_btn.setEnabled(true);
                        Toast.makeText(LoginActivity.this, "无法连接服务器，请确认后端已启动",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
