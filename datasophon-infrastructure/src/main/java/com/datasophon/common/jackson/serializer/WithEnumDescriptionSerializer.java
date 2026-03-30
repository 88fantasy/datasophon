package com.datasophon.common.jackson.serializer;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.jackson.annotation.WithEnumDescription;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zhanghuangbin
 */
public class WithEnumDescriptionSerializer extends AnnotationSerializerBase<Object, WithEnumDescription> {

    private static final Map<String, Expression> CACHE = new ConcurrentHashMap<>();


    public WithEnumDescriptionSerializer() {
    }

    private WithEnumDescriptionSerializer(WithEnumDescription annotation) {
        super(annotation);
    }

    @Override
    protected JsonSerializer<Object> withAnnotationType(WithEnumDescription annotation) {
        return new WithEnumDescriptionSerializer(annotation);
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        String fieldName = getCurrentFieldName(gen);
        writeVal(gen, value);

        String newField = getFieldName(annotation.fieldNameTpl(), fieldName);
        Object newVal = getVal(value);
        if (newVal != null) {
            gen.writeObjectField(newField, newVal);
        }
    }

    private String getFieldName(String fieldNameTpl, String fieldName) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setRootObject(fieldNameTpl);
        ctx.setVariable("field", fieldName);

        Expression expr = CACHE.computeIfAbsent(fieldNameTpl, tpl -> {
            SpelExpressionParser parser = new SpelExpressionParser();
            return parser.parseExpression(fieldNameTpl);
        });

        return (String) expr.getValue(ctx);
    }

    private Object getVal(Object value) {
        if (value == null) {
            return null;
        }
        Class cls = value.getClass();
        if (StrUtil.isNotBlank(annotation.method())) {
            Method method = ReflectUtil.getMethod(cls, annotation.method());
            if (method != null) {
                return ReflectUtil.invoke(value, method);
            }
            throw new IllegalStateException(String.format("class %s do not has a method call %s", cls, annotation.method()));
        }
        return ReflectUtil.getFieldValue(value, annotation.field());
    }
}

