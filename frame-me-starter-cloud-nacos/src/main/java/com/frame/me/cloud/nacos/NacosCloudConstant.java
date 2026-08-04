package com.frame.me.cloud.nacos;

/**
 * Nacos 云组件占位常量类.
 *
 * <p>本模块为极薄 starter：仅引入 SCA 官方 nacos-config / nacos-discovery starter，
 * 配置中心与注册中心的具体能力由官方 starter 自带装配提供，本模块不重写.
 * 刷新解密能力（{@code ME(密文)} 在配置中心运行时刷新后重新解密）由
 * {@code frame-me-starter-cloud} 的 {@code RefreshDecryptListener} 提供，
 * 本模块通过依赖 {@code frame-me-starter-cloud} 自动继承.</p>
 *
 * <p>业务配置全部走 SCA 原生 {@code spring.cloud.nacos.*}，本模块不定义 {@code me.*} 配置项.</p>
 *
 * @author frame-me
 */
public final class NacosCloudConstant {

    private NacosCloudConstant() {
    }
}
