package com.example.myapplication.data;

import java.util.List;

/**
 * 搜索数据访问接口（搜索历史 + 搜索次数统计）。
 * <p>
 * 职责边界：只负责「搜索」领域的轻量数据——
 * 历史记录（去重、最近优先、限量、单删、清空）与关键词搜索次数统计（用于热门搜索排序）。
 * 不涉及商品、用户、订单。
 * <p>
 * 存储实现：{@link SharedPreferencesSearchHistoryRepository}，
 * 均为轻量、非关系型、仅本机有效的关键词数据，故用 SharedPreferences 而非 SQLite。
 */
public interface SearchHistoryRepository {

    /** 读取搜索历史（最近优先，最多返回 limit 条） */
    List<String> getHistory(int limit);

    /** 记录一次搜索：去重后置顶，超过上限自动淘汰最旧 */
    boolean addHistory(String keyword);

    /** 一键清空全部搜索历史 */
    boolean clearHistory();

    /** 删除单条历史记录（关键词不存在时幂等返回 true） */
    boolean removeHistory(String keyword);

    /** 记录一次搜索事件，累计关键词搜索次数（用于热门搜索排序） */
    void recordSearch(String keyword);

    /** 按搜索次数取热门关键词（次数降序，最多 limit 条） */
    List<String> getHotKeywords(int limit);
}
