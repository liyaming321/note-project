package com.knowledgebase.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端单页应用路由转发控制器。
 */
@Controller
public class SpaForwardController {

    /**
     * 将 Vue 页面路由转发到 index.html。
     *
     * @return 前端入口
     */
    @GetMapping(value = {
            "/notes",
            "/notes/**",
            "/settings"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
