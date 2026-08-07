package com.frame.me.sso.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.frame.me.api.result.IResult;
import com.frame.me.base.result.Result;
import com.frame.me.sso.api.IAppApi;
import com.frame.me.sso.api.dto.AppRegisterDTO;
import com.frame.me.sso.api.dto.AppUpdateDTO;
import com.frame.me.sso.api.enums.AccessType;
import com.frame.me.sso.api.vo.AppVO;
import com.frame.me.sso.entity.AppEntity;
import com.frame.me.sso.service.AppService;
import com.frame.me.sso.service.LogoutService;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SSO 应用资源端点，实现 {@link IAppApi}（@SaCheckRole admin 保护）.
 *
 * <p>路由声明与参数绑定注解在接口上统一维护，本类方法仅标 {@code @Override}.</p>
 *
 * @author frame-me
 */
@RestController
@RequiredArgsConstructor
public class AppController implements IAppApi {

    private final AppService appService;
    private final LogoutService logoutService;

    /**
     * 注册应用.
     */
    @SaCheckRole("admin")
    @Override
    public IResult<AppVO> registerApp(AppRegisterDTO dto) {
        AccessType type = AccessType.valueOf(dto.getAccessType());
        AppEntity app = appService.register(
                dto.getAppName(), type, dto.getRedirectUris(),
                dto.getScopes() != null ? dto.getScopes() : "openid");
        return Result.success(toVO(app));
    }

    /**
     * 应用列表.
     */
    @SaCheckRole("admin")
    @Override
    public IResult<List<AppVO>> listApps() {
        return Result.success(appService.list().stream()
                .map(this::toVO).collect(Collectors.toList()));
    }

    /**
     * 更新应用.
     */
    @SaCheckRole("admin")
    @Override
    public IResult<Boolean> updateApp(String appId, AppUpdateDTO app) {
        AppEntity entity = appService.findByAppId(appId);
        if (entity == null) {
            return Result.error(com.frame.me.base.result.ResultCode.ERROR, "应用不存在");
        }
        if (app.getRedirectUris() != null) {
            entity.setRedirectUris(JSON.toJSONString(app.getRedirectUris()));
        }
        if (app.getScopes() != null) {
            entity.setScopes(app.getScopes());
        }
        if (app.getStatus() != null) {
            entity.setStatus(app.getStatus());
        }
        appService.update(entity);
        return Result.success(true);
    }

    /**
     * 重置应用密钥（仅 EXTERNAL）.
     */
    @SaCheckRole("admin")
    @Override
    public IResult<AppVO> resetSecret(String appId) {
        String secret = appService.resetSecret(appId);
        AppEntity app = appService.findByAppId(appId);
        AppVO vo = toVO(app);
        vo.setAppSecret(secret);
        return Result.success(vo);
    }

    /**
     * 禁用应用：禁用即生效——除阻断新发 token 外，联动踢出该应用全部存量会话
     * （用户 token + 应用 token），避免"已颁发 token 自然过期"窗口内继续可用.
     */
    @SaCheckRole("admin")
    @Override
    public IResult<Boolean> disableApp(String appId) {
        appService.disable(appId);
        logoutService.logoutByApp(appId, "app-disabled");
        return Result.success(true);
    }

    /**
     * 按应用踢人：注销该 appId 全部会话（用户 token + 应用 token），返回踢掉的会话数.
     */
    @SaCheckRole("admin")
    @Override
    public IResult<Integer> logoutApp(String appId, String reason) {
        return Result.success(logoutService.logoutByApp(appId, reason));
    }

    private AppVO toVO(AppEntity app) {
        AppVO vo = new AppVO();
        vo.setAppId(app.getAppId());
        vo.setAppName(app.getAppName());
        vo.setAccessType(app.getAccessType());
        vo.setStatus(app.getStatus());
        // appSecretPlain 仅 register/resetSecret 时由 service 设值（transient，不入库），
        // listApps 从 DB 查的 app 无此字段，setAppSecret(null) 行为正确
        vo.setAppSecret(app.getAppSecretPlain());
        return vo;
    }
}
