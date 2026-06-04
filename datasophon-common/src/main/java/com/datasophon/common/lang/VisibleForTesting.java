package com.datasophon.common.lang;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * mark the method is visible only for test
 * @author zhanghuangbin
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface VisibleForTesting {
    
}
