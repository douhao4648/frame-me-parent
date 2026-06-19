package com.frame.me.base.env;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Spring 环境辅助类.
 * <p>
 * 用于获取当前激活的 profile，并提供常用的环境判断方法。
 */
public class EnvironmentHelper {

    /**
     * 默认 profile 名称.
     */
    public static final String DEFAULT_PROFILE = "default";

    /**
     * 开发环境 profile 名称.
     */
    public static final String PROFILE_DEV = "dev";

    /**
     * 测试环境 profile 名称.
     */
    public static final String PROFILE_TEST = "test";

    /**
     * 生产环境 profile 名称.
     */
    public static final String PROFILE_PROD = "prod";

    /**
     * 日常环境 profile 名称.
     */
    public static final String PROFILE_DAILY = "daily";

    /**
     * 预发环境 profile 名称.
     */
    public static final String PROFILE_PRE = "pre";

    private final Environment environment;
    private final Set<String> activeProfiles;

    public EnvironmentHelper(Environment environment) {
        this.environment = environment;
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            this.activeProfiles = Collections.emptySet();
        } else {
            this.activeProfiles = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(profiles)));
        }
    }

    /**
     * 获取所有 active profile.
     *
     * @return active profile 数组；若未配置则返回空数组
     */
    public String[] getActiveProfiles() {
        return activeProfiles.toArray(new String[0]);
    }

    /**
     * 获取首个 active profile.
     * <p>
     * 当未配置任何 active profile 时，返回 {@value #DEFAULT_PROFILE}。
     *
     * @return 首个 active profile 或 default
     */
    public String getActiveProfile() {
        if (activeProfiles.isEmpty()) {
            return DEFAULT_PROFILE;
        }
        return activeProfiles.iterator().next();
    }

    /**
     * 判断指定 profile 是否处于 active 状态.
     *
     * @param profile profile 名称
     * @return 是否激活
     */
    public boolean isProfileActive(String profile) {
        if (!StringUtils.hasText(profile)) {
            return false;
        }
        return activeProfiles.contains(profile);
    }

    /**
     * 当前是否为开发环境.
     *
     * @return 是否激活 dev profile
     */
    public boolean isDev() {
        return isProfileActive(PROFILE_DEV);
    }

    /**
     * 当前是否为测试环境.
     *
     * @return 是否激活 test profile
     */
    public boolean isTest() {
        return isProfileActive(PROFILE_TEST);
    }

    /**
     * 当前是否为生产环境.
     *
     * @return 是否激活 prod profile
     */
    public boolean isProd() {
        return isProfileActive(PROFILE_PROD);
    }

    /**
     * 当前是否为日常环境.
     *
     * @return 是否激活 daily profile
     */
    public boolean isDaily() {
        return isProfileActive(PROFILE_DAILY);
    }

    /**
     * 当前是否为预发环境.
     *
     * @return 是否激活 pre profile
     */
    public boolean isPre() {
        return isProfileActive(PROFILE_PRE);
    }
}
