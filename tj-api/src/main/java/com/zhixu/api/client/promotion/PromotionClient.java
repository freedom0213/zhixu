package com.zhixu.api.client.promotion;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.Map;

@FeignClient("promotion-service")
public interface PromotionClient {
    @GetMapping("/user-coupons/available")
    List<Map<String, Object>> queryAvailableCoupons(@RequestParam("amount") Integer amount);
}
