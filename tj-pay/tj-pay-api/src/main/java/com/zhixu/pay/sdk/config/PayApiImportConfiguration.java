package com.zhixu.pay.sdk.config;


import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.zhixu.pay.sdk.client")
public class PayApiImportConfiguration {

}