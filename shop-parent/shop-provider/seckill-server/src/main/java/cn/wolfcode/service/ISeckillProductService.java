package cn.wolfcode.service;

import cn.wolfcode.domain.SeckillProduct;
import cn.wolfcode.domain.SeckillProductVo;

import java.util.List;

/**
 * Created by lanxw
 */
public interface ISeckillProductService {
    /**
     * 查询秒杀列表数据(从数据库里取)
     * @param time
     * @return
     */
    List<SeckillProductVo> queryByTime(Integer time);

    /**
     * 根据秒杀场次和秒杀商品ID查询秒杀商品vo对象
     * @param time
     * @param seckillId
     * @return
     */
    SeckillProductVo find(Integer time, Long seckillId);

    /**
     * 根据秒杀商品ID扣减库存
     * @param id
     */
    int decrStock(Long id);

    /**
     * 查询秒杀列表数据(从redis缓存中取)
     * @param time
     * @return
     */
    List<SeckillProductVo> queryByTimeFromCache(Integer time);

    /**
     * 根据秒杀场次和秒杀商品ID查询秒杀商品vo对象(从redis缓存中取)
     * @param time
     * @param seckillId
     * @return
     */
    SeckillProductVo findFromCache(Integer time, Long seckillId);

    /**
     * 查询数据库库存同步到Redis中
     * @param time
     * @param seckillId
     */
    void syncStockToRedis(Integer time, Long seckillId);

    /**
     * 增加库存
     * @param seckillId
     */
    void incrStockCount(Long seckillId);
}
