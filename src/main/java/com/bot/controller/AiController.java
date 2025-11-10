package com.bot.controller;

import com.bot.service.DashScopeService;
import jakarta.annotation.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class AiController {
    @Resource
    private DashScopeService dashScopeService;

    //流式输出
    @GetMapping("/chat")
    public Flux<ServerSentEvent<String>> chat(int memoryId,String message){
        return dashScopeService.chatString(memoryId, message)
                .map(chunk ->
                        ServerSentEvent.<String>builder()
                                .data(chunk)
                                .build()
                );
    }
}
