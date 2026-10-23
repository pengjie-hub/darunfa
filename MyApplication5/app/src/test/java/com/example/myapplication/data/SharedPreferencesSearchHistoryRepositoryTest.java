package com.example.myapplication.data;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link SharedPreferencesSearchHistoryRepository} 的本地单元测试（JVM，无需设备）。
 * <p>
 * 用内存版 SharedPreferences 假实现（{@link FakeSharedPreferences}）驱动真实仓储逻辑，
 * 覆盖：去重置顶、上限 10 条、单条删除幂等、一键清空、搜索次数统计与排序。
 */
public class SharedPreferencesSearchHistoryRepositoryTest {

    private FakeSharedPreferences sp;
    private SharedPreferencesSearchHistoryRepository repo;

    @Before
    public void setUp() {
        sp = new FakeSharedPreferences();
        repo = new SharedPreferencesSearchHistoryRepository(sp);
    }

    @Test
    public void addHistory_dedupsAndPrepends() {
        repo.addHistory("苹果");
        repo.addHistory("香蕉");
        repo.addHistory("苹果"); // 重复 -> 去重并置顶

        assertEquals(Arrays.asList("苹果", "香蕉"), repo.getHistory(10));
    }

    @Test
    public void addHistory_capsAtTen_mostRecentFirst() {
        for (int i = 1; i <= 12; i++) {
            repo.addHistory("词" + i);
        }
        List<String> history = repo.getHistory(20);
        assertEquals(10, history.size());
        assertEquals("词12", history.get(0));       // 最近优先
        assertEquals("词3", history.get(history.size() - 1)); // 最旧的被淘汰
    }

    @Test
    public void removeHistory_removesAndIsIdempotent() {
        repo.addHistory("苹果");
        repo.addHistory("香蕉");

        assertTrue(repo.removeHistory("苹果"));
        assertEquals(Arrays.asList("香蕉"), repo.getHistory(10));

        assertTrue(repo.removeHistory("不存在")); // 幂等，不抛异常
    }

    @Test
    public void clearHistory_emptiesAll() {
        repo.addHistory("苹果");
        repo.addHistory("香蕉");

        assertTrue(repo.clearHistory());
        assertTrue(repo.getHistory(10).isEmpty());
    }

    @Test
    public void recordSearch_countsAndSortsDescending() {
        repo.recordSearch("苹果");
        repo.recordSearch("香蕉");
        repo.recordSearch("香蕉");
        repo.recordSearch("橙子");
        repo.recordSearch("橙子");
        repo.recordSearch("橙子");

        // 橙子(3) > 香蕉(2) > 苹果(1)
        assertEquals(Arrays.asList("橙子", "香蕉", "苹果"), repo.getHotKeywords(10));
    }

    @Test
    public void getHotKeywords_respectsLimit() {
        repo.recordSearch("a");
        repo.recordSearch("b");
        repo.recordSearch("c");

        assertEquals(2, repo.getHotKeywords(2).size());
    }

    // ==================== 内存版 SharedPreferences 假实现 ====================

    @SuppressWarnings("unchecked")
    static class FakeSharedPreferences implements SharedPreferences {
        private final Map<String, Object> map = new HashMap<>();
        private final Editor editor = new FakeEditor();

        @Override public Map<String, ?> getAll() { return new HashMap<>(map); }
        @Override public String getString(String key, String def) { Object v = map.get(key); return v == null ? def : (String) v; }
        @Override public Set<String> getStringSet(String key, Set<String> def) { Object v = map.get(key); return v == null ? def : (Set<String>) v; }
        @Override public int getInt(String key, int def) { Object v = map.get(key); return v == null ? def : (Integer) v; }
        @Override public long getLong(String key, long def) { Object v = map.get(key); return v == null ? def : (Long) v; }
        @Override public float getFloat(String key, float def) { Object v = map.get(key); return v == null ? def : (Float) v; }
        @Override public boolean getBoolean(String key, boolean def) { Object v = map.get(key); return v == null ? def : (Boolean) v; }
        @Override public boolean contains(String key) { return map.containsKey(key); }
        @Override public Editor edit() { return editor; }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}

        class FakeEditor implements Editor {
            private final Map<String, Object> pending = new HashMap<>();
            private boolean clearAll = false;

            @Override public Editor putString(String key, String value) { pending.put(key, value); return this; }
            @Override public Editor putStringSet(String key, Set<String> values) { pending.put(key, values); return this; }
            @Override public Editor putInt(String key, int value) { pending.put(key, value); return this; }
            @Override public Editor putLong(String key, long value) { pending.put(key, value); return this; }
            @Override public Editor putFloat(String key, float value) { pending.put(key, value); return this; }
            @Override public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
            @Override public Editor remove(String key) { pending.put(key, null); return this; }
            @Override public Editor clear() { clearAll = true; return this; }
            @Override public boolean commit() { applyPending(); return true; }
            @Override public void apply() { applyPending(); }

            private void applyPending() {
                if (clearAll) map.clear();
                for (Map.Entry<String, Object> e : pending.entrySet()) {
                    if (e.getValue() == null) map.remove(e.getKey());
                    else map.put(e.getKey(), e.getValue());
                }
                pending.clear();
                clearAll = false;
            }
        }
    }
}
