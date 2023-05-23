package cn.wolfcode.web.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.web.feign.fallback.IntegralFeignApiFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Component
@FeignClient(name = "intergral-service",fallback = IntegralFeignApiFallback.class)
public interface IntegralFeignApi {
    @RequestMapping("/intergral/decrIntegral")
    Result decrIntegral(@RequestBody OperateIntergralVo vo);

    @RequestMapping("/intergral/incrIntegral")
    Result incrIntegral(@RequestBody OperateIntergralVo vo);
}
