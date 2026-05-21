package com.datasophon.common.jackson.serializer;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 扩展JsonSerializer的功能。
 *
 * 1. 继承该类的Serializer，可以通过{@link #annotation}字段获取注解在属性字段上的注解
 * 2. 简化jackson的api的使用
 * @author zhanghuangbin
 * @date 2024/10/8
 */
public abstract class AnnotationSerializerBase<T, A extends Annotation> extends JsonSerializer<T> implements ContextualSerializer {

    protected A annotation;

    protected boolean allowAnnotationAbsent;

    protected final Class<A> annotationCls;

    protected AnnotationSerializerBase() {
        this(false);
    }

    protected AnnotationSerializerBase(A annotation) {
        this(annotation == null);
        this.annotation = annotation;
    }

    private AnnotationSerializerBase(boolean allowAnnotationAbsent) {
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof Class<?>) { // sanity check, should never happen
            throw new IllegalArgumentException("Internal error: AnnotationSerializerBase constructed without actual type information");
        }
        ParameterizedType parameterizedType = (ParameterizedType) superClass;
        annotationCls = (Class<A>) parameterizedType.getActualTypeArguments()[1];
        this.allowAnnotationAbsent = allowAnnotationAbsent;
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (property == null) {
            return prov.findNullValueSerializer(null);
        } else {
            annotation = BeanPropertyUtils.findAnnotation(property, annotationCls);
        }
        if (annotation == null && !allowAnnotationAbsent) {
            throw JsonMappingException.from(
                    prov.getGenerator(),
                    StrUtil.format(
                            "use {} require the field annotated by @{}",
                            this.getClass().getSimpleName(),
                            annotationCls.getSimpleName()
                    )
            );
        }
        return withAnnotationType(annotation);
    }


    protected String getCurrentFieldName(JsonGenerator gen) {
        return gen.getOutputContext().getCurrentName();
    }

    protected void writeVal(JsonGenerator gen, Object val) throws IOException {
        if (val == null) {
            gen.writeNull();
        } else {
            gen.writeObject(val);
        }
    }

    protected abstract JsonSerializer<T> withAnnotationType(A annotation);
}
