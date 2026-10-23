package com.example.myapplication.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.R;

/**
 * 「小发」悬浮球入口。
 *
 * <h3>为什么是自定义 View 而不是布局里拼一个 ImageView</h3>
 * 它需要同时具备三种行为，而这三件事必须共享同一套触摸状态：
 * <ol>
 *   <li><b>可拖拽</b>：用户能把球挪到不挡内容的位置 —— 这是"舒适"的关键，
 *       固定位置的气泡早晚会盖住某个按钮；</li>
 *   <li><b>松手吸附</b>：拖完自动贴到最近的左/右边缘，避免球停在屏幕正中间挡住商品；</li>
 *   <li><b>位置持久化</b>：下次进 App 球还在你放的地方，而不是又弹回原处。</li>
 * </ol>
 *
 * <h3>点击 vs 拖拽</h3>
 * 用 {@link ViewConfiguration#getScaledTouchSlop()} 区分：位移没超过 touchSlop 视为点击
 * （走 {@link #performClick()}，即外部注册的 OnClickListener），超过则只当拖拽，不触发点击。
 * 否则用户每次拖完球都会误开一次面板。
 *
 * <h3>底部预留</h3>
 * {@link #bottomReservePx} 是球下滑的禁区高度（默认 72dp，约等于底部导航栏 56dp + 外边距）。
 * 不预留的话，用户能把球拖到导航栏上，之后既点不到导航也点不到球。
 */
public class AssistantFab extends FrameLayout {

    private static final String PREFS = "assistant_ball";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";

    /** 球到左右边缘、以及默认位置的水平留白。 */
    private static final int EDGE_MARGIN_DP = 8;
    /** 底部导航栏（56dp）+ 其外边距（8dp）+ 一点余量，作为下滑禁区。 */
    private static final int BOTTOM_RESERVE_DP = 72;

    private final int touchSlop;
    private final float edgeMarginPx;
    private final float bottomReservePx;

    private float downRawX, downRawY;
    private float startX, startY;
    /** 本次手势是否已经越过 touchSlop 判定为拖拽。 */
    private boolean dragging;
    /** 是否已经尝试过恢复上次位置（只在首次布局后做一次）。 */
    private boolean restored;

    public AssistantFab(@NonNull Context context) {
        this(context, null);
    }

    public AssistantFab(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AssistantFab(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.view_assistant_fab, this, true);

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        edgeMarginPx = dp(EDGE_MARGIN_DP);
        bottomReservePx = dp(BOTTOM_RESERVE_DP);

        setBackgroundResource(R.drawable.bg_assistant_fab);
        setElevation(dp(6));
        setClickable(true);
        setFocusable(true);
        setContentDescription(context.getString(R.string.assistant_fab_desc));
    }

    // ==================================================================
    //  位置持久化
    // ==================================================================

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        View parent = getParentView();
        if (parent == null || parent.getWidth() == 0) return;

        if (!restored) {
            // 首次布局完成 → 尺寸已知，可以安全地把上次保存的位置套用回来
            restored = true;
            restorePosition();
        } else {
            // 屏幕旋转 / 尺寸变化：只做边界修正，避免球被甩到屏幕外
            setX(clampX(getX()));
            setY(clampY(getY()));
        }
    }

    private void restorePosition() {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!sp.contains(KEY_X) || !sp.contains(KEY_Y)) return;
        float x = sp.getFloat(KEY_X, getX());
        float y = sp.getFloat(KEY_Y, getY());
        setX(clampX(x));
        setY(clampY(y));
    }

    private void persistPosition(float x, float y) {
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_X, x)
                .putFloat(KEY_Y, y)
                .apply();
    }

    // ==================================================================
    //  触摸：拖拽 / 吸附 / 点击
    // ==================================================================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = getX();
                startY = getY();
                dragging = false;
                animate().cancel();
                setAlpha(0.8f);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!dragging
                        && Math.hypot(dx, dy) > touchSlop) {
                    dragging = true;
                }
                if (dragging) {
                    setX(clampX(startX + dx));
                    setY(clampY(startY + dy));
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setAlpha(1f);
                if (dragging) {
                    // 松手贴边；注意先把目标值存盘，动画期间用户可能马上又拖，避免漏存
                    float targetX = nearestEdgeX();
                    persistPosition(targetX, getY());
                    animate().x(targetX).setDuration(180).start();
                    dragging = false;
                } else {
                    performClick();
                }
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        // 自定义 onTouchEvent 时必须显式调用，否则 OnClickListener 不会被触发
        return super.performClick();
    }

    // ==================================================================
    //  边界与吸附
    // ==================================================================

    private float nearestEdgeX() {
        View parent = getParentView();
        if (parent == null) return getX();
        float maxX = maxX();
        float mid = (edgeMarginPx + maxX) / 2f;
        return getX() < mid ? edgeMarginPx : maxX;
    }

    private float maxX() {
        View parent = getParentView();
        if (parent == null) return getX();
        return Math.max(edgeMarginPx, parent.getWidth() - getWidth() - edgeMarginPx);
    }

    private float clampX(float x) {
        return Math.min(Math.max(x, edgeMarginPx), maxX());
    }

    private float clampY(float y) {
        View parent = getParentView();
        if (parent == null) return y;
        float maxY = Math.max(0f, parent.getHeight() - getHeight() - bottomReservePx);
        return Math.min(Math.max(y, 0f), maxY);
    }

    @Nullable
    private View getParentView() {
        return getParent() instanceof View ? (View) getParent() : null;
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
