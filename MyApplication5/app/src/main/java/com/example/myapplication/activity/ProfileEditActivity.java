package com.example.myapplication.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.myapplication.R;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.data.UserRepository;
import com.example.myapplication.remote.ApiResponse;
import com.example.myapplication.remote.ApiService;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.UpdatePasswordRequest;
import com.example.myapplication.remote.dto.UpdateProfileRequest;
import com.example.myapplication.remote.dto.UserDTO;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.util.LinkedHashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 编辑个人信息页面（远程优先架构）
 * <p>
 * 数据来源优先级:
 * <ol>
 *   <li><b>远程后端</b> {@code GET /api/user/profile} —— 权威数据源, 与登录认证共用同一套用户体系;</li>
 *   <li><b>本地 SQLite</b> user_table —— 仅作离线兜底缓存;</li>
 *   <li>两者都不可用 → 友好错误态 + 重试 / 重新登录入口。</li>
 * </ol>
 * <p>
 * 字段语义严格对齐后端:
 * <ul>
 *   <li>{@code username} —— 登录凭据, <b>只读不可改</b>;</li>
 *   <li>{@code nickname} —— 昵称(显示名), 可改;</li>
 *   <li>{@code phone} / {@code avatar} —— 可改;</li>
 *   <li>密码走独立接口 {@code PUT /api/user/password}, <b>必须提供原密码</b>。</li>
 * </ul>
 */
public class ProfileEditActivity extends AppCompatActivity {

    private static final String TAG = "ProfileEditActivity";

    private static final String[] AVATAR_NAMES = {
            "avatar_1", "avatar_2", "avatar_3", "avatar_4", "avatar_5", "avatar_6"
    };

    // ===== 三态容器 =====
    private View formScroll, loadingState, errorState;
    private TextView tvErrorTitle, tvErrorDesc;
    private MaterialButton btnRetry, btnOfflineEdit, btnRelogin;

    // ===== 表单控件 =====
    private ImageView ivCurrentAvatar;
    private LinearLayout llAvatarOptions;
    private TextView tvUsernameReadonly;
    private TextInputLayout tilNickname, tilPhone, tilOldPassword, tilPassword, tilConfirmPassword;
    private EditText etNickname, etPhone, etOldPassword, etPassword, etConfirmPassword;
    private MaterialButton btnSave;

    // ===== 基础依赖 =====
    private ApiService api;
    private UserRepository userRepository;
    private SharedPreferences sp;
    private int mUserId = -1;
    private String spUsername;

    // ===== 原始值基准(用于 diff & 只读展示) =====
    private String mOrigUsername = "";
    private String mOrigNickname = "";
    private String mOrigPhone = "";
    private String mOrigAvatar = "avatar_1";

    private boolean mOfflineMode;   // true: 当前数据来自本地缓存(远程不可达)
    private String selectedAvatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_edit);

        sp = getSharedPreferences("user", MODE_PRIVATE);
        mUserId = sp.getInt("user_id", -1);
        spUsername = sp.getString("username", null);

        bindViews();

        // Retrofit 必须显式初始化, 否则 api() 会抛 IllegalStateException
        RetrofitClient.init(this);
        api = RetrofitClient.api();
        userRepository = RepositoryProvider.getInstance(this).user();

        if (mUserId == -1) {
            showErrorState("登录状态已失效",
                    "未检测到有效登录信息，请重新登录后再编辑个人信息。",
                    /*showOfflineEdit=*/false);
            return;
        }

        attemptLoad();
    }

    private void bindViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        formScroll = findViewById(R.id.form_scroll);
        loadingState = findViewById(R.id.loading_state);
        errorState = findViewById(R.id.error_state);
        tvErrorTitle = findViewById(R.id.tv_error_title);
        tvErrorDesc = findViewById(R.id.tv_error_desc);
        btnRetry = findViewById(R.id.btn_retry);
        btnOfflineEdit = findViewById(R.id.btn_offline_edit);
        btnRelogin = findViewById(R.id.btn_relogin);

        btnRetry.setOnClickListener(v -> attemptLoad());
        btnOfflineEdit.setOnClickListener(v -> enterOfflineMode());
        btnRelogin.setOnClickListener(v -> logoutAndRelogin());

        ivCurrentAvatar = findViewById(R.id.iv_current_avatar);
        llAvatarOptions = findViewById(R.id.ll_avatar_options);
        tvUsernameReadonly = findViewById(R.id.tv_username_readonly);
        tilNickname = findViewById(R.id.til_nickname);
        tilPhone = findViewById(R.id.til_phone);
        tilOldPassword = findViewById(R.id.til_old_password);
        tilPassword = findViewById(R.id.til_password);
        tilConfirmPassword = findViewById(R.id.til_confirm_password);
        etNickname = findViewById(R.id.et_nickname);
        etPhone = findViewById(R.id.et_phone);
        etOldPassword = findViewById(R.id.et_old_password);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnSave = findViewById(R.id.btn_save);

        btnSave.setOnClickListener(v -> onSaveClicked());
    }

    // ==================== 加载(远程优先) ====================

    /**
     * 加载用户资料: 先请求后端 {@code GET /api/user/profile};
     * 失败(网络/超时/401/业务错误)时回退本地 SQLite 缓存;
     * 本地也无数据才展示错误态。
     */
    private void attemptLoad() {
        showLoadingState();
        api.profile().enqueue(new Callback<ApiResponse<UserDTO>>() {
            @Override
            public void onResponse(Call<ApiResponse<UserDTO>> call,
                                   Response<ApiResponse<UserDTO>> response) {
                ApiResponse<UserDTO> body = response.body();
                if (response.isSuccessful() && body != null && body.isSuccess() && body.data != null) {
                    mOfflineMode = false;
                    UserDTO dto = body.data;
                    mOrigUsername = safe(dto.username);
                    // 昵称为空时回退 username, 避免界面空白
                    mOrigNickname = TextUtils.isEmpty(dto.nickname) ? mOrigUsername : dto.nickname;
                    mOrigPhone = safe(dto.phone);
                    mOrigAvatar = TextUtils.isEmpty(dto.avatar) ? "avatar_1" : dto.avatar;

                    bindFormFromBaseline();
                    showFormState();
                    // 顺带把权威资料回写本地缓存, 之后断网也能兜底
                    syncToLocalCache(dto);
                    return;
                }
                String msg = (body != null && body.message != null)
                        ? body.message
                        : ("服务端返回异常 (HTTP " + response.code() + ")");
                fallbackToLocal(msg);
            }

            @Override
            public void onFailure(Call<ApiResponse<UserDTO>> call, Throwable t) {
                fallbackToLocal("无法连接服务器"
                        + (t.getMessage() == null ? "" : "：" + t.getMessage()));
            }
        });
    }

    /** 远程加载失败时的兜底: 尝试本地缓存, 仍失败则显示错误态 */
    private void fallbackToLocal(String remoteError) {
        com.example.myapplication.entity.UserInfo local = null;
        try {
            local = userRepository.getUserById(mUserId);
        } catch (Exception e) {
            Log.e(TAG, "本地缓存读取失败", e);
        }
        if (local != null) {
            mOfflineMode = true;
            mOrigUsername = TextUtils.isEmpty(local.getName()) ? safe(spUsername) : local.getName();
            mOrigNickname = mOrigUsername;   // 本地表无 nickname 字段, 用 username 兜底
            mOrigPhone = safe(local.getPhone());
            mOrigAvatar = TextUtils.isEmpty(local.getAvatar()) ? "avatar_1" : local.getAvatar();

            bindFormFromBaseline();
            showFormState();
            Toast.makeText(this, "已切换到离线模式：" + remoteError, Toast.LENGTH_LONG).show();
            return;
        }
        showErrorState("用户信息加载失败",
                remoteError + "\n\n本地缓存中也没有该用户(id=" + mUserId + ")的资料。"
                        + "请检查网络或后端服务后重试，也可选择重新登录。",
                /*showOfflineEdit=*/!TextUtils.isEmpty(spUsername));
    }

    /** 进入离线编辑: 用 SharedPreferences 中的 username 构造最小可用表单 */
    private void enterOfflineMode() {
        mOfflineMode = true;
        mOrigUsername = TextUtils.isEmpty(spUsername) ? ("user_" + mUserId) : spUsername;
        mOrigNickname = mOrigUsername;
        mOrigPhone = "";
        mOrigAvatar = "avatar_1";
        bindFormFromBaseline();
        showFormState();
        Toast.makeText(this, "已进入离线模式，保存时会自动尝试同步到服务器", Toast.LENGTH_SHORT).show();
    }

    /** 用当前基准值(mOrigXxx)渲染表单 + 初始化头像网格与校验(只调用一次) */
    private void bindFormFromBaseline() {
        tvUsernameReadonly.setText(TextUtils.isEmpty(mOrigUsername) ? "—" : mOrigUsername);
        etNickname.setText(mOrigNickname);
        etPhone.setText(mOrigPhone);
        etOldPassword.setText("");
        etPassword.setText("");
        etConfirmPassword.setText("");

        selectedAvatar = TextUtils.isEmpty(mOrigAvatar) ? "avatar_1" : mOrigAvatar;
        updateCurrentAvatar();
        setupAvatarOptions();
        setupValidation();
    }

    /** 把远程资料写回本地 SQLite 缓存(幂等, 失败仅记日志, 不影响主流程) */
    private void syncToLocalCache(UserDTO dto) {
        try {
            userRepository.upsertUser((int) dto.id, safe(dto.username), "");
            if (!TextUtils.isEmpty(dto.phone)) {
                userRepository.updatePhone((int) dto.id, dto.phone);
            }
            if (!TextUtils.isEmpty(dto.avatar)) {
                userRepository.updateAvatar((int) dto.id, dto.avatar);
            }
        } catch (Exception e) {
            Log.w(TAG, "回写本地缓存失败(不影响主流程)", e);
        }
    }

    private void logoutAndRelogin() {
        sp.edit().remove("user_id").remove("username").remove("password").remove("is_login").apply();
        try {
            com.example.myapplication.remote.SessionManager.clear();
        } catch (Exception ignored) {
            // 容错即可
        }
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ==================== 三态切换 ====================

    private void showFormState() {
        formScroll.setVisibility(View.VISIBLE);
        loadingState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
    }

    private void showLoadingState() {
        formScroll.setVisibility(View.GONE);
        loadingState.setVisibility(View.VISIBLE);
        errorState.setVisibility(View.GONE);
    }

    private void showErrorState(String title, String desc, boolean showOfflineEdit) {
        formScroll.setVisibility(View.GONE);
        loadingState.setVisibility(View.GONE);
        errorState.setVisibility(View.VISIBLE);
        tvErrorTitle.setText(title);
        tvErrorDesc.setText(desc);
        btnOfflineEdit.setVisibility(showOfflineEdit ? View.VISIBLE : View.GONE);
    }

    // ==================== 头像 ====================

    private void setupAvatarOptions() {
        llAvatarOptions.removeAllViews();
        int size = (int) (56 * getResources().getDisplayMetrics().density);
        int margin = (int) (8 * getResources().getDisplayMetrics().density);
        for (String name : AVATAR_NAMES) {
            int resId = getResources().getIdentifier(name, "drawable", getPackageName());
            if (resId == 0) continue;
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            iv.setLayoutParams(lp);
            iv.setImageResource(resId);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundResource(R.drawable.circle_shape);
            iv.setClipToOutline(true);
            iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            iv.setOnClickListener(v -> {
                selectedAvatar = name;
                updateCurrentAvatar();
                highlightSelectedAvatar();
                validateForm();
            });
            llAvatarOptions.addView(iv);
        }
        highlightSelectedAvatar();
    }

    private void updateCurrentAvatar() {
        int resId = getResources().getIdentifier(
                TextUtils.isEmpty(selectedAvatar) ? "avatar_1" : selectedAvatar,
                "drawable", getPackageName());
        if (resId != 0) {
            ivCurrentAvatar.setImageResource(resId);
        }
    }

    private void highlightSelectedAvatar() {
        for (int i = 0; i < llAvatarOptions.getChildCount(); i++) {
            View child = llAvatarOptions.getChildAt(i);
            if (child instanceof ImageView) {
                child.setAlpha(AVATAR_NAMES[i].equals(selectedAvatar) ? 1.0f : 0.4f);
            }
        }
    }

    // ==================== 校验 ====================

    private boolean mValidationSetup;   // 防止重试时重复注册 TextWatcher

    private void setupValidation() {
        if (mValidationSetup) {
            // 已注册过, 只重跑一次校验(避免 watcher 叠加导致内存泄漏)
            validateForm();
            return;
        }
        mValidationSetup = true;
        etNickname.addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) { validateNickname(); validateForm(); }
        });
        etPhone.addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) { validatePhone(); validateForm(); }
        });
        etOldPassword.addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) { validateForm(); }
        });
        etPassword.addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) { validateForm(); }
        });
        etConfirmPassword.addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) { validateForm(); }
        });
        validateForm();
    }

    private boolean validateNickname() {
        String name = etNickname.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            tilNickname.setError("昵称不能为空");
            return false;
        }
        if (name.length() < 2 || name.length() > 20) {
            tilNickname.setError("昵称长度需为 2-20 个字符");
            return false;
        }
        tilNickname.setError(null);
        return true;
    }

    private boolean validatePhone() {
        String phone = etPhone.getText().toString().trim();
        if (TextUtils.isEmpty(phone)) {
            tilPhone.setError(null);
            return true; // 选填
        }
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            tilPhone.setError("请输入有效的 11 位手机号");
            return false;
        }
        tilPhone.setError(null);
        return true;
    }

    /**
     * 密码组校验(原密码 / 新密码 / 确认新密码 三者联动):
     * <ul>
     *   <li>三者全空 → 合法(表示不修改密码);</li>
     *   <li>填了新密码 → 原密码必填 + 两项一致 + 长度 6-20 + 不得与原密码相同;</li>
     *   <li>只填了原密码没填新密码 → 提示需一起填写。</li>
     * </ul>
     */
    private boolean validatePasswordGroup() {
        String oldPwd = etOldPassword.getText().toString().trim();
        String newPwd = etPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();

        tilOldPassword.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);

        if (TextUtils.isEmpty(newPwd)) {
            if (!TextUtils.isEmpty(oldPwd) || !TextUtils.isEmpty(confirm)) {
                tilOldPassword.setError("如需修改密码，请同时填写新密码");
                return false;
            }
            return true;
        }

        boolean ok = true;
        if (TextUtils.isEmpty(oldPwd)) {
            tilOldPassword.setError("请输入原密码");
            ok = false;
        } else if (oldPwd.equals(newPwd)) {
            tilPassword.setError("新密码不能与原密码相同");
            ok = false;
        }
        if (newPwd.length() < 6 || newPwd.length() > 20) {
            tilPassword.setError("新密码长度需为 6-20 个字符");
            ok = false;
        }
        if (TextUtils.isEmpty(confirm)) {
            tilConfirmPassword.setError("请再次输入新密码");
            ok = false;
        } else if (!newPwd.equals(confirm)) {
            tilConfirmPassword.setError("两次输入的新密码不一致");
            ok = false;
        }
        return ok;
    }

    private boolean validateForm() {
        boolean valid = validateNickname() && validatePhone() && validatePasswordGroup();
        btnSave.setEnabled(valid);
        btnSave.setAlpha(valid ? 1.0f : 0.5f);
        return valid;
    }

    // ==================== 保存 ====================

    private void onSaveClicked() {
        if (!validateForm()) {
            Toast.makeText(this, "请先修正表单中的错误", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> changes = collectChanges();
        if (changes.isEmpty()) {
            Toast.makeText(this, "未做任何修改", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder msg = new StringBuilder("确认保存以下修改?\n");
        for (Map.Entry<String, String> e : changes.entrySet()) {
            msg.append("• ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("确认修改")
                .setMessage(msg.toString().trim())
                .setPositiveButton("确认保存", (dialog, which) -> performSave())
                .setNegativeButton("取消", null)
                .show();
    }

    private Map<String, String> collectChanges() {
        Map<String, String> changes = new LinkedHashMap<>();
        String newNickname = etNickname.getText().toString().trim();
        String newPhone = etPhone.getText().toString().trim();
        String newPwd = etPassword.getText().toString().trim();

        if (!newNickname.equals(mOrigNickname)) {
            changes.put("昵称", newNickname);
        }
        if (!newPhone.equals(mOrigPhone)) {
            changes.put("手机号", newPhone.isEmpty() ? "(清空)" : newPhone);
        }
        if (!TextUtils.isEmpty(newPwd)) {
            changes.put("密码", "已修改");
        }
        if (selectedAvatar != null && !selectedAvatar.equals(mOrigAvatar)) {
            changes.put("头像", "已更换");
        }
        return changes;
    }

    /** 提交: 先改密码(需原密码校验), 再改资料; 两步都走远程接口 */
    private void performSave() {
        final String newNickname = etNickname.getText().toString().trim();
        final String newPhone = etPhone.getText().toString().trim();
        final String oldPwd = etOldPassword.getText().toString().trim();
        final String newPwd = etPassword.getText().toString().trim();

        final boolean profileChanged = !newNickname.equals(mOrigNickname)
                || !newPhone.equals(mOrigPhone)
                || (selectedAvatar != null && !selectedAvatar.equals(mOrigAvatar));
        final boolean pwdChanged = !TextUtils.isEmpty(newPwd);

        btnSave.setEnabled(false);

        if (pwdChanged) {
            api.updatePassword(new UpdatePasswordRequest(oldPwd, newPwd))
                    .enqueue(new Callback<ApiResponse<Void>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Void>> call,
                                               Response<ApiResponse<Void>> response) {
                            ApiResponse<Void> body = response.body();
                            if (response.isSuccessful() && body != null && body.isSuccess()) {
                                afterPasswordSaved(profileChanged, newNickname, newPhone, newPwd);
                            } else {
                                btnSave.setEnabled(true);
                                String msg = (body != null && body.message != null)
                                        ? body.message
                                        : "密码修改失败 (HTTP " + response.code() + ")";
                                Toast.makeText(ProfileEditActivity.this, msg, Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                            btnSave.setEnabled(true);
                            Toast.makeText(ProfileEditActivity.this,
                                    "网络异常，密码修改失败，请稍后重试", Toast.LENGTH_LONG).show();
                        }
                    });
        } else {
            afterPasswordSaved(profileChanged, newNickname, newPhone, null);
        }
    }

    private void afterPasswordSaved(boolean profileChanged, String newNickname,
                                    String newPhone, String newPwd) {
        if (!profileChanged) {
            // 只改了密码(或什么都没改, 但已在外层拦截), 直接完成
            applyLocalCacheAndFinish(newNickname, newPwd);
            return;
        }

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.nickname = newNickname;
        req.phone = newPhone;
        req.avatar = selectedAvatar;

        api.updateProfile(req).enqueue(new Callback<ApiResponse<UserDTO>>() {
            @Override
            public void onResponse(Call<ApiResponse<UserDTO>> call,
                                   Response<ApiResponse<UserDTO>> response) {
                btnSave.setEnabled(true);
                ApiResponse<UserDTO> body = response.body();
                if (response.isSuccessful() && body != null && body.isSuccess()) {
                    if (body.data != null) syncToLocalCache(body.data);
                    applyLocalCacheAndFinish(newNickname, newPwd);
                } else {
                    String msg = (body != null && body.message != null)
                            ? body.message
                            : "资料保存失败 (HTTP " + response.code() + ")";
                    Toast.makeText(ProfileEditActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<UserDTO>> call, Throwable t) {
                btnSave.setEnabled(true);
                Toast.makeText(ProfileEditActivity.this,
                        "网络异常，资料保存失败，请稍后重试", Toast.LENGTH_LONG).show();
            }
        });
    }

    /** 同步 SharedPreferences 显示名/头像/密码, 提示成功并返回 */
    private void applyLocalCacheAndFinish(String newNickname, String newPwd) {
        SharedPreferences.Editor ed = sp.edit();
        ed.putString("username", newNickname);
        if (selectedAvatar != null) ed.putString("avatar", selectedAvatar);
        if (!TextUtils.isEmpty(newPwd) && sp.getBoolean("is_login", false)) {
            ed.putString("password", newPwd);
        }
        ed.apply();

        Toast.makeText(this, "个人信息修改成功", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** 简化的 TextWatcher, 子类只需实现 afterTextChanged */
    private static abstract class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}