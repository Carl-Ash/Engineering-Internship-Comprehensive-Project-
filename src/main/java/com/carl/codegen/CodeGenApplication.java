package com.carl.codegen;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.carl.codegen.mapper")
public class CodeGenApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeGenApplication.class, args);
    }

}
