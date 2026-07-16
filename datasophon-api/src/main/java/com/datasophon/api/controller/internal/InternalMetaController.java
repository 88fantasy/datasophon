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

package com.datasophon.api.controller.internal;

import com.datasophon.api.load.LoadServiceMeta;
import com.datasophon.api.load.MetaReloadResult;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 元数据管理相关的内部端点（供内部系统或脚本调用）。
 *
 * <p>此控制器不继承 {@link com.datasophon.api.controller.ApiController}，路径为
 * {@code /ddh/internal/meta/**} 而非 {@code /ddh/api/internal/meta/**}。登录/CSRF
 * 拦截器仅覆盖 {@code /ddh/api/**}，{@code basicValidRequestInterceptor} 显式排除
 * {@code /internal/**}，故这些端点不在拦截范围内。
 *
 * <p><b>当前无鉴权</b>。后续如需加固，可参照
 * {@link com.datasophon.api.controller.AgentToolController} 的 X-Agent-Token 方案，
 * 在此增加等价的 X-Internal-Token 校验。
 */
@RestController
@RequestMapping("/internal/meta")
public class InternalMetaController {

    private final LoadServiceMeta loadServiceMeta;

    public InternalMetaController(LoadServiceMeta loadServiceMeta) {
        this.loadServiceMeta = loadServiceMeta;
    }

    @PostMapping("/refresh")
    public MetaReloadResult refresh() {
        return loadServiceMeta.reloadAllMeta();
    }
}
