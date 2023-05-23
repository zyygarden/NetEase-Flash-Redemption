package cn.wolfcode.service;

import cn.wolfcode.domain.Product;

import java.util.List;

/**
 * Created by lanxw
 */
public interface IProductService {

    /**
     * 根据productId集合查询商品
     * @param productIds
     * @return
     */
    List<Product> queryByIds(List<Long> productIds);
}
