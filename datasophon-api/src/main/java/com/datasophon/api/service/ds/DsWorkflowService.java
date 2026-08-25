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

package com.datasophon.api.service.ds;

import com.datasophon.api.dto.v2.DsDagVO;
import com.datasophon.api.dto.v2.DsPageVO;
import com.datasophon.api.dto.v2.DsProjectVO;
import com.datasophon.api.dto.v2.DsWorkflowDefinitionVO;
import com.datasophon.api.dto.v2.DsWorkflowInstanceVO;

/** Read-only DS workflow query service exposed to the REST layer. */
public interface DsWorkflowService {

    DsPageVO<DsProjectVO> projects(Integer clusterId);

    DsPageVO<DsWorkflowDefinitionVO> workflows(Integer clusterId,
                                               long projectCode,
                                               int pageNo,
                                               int pageSize,
                                               String searchVal);

    DsPageVO<DsWorkflowInstanceVO> instances(Integer clusterId,
                                             long projectCode,
                                             long workflowCode,
                                             int limit);

    DsDagVO dag(Integer clusterId, long projectCode, int instanceId);
}
