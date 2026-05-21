package com.datasophon.api.utils;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事务处理工具，用于在方法中，开启一个新的事务
 *
 * @author zhanghuangbin
 * @date 2025/5/22
 */
@Component
public class TransactionalUtils {


    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void doInNewTx(Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public <T> T doInNewTx(Callable<T> callable) {
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public interface Callable<T> {

        T call() throws Exception;
    }

    public interface Runnable {
        void run() throws Exception;
    }

}
