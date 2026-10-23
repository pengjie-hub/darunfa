package com.example.myapplication.entity;

/**
 * 功能项实体类，用于存储功能列表的数据
 */
public class FeatureItem {
    private int iconResId; // 功能图标资源ID
    private String text; // 功能名称
    private int type; // 功能类型：0-垂直排列（第一行），1-水平排列（第二行）

    /**
     * 构造方法
     * @param iconResId 功能图标资源ID
     * @param text 功能名称
     * @param type 功能类型
     */
    public FeatureItem(int iconResId, String text, int type) {
        this.iconResId = iconResId;
        this.text = text;
        this.type = type;
    }

    /**
     * 获取功能图标资源ID
     * @return 功能图标资源ID
     */
    public int getIconResId() {
        return iconResId;
    }

    /**
     * 设置功能图标资源ID
     * @param iconResId 功能图标资源ID
     */
    public void setIconResId(int iconResId) {
        this.iconResId = iconResId;
    }

    /**
     * 获取功能名称
     * @return 功能名称
     */
    public String getText() {
        return text;
    }

    /**
     * 设置功能名称
     * @param text 功能名称
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * 获取功能类型
     * @return 功能类型：0-垂直排列，1-水平排列
     */
    public int getType() {
        return type;
    }

    /**
     * 设置功能类型
     * @param type 功能类型：0-垂直排列，1-水平排列
     */
    public void setType(int type) {
        this.type = type;
    }
}
