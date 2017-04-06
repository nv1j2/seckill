package cn.codingxiaxw.dao.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dyuproject.protostuff.LinkedBuffer;
import com.dyuproject.protostuff.ProtostuffIOUtil;
import com.dyuproject.protostuff.runtime.RuntimeSchema;

import cn.codingxiaxw.dto.RabbitSend;
import cn.codingxiaxw.dto.SeckillExecution;
import cn.codingxiaxw.dto.SeckillResult;
import cn.codingxiaxw.entity.Seckill;
import cn.codingxiaxw.enums.SeckillStatEnum;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisDao {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private final JedisPool jedisPool;
	
	public RedisDao(String ip, int port){
		jedisPool = new JedisPool(ip,port);
	}
	
	private RuntimeSchema<Seckill> schema = RuntimeSchema.createFrom(Seckill.class);
	private RuntimeSchema<SeckillResult> schemaSE = RuntimeSchema.createFrom(SeckillResult.class);
	
	
	/**
	 * 将商品信息从redis里拿出
	 * @param seckillId
	 * @return
	 */
	public Seckill getSeckill(long seckillId){
		//redis操作逻辑
		try {
			Jedis jedis = jedisPool.getResource();
			try {
				String key = "seckill:"+seckillId;
				//并没有实现内部序列化操作
				//get-> byte[] -> 反序列化 -> Object(Seckill)
				//采用自定义序列化方式
				//protostuff: pojo.
				byte[] bytes = jedis.get(key.getBytes());
				//缓存重新获取
				if(bytes != null){
					//空对象
					Seckill seckill = schema.newMessage();
					ProtostuffIOUtil.mergeFrom(bytes, seckill, schema);
					//seckill 被反序列化
					return seckill;
				}
				
			} finally {
				jedis.close();
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	
	/**
	 * 将商品信息放入redis里
	 * @param seckill
	 * @return
	 */
	public String putSeckill(Seckill seckill){
		//set Object(Seckill) -> 序列化 -> byte[]
	    String result = null;
		try {
			Jedis jedis = jedisPool.getResource();
			try {
				String key = "seckill:"+seckill.getSeckillId();
				byte[] bytes = ProtostuffIOUtil.toByteArray(seckill, schema, 
						LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE));
				//超时缓存
				int timeout = 60*60;//1小时
				result = jedis.setex(key.getBytes(), timeout, bytes);
			} finally {
				jedis.close();
			}
		} catch (Exception e) {
		    logger.error(e.getMessage(), e);
		}
		return result;
	}
	
	/**
	 * 将用户秒杀结果放入redis里面
	 * @param seckillId
	 * @param userPhone
	 * @param seckillResult
	 * @return
	 */
	public String putUserSeckillResult(Long seckillId,long userPhone,SeckillResult seckillResult){
	    String result = null ;
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                String key = "user"+ seckillId + ":" + userPhone;
            //超时缓存
            int timeout = 60*60;//1小时
            
            byte[] bytes = ProtostuffIOUtil.toByteArray(seckillResult, schemaSE, 
                    LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE));
            
            result = jedis.setex(key.getBytes(), timeout, bytes);
        } finally {
            jedis.close();
        }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return result ;
   }
	
	
	   
	/**
	 * 将用户秒杀结果从redis里面拿出
	 * @param seckillId
	 * @param userPhone
	 * @return
	 */
	public SeckillResult getUserSeckillResult(Long seckillId,long userPhone){
	    try {
            Jedis jedis = jedisPool.getResource();
            try {
                String key = "user"+ seckillId + ":" + userPhone;
                
                byte[] bytes = jedis.get(key.getBytes());
                //缓存重新获取
                if(bytes != null){
                    SeckillResult<SeckillExecution> seckillResult = schemaSE.newMessage();
                    ProtostuffIOUtil.mergeFrom(bytes, seckillResult, schemaSE);
                    //seckill 被反序列化
                    return seckillResult;
                }else {
                    return new SeckillResult<SeckillExecution>(true,
                            new SeckillExecution(seckillId, SeckillStatEnum.REPEAT_KILL));
                }
                
            } finally {
                jedis.close();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

	/**
	 * 将用户秒杀信息放入redis里
	 * @param seckillId
	 * @param userPhone
	 * @return
	 */
    public String putRabbitSend(long seckillId, long userPhone,byte[] bytes) {
        String result = null ;
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                String key = "send"+ seckillId + ":" + userPhone;
            //超时缓存
            int timeout = 60*60;//1小时
            
            result = jedis.setex(key.getBytes(), timeout, bytes);
        } finally {
            jedis.close();
        }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return result ;
    }

    /**
     * 将用户秒杀信息从redis里拿出
     * @param bytes
     */
    public byte[] getRabbitSend(long seckillId, long userPhone) {
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                String key = "send"+ seckillId + ":" + userPhone;
                
                byte[] bytes = jedis.get(key.getBytes());
                //缓存重新获取
                return bytes;
                
            } finally {
                jedis.close();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return null;

    }
}
