package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.*;
import cn.wolfcode.mapper.OrderInfoMapper;
import cn.wolfcode.mapper.PayLogMapper;
import cn.wolfcode.mapper.RefundLogMapper;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.feign.IntegralFeignApi;
import cn.wolfcode.web.feign.PayFeignApi;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by wolfcode-lanxw
 */
@Service
public class OrderInfoSeviceImpl implements IOrderInfoService {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private PayFeignApi payFeignApi;
    @Autowired
    private PayLogMapper payLogMapper;
    @Autowired
    private RefundLogMapper refundLogMapper;
    @Value("${pay.returnurl}")
    private String returnUrl;
    @Value("${pay.notifyurl}")
    private String notifyUrl;
    @Autowired
    private IntegralFeignApi integralFeignApi;

    @Override
    public OrderInfo findByPhoneAndSeckillId(String phone, Long seckillId) {
        return orderInfoMapper.findByPhoneAndSeckillId(phone, seckillId);
    }

    @Override
    @Transactional //保证原子性
    public OrderInfo doSeckill(String phone, SeckillProductVo seckillProductVo) {
        //4.扣减数据库库存
        int count = seckillProductService.decrStock(seckillProductVo.getId());
        if (count == 0) {
            //影响行数为0说明没有库存,那么应该抛出异常
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        //5.创建秒杀订单(之前优化redis仍然解决不了重复下单问题,这时设置数据表字段唯一索引即可,即插入重复字段会插入失败(这时数据会回滚))
        OrderInfo orderInfo = createOrderInfo(phone, seckillProductVo);
        //放入redis中的Set集合(集合存储该秒杀ID商品所抢购的手机号)
        //Canal里做了
        return orderInfo;
    }

    @Override
    public OrderInfo findByOrderNo(String orderNo) {
        //从redis中查询
        String orderHashKey = SeckillRedisKey.SECKILL_ORDER_HASH.getRealKey("");
        String str = (String) redisTemplate.opsForHash().get(orderHashKey, orderNo);
        return JSON.parseObject(str, OrderInfo.class);
    }

    private OrderInfo createOrderInfo(String phone, SeckillProductVo seckillProductVo) {
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(seckillProductVo, orderInfo);//相同属性才复制
        orderInfo.setUserId(Long.parseLong(phone));//用户ID
        orderInfo.setCreateDate(new Date());//订单创建时间
        orderInfo.setDeliveryAddrId(1L);//收获地址ID
        orderInfo.setSeckillDate(seckillProductVo.getStartDate());//秒杀商品的日期
        orderInfo.setSeckillTime(seckillProductVo.getTime());//秒杀场次
        orderInfo.setOrderNo(String.valueOf(IdGenerateUtil.get().nextId()));//订单编号
        orderInfo.setSeckillId(seckillProductVo.getId());//秒杀ID
        orderInfoMapper.insert(orderInfo);
        return orderInfo;
    }

    @Override
    @Transactional
    public void cancelOrder(String orderNo) {
        System.out.println("超时取消订单逻辑开始...");
        OrderInfo orderInfo = orderInfoMapper.find(orderNo);
        //判断订单是否处于未付款状态
        if (OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())) {
            //修改订单状态
            int effectCount = orderInfoMapper.updateCancelStatus(orderNo, OrderInfo.STATUS_TIMEOUT);
            if (effectCount == 0) {//判断的同时手动取消或者支付了
                return;
            }
            //真实库存回补
            seckillProductService.incrStockCount(orderInfo.getSeckillId());
            //预库存回补
            seckillProductService.syncStockToRedis(orderInfo.getSeckillTime(), orderInfo.getSeckillId());
        }
        System.out.println("超时取消订单逻辑结束...");
    }

    @Override
    public Result<String> payOnline(String orderNo) {
        //根据订单号查询订单对象
        OrderInfo orderInfo = this.findByOrderNo(orderNo);
        if (OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())) {//订单支付未完成
            PayVo vo = new PayVo();
            vo.setOutTradeNo(orderNo);
            vo.setTotalAmount(String.valueOf(orderInfo.getIntergral()));
            vo.setSubject(orderInfo.getProductName());
            vo.setBody(orderInfo.getProductName());
            vo.setNotifyUrl(notifyUrl);
            vo.setReturnUrl(returnUrl);
            Result<String> result = payFeignApi.payOnline(vo);//调用支付宝服务
            return result;//返回html,支付完后立马会调用异步回调修改状态信息,最后调用同步回调重定向返回结果页面
        }
        return Result.error(SeckillCodeMsg.PAY_ERROR);
    }

    @Override
    public int changePayStatus(String orderNo, Integer status, int payType) {
        return orderInfoMapper.changePayStatus(orderNo, status, payType);
    }

    @Override
    public void refundOnline(OrderInfo orderInfo) {
        RefundVo vo = new RefundVo();
        vo.setOutTradeNo(orderInfo.getOrderNo());
        vo.setRefundAmount(String.valueOf(orderInfo.getSeckillPrice()));
        vo.setRefundReason("不要想了");
        Result<Boolean> result = payFeignApi.refund(vo);
        if (result == null || result.hasError() || !result.getData()) {
            throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
        }
        orderInfoMapper.changeRefundStatus(orderInfo.getOrderNo(), OrderInfo.STATUS_REFUND);
    }

    @Override
    @GlobalTransactional
    public void payIntegral(String orderNo) {
        OrderInfo orderInfo = this.findByOrderNo(orderNo);
        if (OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())) {
            //处于未支付状态
            //插入支付日志记录(因为用支付宝支付那边已经整合了,两个页面都在支付时不会重复交易付款,所以不用插入支付记录并且设置订单号是唯一主键,代表同时支付时只能一个成功)
            PayLog log = new PayLog();
            log.setOrderNo(orderNo);
            log.setPayTime(new Date());
            log.setTotalAmount(String.valueOf(orderInfo.getSeckillPrice()));
            log.setPayType(OrderInfo.PAYTYPE_INTERGRAL);
            payLogMapper.insert(log);
            //远程调用积分服务完成积分扣减
            OperateIntergralVo vo = new OperateIntergralVo();
            vo.setUserId(orderInfo.getUserId());
            vo.setValue(orderInfo.getIntergral());
            //调用积分服务TODO
            Result result = integralFeignApi.decrIntegral(vo);
            if (result == null || result.hasError()) {
                throw new BusinessException(SeckillCodeMsg.INTERGRAL_SERVER_ERROR);
            }
            //修改订单状态(涉及到延时付款,刚付款的时候就加入延时队列了,已付款但是延时队列已经拿到了状态要改变(设置状态积))
            int effectCount = orderInfoMapper.changePayStatus(orderNo, OrderInfo.STATUS_ACCOUNT_PAID, OrderInfo.PAYTYPE_INTERGRAL);
            if (effectCount == 0) {
                throw new BusinessException(SeckillCodeMsg.PAY_ERROR);
            }
            //int x = 1/0;
        }
    }

    @Override
    @GlobalTransactional
    public void refundIntegral(OrderInfo orderInfo) {
        //添加退款记录
        RefundLog log = new RefundLog();
        log.setOrderNo(orderInfo.getOrderNo());
        log.setRefundAmount(orderInfo.getIntergral());
        log.setRefundReason("不想要了");
        log.setRefundTime(new Date());
        log.setRefundType(OrderInfo.PAYTYPE_INTERGRAL);
        refundLogMapper.insert(log);//保证不重复退款
        //远程调用服务增加积分
        //远程调用积分服务
        OperateIntergralVo vo = new OperateIntergralVo();
        vo.setUserId(orderInfo.getUserId());
        vo.setValue(orderInfo.getIntergral());
        //调用积分服务TODO
        Result result = integralFeignApi.incrIntegral(vo);
        if (result == null || result.hasError()) {
            throw new BusinessException(SeckillCodeMsg.INTERGRAL_SERVER_ERROR);
        }
        int effectCount = orderInfoMapper.changeRefundStatus(orderInfo.getOrderNo(), OrderInfo.STATUS_REFUND);
        if (effectCount == 0) {
            throw new BusinessException(SeckillCodeMsg.INTERGRAL_SERVER_ERROR);
        }
        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}