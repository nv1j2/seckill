package cn.codingxiaxw.exception;


/**
 * 秒杀等待异常，当秒杀还未执行完成就会出现这个异常
 *
 */
public class SeckillLodingException extends SeckillException{

    public SeckillLodingException(String message) {
        super(message);
    }
    public SeckillLodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
