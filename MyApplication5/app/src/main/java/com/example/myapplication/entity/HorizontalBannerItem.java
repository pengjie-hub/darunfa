package com.example.myapplication.entity;

import java.util.List;

public class HorizontalBannerItem {
    private List<Integer> bannerImageList; // 轮播图资源列表
    private String title;                  // 标题文本
    private String subtitle;               // 副标题文本

    public HorizontalBannerItem(List<Integer> bannerImageList, String title, String subtitle) {
        this.bannerImageList = bannerImageList;
        this.title = title;
        this.subtitle = subtitle;
    }

    // Getter方法
    public List<Integer> getBannerImageList() { return bannerImageList; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
}