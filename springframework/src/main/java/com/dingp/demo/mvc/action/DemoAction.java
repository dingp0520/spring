package com.dingp.demo.mvc.action;

import com.dingp.demo.service.IDemoService;
import com.dingp.mvcframework.annotation.DPAutowried;
import com.dingp.mvcframework.annotation.DPController;
import com.dingp.mvcframework.annotation.DPRequestMapping;
import com.dingp.mvcframework.annotation.DPRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@DPController
@DPRequestMapping("/demo")
public class DemoAction {

    @DPAutowried
    private IDemoService iDemoService;

    @DPRequestMapping("/query.json")
    public void query(HttpServletRequest request, HttpServletResponse response, @DPRequestParam("name") String name){
        String result = iDemoService.get();
        try {
            response.getWriter().write(result);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @DPRequestMapping("/add.json")
    public void add(HttpServletRequest request, HttpServletResponse response,@DPRequestParam("a") Integer a,@DPRequestParam("b") Integer b){
        try {
            response.getWriter().write(a+"+"+b+"="+(a+b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @DPRequestMapping("/remove.json")
    public void remove(HttpServletRequest request, HttpServletResponse response,@DPRequestParam("id") Integer id){

    }
}
