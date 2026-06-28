package com.frame.me.tester.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户创建事件负载.
 *
 * @author frame-me
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatedPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
}
