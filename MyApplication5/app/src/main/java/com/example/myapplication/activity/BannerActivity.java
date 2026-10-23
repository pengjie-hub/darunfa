package com.example.myapplication.activity;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import com.example.myapplication.R;
import com.example.myapplication.adapter.BannerAdapter;
import java.util.ArrayList;
import java.util.List;

public class BannerActivity extends AppCompatActivity {
    private ViewPager2 viewPager;
    private List<Integer> bannerImages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_banner);

        // 初始化Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // 启用返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        viewPager = findViewById(R.id.banner_view_pager);

        // 初始化轮播图数据
        initBannerData();

        // 设置适配器
        BannerAdapter adapter = new BannerAdapter(bannerImages);
        viewPager.setAdapter(adapter);

        // 启用自动轮播
        startAutoScroll();
    }
    
    // 处理Toolbar返回按钮点击事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // 返回上一个页面
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initBannerData() {
        // 添加指定的四张促销海报轮播图
        bannerImages.add(R.drawable.cxhb_1);
        bannerImages.add(R.drawable.cxhb_2);
        bannerImages.add(R.drawable.cxhb_3);
        bannerImages.add(R.drawable.cxhb_4);
    }

    private void startAutoScroll() {
        // 这里可以添加自动轮播逻辑
        // 例如使用Handler定时切换页面
    }
}
