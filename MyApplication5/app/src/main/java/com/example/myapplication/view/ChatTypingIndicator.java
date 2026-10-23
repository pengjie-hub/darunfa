package com.example.myapplication.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import com.example.myapplication.R;

/**
 * 打字指示器：三个小圆点交错呼吸，模拟 AI 正在输入。
 * <p>
 * 实现要点：
 * <ul>
 *   <li>纯 onDraw 绘制 + 三个独立 {@link ValueAnimator}，避免占用主线程做插值；</li>
 *   <li>用 onAttachedToWindow / onDetachedFromWindow 控制动画启停，避免泄漏；</li>
 *   <li>透明度 + 缩放双轨呼吸，整体观感更接近主流聊天应用的"加载中"。</li>
 * </ul>
 */
public class ChatTypingIndicator extends View {

    private static final int DOT_COUNT = 3;
    private static final long STAGGER_MS = 160L;     // 相邻圆点的动画错开时间
    private static final long DURATION_MS = 720L;    // 单个圆点一次呼吸周期

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dotRadius;
    private final float dotGap;
    private final int baseColor;

    /** 三个圆点当前的"进度"（0..1），由三个 ValueAnimator 各自驱动。 */
    private final float[] phases = new float[DOT_COUNT];
    private final ValueAnimator[] animators = new ValueAnimator[DOT_COUNT];

    public ChatTypingIndicator(Context context) {
        this(context, null);
    }

    public ChatTypingIndicator(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChatTypingIndicator(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        dotRadius = getResources().getDimension(R.dimen.chat_typing_dot_size) / 2f;
        dotGap = getResources().getDimension(R.dimen.chat_typing_dot_gap);
        baseColor = getResources().getColor(R.color.chat_typing_dot);
        paint.setColor(baseColor);
        // 未启动时保证有合理测量高度
        setMinimumHeight((int) (2 * dotRadius + density * 2));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cy = getHeight() / 2f;
        // 三个圆点从左到右
        float totalWidth = DOT_COUNT * 2 * dotRadius + (DOT_COUNT - 1) * dotGap;
        float startX = (getWidth() - totalWidth) / 2f + dotRadius;
        for (int i = 0; i < DOT_COUNT; i++) {
            float t = phases[i];
            // 透明度 0.4..1.0，缩放 0.7..1.0，让"亮"的那一点更突出
            float alpha = 0.4f + 0.6f * t;
            float scale = 0.7f + 0.3f * t;
            paint.setAlpha((int) (alpha * 255));
            float cx = startX + i * (2 * dotRadius + dotGap);
            canvas.drawCircle(cx, cy, dotRadius * scale, paint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 宽度由父容器决定；高度至少容纳两个圆点 + 一点呼吸空间
        int desiredH = (int) (2 * dotRadius + 4 * getResources().getDisplayMetrics().density);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int hSize = MeasureSpec.getSize(heightMeasureSpec);
        int height;
        if (hMode == MeasureSpec.EXACTLY) {
            height = hSize;
        } else if (hMode == MeasureSpec.AT_MOST) {
            height = Math.min(desiredH, hSize);
        } else {
            height = desiredH;
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimators();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimators();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            startAnimators();
        } else {
            stopAnimators();
        }
    }

    private void startAnimators() {
        if (animators[0] != null && animators[0].isStarted()) return;
        long[] starts = {0L, STAGGER_MS, 2 * STAGGER_MS};
        for (int i = 0; i < DOT_COUNT; i++) {
            final int idx = i;
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(DURATION_MS);
            a.setStartDelay(starts[i]);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setRepeatMode(ValueAnimator.RESTART);
            a.setInterpolator(new LinearInterpolator());
            a.addUpdateListener(v -> {
                phases[idx] = (float) v.getAnimatedValue();
                invalidate();
            });
            animators[i] = a;
            a.start();
        }
    }

    private void stopAnimators() {
        for (int i = 0; i < DOT_COUNT; i++) {
            if (animators[i] != null) {
                animators[i].cancel();
                animators[i] = null;
            }
            phases[i] = 0f;
        }
        invalidate();
    }
}
