package com.frame.me.tester.controller;

import com.frame.me.api.result.IResult;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.exception.InternalException;
import com.frame.me.base.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public IResult<String> health() {
        String text = null;
        return Result.success(text.toUpperCase());
    }
}
