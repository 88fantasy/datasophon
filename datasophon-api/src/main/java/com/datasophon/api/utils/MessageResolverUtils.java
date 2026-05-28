/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package com.datasophon.api.utils;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import cn.hutool.extra.spring.SpringUtil;

/**
 * desc： 获取i18n资源文件
 */
public class MessageResolverUtils {
    
    @Autowired
    private static MessageSource messageSource = SpringUtil.getBean(MessageSource.class);
    
    public MessageResolverUtils() {
    }
    
    /**
     * 根据 messageKey 获取国际化消息 委托给 spring messageSource
     *
     * @param code 消息key
     * @return 解析后的国际化
     */
    public static String getMessage(Object code) {
        return messageSource.getMessage(code.toString(), null, code.toString(), LocaleContextHolder.getLocale());
    }
    
    /**
     * 根据 messageKey 和参数 获取消息 委托给 spring messageSource
     *
     * @param code        消息key
     * @param messageArgs 参数
     * @return 解析后的国际化
     */
    public static String getMessages(Object code, Object... messageArgs) {
        Object[] objs = Arrays.stream(messageArgs).map(MessageResolverUtils::getMessage).toArray();
        String message =
                messageSource.getMessage(code.toString(), objs, code.toString(), LocaleContextHolder.getLocale());
        return message;
    }
}
