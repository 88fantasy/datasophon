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

package com.datasophon.api.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「会向目标集群下发变更」的接口：集群为接管模式（{@code manage_mode = IMPORTED}）时一律拒绝。
 *
 * <p>接管的语义是只读监控——datasophon 绝不向目标集群写入任何东西。仅靠前端隐藏按钮不够，
 * 直接调 API 同样必须被拒，因此校验放在
 * {@link com.datasophon.api.interceptor.ImportedClusterGuardInterceptor} 里统一做。
 *
 * <p>使用前提：接口路径上必须有 {@code {clusterId}} 路径变量，拦截器据此定位集群。
 * 不满足这个前提的写入口（如旧版 {@code cluster/k8sInstance/removeInstanceId/{instanceId}}）
 * 需要在 service 层自行校验，不能只靠本注解。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ImportedReadOnly {

    /**
     * 拒绝时提示用户的动作说明，拼进错误信息里，例如「修改配置」。
     */
    String value() default "该操作";
}
