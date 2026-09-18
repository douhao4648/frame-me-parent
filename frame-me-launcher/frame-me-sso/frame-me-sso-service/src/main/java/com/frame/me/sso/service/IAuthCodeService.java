package com.frame.me.sso.service;

/**
 * SSO 授权码服务接口.
 *
 * <p>Redis 为唯一存储（短时效 60s、一次性、原子防重放）。授权码 60s 过期，
 * Redis 故障期间整个登录流程都跑不了（sa-token 会话也在 Redis），DB 兜底无意义.</p>
 *
 * @author frame-me
 */
public interface IAuthCodeService {

    /**
     * 签发授权码.
     *
     * <p>value 用 JSON 序列化：scopes/redirectUri 用户可控，":" 等分隔符拼接会被注入错位.</p>
     */
    String issue(String appId, Long userId, String scopes, String redirectUri);

    /**
     * 只读查看授权码（不消费）.
     *
     * <p>供 token 端点「peek → 校验应用/密钥/redirectUri → consume」顺序使用：
     * 校验失败时不烧码，防止任何人拿合法 code + 错误密钥调一次就把码失效（登录 DoS）.</p>
     *
     * @return null 表示无效或已使用
     */
    CodePayload peek(String code);

    /**
     * 校验并消费授权码（一次性，原子操作）.
     *
     * <p>{@code GETDEL}（Redis 6.2+）一次往返取值的同时删除，并发下只有一个请求能拿到
     * value，杜绝 GET+DELETE 两次往返的重放窗口.</p>
     *
     * @return null 表示无效或已使用
     */
    CodePayload consume(String code);

    /**
     * 授权码负载.
     */
    class CodePayload {

        public String appId;
        public Long userId;
        public String scopes;
        public String redirectUri;
    }
}
