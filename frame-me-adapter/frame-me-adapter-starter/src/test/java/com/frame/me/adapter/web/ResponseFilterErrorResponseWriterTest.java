package com.frame.me.adapter.web;

import com.frame.me.base.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ResponseFilterErrorResponseWriter} 单元测试.
 *
 * @author frame-me
 */
class ResponseFilterErrorResponseWriterTest {

    private final ResponseFilterErrorResponseWriter writer =
            new ResponseFilterErrorResponseWriter(new ObjectMapper());

    @Test
    void testWriteUnauthorizedResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, ResultCode.UNAUTHORIZED, null);

        assertEquals(200, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"code\":401"));
        assertTrue(body.contains("\"message\":\"未授权\""));
        assertTrue(body.contains("\"result\":null"));
        assertTrue(body.contains("\"requestId\":null"));
    }
}
