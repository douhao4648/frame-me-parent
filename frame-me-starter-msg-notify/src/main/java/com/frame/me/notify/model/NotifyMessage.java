package com.frame.me.notify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用通知消息体.
 *
 * <p>各通道实现按需提取字段，多余字段可忽略。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyMessage {

    /** 消息标题. */
    private String title;

    /** 消息内容（纯文本或 Markdown）. */
    private String content;

    /** 接收者列表. */
    private List<String> receivers;

    /** 抄送列表（邮件专用）. */
    private List<String> cc;

    /** 密送列表（邮件专用）. */
    private List<String> bcc;

    /** 附件路径列表（邮件专用）. */
    private List<String> attachments;

    /** 扩展属性（各通道自定义）. */
    private Map<String, Object> extras;

    /** 模板标识或模板内容字符串. */
    private String template;

    /** 模板参数. */
    @Builder.Default
    private Map<String, Object> templateParams = new HashMap<>();

    /** 模板类型：html / markdown / placeholder，为空时按 html 处理. */
    private String templateType;

    public static NotifyMessage of(String title, String content, List<String> receivers) {
        return NotifyMessage.builder()
                .title(title)
                .content(content)
                .receivers(receivers)
                .build();
    }
}
