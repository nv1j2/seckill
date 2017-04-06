package cn.codingxiaxw.dto;

import java.util.Date;

public class RabbitSend {

    private long seckillId;
    private long userPhone;
    private String md5 ;
    private Date nowTime ;
    
    public RabbitSend(){}
    public RabbitSend(long seckillId, long userPhone, String md5, Date nowTime) {
        this.seckillId = seckillId;
        this.userPhone = userPhone;
        this.md5 = md5;
        this.nowTime = nowTime;
    }
    public long getSeckillId() {
        return seckillId;
    }
    @Override
    public String toString() {
        return "RabbitSend [seckillId=" + seckillId + ", userPhone=" + userPhone + ", md5=" + md5 + ", nowTime="
                + nowTime + "]";
    }
    public void setSeckillId(long seckillId) {
        this.seckillId = seckillId;
    }
    public long getUserPhone() {
        return userPhone;
    }
    public void setUserPhone(long userPhone) {
        this.userPhone = userPhone;
    }
    public String getMd5() {
        return md5;
    }
    public void setMd5(String md5) {
        this.md5 = md5;
    }
    public Date getNowTime() {
        return nowTime;
    }
    public void setNowTime(Date nowTime) {
        this.nowTime = nowTime;
    }
}
