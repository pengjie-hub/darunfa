package com.example.myapplication.adapter;

import android.os.Handler;  // 修正：使用Android的Handler类
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;  // 修正：移除多余空格
import androidx.recyclerview.widget.RecyclerView;  // 修正：移除多余空格
import androidx.viewpager2.widget.ViewPager2;
import com.example.myapplication.R;  // 修正：移除多余空格
import com.example.myapplication.entity.HorizontalBannerItem;  // 修正：移除多余空格
import java.util.List;

public class HorizontalBannerAdapter extends RecyclerView.Adapter<HorizontalBannerAdapter.ViewHolder> {
    private List<HorizontalBannerItem> mDataList;

    public HorizontalBannerAdapter(List<HorizontalBannerItem> dataList) {
        mDataList = dataList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 加载新创建的列表项模板
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_horizontal_with_banner, parent, false);
        return new ViewHolder(view);  // 修正：移除多余空格
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HorizontalBannerItem item = mDataList.get(position);

        // 设置文本内容
        holder.tvTitle.setText(item.getTitle());
        holder.tvSubtitle.setText(item.getSubtitle());

        // 初始化轮播图（复用现有BannerAdapter）
        BannerAdapter bannerAdapter = new BannerAdapter(item.getBannerImageList());
        holder.viewPager2.setAdapter(bannerAdapter);

        // 重置当前位置并启动自动轮播
        holder.currentPosition = 0;
        holder.startAutoScroll();
    }

    @Override
    public int getItemCount() {
        return mDataList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewPager2 viewPager2;
        TextView tvTitle;
        TextView tvSubtitle;
        // 添加自动轮播相关变量
        private Handler handler = new Handler(Looper.getMainLooper());  // 现在使用正确的Android Handler类
        private Runnable autoScrollRunnable;
        private int currentPosition = 0;

        ViewHolder(View itemView) {
            super(itemView);
            viewPager2 = itemView.findViewById(R.id.item_banner);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSubtitle = itemView.findViewById(R.id.tv_subtitle);

            // 设置轮播图方向为水平
            viewPager2.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);

            // 初始化自动轮播任务
            initAutoScroll();

            // 添加触摸监听（触摸时暂停轮播）
            viewPager2.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        stopAutoScroll();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        startAutoScroll();
                        break;
                }
                return false; // 不消费触摸事件，保证ViewPager2正常滑动
            });
        }

        // 初始化自动轮播任务
        private void initAutoScroll() {
            autoScrollRunnable = new Runnable() {
                @Override
                public void run() {
                    if (viewPager2.getAdapter() == null) return;

                    int itemCount = viewPager2.getAdapter().getItemCount();
                    if (itemCount <= 1) return; // 只有一张图时不轮播

                    currentPosition = (currentPosition + 1) % itemCount;
                    viewPager2.setCurrentItem(currentPosition, true);
                    handler.postDelayed(this, 3000); // 3秒切换一次
                }
            };
        }

        // 开始自动轮播
        public void startAutoScroll() {
            stopAutoScroll(); // 先停止再启动，避免重复post
            handler.postDelayed(autoScrollRunnable, 3000);
        }

        // 停止自动轮播
        public void stopAutoScroll() {
            handler.removeCallbacks(autoScrollRunnable);
        }
    }
}