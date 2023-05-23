package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.domain.AccountTransaction;
import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.mapper.AccountTransactionMapper;
import cn.wolfcode.mapper.UsableIntegralMapper;
import cn.wolfcode.service.IUsableIntegralService;
import cn.wolfcode.web.msg.IntergralCodeMsg;
import com.alibaba.fastjson.JSON;
import io.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.crypto.Data;
import java.util.Date;

/**
 * Created by lanxw
 */
@Service
public class UsableIntegralServiceImpl implements IUsableIntegralService {
    @Autowired
    private UsableIntegralMapper usableIntegralMapper;
    @Autowired
    private AccountTransactionMapper accountTransactionMapper;

    @Override
    public void decrIntegral(OperateIntergralVo vo) {
        //可能账户积分不足以支付该商品,那么返回0
        int effectCount = usableIntegralMapper.decrIntergral(vo.getUserId(), vo.getValue());
        if (effectCount == 0) {
            throw new BusinessException(IntergralCodeMsg.INTERGRAL_NOT_ENOUGH);
        }
    }

    @Override
    public void incrIntegral(OperateIntergralVo vo) {
        usableIntegralMapper.incrIntergral(vo.getUserId(), vo.getValue());
    }

    @Override
    @Transactional
    public void decrIntegralTry(OperateIntergralVo vo, BusinessActionContext context) {
        System.out.println("执行try方法");
        //插入事务控制表
        AccountTransaction log = new AccountTransaction();
        log.setTxId(context.getXid());//全局事务ID
        log.setActionId(context.getBranchId());//分支事务ID
        Date now = new Date();
        log.setGmtCreated(now);
        log.setGmtModified(now);
        log.setUserId(vo.getUserId());
        log.setAmount(vo.getValue());
        accountTransactionMapper.insert(log);
        //执行业务逻辑-->减积分
        int effectCount = usableIntegralMapper.decrIntergral(vo.getUserId(), vo.getValue());
        if (effectCount == 0) {//积分不够了
            throw new BusinessException(IntergralCodeMsg.INTERGRAL_NOT_ENOUGH);
        }
    }

    @Override
    public void decrIntegralCommit(BusinessActionContext context) {
        System.out.println("执行commit方法");
        //查询事务记录
        AccountTransaction accountTransaction = accountTransactionMapper.get(context.getXid(), context.getBranchId());
        //如果为空-->写MQ通知管理员
        if (accountTransaction != null) {//如果不为空-->判断状态,如果状态为TRY,执行COMMIT逻辑,更新日志状态
            if (AccountTransaction.STATE_TRY == accountTransaction.getState()) {
                //空操作,更新日志
                accountTransactionMapper.updateAccountTransactionState(context.getXid(), context.getBranchId(), AccountTransaction.STATE_COMMIT, AccountTransaction.STATE_TRY);
            } else if (AccountTransaction.STATE_COMMIT == accountTransaction.getState()) {
                //不做事情
            }
        }
    }

    @Override
    @Transactional
    public void decrIntegralRollback(BusinessActionContext context) {
        System.out.println("执行cancel方法");
        //查询事务记录
        AccountTransaction accountTransaction = accountTransactionMapper.get(context.getXid(), context.getBranchId());
        if (accountTransaction != null) {
            //存在日志记录
            if (AccountTransaction.STATE_TRY == accountTransaction.getState()) {//处于TRY状态
                //将状态修改为Cancel
                accountTransactionMapper.updateAccountTransactionState(context.getXid(), context.getBranchId(), AccountTransaction.STATE_CANCEL, AccountTransaction.STATE_TRY);
                //执行Cancel业务逻辑,添加积分

                usableIntegralMapper.incrIntergral(accountTransaction.getUserId(), accountTransaction.getAmount());
            } else if (AccountTransaction.STATE_CANCEL == accountTransaction.getState()) {//说明之前已经执行过Cancel
                //幂等处理,不做事情
            }
        } else {//不存在日志记录
            //插入日志记录
            String str = (String) context.getActionContext("vo");
            OperateIntergralVo vo = JSON.parseObject(str, OperateIntergralVo.class);
            //插入事务控制表
            AccountTransaction log = new AccountTransaction();
            log.setTxId(context.getXid());//全局事务ID
            log.setActionId(context.getBranchId());//分支事务ID
            Date now = new Date();
            log.setGmtCreated(now);
            log.setGmtModified(now);
            log.setUserId(vo.getUserId());
            log.setAmount(vo.getValue());
            log.setState(AccountTransaction.STATE_CANCEL);
            accountTransactionMapper.insert(log);
        }

    }
}
