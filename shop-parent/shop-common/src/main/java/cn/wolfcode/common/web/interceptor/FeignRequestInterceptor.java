package cn.wolfcode.common.web.interceptor;

import cn.wolfcode.common.constants.CommonConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * 每次远程调用都打上标记1
 * Created by lanxw
 */
public class FeignRequestInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        template.header(CommonConstants.FEIGN_REQUEST_KEY,CommonConstants.FEIGN_REQUEST_TRUE);
    }
}
