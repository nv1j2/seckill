package cn.codingxiaxw.service.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cn.codingxiaxw.dao.SeckillDao;
import cn.codingxiaxw.dao.SuccessKilledDao;
import cn.codingxiaxw.dto.RabbitSend;
import cn.codingxiaxw.dto.SeckillExecution;
import cn.codingxiaxw.entity.SuccessKilled;
import cn.codingxiaxw.enums.SeckillStatEnum;
import cn.codingxiaxw.exception.RepeatKillException;
import cn.codingxiaxw.exception.SeckillCloseException;
import cn.codingxiaxw.exception.SeckillException;


@Component
public class SeckillInsert {
    
    private Logger logger= LoggerFactory.getLogger(this.getClass());
//    
    @Autowired
//    @Resource
    private SeckillDao seckillDao ;

    @Autowired 
//    @Resource
    private SuccessKilledDao successKilledDao;

//    @Autowired
//    private RedisDao redisDao;
    
    public SeckillExecution SQLrun(RabbitSend rabbitSend)throws SeckillException, RepeatKillException, SeckillCloseException{
        
        long seckillId = rabbitSend.getSeckillId();
        long userPhone = rabbitSend.getUserPhone(); 
//        String md5 = rabbitSend.getMd5();
        Date nowTime = rabbitSend.getNowTime();
        
        /*Map<String,Object> map = new HashMap<String,Object>() ;
        map.put("seckillId", seckillId) ;
        map.put("phone", userPhone) ;
        map.put("killTime", killTime) ;
        map.put("result", null) ;
        try{
            seckillDao.killByProcedure(map);//调用存储过程的代码
            //获取result
            int result = MapUtils.getInteger(map, "result",1) ;
            if(result==1){
                //TODO
                SuccessKilled sk = successKilledDao.queryByIdWithSeckill(seckillId, userPhone) ;
                return new SeckillExecution(seckillId,SeckillStatEnum.SUCCESS,sk) ;
            }else{
                return new SeckillExecution(seckillId,SeckillStatEnum.stateOf(result)) ;
            }
        }catch(Exception e){
            logger.error(e.getMessage(),e);
            return new SeckillExecution(seckillId,SeckillStatEnum.INNER_ERROR) ;//执行出异常了
        }*/
        
        
        //执行秒杀逻辑:减库存+增加购买明细，可通过替换顺序减少行级锁的持有时间
        try{
            //秒杀成功,增加明细
            int insertCount=successKilledDao.insertSuccessKilled(seckillId,userPhone);
            //看是否该明细被重复插入，即用户是否重复秒杀
            if (insertCount<=0)
            {
                throw new RepeatKillException("seckill repeated");
            }else {
                //减库存，热点商品竞争在此
                int updateCount=seckillDao.reduceNumber(seckillId,nowTime);
                if (updateCount<=0)
                {
                    //没有更新库存记录，说明秒杀结束，rollback
                    throw new SeckillCloseException("seckill is closed");
                }else {
                    //秒杀成功,得到成功插入的明细记录,并返回成功秒杀的信息，commit
                    SuccessKilled successKilled=successKilledDao.queryByIdWithSeckill(seckillId,userPhone);
                    return new SeckillExecution(seckillId, SeckillStatEnum.SUCCESS,successKilled); 
                }
            }
        }catch (SeckillCloseException e1)
        {
            throw e1;
        }catch (RepeatKillException e2)
        {
            throw e2;
        }catch (Exception e)
        {
            logger.error(e.getMessage(),e);
            //所以编译期异常转化为运行期异常
            throw new SeckillException("seckill inner error :"+e.getMessage());
        }
        
    }
}
