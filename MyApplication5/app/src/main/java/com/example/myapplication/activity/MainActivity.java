package com.example.myapplication.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import com.example.myapplication.R;
import com.example.myapplication.fragment.AssistantSheetFragment;
import com.example.myapplication.fragment.CategoryFragment;
import com.example.myapplication.fragment.CarFragment;
import com.example.myapplication.fragment.HomeFragment;
import com.example.myapplication.fragment.MineFragment;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.view.AssistantFab;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

public class MainActivity extends AppCompatActivity {

    private HomeFragment homeFragment;
    private CategoryFragment categoryFragment;
    private MineFragment mineFragment;
    private CarFragment carFragment;
    private BottomNavigationView main_bottom_nv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 兜底初始化：本项目没有自定义 Application，RetrofitClient 只在登录/注册等页面 init。
        // 首页 Fragment 一创建就会请求分类/商品，所以这里补一次（幂等，已初始化时直接返回），
        // 否则「进程被杀后系统直接恢复到 MainActivity」这类入口会抛
        // IllegalStateException: RetrofitClient 尚未初始化。
        RetrofitClient.init(getApplicationContext());

        main_bottom_nv = findViewById(R.id.main_bottom_nv);
        main_bottom_nv.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.navigation_home) {
                    selectedFragment(0);
                } else if (item.getItemId() == R.id.navigation_dashboard) {
                    selectedFragment(1);
                } else if (item.getItemId() == R.id.navigation_notifications) {
                    selectedFragment(2);
                } else if (item.getItemId() == R.id.navigation_my) {
                    selectedFragment(3);
                }
                return true;
            }
        });
        selectedFragment(0);

        // 「小发」悬浮球：四个 tab 全局常驻，点一下升半屏面板。
        // 未登录的处理统一收在 AssistantSheetFragment.open() 里。
        AssistantFab fabAssistant = findViewById(R.id.fab_assistant);
        fabAssistant.setOnClickListener(v -> openAssistant());
    }

    /**
     * 打开「小发」助手面板（供 Fragment 复用，例如「我的」页里的小发卡片）。
     */
    public void openAssistant() {
        AssistantSheetFragment.open(this);
    }

    private void selectedFragment(int position) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        hideFragment(transaction);

        switch (position) {
            case 0:
                if (homeFragment == null) {
                    homeFragment = new HomeFragment();
                    transaction.add(R.id.content, homeFragment);
                } else {
                    transaction.show(homeFragment);
                }
                break;
            case 1:
                if (categoryFragment == null) {
                    categoryFragment = new CategoryFragment();
                    transaction.add(R.id.content, categoryFragment);
                } else {
                    transaction.show(categoryFragment);
                }
                break;
            case 2:
                if (carFragment == null) {
                    carFragment = new CarFragment();
                    transaction.add(R.id.content, carFragment);
                } else {
                    transaction.show(carFragment);
                }
                break;
            case 3:
                if (mineFragment == null) {
                    mineFragment = new MineFragment();
                    transaction.add(R.id.content, mineFragment);
                } else {
                    transaction.show(mineFragment);
                }
                break;
        }
        transaction.commitNow();
    }
    private void hideFragment(FragmentTransaction transaction){
        if (homeFragment != null) {
            transaction.hide(homeFragment);
        }
        if (mineFragment != null) {
            transaction.hide(mineFragment);
        }
        if (categoryFragment != null) {
            transaction.hide(categoryFragment);
        }
        if (carFragment != null) {
            transaction.hide(carFragment);
        }
    }
    
    // 处理从其他Activity返回的结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            // 检查是否需要切换到购物车标签
            if (data.hasExtra("tab_index")) {
                int tabIndex = data.getIntExtra("tab_index", 0);
                // 如果指定的是购物车标签(索引2)
                if (tabIndex == 2) {
                    // 选中底部导航的购物车项
                    main_bottom_nv.setSelectedItemId(R.id.navigation_notifications);
                }
            }
        }
    }

    /**
     * 切换到分类页并定位到指定分类(供 HomeFragment 调用)
     */
    public void switchToCategory(String categoryName) {
        if (categoryName == null) return;
        // 切换到分类 Fragment
        selectedFragment(1);
        // 通知分类 Fragment 选定该分类
        if (categoryFragment != null) {
            categoryFragment.selectCategoryByName(categoryName.trim());
        }
    }

    /**
     * 切换到首页(供 CarFragment 空购物车"去购物"按钮调用)
     */
    public void switchToHome() {
        main_bottom_nv.setSelectedItemId(R.id.navigation_home);
    }
    
    // 处理直接跳转到MainActivity并指定标签页的情况
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        
        // 检查是否需要直接跳转到购物车标签
        if (intent != null && intent.getBooleanExtra("launch_cart", false)) {
            // 选中底部导航的购物车项
            main_bottom_nv.setSelectedItemId(R.id.navigation_notifications);
        }
    }
}