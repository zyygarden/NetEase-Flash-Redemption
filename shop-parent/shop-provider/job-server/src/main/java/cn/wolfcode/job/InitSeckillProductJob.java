package cn.wolfcode.job;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.redis.JobRedisKey;
import cn.wolfcode.web.feign.SeckillProductFeignAPI;
import com.alibaba.fastjson.JSON;
import com.dangdang.ddframe.job.api.ShardingContext;
import com.dangdang.ddframe.job.api.simple.SimpleJob;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@Setter
@Getter
public class InitSeckillProductJob implements SimpleJob {

    @Value("${jobCron.initSeckillProduct}")
    private String cron;

    @Autowired
    private SeckillProductFeignAPI seckillProductFeignAPI;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void execute(ShardingContext shardingContext) {
        //远程调用秒杀服务获取秒杀列表集合
        String time = shardingContext.getShardingParameter();
        Result<List<SeckillProductVo>> result = seckillProductFeignAPI.queryByTimeForJob(Integer.parseInt(time));
        if (result == null || result.hasError()) {
            //通知管理员
            return;
        }
        List<SeckillProductVo> seckillProductVoList = result.getData();

        //删除之前的数据
        String key = JobRedisKey.SECKILL_PRODUCT_HASH.getRealKey(time);//seckillProductHash:10(库存数量key)
        redisTemplate.delete(key);

        //订单同步到redis数据库,假如有10000个订单和10个库存,保证10000个订单都进redis查找(内存),随后只选出10个进Service操作数据库
        //否则10000个订单都进数据库从磁盘读取数据到内存,会导致服务器崩溃
        String seckillStockCountKey = JobRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(time);
        redisTemplate.delete(seckillStockCountKey);//把之前的删掉

        //存储集合数据到redis
        for (SeckillProductVo vo : seckillProductVoList) {
            redisTemplate.opsForHash().put(key, String.valueOf(vo.getId()), JSON.toJSONString(vo));
            //将库存同步到redis
            redisTemplate.opsForHash().put(seckillStockCountKey,String.valueOf(vo.getId()),String.valueOf(vo.getStockCount()));
        }
    }
}