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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.dto.v2;

import java.util.List;

import lombok.Data;

/** One Query or Load row in a Doris active-task snapshot. */
@Data
public class DorisActiveTaskVO {

    private String taskId;
    private String type;
    private String user;
    private String clientAddress;
    private String sql;
    private Long elapsedMs;
    private String startTime;
    private Long currentMemoryBytes;
    private Long peakMemoryBytes;
    private Long scanRows;
    private Long scanBytes;
    private Long cpuTimeMs;
    private Long shuffleSendBytes;
    private Long shuffleSendRows;
    private Long spillWriteBytesToLocalStorage;
    private Long spillReadBytesFromLocalStorage;
    private Long workloadGroupId;
    private String workloadGroupName;
    private String feHost;
    private String queryStatus;
    private String queueStartTime;
    private String queueEndTime;
    private Boolean truncated;
    private List<DorisBeTaskDetailVO> beDetails;
}
