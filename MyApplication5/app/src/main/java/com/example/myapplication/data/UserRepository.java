package com.example.myapplication.data;

import com.example.myapplication.entity.UserInfo;

/**
 * 用户数据访问接口。
 * <p>
 * 职责边界：只负责「用户账号与个人资料」的读写——
 * 注册、登录校验、按 id 查询用户、以及用户名/密码/手机号/头像的更新。
 * 不涉及商品、订单、购物车、评论等其它领域。
 */
public interface UserRepository {

    /** 注册新用户，返回新用户 id，失败（如用户名重复）返回 -1 */
    int register(String username, String password);

    /**
     * 同步后端用户到本地 user_table(幂等)。
     * <p>
     * 用于登录/注册成功后, 把后端返回的 userId + username 落到本地,
     * 保证 ProfileEditActivity 等本地用户表的读路径能命中。
     *
     * @param userId   后端用户主键(必填, > 0)
     * @param username 用户名(必填)
     * @param password 密码(可空, 用于本地登录态兼容)
     * @return true 表示写入成功或记录已存在
     */
    boolean upsertUser(int userId, String username, String password);

    /** 按用户名查询用户，用于登录校验；不存在返回 null */
    UserInfo login(String username);

    /** 按用户 id 查询用户；不存在返回 null */
    UserInfo getUserById(int userId);

    boolean updateUsername(int userId, String newUsername);

    boolean updatePassword(int userId, String newPassword);

    boolean updatePhone(int userId, String newPhone);

    boolean updateAvatar(int userId, String newAvatar);
}
