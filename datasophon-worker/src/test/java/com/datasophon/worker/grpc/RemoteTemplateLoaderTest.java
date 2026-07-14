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

package com.datasophon.worker.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.datasophon.worker.utils.RemoteTemplateLoader;

import java.io.Reader;
import java.io.StringWriter;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class RemoteTemplateLoaderTest {

    private HttpServer server;
    private RemoteTemplateLoader loader;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ddh/api/service/install/downloadTemplate", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            byte[] body = query != null && query.contains("valid.ftl")
                    ? "template-content".getBytes()
                    : new byte[0];
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        loader = new RemoteTemplateLoader("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void emptyRemoteTemplateFallsBackToNextLoader() throws Exception {
        assertThat(loader.findTemplateSource("missing.ftl")).isNull();
    }

    @Test
    void nonEmptyRemoteTemplateCanBeRead() throws Exception {
        Object source = loader.findTemplateSource("valid.ftl");
        try (Reader reader = loader.getReader(source, "UTF-8")) {
            StringWriter content = new StringWriter();
            reader.transferTo(content);
            assertThat(content).hasToString("template-content");
        }
    }
}
