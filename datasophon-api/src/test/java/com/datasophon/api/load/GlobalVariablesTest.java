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

package com.datasophon.api.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

/**
 * 验证 GlobalVariables 的快照语义与并发安全（审计项 H1）：
 * getVariables 必须返回隔离快照，且与 put 的 clear+putAll 互斥，
 * 并发读写下不得出现 CME 或"半填充"变量集。
 */
class GlobalVariablesTest {
    
    @Test
    void getVariablesReturnsIsolatedSnapshot() {
        Integer clusterId = 90001;
        Map<String, String> source = new HashMap<>();
        source.put("k1", "v1");
        GlobalVariables.put(clusterId, source);
        
        Map<String, String> snapshot = GlobalVariables.getVariables(clusterId);
        assertEquals("v1", snapshot.get("k1"));
        
        // 全局后续写入不影响已取出的快照
        GlobalVariables.putValue(clusterId, "k2", "v2");
        assertFalse(snapshot.containsKey(GlobalVariables.surroundKey("k2")));
        
        // 修改快照不影响全局
        snapshot.put("local", "x");
        assertFalse(GlobalVariables.getVariables(clusterId).containsKey("local"));
    }
    
    @Test
    void getVariablesOfUnknownClusterReturnsEmptyMap() {
        Map<String, String> snapshot = GlobalVariables.getVariables(99999);
        assertNotNull(snapshot);
        assertTrue(snapshot.isEmpty());
    }
    
    @Test
    void concurrentPutAndSnapshotNeverYieldsHalfFilledView() throws Exception {
        Integer clusterId = 90002;
        int keysPerSet = 50;
        Map<String, String> setA = new HashMap<>();
        Map<String, String> setB = new HashMap<>();
        for (int i = 0; i < keysPerSet; i++) {
            setA.put("a" + i, "va");
            setB.put("b" + i, "vb");
        }
        GlobalVariables.put(clusterId, setA);
        
        AtomicBoolean stop = new AtomicBoolean(false);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            Future<?> writer = pool.submit(() -> {
                await(start);
                boolean useA = false;
                while (!stop.get()) {
                    GlobalVariables.put(clusterId, useA ? setA : setB);
                    useA = !useA;
                }
            });
            Future<Boolean> reader = pool.submit(() -> {
                await(start);
                for (int i = 0; i < 2000; i++) {
                    Map<String, String> snapshot = GlobalVariables.getVariables(clusterId);
                    // 模拟 gRPC 序列化遍历：不得抛 CME
                    int count = 0;
                    boolean hasA = false;
                    boolean hasB = false;
                    for (Map.Entry<String, String> entry : snapshot.entrySet()) {
                        count++;
                        if (entry.getKey().startsWith("a")) {
                            hasA = true;
                        } else if (entry.getKey().startsWith("b")) {
                            hasB = true;
                        }
                    }
                    // 快照必须是完整的 setA 或完整的 setB，不能混合或残缺
                    if (count != keysPerSet || (hasA && hasB)) {
                        return false;
                    }
                }
                return true;
            });
            start.countDown();
            assertTrue(reader.get(60, TimeUnit.SECONDS), "snapshot must be atomic: full setA or full setB");
            stop.set(true);
            writer.get(10, TimeUnit.SECONDS);
        } finally {
            stop.set(true);
            pool.shutdownNow();
        }
    }
    
    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
