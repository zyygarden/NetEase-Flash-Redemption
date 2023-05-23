package cn.wolfcode.web.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.web.feign.fallback.PayFeignApiFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Map;

@Component
@FeignClient(name = "pay-service",fallback = PayFeignApiFallback.class)
public interface PayFeignApi {
    @RequestMapping("/alipay/payOnline")
    Result<String> payOnline(@RequestBody PayVo vo);
    @RequestMapping("alipay/rsaCheckV1")
    Result<Boolean> rsaCheckV1(@RequestParam Map<String, String> params);
    @RequestMapping("alipay/refund")
    Result<Boolean> refund(@RequestBody RefundVo vo);
}
