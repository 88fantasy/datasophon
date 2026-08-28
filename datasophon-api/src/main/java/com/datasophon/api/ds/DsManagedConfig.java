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

package com.datasophon.api.ds;

import java.util.Map;

/**
 * SSOT：哪些 DS 配置项由平台托管（不接受用户输入，值来自其他服务的凭据/变量），
 * 供配置合并（{@code ServiceConfigUtils}）与 DDL 刷新（{@code DdlMetaServiceImpl}）两条路径共用，
 * 避免各自维护一份清单导致悄悄失步。
 */
public final class DsManagedConfig {

    public static final String SERVICE_NAME = "DS";

    public static final Map<String, String> OBJECT_STORAGE_CREDENTIALS = Map.of(
            "aws.s3.access.key.id", "${ROOT.Rustfs.access_key}",
            "aws.s3.access.key.secret", "${ROOT.Rustfs.secret_key}");

    private DsManagedConfig() {
    }
}
