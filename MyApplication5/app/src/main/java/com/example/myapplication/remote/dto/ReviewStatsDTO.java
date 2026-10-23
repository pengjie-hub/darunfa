package com.example.myapplication.remote.dto;

/**
 * 评论统计（后端 /product/{id}/stats 返回）。
 */
public class ReviewStatsDTO {
    public Integer count;
    public Double averageRating;
}
