package com.example.myapplication.data;

import android.content.Context;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.entity.UserInfo;

import java.util.List;

/**
 * 基于本地 SQLite 的数据访问实现（适配器）。
 * <p>
 * 目前仅保留「用户资料」与「浏览历史」两个仍走本地存储的领域；
 * 商品、购物车、订单、评论已迁移到远程后端（见 Remote*Repository）。
 */
public class SqliteDataRepository implements UserRepository, BrowseHistoryRepository {

    private final AppDatabase db;

    public SqliteDataRepository(Context context) {
        this.db = AppDatabase.getInstance(context.getApplicationContext());
    }

    // ==================== UserRepository ====================
    @Override
    public int register(String username, String password) {
        return db.register(username, password);
    }

    @Override
    public boolean upsertUser(int userId, String username, String password) {
        return db.upsertUser(userId, username, password);
    }

    @Override
    public UserInfo login(String username) {
        return db.login(username);
    }

    @Override
    public UserInfo getUserById(int userId) {
        return db.getUserById(userId);
    }

    @Override
    public boolean updateUsername(int userId, String newUsername) {
        return db.updateUsername(userId, newUsername);
    }

    @Override
    public boolean updatePassword(int userId, String newPassword) {
        return db.updatePassword(userId, newPassword);
    }

    @Override
    public boolean updatePhone(int userId, String newPhone) {
        return db.updatePhone(userId, newPhone);
    }

    @Override
    public boolean updateAvatar(int userId, String newAvatar) {
        return db.updateAvatar(userId, newAvatar);
    }

    // ==================== BrowseHistoryRepository ====================
    @Override
    public boolean addBrowseHistory(int userId, int productId, String browseTime) {
        return db.addBrowseHistory(userId, productId, browseTime);
    }

    @Override
    public List<Integer> getBrowseHistoryProductIds(int userId, int limit) {
        return db.getBrowseHistoryProductIds(userId, limit);
    }

    @Override
    public boolean clearBrowseHistory(int userId) {
        return db.clearBrowseHistory(userId);
    }
}
