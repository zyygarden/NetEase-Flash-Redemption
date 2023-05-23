package cn.wolfcode.web.controller;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.SeckillProduct;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.service.ISeckillProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Created by lanxw
 * 秒杀商品信息查询
 */
@RestController
@RequestMapping("/seckillProduct")
@Slf4j
public class SeckillProductController {
    @Autowired
    private ISeckillProductService seckillProductService;

//    @RequestMapping("/queryByTime")
//    public Result<List<SeckillProductVo>> queryByTime(Integer time) {//商品列表(从数据库中取)
//        return Result.success(seckillProductService.queryByTime(time));
//    }

    @RequestMapping("/queryByTime")
    public Result<List<SeckillProductVo>> queryByTime(Integer time) {//商品列表(从redis缓存中取)
        return Result.success(seckillProductService.queryByTimeFromCache(time));
    }

//    @RequestMapping("/find")
//    public Result<SeckillProductVo> find(Integer time, Long seckillId) {//商品详情(从数据库中取)
//        return Result.success(seckillProductService.find(time, seckillId));
//    }

    @RequestMapping("/find")
    public Result<SeckillProductVo> find(Integer time, Long seckillId) {//商品详情(从redis缓存中取)
        return Result.success(seckillProductService.findFromCache(time, seckillId));
    }

    @RequestMapping("/queryByTimeForJob")
    public Result<List<SeckillProductVo>> queryByTimeForJob(Integer time) {
        return Result.success(seckillProductService.queryByTime(time));
    }
}
