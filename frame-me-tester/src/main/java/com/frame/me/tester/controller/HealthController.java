package com.frame.me.tester.controller;

import com.frame.me.common.exception.BusinessException;
import com.frame.me.common.exception.InternalException;
import com.frame.me.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public Result<String> health() {
        String text = null;
        return Result.success(text.toUpperCase());
    }
}
