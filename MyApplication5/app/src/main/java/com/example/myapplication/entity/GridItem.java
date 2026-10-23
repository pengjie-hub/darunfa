package com.example.myapplication.entity;

public class GridItem {
    private int imageRes; // 图片资源ID
    private String text;  // 文字内容
    private int badge;    // 角标数量(>0 时显示)

    public GridItem(int imageRes, String text) {
        this.imageRes = imageRes;
        this.text = text;
        this.badge = 0;
    }

    public GridItem(int imageRes, String text, int badge) {
        this.imageRes = imageRes;
        this.text = text;
        this.badge = badge;
    }

    // Getter 方法
    public int getImageRes() { return imageRes; }
    public String getText() { return text; }
    public int getBadge() { return badge; }

    // Setter 方法
    public void setImageRes(int imageRes) { this.imageRes = imageRes; }
    public void setText(String text) { this.text = text; }
    public void setBadge(int badge) { this.badge = badge; }
}
