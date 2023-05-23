package cn.wolfcode.web.controller;

import cn.wolfcode.common.constants.CommonConstants;
import cn.wolfcode.common.web.CommonCodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.UserUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by lanxw
 */
@RestController
@RequestMapping("/order")
@Slf4j
public class OrderInfoController {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private IOrderInfoService orderInfoService;

    @RequireLogin //必须登录才能访问
    @RequestMapping("/doSeckill")
    public Result<String> doSeckill(Integer time, Long seckillId, HttpServletRequest request) {
        //1.判断是否处于抢购时间
        SeckillProductVo seckillProductVo = seckillProductService.findFromCache(time, seckillId);
//        boolean legalTime = DateUtil.isLegalTime(seckillProductVo.getStartDate(), time);
//        if (!legalTime) {
//            return Result.error(CommonCodeMsg.ILLEGAL_OPERATION);
//        }
        //2.一个用户只能抢购一个商品(优化:之前是查数据库,现在通过判断redis种是否有该用户的手机号,有则抛出异常)
        //获取token信息
        String token = request.getHeader(CommonConstants.TOKEN_NAME);
        //根据token从redis种获取手机号
        String phone = UserUtil.getUserPhone(redisTemplate, token);
        //根据手机号和秒杀ID查询是否存在订单ID,不存在则说明没买过
//        OrderInfo orderInfo = orderInfoService.findByPhoneAndSeckillId(phone, seckillId);
//        if (orderInfo != null) {
//            //提示重复下单
//            return Result.error(SeckillCodeMsg.REPEAT_SECKILL);
//        }
        String orderSetKey = SeckillRedisKey.SECKILL_ORDER_SET.getRealKey(String.valueOf(seckillId));
        if (redisTemplate.opsForSet().isMember(orderSetKey,phone)) {
            //提示重复下单
            return Result.error(SeckillCodeMsg.REPEAT_SECKILL);
        }
        //3.保证库存数量足够
        //使用Redis控制秒杀请求人数
        String seckillStockCountKey = SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(String.valueOf(time));
        //利用redis的原子性减一,返回减完了之后的数量(redis内部单线程一个一个处理)
        Long remainCount = redisTemplate.opsForHash().increment(seckillStockCountKey, String.valueOf(seckillId), -1);
        if (remainCount < 0) {
            return Result.error(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        //使用MQ方式进行异步下单(指的下单动作在另外一个线程)
        OrderMessage message = new OrderMessage(time,seckillId,token,Long.parseLong(phone));
        rocketMQTemplate.syncSend(MQConstant.ORDER_PEDDING_TOPIC,message);
//        //4.扣减数据库库存(第四步和第五步保证原子性)
//        //5.创建秒杀订单
//        //放入redis中的Set集合(集合存储该秒杀ID商品所抢购的手机号)
//        OrderInfo orderInfo = orderInfoService.doSeckill(phone, seckillProductVo);
        return Result.success("成功进入秒杀队列,请耐心等待结果");
    }

    @RequireLogin
    @RequestMapping("/find")
    public Result<OrderInfo> find(String orderNo, HttpServletRequest request) {
        OrderInfo orderInfo = orderInfoService.findByOrderNo(orderNo);
        String token = request.getHeader(CommonConstants.TOKEN_NAME);
        String phone = UserUtil.getUserPhone(redisTemplate, token);
        if (!phone.equals(String.valueOf(orderInfo.getUserId()))) {//只能看到自己订单
            return Result.error(CommonCodeMsg.ILLEGAL_OPERATION);
        }
        return Result.success(orderInfo);
    }

}
