package com.example.myapplication.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 可复用的 AI 聊天组件（"京言助手"风格）。
 * <p>
 * 能力一览：
 * <ol>
 *   <li>消息列表（RecyclerView）支持 USER / AI 两类气泡；</li>
 *   <li><b>流式响应</b>：用客户端模拟"逐字显示"效果（按 {@code chatTypingSpeedMs} / {@code chatCharsPerTick} 节流）。</li>
 *   <li><b>上下文</b>：在请求体中带上最近 N 条历史（{@code chatMaxContextMessages}），让支持的后端可以实现连续对话；</li>
 *   <li>状态机：STREAMING → DONE / ABORTED / ERROR，附带加载指示器与重试/中断元信息；</li>
 *   <li>深浅色主题：依赖 DayNight + {@code values-night/} 资源自动切换；</li>
 *   <li>配置：所有参数（API 地址、请求头、占位、速度、上下文长度、颜色）都可通过 XML 声明或 {@link Builder} 代码设置；</li>
 *   <li>响应式：手机/平板用 {@code values-sw600dp/dimens_chat.xml} 区分气泡宽度与字号。</li>
 * </ol>
 *
 * <h3>如何接入一个新后端</h3>
 * <pre>{@code
 * <com.example.myapplication.view.ChatAssistantView
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     app:chatApiUrl="https://your.host/api/chat"
 *     app:chatHeaderAuth="Bearer your-token"
 *     app:chatPlaceholder="问我任何问题"
 *     app:chatTypingSpeedMs="28" />
 * }</pre>
 *
 * <h3>关于流式</h3>
 * <p>当前实现是<b>客户端模拟</b>：先拿到完整 AI 回复，再按 tick 逐字写入到消息气泡。
 * 这让组件不依赖服务端的 SSE / chunked 能力即可提供逐字显示的 UX。
 * 如果后端升级为真正的 SSE（{@code text/event-stream}），只需把内部
 * {@code HttpStreamClient} 替换为逐 chunk 解析的版本，组件外层接口不需要变。
 *
 * <h3>关于上下文</h3>
 * <p>请求体会带 {@code history} 数组与 {@code sessionFirst} 布尔值。后端（{@code AssistantChatService}）
 * 对已登录用户优先使用自己数据库里的持久历史，{@code history} 只在匿名场景下兜底；
 * {@code sessionFirst} 用于判断是否为「本会话第一句」，据此决定要不要做日期+星期的开场问候。
 *
 * <h3>关于历史回放</h3>
 * <p>气泡是纯内存态，Activity 销毁即丢。要让"重进页面还能看到前面的对话"，
 * 宿主在注入鉴权头之后调一次 {@link #loadHistory()}：它会 GET {@code chatHistoryUrl}
 * （未配置时按 {@code .../chat → .../history} 推导）并回放最近 20 条。
 * 回放的消息是 {@code DONE} 纯文本 —— 工具卡片没有落库，不会重现。
 */
public class ChatAssistantView extends LinearLayout {

    private static final String TAG = "ChatAssistantView";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public interface OnMessageSentListener {
        /** 用户点击了发送，回调通知业务侧（可用于埋点等）。 */
        void onSent(@NonNull String text);
        /** AI 完整回复生成完毕（成功、中断、失败均触发）。 */
        void onReplyFinished(@NonNull ChatMessage finalMessage);
    }

    // ==================== 视图引用 ====================
    private final RecyclerView rvMessages;
    private final EditText etInput;
    private final MaterialButton btnSend;
    /** 开场引导块：消息列表为空时显示（自报家门 + 能力入口 + 快捷问题）。 */
    private final View llWelcome;
    private final ChatMessageAdapter adapter;
    private final LinearLayoutManager layoutManager;

    // ==================== 状态 ====================
    /** 全部消息（含 USER + AI）。UI 上展示。 */
    private final List<ChatMessage> messages = new ArrayList<>();
    /** 当前正在打字的那条 AI 消息。null 表示无活动流。 */
    @Nullable private ChatMessage currentStreaming;
    /** 控制打字机的 Handler / 取消回调 / 任务 ID。 */
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private Runnable pendingTick;
    @Nullable private Call httpCall;
    /** 历史回放请求（与 {@link #httpCall} 分开，避免拉历史时打断正在进行的对话）。 */
    @Nullable private Call historyCall;
    @Nullable private OkHttpClient httpClient;

    // ==================== 配置 ====================
    private String apiUrl = "http://localhost:8080/api/chat";
    /** 历史回放接口 URL；为空时从 {@link #apiUrl} 推导（把结尾的 /chat 换成 /history）。 */
    private String historyUrl;
    private Map<String, String> headers = new HashMap<>();
    private String placeholder;
    private String sendText;
    private int typingSpeedMs = 28;
    private int charsPerTick = 1;
    private int maxContextMessages = 20;
    private long httpTimeoutMs = 30_000L;

    // ==================== 回调 ====================
    @Nullable private OnMessageSentListener listener;

    public ChatAssistantView(Context context) {
        this(context, null);
    }

    public ChatAssistantView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChatAssistantView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_chat_assistant, this, true);

        rvMessages = findViewById(R.id.rv_chat_messages);
        etInput = findViewById(R.id.et_chat_input);
        btnSend = findViewById(R.id.btn_chat_send);
        llWelcome = findViewById(R.id.ll_chat_welcome);

        adapter = new ChatMessageAdapter();
        layoutManager = new LinearLayoutManager(context);
        layoutManager.setStackFromEnd(true);  // 最新的消息贴近输入栏
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);

        applyAttrs(context, attrs);
        bindInput();
        bindWelcome();
    }

    private void applyAttrs(Context context, @Nullable AttributeSet attrs) {
        if (attrs == null) return;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ChatAssistantView);
        try {
            String url = a.getString(R.styleable.ChatAssistantView_chatApiUrl);
            if (!TextUtils.isEmpty(url)) apiUrl = url;
            String hUrl = a.getString(R.styleable.ChatAssistantView_chatHistoryUrl);
            if (!TextUtils.isEmpty(hUrl)) historyUrl = hUrl;
            String auth = a.getString(R.styleable.ChatAssistantView_chatHeaderAuth);
            if (!TextUtils.isEmpty(auth)) headers.put("Authorization", auth);
            placeholder = a.getString(R.styleable.ChatAssistantView_chatPlaceholder);
            sendText = a.getString(R.styleable.ChatAssistantView_chatSendText);
            typingSpeedMs = a.getInt(R.styleable.ChatAssistantView_chatTypingSpeedMs, typingSpeedMs);
            charsPerTick = Math.max(1, a.getInt(R.styleable.ChatAssistantView_chatCharsPerTick, charsPerTick));
            maxContextMessages = Math.max(0, a.getInt(R.styleable.ChatAssistantView_chatMaxContextMessages, maxContextMessages));
        } finally {
            a.recycle();
        }

        if (TextUtils.isEmpty(placeholder)) {
            placeholder = getResources().getString(R.string.chat_default_placeholder);
        }
        etInput.setHint(placeholder);
        if (!TextUtils.isEmpty(sendText)) {
            btnSend.setText(sendText);
        }
    }

    private void bindInput() {
        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                // 有内容 + 当前没在流式 → 发送按钮可用
                boolean canSend = s.toString().trim().length() > 0 && !isStreaming();
                btnSend.setEnabled(canSend);
            }
        });

        etInput.setOnEditorActionListener((v, actionId, event) -> {
            // 回车直接发送（不换行）
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                attemptSend();
                return true;
            }
            return false;
        });

        btnSend.setOnClickListener(v -> attemptSend());
    }

    /**
     * 绑定开场引导里的「能力入口」与「快捷问题」。
     * <p>
     * 这些控件要发什么话，由各自 XML 里的 {@code android:tag} 决定 ——
     * 文案留在布局里，Java 只负责"取 tag → 当作用户输入发出去"，
     * 以后改话术不用动代码。
     */
    private void bindWelcome() {
        int[] clickableIds = {
                R.id.btn_welcome_find, R.id.btn_welcome_compare,
                R.id.btn_welcome_logistics, R.id.btn_welcome_order,
                R.id.btn_welcome_cart, R.id.btn_welcome_checkout, R.id.btn_welcome_track
        };
        for (int id : clickableIds) {
            View v = llWelcome.findViewById(id);
            if (v == null) continue;
            v.setOnClickListener(view -> {
                Object tag = view.getTag();
                if (tag == null) return;
                sendMessage(tag.toString());
            });
        }
        updateWelcomeVisibility();
    }

    /** 开场引导只在「一条消息都没有」时出现，一旦开始对话就让位给消息列表。 */
    private void updateWelcomeVisibility() {
        if (llWelcome == null) return;
        llWelcome.setVisibility(messages.isEmpty() ? VISIBLE : GONE);
    }

    /**
     * 以用户身份发送一条消息（供开场引导的胶囊 / 快捷问题调用）。
     * <p>
     * 与手动输入走完全相同的发送流程（{@link #attemptSend()}），
     * 因此流式保护、错误处理、历史上下文这些行为都自动一致。
     */
    public void sendMessage(@Nullable String text) {
        if (text == null) return;
        String t = text.trim();
        if (t.isEmpty() || isStreaming()) return;
        etInput.setText(t);
        attemptSend();
    }

    // ==================== 公开 API ====================

    public void setOnMessageSentListener(@Nullable OnMessageSentListener l) {
        this.listener = l;
    }

    /** 当前是否有 AI 正在打字（用于外部禁用某些交互）。 */
    public boolean isStreaming() {
        return currentStreaming != null;
    }

    /** 主动中断当前流式回复（用户行为或外部取消）。 */
    public void cancelStreaming() {
        cancelHttp();
        cancelTypingTick();
        if (currentStreaming != null) {
            updateMessage(clearPendingHint(currentStreaming).withAborted());
        }
    }

    /** 清空所有消息并中断任何进行中的请求。 */
    public void clearMessages() {
        cancelStreaming();
        messages.clear();
        adapter.submitList(Collections.emptyList());
        updateWelcomeVisibility();
    }

    /**
     * 把「正在为你查询…」的占位文案抹掉。
     * <p>
     * 占位文案是有内容的 STREAMING 消息；如果直接给它叠 {@code 已中断 / 加载失败} 副文字，
     * 界面上会同时出现"正在查询"和"加载失败"两句自相矛盾的提示。
     * 只有当真回复还没开始打字（内容仍等于占位文案）时才清，已经开始输出就保留已有内容。
     */
    private ChatMessage clearPendingHint(@NonNull ChatMessage m) {
        String hint = getContext().getString(R.string.chat_pending_hint);
        return hint.equals(m.content) ? m.withContent("", m.status) : m;
    }

    /**
     * 从服务端拉取并回放当前登录用户的历史对话（最近 20 条）。
     * <p>
     * 为什么需要它：「小发」的对话本来就会由后端写入 {@code ai_chat_messages}，
     * 但那只是给模型看的上下文；<b>UI 上的气泡是纯内存的</b>，
     * Activity 一销毁就全没了 —— 表现就是"重进小发，前面的对话不见了"。
     * 宿主 Activity 在 {@code onCreate} 里调一次本方法即可复原。
     * <p>
     * 行为约束：
     * <ul>
     *   <li>已有消息（例如旋转屏重建后本地仍在）时不覆盖；</li>
     *   <li>拉取失败只打日志，不弹错 —— 历史是锦上添花，不能影响正常提问；</li>
     *   <li>回放的消息是 {@code DONE} 状态、<b>不带附件卡片</b>（工具卡片没有落库）。</li>
     * </ul>
     */
    public void loadHistory() {
        final String url = resolveHistoryUrl();
        if (TextUtils.isEmpty(url)) {
            Log.w(TAG, "未配置历史接口（apiUrl 不以 /chat 结尾），跳过历史回放");
            return;
        }
        if (!messages.isEmpty()) return; // 本地已有对话 → 不覆盖

        cancelHistory();
        Request.Builder rb = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> h : headers.entrySet()) {
            rb.header(h.getKey(), h.getValue());
        }
        rb.header("Accept", "application/json");

        if (httpClient == null) httpClient = buildHttpClient();
        historyCall = httpClient.newCall(rb.build());
        historyCall.enqueue(new okhttp3.Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.w(TAG, "拉取历史失败: " + e.getMessage());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        Log.w(TAG, "拉取历史失败: HTTP " + response.code());
                        return;
                    }
                    final List<ChatMessage> replay = parseHistory(body.string());
                    if (replay.isEmpty()) return;
                    handler.post(() -> applyHistory(replay));
                } catch (IOException e) {
                    Log.w(TAG, "读取历史响应失败", e);
                }
            }
        });
    }

    /** 历史接口 URL：优先用显式配置，其次按 {@code .../chat → .../history} 约定推导。 */
    @Nullable
    private String resolveHistoryUrl() {
        if (!TextUtils.isEmpty(historyUrl)) return historyUrl;
        if (TextUtils.isEmpty(apiUrl)) return null;
        if (apiUrl.endsWith("/chat")) {
            return apiUrl.substring(0, apiUrl.length() - "/chat".length()) + "/history";
        }
        return null;
    }

    /**
     * 解析历史响应。兼容两种 schema：
     * <ul>
     *   <li>统一响应包装 {@code {code:0, data:[{role, content}]}}（当前后端）</li>
     *   <li>裸数组或 {@code {history:[...]}}（老后端 / 其它后端）</li>
     * </ul>
     */
    @NonNull
    private List<ChatMessage> parseHistory(String json) {
        List<ChatMessage> out = new ArrayList<>();
        try {
            JSONArray arr;
            String trimmed = json == null ? "" : json.trim();
            if (trimmed.startsWith("[")) {
                arr = new JSONArray(trimmed);
            } else {
                JSONObject o = new JSONObject(trimmed);
                arr = o.optJSONArray("data");
                if (arr == null) arr = o.optJSONArray("history");
                if (arr == null) {
                    JSONObject data = o.optJSONObject("data");
                    if (data != null) arr = data.optJSONArray("history");
                }
            }
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                String content = item.optString("content", "");
                if (content.isEmpty()) continue;
                String role = item.optString("role", "assistant");
                out.add("user".equalsIgnoreCase(role)
                        ? ChatMessage.user(content)
                        : ChatMessage.aiDone(content));
            }
        } catch (JSONException e) {
            Log.w(TAG, "历史 JSON 解析失败", e);
        }
        return out;
    }

    /** 把回放消息并入列表（主线程）。 */
    private void applyHistory(@NonNull List<ChatMessage> replay) {
        // 用户在历史到达前已经抢先发问 → 不要把这些老消息插到当前对话中间
        if (isStreaming() || !messages.isEmpty()) return;
        messages.addAll(replay);
        updateWelcomeVisibility();
        adapter.submitList(new ArrayList<>(messages), () -> scrollToEnd());
    }

    /** 代码侧配置入口。XML 已设置过的字段会被覆盖。 */
    public Builder config() {
        return new Builder(this);
    }

    // ==================== 发送 / 接收主流程 ====================

    private void attemptSend() {
        String text = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(text) || isStreaming()) return;
        // 1) 推入用户消息
        ChatMessage userMsg = ChatMessage.user(text);
        appendMessage(userMsg);
        // 2) 清空输入并禁用按钮
        etInput.setText("");
        btnSend.setEnabled(false);
        // 3) 插入 AI 占位。带一句"正在为你查询…"：后端要跑完工具调用（2-8 秒）才返回，
        //    空气泡会让用户以为卡死，有明确文案的等待感知短得多。
        ChatMessage aiMsg = ChatMessage.aiPending(getContext().getString(R.string.chat_pending_hint));
        appendMessage(aiMsg);
        currentStreaming = aiMsg;
        if (listener != null) listener.onSent(text);
        // 4) 发请求
        sendToBackend(text, aiMsg);
    }

    private void sendToBackend(String userText, ChatMessage aiMsg) {
        // 构造请求体：message + history（截取最近 N 条）
        String body;
        try {
            body = buildRequestBody(userText);
        } catch (JSONException e) {
            Log.w(TAG, "构造请求体失败", e);
            finishWithError(aiMsg, "请求体构造失败");
            return;
        }

        Request.Builder rb = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(body, JSON));
        for (Map.Entry<String, String> h : headers.entrySet()) {
            rb.header(h.getKey(), h.getValue());
        }
        // 走 JSON，避免后端要求 text/plain
        rb.header("Content-Type", "application/json; charset=utf-8");
        rb.header("Accept", "application/json");

        cancelHttp();
        if (httpClient == null) {
            httpClient = buildHttpClient();
        }
        httpCall = httpClient.newCall(rb.build());
        httpCall.enqueue(new okhttp3.Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                handler.post(() -> {
                    if (aiMsg != currentStreaming) return; // 已被新消息替换
                    finishWithError(aiMsg, e.getMessage() == null ? "网络错误" : e.getMessage());
                });
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody respBody = response.body()) {
                    if (!response.isSuccessful() || respBody == null) {
                        String err = "HTTP " + response.code();
                        handler.post(() -> {
                            if (aiMsg != currentStreaming) return;
                            finishWithError(aiMsg, err);
                        });
                        return;
                    }
                    String text = respBody.string();
                    ChatReply reply = parseReply(text);
                    if (reply == null) {
                        handler.post(() -> {
                            if (aiMsg != currentStreaming) return;
                            finishWithError(aiMsg, "响应格式错误");
                        });
                        return;
                    }
                    handler.post(() -> {
                        if (aiMsg != currentStreaming) return;
                        // 文本先流式显示；附件在 typing 结束后一次性附上。
                        if (reply.attachments.isEmpty()) {
                            startTypingSimulation(aiMsg, reply.text);
                        } else {
                            // 有附件：先把"正文"按流式显示；终态时合并 attachments
                            startTypingSimulationWithAttachments(aiMsg, reply.text, reply.attachments);
                        }
                    });
                } catch (IOException e) {
                    handler.post(() -> {
                        if (aiMsg != currentStreaming) return;
                        finishWithError(aiMsg, e.getMessage() == null ? "网络错误" : e.getMessage());
                    });
                }
            }
        });
    }

    /**
 * 从响应 JSON 中取出 reply + attachments。
 * <p>
 * 兼容两种 schema：
 * <ul>
 *   <li>老 {reply: "..."} —— 无 attachments</li>
 *   <li>新 {reply: "..."} —— 无 attachments</li>
 *   <li>{reply: "...", attachments: [{type, data}, ...]} —— 完整 assistant 响应</li>
 *   <li>{answer: "..."} —— 运营助手命名</li>
 *   <li>{data:{reply/answer}} —— 统一响应包装</li>
 * </ul>
     */
    @Nullable
    private ChatReply parseReply(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String reply = pickReplyField(o);
            List<ChatAttachment> atts = pickAttachments(o);
            if (reply == null && atts.isEmpty()) return null;
            return new ChatReply(reply == null ? "" : reply, atts);
        } catch (JSONException ignored) {
        }
        return null;
    }

    @Nullable
    private String pickReplyField(JSONObject o) {
        if (o.has("reply")) return o.optString("reply");
        if (o.has("answer")) return o.optString("answer");
        JSONObject data = o.optJSONObject("data");
        if (data != null) {
            if (data.has("reply")) return data.optString("reply");
            if (data.has("answer")) return data.optString("answer");
        }
        return null;
    }

    /** 从响应中解 attachments[] — 容错：缺字段 / 类型错都不抛，返回空列表。 */
    @NonNull
    private List<ChatAttachment> pickAttachments(JSONObject o) {
        // 兼容 {attachments: [{type, data}, ...]} 直接顶层，或 {data:{attachments:[...]}}
        JSONArray arr = o.optJSONArray("attachments");
        if (arr == null) {
            JSONObject data = o.optJSONObject("data");
            if (data != null) arr = data.optJSONArray("attachments");
        }
        if (arr == null) return Collections.emptyList();
        List<ChatAttachment> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject one = arr.optJSONObject(i);
            if (one == null) continue;
            String typeStr = one.optString("type");
            // 后端 data 是 JsonNode（Object or Array）。在客户端我们拿不到原始字符串，
            // 走一遍中间转储：把 JsonNode 序列回字符串再 parse，得到 JSONObject/JSONArray。
            // 由于 Android org.json 不直接暴露 deep copy，这里直接调 toString 再 new JSONObject。
            JSONObject dataJson = one.optJSONObject("data");
            if (dataJson == null) {
                // 可能是数组（重复工具调用被合并），JSONArray 不能塞进 JSONObject，但 ChatAttachment 接受
                // 任意 JSONObject —— 我们包一层 {"_items": [...]}
                org.json.JSONArray a = one.optJSONArray("data");
                if (a != null) {
                    dataJson = new JSONObject();
                    try { dataJson.put("_items", a); } catch (JSONException ignored) {}
                }
            }
            if (dataJson == null) continue;
            ChatAttachment parsed = ChatAttachment.parse(typeStr, dataJson);
            if (parsed != null) out.add(parsed);
        }
        return out;
    }

    /** 内部小结构：包含回复文本 + 附件。 */
    private static final class ChatReply {
        @NonNull final String text;
        @NonNull final List<ChatAttachment> attachments;
        ChatReply(@NonNull String text, @NonNull List<ChatAttachment> attachments) {
            this.text = text;
            this.attachments = attachments;
        }
    }

    /** 构造请求 JSON：{ message, history: [{role,content}], sessionFirst }。 */
    private String buildRequestBody(String userText) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("message", userText);
        JSONArray history = new JSONArray();
        // 排除最后 2 条：当前用户消息（已放入 "message"）+ AI 占位（content 为空）。
        // 再往前最多取 maxContextMessages * 2 条（约 N 轮问答），按时间正序。
        int end = Math.max(0, messages.size() - 2);
        int start = Math.max(0, end - maxContextMessages * 2);
        for (int i = start; i < end; i++) {
            ChatMessage m = messages.get(i);
            JSONObject item = new JSONObject();
            item.put("role", m.role == ChatMessage.Role.USER ? "user" : "assistant");
            item.put("content", m.content);
            history.put(item);
        }
        root.put("history", history);

        // 是否为本会话第一条消息：本次发送前若从未出现过用户消息，则为首条。
        // 后端据此决定是否在回复里做「日期 + 星期」开场问候
        // （后端还会从数据库读跨会话历史，因此无法自行判断"新会话首句"）。
        boolean sessionFirst = true;
        for (int i = 0; i < end; i++) {
            if (messages.get(i).role == ChatMessage.Role.USER) {
                sessionFirst = false;
                break;
            }
        }
        root.put("sessionFirst", sessionFirst);
        return root.toString();
    }

    // ==================== 客户端模拟打字 ====================

    private void startTypingSimulation(ChatMessage aiMsg, String fullReply) {
        startTypingSimulationInternal(aiMsg, fullReply, Collections.emptyList());
    }

    /**
     * 升级版：typing 结束时一次性挂上 attachments，让 {@link ChatMessageAdapter} 把卡片渲染出来。
     * 注意 attachments 一定在 typing 完成后再挂，避免流式滚动 + 布局变更双重作用导致闪烁。
     */
    private void startTypingSimulationWithAttachments(ChatMessage aiMsg, String fullReply,
                                                       List<ChatAttachment> attachments) {
        startTypingSimulationInternal(aiMsg, fullReply, attachments);
    }

    private void startTypingSimulationInternal(ChatMessage aiMsg, String fullReply,
                                                List<ChatAttachment> attachments) {
        // 取消任何遗留 tick
        cancelTypingTick();
        // 在 AI 消息对象的 content 上逐字增长
        final StringBuilder buffer = new StringBuilder();
        final int total = fullReply.length();
        final List<ChatAttachment> finalAtt = ChatAttachment.copyOf(attachments);

        Runnable tick = new Runnable() {
            @Override public void run() {
                if (aiMsg != currentStreaming) return; // 已被中断
                int remaining = total - buffer.length();
                if (remaining <= 0) {
                    // 全部写完
                    ChatMessage done = currentStreaming.withContent(buffer.toString(), ChatMessage.Status.DONE);
                    if (!finalAtt.isEmpty()) {
                        done = done.withAttachments(finalAtt);
                    }
                    updateMessage(done);
                    currentStreaming = null;
                    if (listener != null) listener.onReplyFinished(done);
                    return;
                }
                int n = Math.min(charsPerTick, remaining);
                buffer.append(fullReply, buffer.length(), buffer.length() + n);
                ChatMessage next = currentStreaming.withContent(buffer.toString(), ChatMessage.Status.STREAMING);
                updateMessage(next);
                pendingTick = this;
                handler.postDelayed(this, typingSpeedMs);
            }
        };
        pendingTick = tick;
        handler.postDelayed(tick, typingSpeedMs);
    }

    private void finishWithError(ChatMessage aiMsg, String err) {
        cancelTypingTick();
        cancelHttp();
        if (currentStreaming == aiMsg) {
            currentStreaming = null;
        }
        ChatMessage failed = clearPendingHint(aiMsg).withError(err);
        updateMessage(failed);
        if (listener != null) listener.onReplyFinished(failed);
    }

    // ==================== 消息列表维护 ====================

    private void appendMessage(ChatMessage m) {
        messages.add(m);
        updateWelcomeVisibility();
        // 重建一个可变副本，避免 ListAdapter 在 DiffUtil 阶段抛 ConcurrentModificationException
        List<ChatMessage> copy = new ArrayList<>(messages);
        adapter.submitList(copy, () -> scrollToEnd());
    }

    /** 替换同 id 的那条（用于流式增量更新）。 */
    private void updateMessage(ChatMessage updated) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).id == updated.id) {
                messages.set(i, updated);
                List<ChatMessage> copy = new ArrayList<>(messages);
                adapter.submitList(copy, () -> {
                    // 流式过程中持续滚到底，保证用户能看到最新字符
                    if (updated.status == ChatMessage.Status.STREAMING) {
                        scrollToEnd();
                    }
                });
                return;
            }
        }
    }

    private void scrollToEnd() {
        int n = adapter.getItemCount();
        if (n > 0) layoutManager.scrollToPosition(n - 1);
    }

    private void cancelTypingTick() {
        if (pendingTick != null) {
            handler.removeCallbacks(pendingTick);
            pendingTick = null;
        }
    }

    private void cancelHttp() {
        if (httpCall != null) {
            httpCall.cancel();
            httpCall = null;
        }
    }

    private void cancelHistory() {
        if (historyCall != null) {
            historyCall.cancel();
            historyCall = null;
        }
    }

    /** 统一的 OkHttpClient 构造（对话与历史回放共用，超时参数一致）。 */
    @NonNull
    private OkHttpClient buildHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(httpTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(httpTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .writeTimeout(httpTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelStreaming();
        cancelHistory();
        handler.removeCallbacksAndMessages(null);
    }

    // ==================== 代码侧配置入口 ====================

    public static class Builder {
        private final ChatAssistantView v;
        Builder(ChatAssistantView v) { this.v = v; }
        public Builder apiUrl(String url) { v.apiUrl = url; return this; }
        /** 历史回放接口；不设置时自动按 {@code .../chat → .../history} 推导。 */
        public Builder historyUrl(String url) { v.historyUrl = url; return this; }
        public Builder header(String key, String value) { v.headers.put(key, value); return this; }
        public Builder placeholder(String text) {
            v.placeholder = text;
            v.etInput.setHint(text);
            return this;
        }
        public Builder sendText(String text) { v.sendText = text; v.btnSend.setText(text); return this; }
        public Builder typingSpeedMs(int ms) { v.typingSpeedMs = Math.max(0, ms); return this; }
        public Builder charsPerTick(int n) { v.charsPerTick = Math.max(1, n); return this; }
        public Builder maxContextMessages(int n) { v.maxContextMessages = Math.max(0, n); return this; }
        public Builder httpTimeoutMs(long ms) { v.httpTimeoutMs = ms; return this; }
        public ChatAssistantView build() { return v; }
    }
}
