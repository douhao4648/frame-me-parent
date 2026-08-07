package com.frame.me.sso.api;

import com.frame.me.api.result.IResult;
import com.frame.me.sso.api.dto.AppRegisterDTO;
import com.frame.me.sso.api.dto.AppUpdateDTO;
import com.frame.me.sso.api.vo.AppVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * SSO 应用资源 API 契约（需 admin 角色）.
 *
 * <p>REST 风格：集合路径用复数 {@code /api/apps}，单项操作挂在 {@code /{appId}} 下，
 * 非 CRUD 动作用 {@code POST /{appId}/<action>} 子路径.
 *
 * <p>服务端 {@code AppController implements IAppApi}，
 * 下游管理工具可用 HTTP Interface 代理调用.</p>
 *
 * @author frame-me
 */
@Tag(name = "SSO 应用管理", description = "应用注册/更新/禁用/列表")
@HttpExchange("/api/apps")
public interface IAppApi {

    /**
     * 注册应用（集合根创建）.
     */
    @Operation(summary = "注册应用", description = "登记内部/外部应用，分配 appId")
    @PostExchange("/")
    IResult<AppVO> registerApp(@Valid @RequestBody AppRegisterDTO dto);

    /**
     * 应用列表（集合根查询）.
     */
    @Operation(summary = "应用列表", description = "列出全部已注册应用")
    @GetExchange("/")
    IResult<List<AppVO>> listApps();

    /**
     * 更新应用（全量更新）.
     */
    @Operation(summary = "更新应用", description = "更新回调白名单、scope、状态")
    @PostExchange("/{appId}")
    IResult<Boolean> updateApp(@Parameter(description = "应用 ID", required = true) @PathVariable String appId,
                               @Valid @RequestBody AppUpdateDTO app);

    /**
     * 重置应用密钥（子动作）.
     */
    @Operation(summary = "重置密钥", description = "重置应用密钥，明文仅此一次返回")
    @PostExchange("/{appId}/reset-secret")
    IResult<AppVO> resetSecret(@Parameter(description = "应用 ID", required = true) @PathVariable String appId);

    /**
     * 禁用应用（状态变更子动作，非物理删除）.
     */
    @Operation(summary = "禁用应用", description = "禁用应用并联动踢出该应用全部存量会话（禁用即生效）")
    @PostExchange("/{appId}/disable")
    IResult<Boolean> disableApp(@Parameter(description = "应用 ID", required = true) @PathVariable String appId);

    /**
     * 按应用踢人（注销该 appId 全部会话，子动作）.
     */
    @Operation(summary = "按应用踢人", description = "注销该应用全部会话（用户 token + client_credentials 应用 token），发 UserLogoutEvent（userId=null）")
    @PostExchange("/{appId}/logout")
    IResult<Integer> logoutApp(@Parameter(description = "应用 ID", required = true) @PathVariable String appId,
                               @Parameter(description = "踢人原因") @RequestParam(defaultValue = "admin") String reason);
}
