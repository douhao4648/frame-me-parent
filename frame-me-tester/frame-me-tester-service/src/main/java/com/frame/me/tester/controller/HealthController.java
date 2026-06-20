package com.frame.me.tester.controller;

import com.frame.me.api.result.IResult;
import com.frame.me.base.result.Result;
import com.frame.me.tester.api.IHealthApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController implements IHealthApi {

    @Override
    public IResult<String> health() {
        String text = null;
        return Result.success(text.toUpperCase());
    }
}
