package com.frame.me.sso.service;

import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.frame.me.sso.entity.UserEntity;
import com.frame.me.sso.mapper.UserMapper;
import com.frame.me.sso.service.evictor.UserCacheEvictor;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SSO 用户服务.
 *
 * <p>按账号/ID 的单条查询走 L1(Caffeine)+L2(Redis) 两级缓存（60s，缓存空值防穿透）：
 * 登录、/userinfo、{@code SsoStpInterface} 角色检查共享同一份缓存。
 * 写路径（更新/删除）同步失效——缓存的是密码哈希/状态/角色等安全敏感数据，
 * 不能只靠 TTL 等自然过期。</p>
 *
 * @author frame-me
 */
@Service
@RequiredArgsConstructor
public class UserService {

    public static final String CACHE_USER_ACCOUNT = "sso:user:account:";
    public static final String CACHE_USER_ID = "sso:user:id:";

    private final UserMapper userMapper;
    private final UserCacheEvictor cacheEvictor;

    /**
     * 根据账号查询用户.
     */
    @Cached(name = CACHE_USER_ACCOUNT, key = "#account", cacheType = CacheType.BOTH,
            expire = 60, cacheNullValue = true)
    public UserEntity findByAccount(String account) {
        return userMapper.selectOneByQuery(QueryWrapper.create().eq("account", account));
    }

    /**
     * 根据用户 ID 查询用户.
     */
    @Cached(name = CACHE_USER_ID, key = "#id", cacheType = CacheType.BOTH,
            expire = 60, cacheNullValue = true)
    public UserEntity findById(Long id) {
        return userMapper.selectOneById(id);
    }

    /**
     * 查询全部用户.
     */
    public List<UserEntity> list() {
        return userMapper.selectListByQuery(QueryWrapper.create());
    }

    /**
     * 保存用户.
     *
     * <p>失效账号缓存：创建前的存在性检查可能已把"账号不存在"（null）缓存，
     * 不失效则新建用户在 TTL 内仍被当作不存在。</p>
     */
    @CacheInvalidate(name = CACHE_USER_ACCOUNT, key = "#user.account")
    public void save(UserEntity user) {
        userMapper.insert(user);
    }

    /**
     * 更新用户（同步失效按 ID 与按账号两份缓存）.
     */
    @CacheInvalidate(name = CACHE_USER_ID, key = "#user.id")
    @CacheInvalidate(name = CACHE_USER_ACCOUNT, key = "#user.account")
    public void update(UserEntity user) {
        userMapper.update(user);
    }

    /**
     * 删除用户（逻辑删除，BaseEntity.deleted 标志）.
     *
     * <p>先按 ID 取账号再同步失效两份缓存；失效走 {@link UserCacheEvictor}
     * 独立 bean，避免自调用绕过 AOP 导致注解不生效。</p>
     */
    public void delete(Long id) {
        UserEntity user = findById(id);
        userMapper.deleteById(id);
        cacheEvictor.evictById(id);
        if (user != null) {
            cacheEvictor.evictByAccount(user.getAccount());
        }
    }
}
