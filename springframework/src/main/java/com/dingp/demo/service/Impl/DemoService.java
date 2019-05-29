package com.dingp.demo.service.Impl;

import com.dingp.demo.service.IDemoService;

/**
 * @author Administrator
 */
public class DemoService implements IDemoService {

    @Override
    public String get() {
        return "hello";
    }
}
