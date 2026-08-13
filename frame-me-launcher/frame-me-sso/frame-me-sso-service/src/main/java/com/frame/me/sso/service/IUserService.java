package com.frame.me.sso.service;

import com.frame.me.sso.entity.UserEntity;

import java.util.List;

/**
 * SSO 用户服务接口.
 *
 * <p>按账号/ID 的单条查询走 L1(Caffeine)+L2(Redis) 两级缓存（60s，缓存空值防穿透）：
 * 登录、/userinfo、{@code SsoStpInterface} 角色检查共享同一份缓存。
 * 写路径（更新/删除）同步失效——缓存的是密码哈希/状态/角色等安全敏感数据，
 * 不能只靠 TTL 等自然过期。</p>
 *
 * @author frame-me
 */
public interface IUserService {

    /**
     * 按账号查询缓存名（L1+L2，60s）.
     */
    String CACHE_USER_ACCOUNT = "sso:user:account:";
    /**
     * 按 ID 查询缓存名（L1+L2，60s）.
     */
    String CACHE_USER_ID = "sso:user:id:";

    /**
     * 根据账号查询用户.
     */
    UserEntity findByAccount(String account);

    /**
     * 根据用户 ID 查询用户.
     */
    UserEntity findById(Long id);

    /**
     * 查询全部用户.
     */
    List<UserEntity> list();

    /**
     * 保存用户.
     *
     * <p>失效账号缓存：创建前的存在性检查可能已把"账号不存在"（null）缓存，
     * 不失效则新建用户在 TTL 内仍被当作不存在。</p>
     */
    void save(UserEntity user);

    /**
     * 更新用户（同步失效按 ID 与按账号两份缓存）.
     */
    void update(UserEntity user);

    /**
     * 删除用户（逻辑删除，BaseEntity.deleted 标志）.
     */
    void delete(Long id);
}
