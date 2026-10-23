package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 基于 SharedPreferences 的搜索数据实现（历史 + 次数统计）。
 * <p>
 * 历史关键词以 JSON 数组字符串持久化（最近优先，下标 0 为最近一次）。
 * 搜索次数以 JSON 对象持久化（keyword -> count），用于热门搜索排序。
 * 均使用 Android 内置 org.json，不引入第三方依赖。
 */
public class SharedPreferencesSearchHistoryRepository implements SearchHistoryRepository {

    private static final String PREFS_NAME = "search_history";
    private static final String KEY_KEYWORDS = "keywords";
    private static final String KEY_COUNTS = "counts";
    private static final int MAX_HISTORY = 10;

    private final SharedPreferences sp;

    public SharedPreferencesSearchHistoryRepository(Context context) {
        this(context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE));
    }

    /** 测试/注入用构造器：直接注入 SharedPreferences（同包可见） */
    SharedPreferencesSearchHistoryRepository(SharedPreferences sp) {
        this.sp = sp;
    }

    // ==================== 历史 ====================

    @Override
    public synchronized List<String> getHistory(int limit) {
        List<String> list = new ArrayList<>();
        String raw = sp.getString(KEY_KEYWORDS, "");
        if (raw == null || raw.isEmpty()) {
            return list;
        }
        try {
            JSONArray array = new JSONArray(raw);
            int count = Math.min(limit, array.length());
            for (int i = 0; i < count; i++) {
                String kw = array.optString(i, "").trim();
                if (!kw.isEmpty()) {
                    list.add(kw);
                }
            }
        } catch (Exception ignored) {
            // 数据损坏时按空历史处理
        }
        return list;
    }

    @Override
    public synchronized boolean addHistory(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return false;
        }
        String kw = keyword.trim();
        List<String> list = getHistory(MAX_HISTORY * 2);
        list.remove(kw);
        list.add(0, kw);
        if (list.size() > MAX_HISTORY) {
            list = new ArrayList<>(list.subList(0, MAX_HISTORY));
        }
        return persistKeywords(list);
    }

    @Override
    public synchronized boolean clearHistory() {
        return sp.edit().remove(KEY_KEYWORDS).commit();
    }

    @Override
    public synchronized boolean removeHistory(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return false;
        }
        String kw = keyword.trim();
        List<String> list = getHistory(MAX_HISTORY * 2);
        if (!list.remove(kw)) {
            return true;
        }
        return persistKeywords(list);
    }

    // ==================== 次数统计（热门搜索） ====================

    @Override
    public synchronized void recordSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return;
        }
        String kw = keyword.trim();
        JSONObject counts = readCounts();
        try {
            counts.put(kw, counts.optInt(kw, 0) + 1);
            sp.edit().putString(KEY_COUNTS, counts.toString()).commit();
        } catch (Exception ignored) {
            // 序列化失败时静默忽略本次统计，不影响搜索主流程
        }
    }

    @Override
    public synchronized List<String> getHotKeywords(int limit) {
        JSONObject counts = readCounts();
        List<String> keys = new ArrayList<>();
        Iterator<String> it = counts.keys();
        while (it.hasNext()) {
            keys.add(it.next());
        }
        keys.sort((a, b) -> Integer.compare(counts.optInt(b, 0), counts.optInt(a, 0)));
        List<String> result = new ArrayList<>();
        int n = Math.min(limit, keys.size());
        for (int i = 0; i < n; i++) {
            result.add(keys.get(i));
        }
        return result;
    }

    // ==================== 内部工具 ====================

    private boolean persistKeywords(List<String> list) {
        JSONArray array = new JSONArray();
        for (String kw : list) {
            array.put(kw);
        }
        return sp.edit().putString(KEY_KEYWORDS, array.toString()).commit();
    }

    private JSONObject readCounts() {
        String raw = sp.getString(KEY_COUNTS, "{}");
        try {
            return new JSONObject(raw);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
