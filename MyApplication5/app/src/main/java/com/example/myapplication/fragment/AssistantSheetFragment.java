package com.example.myapplication.fragment;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.example.myapplication.R;
import com.example.myapplication.activity.LoginActivity;
import com.example.myapplication.remote.SessionManager;
import com.example.myapplication.view.ChatAssistantView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * 「小发」半屏助手面板。
 *
 * <h3>为什么不做全屏 Activity</h3>
 * 用户找助手的高频时机是"正在逛商品、突然有个问题"，全屏页会把他从浏览流里拽出来，
 * 问完还得退出、回到原来的位置。半屏面板升起后上面仍留着 28% 的页面，
 * 关掉就回到原处，浏览上下文不丢 —— 这是"舒适"的核心。
 *
 * <h3>两个容易踩的坑</h3>
 * <ol>
 *   <li><b>半展开比例会失效</b>：BottomSheetDialog 默认高度是 wrap_content，
 *       内容多高面板就多高，{@code setHalfExpandedRatio} 就没意义了。
 *       必须先把 design_bottom_sheet 的高度改成 MATCH_PARENT，比例才生效。</li>
 *   <li><b>软键盘会把面板顶飞</b>：输入时若不处理，键盘会把整个面板连同输入框推出屏幕。
 *       这里的做法是 window 设 {@code SOFT_INPUT_ADJUST_RESIZE}（压缩窗口而非顶起内容），
 *       再用 {@code OnGlobalLayoutListener} 监听可见区域变化，一旦发现键盘弹出就把面板顶到全展开。</li>
 * </ol>
 *
 * <h3>入口</h3>
 * 统一走 {@link #open(FragmentActivity)}：它会处理未登录的情况（提示 + 跳登录页），
 * 悬浮球和「我的」页卡片都调它，避免两处各写一遍判断。
 */
public class AssistantSheetFragment extends BottomSheetDialogFragment {

    public static final String TAG = "AssistantSheet";

    /** 半展开时面板占据屏高的比例，上方留出 28% 让用户仍能看到底下的页面。 */
    private static final float HALF_EXPANDED_RATIO = 0.72f;

    /** 可见区域缩小超过该比例即认定软键盘弹出。 */
    private static final float KEYBOARD_THRESHOLD = 0.15f;

    private boolean keyboardOpen;

    private final ViewTreeObserver.OnGlobalLayoutListener keyboardListener =
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    View root = getView();
                    if (root == null) return;
                    Rect visible = new Rect();
                    root.getWindowVisibleDisplayFrame(visible);
                    int full = root.getRootView().getHeight();
                    if (full <= 0) return;
                    boolean open = (full - visible.bottom) > full * KEYBOARD_THRESHOLD;
                    if (open == keyboardOpen) return;
                    keyboardOpen = open;
                    // 键盘弹出 → 顶到全展开，保证输入框和最后一条消息都在可视区内
                    if (open) setBehaviorState(BottomSheetBehavior.STATE_EXPANDED);
                }
            };

    /**
     * 打开助手面板（唯一推荐入口）。
     * 未登录时不弹面板，而是提示并跳转登录页。
     */
    public static void open(@NonNull FragmentActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (!SessionManager.isLoggedIn()) {
            Toast.makeText(activity, R.string.chat_attachment_need_login, Toast.LENGTH_SHORT).show();
            activity.startActivity(new Intent(activity, LoginActivity.class));
            return;
        }
        new AssistantSheetFragment().show(activity.getSupportFragmentManager(), TAG);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_assistant_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ChatAssistantView chat = view.findViewById(R.id.chat_view);

        // 先注入 token 再拉历史：顺序反了历史请求会 401
        String token = SessionManager.getToken();
        if (!TextUtils.isEmpty(token)) {
            chat.config().header("Authorization", "Bearer " + token).build();
        }
        chat.loadHistory();

        view.getViewTreeObserver().addOnGlobalLayoutListener(keyboardListener);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (!(dialog instanceof BottomSheetDialog)) return;

        Window window = dialog.getWindow();
        if (window != null) {
            // 压缩窗口给键盘让位，而不是把面板整体顶上去
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;

        // 坑 1：先撑满高度，halfExpandedRatio 才有效
        ViewGroup.LayoutParams lp = sheet.getLayoutParams();
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        sheet.setLayoutParams(lp);

        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        // 注意是 setFitToContents（复数）。API 名不确定时别靠记忆猜 ——
        // 解 material-x.y.z.aar 里的 classes.jar 用 javap 看一眼最省时间。
        behavior.setFitToContents(false);
        behavior.setDraggable(true);
        behavior.setHideable(true);
        // 不允许停在"一条细缝"的折叠态：往下拖就直接关闭，符合直觉
        behavior.setSkipCollapsed(true);
        behavior.setHalfExpandedRatio(HALF_EXPANDED_RATIO);
        // 等一次布局再设状态，避免 behavior 尚未 attach 时被忽略
        sheet.post(() -> behavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED));
    }

    /** 把面板切到指定状态（软键盘联动用）。 */
    private void setBehaviorState(int state) {
        Dialog dialog = getDialog();
        if (!(dialog instanceof BottomSheetDialog)) return;
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
        behavior.setState(state);
    }

    @Override
    public void onDestroyView() {
        View root = getView();
        if (root != null) {
            root.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardListener);
        }
        super.onDestroyView();
    }
}
