package com.example.demo.hello;

import org.kurento.client.KurentoClient;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @RequestMapping("/index")
    public String index() {
        KurentoClient.create();
        return "index test";
    }
}
