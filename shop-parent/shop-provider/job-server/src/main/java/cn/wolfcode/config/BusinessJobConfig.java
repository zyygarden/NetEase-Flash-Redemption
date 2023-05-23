package cn.wolfcode.config;

import cn.wolfcode.job.InitSeckillProductJob;
import cn.wolfcode.job.UserCacheJob;
import cn.wolfcode.util.ElasticJobUtil;
import com.dangdang.ddframe.job.lite.config.LiteJobConfiguration;
import com.dangdang.ddframe.job.lite.spring.api.SpringJobScheduler;
import com.dangdang.ddframe.job.reg.base.CoordinatorRegistryCenter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 任务配置
 * Created by lanxw
 */
@Configuration
public class BusinessJobConfig {
    @Bean(initMethod = "init")
    public SpringJobScheduler initUserCacheJob(CoordinatorRegistryCenter registryCenter, UserCacheJob userCacheJob){
        LiteJobConfiguration jobConfiguration = ElasticJobUtil.createDefaultSimpleJobConfiguration(userCacheJob.getClass(), userCacheJob.getCron());
        SpringJobScheduler springJobScheduler = new SpringJobScheduler(userCacheJob, registryCenter,jobConfiguration );
        return springJobScheduler;
    }

    @Bean(initMethod = "init")
    public SpringJobScheduler initSPJob(CoordinatorRegistryCenter registryCenter, InitSeckillProductJob seckillProductJob){
        //Job配置
        LiteJobConfiguration jobConfiguration = ElasticJobUtil.createJobConfiguration(
                                                    seckillProductJob.getClass(),//任务类的字节码
                                                    seckillProductJob.getCron(),//任务类的cron表达式
                                                    3,//分片个数
                                                    "0=10,1=12,2=14",//分片参数
                                                    false);//不是dataflow类型
        SpringJobScheduler springJobScheduler = new SpringJobScheduler(seckillProductJob, registryCenter,jobConfiguration );
        return springJobScheduler;
    }
}
