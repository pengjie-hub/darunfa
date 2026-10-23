package com.example.myapplication.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.adapter.CsMessageAdapter;
import com.example.myapplication.data.CsRepository;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.CsConversationDTO;
import com.example.myapplication.remote.dto.CsMessageDTO;
import com.example.myapplication.remote.dto.CsSendResponse;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 在线客服聊天页（前台客户侧）。
 * <p>
 * 交互：客户发消息 → 后端把该会话历史一并交给大模型生成回复 → 本页把「我的气泡」与
 * 「客服气泡」一起追加到列表里，所以对话是自动答复的，不需要人工在场。
 * <p>
 * 状态提示：顶栏副标题显示当前是「AI 应答」还是「人工跟进」；客户说「转人工」或 AI 调用失败时，
 * 后端会把会话切到人工，后台客服就能在客服管理页看到并接手。
 */
public class CsChatActivity extends AppCompatActivity {

    /** 加载状态：整页 loading（首次进入时用）。 */
    private View stateLoading;
    private RecyclerView rvMessages;
    private EditText etInput;
    private MaterialButton btnSend;
    private TextView tvStatus;

    private CsMessageAdapter adapter;
    private CsRepository csRepository;

    /** 发送中标志：防止连点导致重复提问（模型回复较慢，很容易被连点）。 */
    private boolean sending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cs_chat);

        // 本页可能被「我的」页直接拉起，必须先保证 Retrofit 初始化（项目无自定义 Application）
        RetrofitClient.init(getApplicationContext());
        csRepository = RepositoryProvider.getInstance(this).cs();

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        stateLoading = findViewById(R.id.state_cs_loading);
        rvMessages = findViewById(R.id.rv_cs_messages);
        etInput = findViewById(R.id.et_cs_input);
        btnSend = findViewById(R.id.btn_cs_send);
        tvStatus = findViewById(R.id.tv_cs_status);

        applyImeInsets();

        adapter = new CsMessageAdapter();
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendCurrentInput());
        // 键盘「发送」键直接发出去
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            sendCurrentInput();
            return true;
        });

        bindQuickChip(R.id.chip_q1);
        bindQuickChip(R.id.chip_q2);
        bindQuickChip(R.id.chip_q3);
        bindQuickChip(R.id.chip_q4);

        loadConversation();
    }

    /**
     * 让输入栏避开软键盘。
     * <p>
     * 本项目 {@code minSdk/targetSdk = 36}，系统对这类应用强制 edge-to-edge 布局，
     * 也就是「窗口不再为软键盘让位」（老的 {@code adjustResize} 会失效）。
     * 因此必须在收到 IME insets 时手动把底部内边距撑起来，否则键盘一弹出，
     * 输入框和「发送」按钮会被键盘整块盖住 —— 用户看不见自己打的字，也点不到发送。
     * <p>
     * 取 systemBars 与 ime 的较大值：键盘收起时留出导航栏的高度，弹出时留出键盘高度。
     */
    private void applyImeInsets() {
        View root = findViewById(R.id.root_cs_chat);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(0, 0, 0, Math.max(systemBars.bottom, ime.bottom));
            // 键盘顶起来之后可视区变小，把最后一条消息重新滚进视野，避免「回复看不见」
            if (ime.bottom > 0) scrollToBottom();
            return insets;
        });
    }

    /** 快捷问题：点一下直接发出去。 */
    private void bindQuickChip(int viewId) {
        TextView chip = findViewById(viewId);
        if (chip == null) return;
        chip.setOnClickListener(v -> {
            if (sending) return;
            String q = chip.getText().toString();
            etInput.setText(q);
            sendCurrentInput();
        });
    }

    /** 拉会话 + 历史消息。 */
    private void loadConversation() {
        stateLoading.setVisibility(View.VISIBLE);
        csRepository.session(new ApiCallback<CsConversationDTO>() {
            @Override
            public void onSuccess(CsConversationDTO data) {
                applyConversation(data);
                loadMessages();
            }

            @Override
            public void onError(int code, String message) {
                stateLoading.setVisibility(View.GONE);
                if (handleAuthError(code)) return;
                Toast.makeText(CsChatActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMessages() {
        csRepository.messages(new ApiCallback<List<CsMessageDTO>>() {
            @Override
            public void onSuccess(List<CsMessageDTO> data) {
                stateLoading.setVisibility(View.GONE);
                adapter.submit(data);
                // 首次进入还没有任何对话时，补一条欢迎语，避免空白页
                if (adapter.size() == 0) {
                    adapter.append(welcomeMessage());
                }
                scrollToBottom();
            }

            @Override
            public void onError(int code, String message) {
                stateLoading.setVisibility(View.GONE);
                if (handleAuthError(code)) return;
                Toast.makeText(CsChatActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendCurrentInput() {
        if (sending) return;
        String content = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(content)) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show();
            return;
        }
        sending = true;
        btnSend.setEnabled(false);
        btnSend.setText("发送中");
        etInput.setText("");

        csRepository.send(content, new ApiCallback<CsSendResponse>() {
            @Override
            public void onSuccess(CsSendResponse data) {
                sending = false;
                btnSend.setEnabled(true);
                btnSend.setText("发送");
                if (data == null) {
                    Toast.makeText(CsChatActivity.this, "发送失败，请重试", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 用服务端回传的两条消息追加气泡，保证顺序与内容以后端为准
                if (data.userMessage != null) adapter.append(data.userMessage);
                if (data.reply != null) adapter.append(data.reply);
                scrollToBottom();
                updateStatus(data.conversationStatus);
            }

            @Override
            public void onError(int code, String message) {
                sending = false;
                btnSend.setEnabled(true);
                btnSend.setText("发送");
                if (handleAuthError(code)) return;
                Toast.makeText(CsChatActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyConversation(CsConversationDTO c) {
        if (c != null) updateStatus(c.status);
    }

    private void updateStatus(String status) {
        if (tvStatus == null) return;
        if ("HUMAN".equals(status)) {
            tvStatus.setText("人工客服跟进中");
        } else if ("CLOSED".equals(status)) {
            tvStatus.setText("会话已结束");
        } else {
            tvStatus.setText("AI 智能客服 · 7×24 在线");
        }
    }

    private void scrollToBottom() {
        if (adapter.getItemCount() > 0) {
            rvMessages.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    /** 本地欢迎语（不发请求，仅客户端展示）。 */
    private CsMessageDTO welcomeMessage() {
        CsMessageDTO m = new CsMessageDTO();
        m.sender = "AI";
        m.senderName = "AI 客服";
        m.content = "您好，我是大润发智能客服～ 商品、订单、配送、退换货、优惠券等问题都可以问我。"
                + "如果想找真人，回复「转人工」即可。";
        return m;
    }

    /** 401 统一处理：提示并回登录页。 */
    private boolean handleAuthError(int code) {
        if (code == 401) {
            Toast.makeText(this, "登录已过期，请重新登录", Toast.LENGTH_SHORT).show();
            finish();
            return true;
        }
        return false;
    }
}
