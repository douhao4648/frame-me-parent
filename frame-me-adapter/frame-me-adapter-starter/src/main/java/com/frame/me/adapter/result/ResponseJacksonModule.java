package com.frame.me.adapter.result;

import com.frame.me.api.result.IResult;
import com.frame.me.base.result.ResultJacksonModule;

public class ResponseJacksonModule extends ResultJacksonModule {
    public ResponseJacksonModule() {
        addAbstractTypeMapping(IResult.class, Response.class);
    }
}