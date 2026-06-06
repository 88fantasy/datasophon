package com.datasophon.common.jackson.serializer;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import org.springframework.core.annotation.AnnotatedElementUtils;

import com.fasterxml.jackson.databind.BeanProperty;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;

/**
 * @author zhanghuangbin
 */
public class BeanPropertyUtils {
    
    /**
     * 获取annotation，同时，利用spring的alias功能
     *
     * @param <A>
     */
    public static <A extends Annotation> A findAnnotation(BeanProperty property, Class<A> annotationCls) {
        Member member = property.getMember().getMember();
        
        A annotation = null;
        if (member instanceof AnnotatedElement) {
            AnnotatedElement element = (AnnotatedElement) member;
            annotation = AnnotatedElementUtils.findMergedAnnotation(element, annotationCls);
            if (annotation == null) {
                if (element instanceof Method) {
                    element = getField((Method) element);
                } else if (element instanceof Field) {
                    element = getBeanGetter((Field) element);
                }
            }
            
            annotation = AnnotatedElementUtils.findMergedAnnotation(element, annotationCls);
        }
        
        if (annotation == null) {
            annotation = property.getAnnotation(annotationCls);
        }
        
        return annotation;
    }
    
    private static Field getField(Method method) {
        if (!ReflectUtil.isGetterOrSetterIgnoreCase(method)) {
            throw new IllegalArgumentException(String.format("method %s is not getter/setter", method.getName()));
        }
        Class clazz = method.getDeclaringClass();
        String fieldName = getFieldName(method.getName());
        return ReflectUtil.getField(clazz, fieldName);
    }
    
    private static Method getBeanGetter(Field field) {
        Class clazz = field.getDeclaringClass();
        String methodName = "get" + StrUtil.upperFirst(field.getName());
        Method method = ReflectUtil.getMethod(clazz, methodName);
        if (method != null) {
            return method;
        }
        methodName = "is" + StrUtil.upperFirst(field.getName());
        return ReflectUtil.getMethod(clazz, methodName);
    }
    
    private static String getFieldName(String methodName) {
        String fieldName = methodName;
        if (methodName.startsWith("get") || methodName.startsWith("set")) {
            fieldName = methodName.substring(3);
        } else if (methodName.startsWith("is")) {
            fieldName = methodName.substring(2);
        }
        return StrUtil.lowerFirst(fieldName);
    }
    
}
