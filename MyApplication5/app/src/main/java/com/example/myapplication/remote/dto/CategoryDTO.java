package com.example.myapplication.remote.dto;

/**
 * 商品分类（后端 Category 实体对应）。
 */
public class CategoryDTO {
    public long id;
    public String name;
    public long parentId;
    public int sortOrder;
    public String icon;
    public int status;
}
