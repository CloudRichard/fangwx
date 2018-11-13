package com.cz.controller;

import com.cz.service.UserService;
import com.cz.utils.QRCodeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {


    @RequestMapping("/hello")
    public String hello(){

        return "hello";
    }
}
