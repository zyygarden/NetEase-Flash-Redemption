package cn.wolfcode.mapper;

import cn.wolfcode.domain.SeckillProduct;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Created by lanxw
 */
@Mapper
public interface SeckillProductMapper {
    /**
     * 根据time时间场次查询对应的秒杀商品集合
     * @param time
     * @return
     */
    List<SeckillProduct> queryCurrentlySeckillProduct(Integer time);

    /**
     * 对秒杀商品库存进行递减操作
     * @param seckillId
     * @return 受影响行数(为0则表示没有成功则抛出异常,并且重新生成订单)
     */
    int decrStock(Long seckillId);

    /**
     * 对秒杀商品库存进行增加操作
     * @param seckillId
     * @return
     */
    int incrStock(Long seckillId);

    /**
     * 获取数据库中商品库存的数量
     * @param seckillId
     * @return
     */
    int getStockCount(Long seckillId);

    /**
     * 根据秒杀商品ID获取秒杀商品对象
     * @param seckillId
     * @return
     */
    SeckillProduct find(Long seckillId);
}
