package cn.codingxiaxw.service.impl;


import cn.codingxiaxw.dao.SeckillDao;
import cn.codingxiaxw.dao.SuccessKilledDao;
import cn.codingxiaxw.dao.cache.RedisDao;
import cn.codingxiaxw.dto.Exposer;
import cn.codingxiaxw.dto.RabbitSend;
import cn.codingxiaxw.dto.SeckillExecution;
import cn.codingxiaxw.dto.SeckillResult;
import cn.codingxiaxw.entity.Seckill;
import cn.codingxiaxw.entity.SuccessKilled;
import cn.codingxiaxw.enums.SeckillStatEnum;
import cn.codingxiaxw.exception.RepeatKillException;
import cn.codingxiaxw.exception.SeckillCloseException;
import cn.codingxiaxw.exception.SeckillException;
import cn.codingxiaxw.exception.SeckillLodingException;
import cn.codingxiaxw.service.SeckillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import com.dyuproject.protostuff.LinkedBuffer;
import com.dyuproject.protostuff.ProtostuffIOUtil;
import com.dyuproject.protostuff.runtime.RuntimeSchema;

import java.util.Date;
import java.util.List;
import java.util.Random;



/**
 * Created by codingBoy on 16/11/28.
 */
//@Component @Service @Dao @Controller
@Service
public class SeckillServiceImpl implements SeckillService{
    //日志对象
    private Logger logger= LoggerFactory.getLogger(this.getClass());

    private RuntimeSchema<RabbitSend> schema = RuntimeSchema.createFrom(RabbitSend.class);
    
    //加入一个混淆字符串(秒杀接口)的salt，为了我避免用户猜出我们的md5值，值任意给，越复杂越好
    private final String salt="shsdssljdd'l.";

    //注入Service依赖
    @Autowired //@Resource
    private SeckillDao seckillDao;

    @Autowired //@Resource
    private SuccessKilledDao successKilledDao;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    @Autowired
    private RedisDao redisDao;
    
    
    public List<Seckill> getSeckillList() {
        return seckillDao.queryAll(0,4);
    }

    public Seckill getById(long seckillId) {
        return seckillDao.queryById(seckillId);
    }
    
    public Exposer exportSeckillUrl(long seckillId) {
        // 优化点：缓存优化：超时的基础上维护一致性
        //1.访问redis
        Seckill seckill = redisDao.getSeckill(seckillId);
        if(seckill == null){
            //2.访问数据库
            seckill = seckillDao.queryById(seckillId);
            if(seckill == null){
                return new Exposer(false, seckillId);
            }else{
                //3.放入redis
                redisDao.putSeckill(seckill);
            }
        }
        
        Date startTime=seckill.getStartTime();
        Date endTime=seckill.getEndTime();
        //系统当前时间
        Date nowTime=new Date();
        if (startTime.getTime()>nowTime.getTime() || endTime.getTime()<nowTime.getTime())
        {
            return new Exposer(false,seckillId,nowTime.getTime(),startTime.getTime(),endTime.getTime());
        }

        //秒杀开启，返回秒杀商品的id、用给接口加密的md5
        String md5=getMD5(seckillId);
        return new Exposer(true,md5,seckillId);
    }

    private String getMD5(long seckillId)
    {
        String base=seckillId+"/"+salt;
        String md5= DigestUtils.md5DigestAsHex(base.getBytes());
        return md5;
    }

    //秒杀是否成功，成功:减库存，增加明细；失败:抛出异常，事务回滚
    @Transactional
    /**
     * 使用注解控制事务方法的优点:
     * 1.开发团队达成一致约定，明确标注事务方法的编程风格
     * 2.保证事务方法的执行时间尽可能短，不要穿插其他网络操作RPC/HTTP请求或者剥离到事务方法外部
     * 3.不是所有的方法都需要事务，如只有一条修改操作、只读操作不要事务控制
     */
    public SeckillExecution executeSeckill(long seckillId, long userPhone, String md5)
            throws SeckillException, RepeatKillException, SeckillCloseException {

        if (md5==null||!md5.equals(getMD5(seckillId)))
        {
            throw new SeckillException("seckill data rewrite");//秒杀数据被重写了
        }
        
        //直接取一个随机数,如果不在这个随机数范围内,直接返回
        if((Math.random()*10) > 8){
            throw new SeckillCloseException("seckill is closed");
        }
        
        //立刻更新Redis中用户的缓存信息，再放入RabbitMQ，让消费者消费 
        
        RabbitSend rabbitSend = new RabbitSend(seckillId,userPhone,md5,new Date());
        byte[] bytes = ProtostuffIOUtil.toByteArray(rabbitSend, schema, 
                LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE));
        
        
        byte[] getBytes = redisDao.getRabbitSend(seckillId,userPhone);
        if (getBytes == null) {
            redisDao.putRabbitSend(seckillId,userPhone,bytes);
            rabbitTemplate.convertAndSend("queueTestKey", bytes);
        }else {
            throw new RepeatKillException("seckill repeated");
        }
        throw new SeckillLodingException("please loading ");
    }

    @Override
    public SeckillResult<SeckillExecution> seckillCheck(Long seckillId, Long phone, String md5)
            throws RepeatKillException, SeckillCloseException,SeckillException,SeckillLodingException {
        if (md5==null||!md5.equals(getMD5(seckillId)))
        {
            throw new SeckillException("seckill data rewrite");//秒杀数据被重写了
        }
        return redisDao.getUserSeckillResult(seckillId, phone);
    }
    
}







