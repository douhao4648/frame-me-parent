package com.frame.me.adapter.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.frame.me.api.result.IResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 旧系统响应信封：将 {@link IResult} 的规范字段（{@code msg}/{@code data}/{@code rid}）
 * 重命名为旧系统字段（{@code message}/{@code result}/{@code requestId}）。
 *
 * <p>接口 getter 委托到旧字段实现，保证 JVM 内以 {@link IResult} 视角读取时契约完整；
 * 规范字段名与 {@code success} 均标记 {@link JsonIgnore}，
 * 序列化报文只携带 {@code code}/{@code message}/{@code result}/{@code requestId} 四个字段。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Response<T> implements Serializable, IResult<T> {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

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

    @Override
    @JsonIgnore
    public String getMsg() {
        return message;
    }

    @Override
    @JsonIgnore
    public T getData() {
        return result;
    }

    /**
     * 旧系统信封无错误详情字段，恒为 null.
     */
    @Override
    @JsonIgnore
    public String getErr() {
        return null;
    }

    @Override
    @JsonIgnore
    public String getRid() {
        return requestId;
    }

    @Override
    @JsonIgnore
    public boolean isSuccess() {
        return IResult.super.isSuccess();
    }
}
