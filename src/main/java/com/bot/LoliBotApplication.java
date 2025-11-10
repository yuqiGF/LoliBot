package com.bot;

//import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
//@MapperScan("com.bot.mapper")
public class LoliBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoliBotApplication.class, args);
    }
}
