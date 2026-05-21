package com.datasophon.common.jackson.serializer;

import cn.hutool.core.util.ReflectUtil;
import com.datasophon.common.jackson.annotation.WithEnumSourceDescription;
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
public class WithEnumSourceDescriptionSerializer extends AnnotationSerializerBase<Object, WithEnumSourceDescription> {

    private static final Map<String, Expression> CACHE = new ConcurrentHashMap<>();


    public WithEnumSourceDescriptionSerializer() {
    }

    private WithEnumSourceDescriptionSerializer(WithEnumSourceDescription annotation) {
        super(annotation);
    }

    @Override
    protected JsonSerializer<Object> withAnnotationType(WithEnumSourceDescription annotation) {
        return new WithEnumSourceDescriptionSerializer(annotation);
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
        // 从@WithEnumSourceDescription#datasource 获取枚举类
        Class<? extends Enum<?>> enumClass = annotation.datasource();
        Enum<?>[] enums = enumClass.getEnumConstants();

        // 获取 valueMapping 和 descMapping 对应的方法
        Method valueMethod = ReflectUtil.getMethodByName(enums[0].getClass(), annotation.valueMapping());
        Method descMethod = ReflectUtil.getMethodByName(enums[0].getClass(), annotation.descMapping());

        if (valueMethod == null || descMethod == null) {
            return null;
        }

        // 遍历枚举类，如果枚举类的 valueMapping 方法的值等于当前值，则返回 descMapping 的值
        for (Enum<?> enumConstant : enums) {
            Object enumValue = ReflectUtil.invoke(enumConstant, valueMethod);
            if (value.equals(enumValue)) {
                return ReflectUtil.invoke(enumConstant, descMethod);
            }
        }

        return null;
    }
}
