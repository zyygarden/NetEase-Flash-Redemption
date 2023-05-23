package cn.wolfcode.web.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.web.feign.fallback.SeckillProductFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@Component
@FeignClient(name = "seckill-service",fallback = SeckillProductFeignFallback.class)
public interface SeckillProductFeignAPI {

    @RequestMapping("/seckillProduct/queryByTimeForJob")
    Result<List<SeckillProductVo>> queryByTimeForJob(@RequestParam("time") Integer time);

}
