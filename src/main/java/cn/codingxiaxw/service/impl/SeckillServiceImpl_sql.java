package cn.codingxiaxw.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.dyuproject.protostuff.ProtostuffIOUtil;
import com.dyuproject.protostuff.runtime.RuntimeSchema;

import cn.codingxiaxw.dao.cache.RedisDao;
import cn.codingxiaxw.dto.RabbitSend;
import cn.codingxiaxw.dto.SeckillExecution;
import cn.codingxiaxw.dto.SeckillResult;
import cn.codingxiaxw.enums.SeckillStatEnum;
import cn.codingxiaxw.exception.RepeatKillException;
import cn.codingxiaxw.exception.SeckillCloseException;


@Component
public class SeckillServiceImpl_sql {
    
    public final static short REPEAT_KILL = -1 ;
    public final static short END = 0 ;
    public final static short INNER_ERROR = -2 ;
    @Autowired
    private RedisDao redisDao;
    
    @Autowired
    private SeckillInsert seckillInsert;
    
    private RuntimeSchema<RabbitSend> schema = RuntimeSchema.createFrom(RabbitSend.class);
    public void recvUserInfo(byte[] bytes){
        
       RabbitSend rabbitSend = schema.newMessage();
        if(bytes != null){
            ProtostuffIOUtil.mergeFrom(bytes, rabbitSend, schema);
            try {
                
                SeckillExecution seckillExecution = seckillInsert.SQLrun(rabbitSend);
                redisDao.putUserSeckillResult(rabbitSend.getSeckillId(), rabbitSend.getUserPhone(), 
                        new SeckillResult<SeckillExecution>(true, seckillExecution));
            } catch (SeckillCloseException e1)
            {
                redisDao.putUserSeckillResult(rabbitSend.getSeckillId(), rabbitSend.getUserPhone(), 
                        new SeckillResult<SeckillExecution>(true,
                                new SeckillExecution(rabbitSend.getSeckillId(), SeckillStatEnum.END)));
            }catch (RepeatKillException e2)
            {
                redisDao.putUserSeckillResult(rabbitSend.getSeckillId(), rabbitSend.getUserPhone(), 
                        new SeckillResult<SeckillExecution>(true,
                                new SeckillExecution(rabbitSend.getSeckillId(), SeckillStatEnum.REPEAT_KILL)));
                
            }catch (Exception e)
            {
                redisDao.putUserSeckillResult(rabbitSend.getSeckillId(), rabbitSend.getUserPhone(), 
                        new SeckillResult<SeckillExecution>(true,
                                new SeckillExecution(rabbitSend.getSeckillId(), SeckillStatEnum.INNER_ERROR)));
            }
        }
    }
}
