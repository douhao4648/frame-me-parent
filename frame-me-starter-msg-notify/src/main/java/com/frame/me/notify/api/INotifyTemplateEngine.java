package com.frame.me.notify.api;

import java.util.Map;

/**
 * 通知模板引擎接口.
 */
public interface INotifyTemplateEngine {

    /**
     * 渲染模板.
     *
     * @param template 模板内容或模板标识
     * @param params   模板参数
     * @return 渲染后的内容
     */
    String render(String template, Map<String, Object> params);

    /**
     * 是否支持该模板类型.
     *
     * @param templateType 模板类型
     * @return true 表示支持
     */
    boolean supports(String templateType);

}
