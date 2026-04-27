package com.tianji.promotion.utils;

import com.tianji.common.exceptions.BizIllegalException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.springframework.core.Ordered;

/**切面执行者
 * 根据以下标记的注解 进行标记的注解自动进行的操作
 */
@Aspect
@RequiredArgsConstructor
public class MyLockAspect implements Ordered {

    private final MyLockFactory LockFactory ;

    @Around("@annotation(myLock)")
    public Object tryLock(ProceedingJoinPoint pjp, MyLock myLock) throws Throwable {
        //1.创建锁对象 根据锁工厂创建锁对象
        RLock lock = LockFactory.getLock(myLock.lockType(),myLock.name());
        //2.尝试获取锁
        boolean isLock = myLock.lockStrategy().tryLock(lock,myLock);
        //3.判断是否成功
        if(!isLock){
            //3.1 失败，抛异常
            return null;
        }
        try{
            //3.2 成功，执行业务
            return pjp.proceed();
        }finally{
            //4. 释放锁
            lock.unlock();
        }
    }

    //表示切面方法执行有限度 0 最优先
    @Override
    public int getOrder() {
        return 0;
    }
}
