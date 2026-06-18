package com.frame.me.adapter.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Response<T> implements Serializable {

    /**
     * 编号.
     */
    private Integer code;

    /**
     * 信息.
     */
    private String message;

    /**
     * 结果数据
     */
    private T result;

    /**
     * 请求ID
     */
    private String requestId;

}
