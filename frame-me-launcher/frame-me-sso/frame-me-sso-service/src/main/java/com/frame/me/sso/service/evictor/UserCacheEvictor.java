package com.frame.me.sso.service.evictor;

import com.alicp.jetcache.anno.CacheInvalidate;
import com.frame.me.sso.service.UserService;
import org.springframework.stereotype.Component;

/**
 * 用户缓存失效器.
 *
 * <p>独立 bean 承载失效注解：{@code UserService.delete} 内部若直接调用本类方法，
 * 自调用绕过 AOP 代理注解不生效，故拆出由外部注入调用。</p>
 *
 * @author frame-me
 */
@Component
public class UserCacheEvictor {

    @CacheInvalidate(name = UserService.CACHE_USER_ID, key = "#id")
    public void evictById(Long id) {
        // 仅承载失效注解
    }

    @CacheInvalidate(name = UserService.CACHE_USER_ACCOUNT, key = "#account")
    public void evictByAccount(String account) {
        // 仅承载失效注解
    }
}
