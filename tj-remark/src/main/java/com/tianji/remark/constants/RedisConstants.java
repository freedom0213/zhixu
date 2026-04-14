package com.tianji.remark.constants;

public interface RedisConstants {
    //set结构 key是业务id
    String LIKES_BIZ_KEY_PREFIX = "likes:set:biz:";
    //zSet结构 key是业务类型 fieldKey是业务id
    String LIKES_TIMES_KEY_PREFIX = "likes:time:type:";
}
