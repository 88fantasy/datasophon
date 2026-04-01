package com.datasophon.common.jackson.annotation;

import com.datasophon.common.jackson.serializer.WithEnumSourceDescriptionSerializer;
import com.fasterxml.jackson.annotation.JacksonAnnotation;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 序列化时，输出枚举对象(或者枚举值)的含义
 * eg:
 * <pre>
 *     enum NotifyType{
 *         sms;
 *
 *         public String getDescription() {
 *             return "短信";
 *         }
 *     }
 *     class Bean {
 *
 *         @Desensitize(type=DesensitizeType.PASSWORD)
 *         private NotifyType type;
 *     }
 * </pre>
 *
 * // output: { "type": "sms", "typeName": "短信"}
 *
 * @author zhanghuangbin
 * @date 2024/10/9
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@JacksonAnnotation
@JacksonAnnotationsInside
@JsonSerialize(using = WithEnumSourceDescriptionSerializer.class)
public @interface WithEnumSourceDescription {


    String valueMapping();

    String descMapping();

    Class<? extends Enum<?>> datasource();

    String fieldNameTpl() default "#field + 'Name'";


}
