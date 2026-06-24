package com.frame.me.base.result;

import com.frame.me.api.result.IResult;
import tools.jackson.databind.module.SimpleModule;

public class ResultJacksonModule extends SimpleModule {
    public ResultJacksonModule() {
        addAbstractTypeMapping(IResult.class, Result.class);
    }
}