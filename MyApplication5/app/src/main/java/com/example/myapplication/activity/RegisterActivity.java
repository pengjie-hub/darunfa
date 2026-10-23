package com.example.myapplication.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.myapplication.R;
import com.example.myapplication.remote.ApiResponse;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.LoginResponse;
import com.example.myapplication.remote.dto.RegisterRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout tilUsername, tilPassword, tilConfirmPassword;
    private TextInputEditText et_username, et_password, et_confirm_password;
    private MaterialButton register_btn;
    private TextView tvToLogin;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        RetrofitClient.init(this);

        tilUsername = findViewById(R.id.til_username);
        tilPassword = findViewById(R.id.til_password);
        tilConfirmPassword = findViewById(R.id.til_confirm_password);
        et_username = findViewById(R.id.et_username);
        et_password = findViewById(R.id.et_password);
        et_confirm_password = findViewById(R.id.et_confirm_password);
        register_btn = findViewById(R.id.register_btn);
        tvToLogin = findViewById(R.id.tv_to_login);
        toolbar = findViewById(R.id.toolbar);

        toolbar.setNavigationOnClickListener(v -> finish());

        tvToLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        register_btn.setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        String username = et_username.getText().toString().trim();
        String password = et_password.getText().toString().trim();
        String confirm = et_confirm_password.getText().toString().trim();

        // 清空旧错误
        tilUsername.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);

        boolean valid = true;
        if (TextUtils.isEmpty(username)) {
            tilUsername.setError("请输入用户名");
            valid = false;
        } else if (username.length() < 2 || username.length() > 20) {
            tilUsername.setError("用户名长度需为 2-20 个字符");
            valid = false;
        }
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("请输入密码");
            valid = false;
        } else if (password.length() < 6 || password.length() > 20) {
            tilPassword.setError("密码长度需为 6-20 个字符");
            valid = false;
        }
        if (TextUtils.isEmpty(confirm)) {
            tilConfirmPassword.setError("请再次输入密码");
            valid = false;
        } else if (!password.equals(confirm)) {
            tilConfirmPassword.setError("两次输入的密码不一致");
            valid = false;
        }
        if (!valid) return;

        register_btn.setEnabled(false);
        RetrofitClient.api().register(new RegisterRequest(username, password))
                .enqueue(new Callback<ApiResponse<LoginResponse>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<LoginResponse>> call,
                                           Response<ApiResponse<LoginResponse>> response) {
                        register_btn.setEnabled(true);
                        ApiResponse<LoginResponse> body = response.body();
                        if (body != null && body.isSuccess()) {
                            Toast.makeText(RegisterActivity.this, "注册成功,请登录",
                                    Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                            finish();
                        } else {
                            String msg = (body != null && body.message != null)
                                    ? body.message : "注册失败";
                            tilUsername.setError(msg);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<LoginResponse>> call, Throwable t) {
                        register_btn.setEnabled(true);
                        Toast.makeText(RegisterActivity.this, "无法连接服务器，请确认后端已启动",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
