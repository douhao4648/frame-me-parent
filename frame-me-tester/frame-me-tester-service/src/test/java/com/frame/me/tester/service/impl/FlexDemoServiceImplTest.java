package com.frame.me.tester.service.impl;

import com.frame.me.base.exception.BusinessException;
import com.frame.me.tester.api.dto.FlexDemoDTO;
import com.frame.me.tester.entity.FlexDemoEntity;
import com.frame.me.tester.mapper.FlexDemoMapper;
import com.frame.me.tester.service.convert.FlexDemoConvert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FlexDemoServiceImpl} 乐观锁校验单元测试.
 *
 * @author frame-me
 */
class FlexDemoServiceImplTest {

    private FlexDemoMapper flexDemoMapper;
    private FlexDemoConvert flexDemoConvert;
    private FlexDemoServiceImpl service;

    @BeforeEach
    void setUp() {
        flexDemoMapper = mock(FlexDemoMapper.class);
        flexDemoConvert = mock(FlexDemoConvert.class);
        service = new FlexDemoServiceImpl(flexDemoMapper, flexDemoConvert);
    }

    /**
     * version 为 null 时拒绝更新：MyBatis-Flex 在 version 为 null 时跳过乐观锁检查，
     * 不校验则并发更新失去保护.
     */
    @Test
    void updateRejectsNullVersion() {
        FlexDemoEntity exist = new FlexDemoEntity();
        exist.setId(1L);
        exist.setVersion(1);
        when(flexDemoMapper.selectOneById(1L)).thenReturn(exist);

        FlexDemoDTO dto = new FlexDemoDTO();
        dto.setName("updated");
        // version 留空

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("version");

        // 校验失败不得进入更新
        verify(flexDemoMapper, never()).update(any());
    }

    /**
     * version 非空时正常更新.
     */
    @Test
    void updateAcceptsNonNullVersion() {
        FlexDemoEntity exist = new FlexDemoEntity();
        exist.setId(1L);
        exist.setVersion(1);
        when(flexDemoMapper.selectOneById(1L)).thenReturn(exist);
        FlexDemoEntity entity = new FlexDemoEntity();
        entity.setId(1L);
        entity.setVersion(1);
        when(flexDemoConvert.toEntity(any())).thenReturn(entity);
        when(flexDemoMapper.update(any())).thenReturn(1);

        FlexDemoDTO dto = new FlexDemoDTO();
        dto.setName("updated");
        dto.setVersion(1);

        assertThat(service.update(1L, dto)).isTrue();
        verify(flexDemoMapper).update(any());
    }
}
