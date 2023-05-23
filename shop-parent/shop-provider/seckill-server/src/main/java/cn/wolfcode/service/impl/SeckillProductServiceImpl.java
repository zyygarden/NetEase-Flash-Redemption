package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.Product;
import cn.wolfcode.domain.SeckillProduct;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mapper.SeckillProductMapper;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.web.feign.ProductFeignApi;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Created by lanxw
 */
@Service
public class SeckillProductServiceImpl implements ISeckillProductService {
    @Autowired
    private SeckillProductMapper seckillProductMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private ProductFeignApi productFeignApi;

    @Override
    public List<SeckillProductVo> queryByTime(Integer time) {
        //1.查询秒杀商品集合数据(场次查询当天数据)
        List<SeckillProduct> seckillProductList = seckillProductMapper.queryCurrentlySeckillProduct(time);
        if (seckillProductList.size() == 0) {
            return Collections.EMPTY_LIST;
        }
        //2.遍历秒杀商品集合数据,获取商品ID集合
        List<Long> productIds = new ArrayList<>();
        for (SeckillProduct seckillProduct : seckillProductList) {
            productIds.add(seckillProduct.getProductId());
        }
        //3.远程调用,获取商品集合
        Result<List<Product>> result = productFeignApi.queryByIds(productIds);
        if (result == null || result.hasError()) {//如果调用的商品服务挂了或者商品服务调用的其它服务出现异常
            throw new BusinessException(SeckillCodeMsg.PRODUCT_SERVER_ERROR);
        }
        List<Product> productList = result.getData();
        //定义productId和Product对象的映射关系
        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : productList) {
            productMap.put(product.getId(), product);
        }
        //4.将商品和秒杀商品数据聚合,封装成Vo对象并返回
        List<SeckillProductVo> seckillProductVoList = new ArrayList<>();
        for (SeckillProduct seckillProduct : seckillProductList) {
            SeckillProductVo vo = new SeckillProductVo();
            //把seckillProduct和product数据封装到vo中
            Product product = productMap.get(seckillProduct.getProductId());
            //将属性拷贝到vo对象中
            BeanUtils.copyProperties(product, vo);
            BeanUtils.copyProperties(seckillProduct, vo);//放后面,保证vo的ID和秒杀商品的ID相同
            vo.setCurrentCount(seckillProduct.getStockCount());//默认等于库存数量
            seckillProductVoList.add(vo);
        }
        return seckillProductVoList;
    }

    @Override
    public SeckillProductVo find(Integer time, Long seckillId) {
        //查询秒杀商品对象
        SeckillProduct seckillProduct = seckillProductMapper.find(seckillId);
        //根据id查询商品对象
        List<Long> productIds = new ArrayList<>();
        productIds.add(seckillProduct.getProductId());
        Result<List<Product>> result = productFeignApi.queryByIds(productIds);
        if (result == null || result.hasError()) {//如果调用的商品服务挂了或者商品服务调用的其它服务出现异常
            throw new BusinessException(SeckillCodeMsg.PRODUCT_SERVER_ERROR);
        }
        Product product = result.getData().get(0);
        //将数据封装成vo对象
        SeckillProductVo vo = new SeckillProductVo();
        BeanUtils.copyProperties(product, vo);
        BeanUtils.copyProperties(seckillProduct, vo);
        vo.setCurrentCount(seckillProduct.getStockCount());
        return vo;
    }

    @Override
    public int decrStock(Long id) {
        return seckillProductMapper.decrStock(id);
    }

    @Override
    public List<SeckillProductVo> queryByTimeFromCache(Integer time) {
        String key = SeckillRedisKey.SECKILL_PRODUCT_HASH.getRealKey(String.valueOf(time));
        List<Object> objectList = redisTemplate.opsForHash().values(key);//拿所有value
        List<SeckillProductVo> seckillProductVoList = new ArrayList<>();
        for (Object objstr : objectList) {
            seckillProductVoList.add(JSON.parseObject((String) objstr, SeckillProductVo.class));
        }
        return seckillProductVoList;
    }

    @Override
    public SeckillProductVo findFromCache(Integer time, Long seckillId) {
        String key = SeckillRedisKey.SECKILL_PRODUCT_HASH.getRealKey(String.valueOf(time));
        Object strObj = redisTemplate.opsForHash().get(key, String.valueOf(seckillId));//拿单个value
        SeckillProductVo seckillProductVo = JSON.parseObject((String) strObj, SeckillProductVo.class);
        return seckillProductVo;
    }

    @Override
    public void syncStockToRedis(Integer time, Long seckillId) {
        SeckillProduct seckillProduct = seckillProductMapper.find(seckillId);
        if (seckillProduct.getStockCount() > 0) {
            //orderStockCount:10
            String key = SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(String.valueOf(time));
            //redis覆盖之前的数量(将数据库库存同步到redis中)
            redisTemplate.opsForHash().put(key,String.valueOf(seckillId),String.valueOf(seckillProduct.getStockCount()));
        }
    }

    @Override
    public void incrStockCount(Long seckillId) {
        seckillProductMapper.incrStock(seckillId);
    }
}