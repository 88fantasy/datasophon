package com.datasophon.k8sagent.aop;

import com.datasophon.common.utils.Result;

import lombok.RequiredArgsConstructor;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 对json返回数据，通过Result类进行修饰。
 * 由于不确定系统架构，所以，不同Controller来返回通用的数据格式
 *
 * @author zhanghuangbin
 * @date 2025/2/12
 */
@ControllerAdvice
@Component
@RequiredArgsConstructor
public class UniResponseBodyWrapperAdvice implements ResponseBodyAdvice<Object> {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return (AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType) || StringHttpMessageConverter.class.isAssignableFrom(converterType))
                && !Result.class.isAssignableFrom(returnType.getParameterType());
    }
    
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result) {
            return body;
        }
        
        // String类型会被StringHttpMessageConverter处理，所以需要先包裹，再json化。
        if (returnType.getParameterType() == String.class) {
            try {
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return objectMapper.writeValueAsString(Result.success(body));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        } else {
            return Result.success(body);
        }
    }
}
