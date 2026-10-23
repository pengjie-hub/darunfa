package com.example.myapplication.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

/**
 * 支持「横向滑动」回调的 NestedScrollView。
 * <p>
 * 首页整页是一条纵向滚动链（见 {@code fragment_home.xml}），分类切换不再用 ViewPager2
 * （ViewPager2 是 final 类、且嵌在 NestedScrollView 里靠 wrap_content 量高并不可靠）。
 * 于是把「横滑切分类」做成滚动容器的一个手势监听：
 * <ul>
 *   <li>只在 {@link #onInterceptTouchEvent} 里把事件喂给 GestureDetector，
 *       <b>不消费事件</b>，纵向滚动完全不受影响；</li>
 *   <li>判定为横向 fling（横向位移明显大于纵向）才回调，避免误触。</li>
 * </ul>
 * 回调里带上手指按下时的屏幕 Y 坐标，调用方可以据此只在「商品区域」响应，不受头部横向轮播干扰。
 */
public class SwipeAwareNestedScrollView extends NestedScrollView {

    /** 触发横滑所需的最小横向位移（px）。 */
    private static final float MIN_FLING_DISTANCE_PX = 120f;

    /** 横向位移需比纵向大多少倍才算「横滑」。 */
    private static final float HORIZONTAL_DOMINANCE = 1.5f;

    public interface OnHorizontalFlingListener {
        /**
         * @param direction 手指左滑（想看下一个）为 1，手指右滑（想看上一个）为 -1
         * @param downRawY  手指按下时的屏幕 Y 坐标
         */
        void onHorizontalFling(int direction, float downRawY);
    }

    private GestureDetector gestureDetector;
    private OnHorizontalFlingListener flingListener;

    public SwipeAwareNestedScrollView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public SwipeAwareNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SwipeAwareNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs,
                                      int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2,
                                           float velocityX, float velocityY) {
                        if (e1 == null || flingListener == null) return false;
                        final float dx = e2.getX() - e1.getX();
                        final float dy = e2.getY() - e1.getY();
                        if (Math.abs(dx) < MIN_FLING_DISTANCE_PX) return false;
                        if (Math.abs(dx) <= Math.abs(dy) * HORIZONTAL_DOMINANCE) return false;
                        flingListener.onHorizontalFling(dx < 0 ? 1 : -1, e1.getRawY());
                        return true;
                    }
                });
    }

    public void setOnHorizontalFlingListener(OnHorizontalFlingListener listener) {
        this.flingListener = listener;
    }

    /**
     * 只在这里喂手势事件。
     * <p>
     * <b>不要同时在 {@link #onTouchEvent} 里再喂一次</b>：同一个手势的 DOWN/MOVE/UP
     * 会先后经过两个回调，喂两次会让 GestureDetector 把一次横滑识别成两次，
     * 出现「滑一下跳两个分类」。
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) gestureDetector.onTouchEvent(ev);
        return super.onInterceptTouchEvent(ev);
    }
}
