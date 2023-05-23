package cn.wolfcode.mq;

import cn.wolfcode.service.IOrderInfoService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 来到这个消息队列说明过了十分钟,则要取消订单
 */
@Component
@RocketMQMessageListener(consumerGroup = "OrderPayTimeOutGroup", topic = MQConstant.ORDER_PAY_TIMEOUT_TOPIC)
public class OrderPayTimeOutQueueListener implements RocketMQListener<OrderMQResult> {

    @Autowired
    private IOrderInfoService orderInfoService;

    @Override
    public void onMessage(OrderMQResult orderMQResult) {
        System.out.println("取消订单逻辑");
        //取消订单
        orderInfoService.cancelOrder(orderMQResult.getOrderNo());
    }
}
