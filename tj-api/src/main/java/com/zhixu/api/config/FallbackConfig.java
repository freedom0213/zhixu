package com.zhixu.api.config;

import com.zhixu.api.client.learning.fallback.LearningClientFallback;
import com.zhixu.api.client.remark.fallback.RemarkClientFallback;
import com.zhixu.api.client.trade.fallback.TradeClientFallback;
import com.zhixu.api.client.user.fallback.UserClientFallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FallbackConfig {
    @Bean
    public LearningClientFallback learningClientFallback(){
        return new LearningClientFallback();
    }

    @Bean
    public TradeClientFallback tradeClientFallback(){
        return new TradeClientFallback();
    }

    @Bean
    public UserClientFallback userClientFallback(){
        return new UserClientFallback();
    }

    @Bean
    public RemarkClientFallback remarkClientFallback(){return new RemarkClientFallback();}

}
