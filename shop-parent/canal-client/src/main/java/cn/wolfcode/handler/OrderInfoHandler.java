package cn.wolfcode.handler;

import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.redis.SeckillRedisKey;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import top.javatool.canal.client.annotation.CanalTable;
import top.javatool.canal.client.handler.EntryHandler;

@Slf4j
@Component
@CanalTable(value = "t_order_info")
public class OrderInfoHandler implements EntryHandler<OrderInfo> {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void insert(OrderInfo orderInfo) {//新建订单
        log.info("当有数据插入的时候会触发这个方法");
        //seckillOrderSet:12 ===> [1388889999]
        String orderSetKey = SeckillRedisKey.SECKILL_ORDER_SET.getRealKey(String.valueOf(orderInfo.getSeckillId()));
        redisTemplate.opsForSet().add(orderSetKey, String.valueOf(orderInfo.getUserId()));
        //将创建好的订单对象,存储到redis的hash结构
        String orderHashKey = SeckillRedisKey.SECKILL_ORDER_HASH.getRealKey("");
        redisTemplate.opsForHash().put(orderHashKey,orderInfo.getOrderNo(), JSON.toJSONString(orderInfo));
    }

    @Override
    public void update(OrderInfo before, OrderInfo after) {//状态更新
        log.info("当有数据更新的时候会触发这个方法");
        String orderHashKey = SeckillRedisKey.SECKILL_ORDER_HASH.getRealKey("");
        redisTemplate.opsForHash().put(orderHashKey,after.getOrderNo(), JSON.toJSONString(after));
    }

    @Override
    public void delete(OrderInfo orderInfo) {
        log.info("当有数据删除的时候会触发这个方法");
    }
}
