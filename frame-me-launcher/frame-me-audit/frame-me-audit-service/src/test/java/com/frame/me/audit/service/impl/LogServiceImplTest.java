package com.frame.me.audit.service.impl;

import com.frame.me.audit.api.query.LogQuery;
import com.frame.me.audit.api.vo.LogVO;
import com.frame.me.audit.entity.LogEntity;
import com.frame.me.audit.mapper.LogMapper;
import com.frame.me.audit.service.convert.LogConvert;
import com.frame.me.base.exception.BusinessException;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LogServiceImpl} 单元测试：查询条件构建与详情不存在分支.
 *
 * @author frame-me
 */
@ExtendWith(MockitoExtension.class)
class LogServiceImplTest {

    @Mock
    private LogMapper auditLogMapper;
    @Mock
    private LogConvert auditLogConvert;
    @InjectMocks
    private LogServiceImpl auditLogService;

    /**
     * list：全部搜索条件都应拼进 QueryWrapper，且默认按 timestamp 倒序.
     */
    @Test
    void list_buildsAllSearchConditions() {
        LogQuery query = new LogQuery();
        query.setAction("login");
        query.setCategory("AUTH");
        query.setOperatorId("admin");
        query.setDescription("登");
        query.setSourceService("frame-me-sso");
        query.setSuccess(Boolean.TRUE);
        query.setStartTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 8, 13, 0, 0));
        when(auditLogMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        auditLogService.list(query);

        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(auditLogMapper).selectListByQuery(captor.capture());
        String sql = captor.getValue().toSQL();
        assertThat(sql).contains("`action` LIKE");
        assertThat(sql).contains("`category` =");
        assertThat(sql).contains("`operator_id` LIKE");
        assertThat(sql).contains("`description` LIKE");
        assertThat(sql).contains("`source_service` =");
        assertThat(sql).contains("`success` =");
        assertThat(sql).contains("`timestamp` >=");
        assertThat(sql).contains("`timestamp` <=");
        // ORDER BY 为 PageUtils 白名单解析后的裸 SQL 片段，不做反引号/大写转换
        assertThat(sql).contains("ORDER BY timestamp desc");
    }

    /**
     * list：空条件时无 WHERE，仅默认排序.
     */
    @Test
    void list_blankQueryOnlyDefaultOrder() {
        LogQuery query = new LogQuery();
        when(auditLogMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        auditLogService.list(query);

        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(auditLogMapper).selectListByQuery(captor.capture());
        String sql = captor.getValue().toSQL();
        assertThat(sql).doesNotContain("WHERE");
        assertThat(sql).contains("ORDER BY timestamp desc");
    }

    /**
     * page：分页参数透传 + 条件复用.
     */
    @Test
    void page_paginatesWithConditions() {
        LogQuery query = new LogQuery();
        query.setCurrent(2L);
        query.setSize(20L);
        query.setCategory("AUTH");
        Page<LogEntity> page = new Page<>(List.of(), 2, 20, 0);
        when(auditLogMapper.paginate(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        var result = auditLogService.page(query);

        ArgumentCaptor<Page<LogEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<QueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(auditLogMapper).paginate(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(20);
        assertThat(wrapperCaptor.getValue().toSQL()).contains("`category` =");
        assertThat(result.getCurrent()).isEqualTo(2L);
        assertThat(result.getSize()).isEqualTo(20L);
    }

    /**
     * getById：mapper.xml 查询命中返回 VO，未命中抛 NOT_FOUND.
     */
    @Test
    void getById_foundAndNotFound() {
        LogVO vo = new LogVO();
        vo.setId(1L);
        when(auditLogMapper.getById(1L)).thenReturn(vo);

        assertThat(auditLogService.getById(1L)).isSameAs(vo);

        when(auditLogMapper.getById(2L)).thenReturn(null);
        assertThatThrownBy(() -> auditLogService.getById(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审计日志 2 不存在");
    }

    /**
     * LIKE 查询值含通配符 {@code %} / {@code _} 时应被转义为字面量，而非作为 SQL 通配符.
     *
     * <p>用户传 {@code 100%} 应匹配字面量 {@code 100%} 而非"以 100 开头的任意串"；
     * 传 {@code a_b} 应匹配字面 {@code a_b} 而非"a+任意单字符+b".</p>
     */
    @Test
    void list_likeValueEscapesWildcards() {
        LogQuery query = new LogQuery();
        query.setOperatorId("100%");
        query.setDescription("a_b");
        when(auditLogMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        auditLogService.list(query);

        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(auditLogMapper).selectListByQuery(captor.capture());
        String sql = captor.getValue().toSQL();
        // 转义正确：escapeLike 把 100% → 100\%（反斜杠转义 %），toSQL 内联时再对 \ 做字符串转义
        // 成 100\\%（双反斜杠 + %）。a_b → a\_b → toSQL 成 a\\_b 同理。
        // 关键：用户传入的 %/_ 被反斜杠前缀转义为字面量，不再作为 LIKE 通配符裸奔。
        assertThat(sql).contains("100\\\\%");
        assertThat(sql).contains("a\\\\_b");
    }
}
