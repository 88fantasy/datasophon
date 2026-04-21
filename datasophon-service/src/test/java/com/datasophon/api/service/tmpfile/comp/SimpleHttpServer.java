package com.datasophon.api.service.tmpfile.comp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 简单的 HTTP 服务器用于测试
 * @author zhanghuangbin
 * @date 2026/04/21
 */
class SimpleHttpServer implements Runnable {

    private final int port;
    private final String responseContent;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private java.net.ServerSocket serverSocket;
    private Thread serverThread;

    SimpleHttpServer(int port, String responseContent) {
        this.port = port;
        this.responseContent = responseContent;
    }

    void start() throws IOException {
        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(5000);
        running.set(true);
        serverThread = new Thread(this);
        serverThread.start();
    }

    void stop() {
        running.set(false);
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // 忽略关闭异常
            }
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                java.net.Socket clientSocket = serverSocket.accept();
                handleRequest(clientSocket);
            } catch (IOException e) {
                if (running.get()) {
                    // 非正常关闭的异常
                }
            }
        }
    }

    private void handleRequest(java.net.Socket socket) throws IOException {
        // 读取请求（简单跳过）
        socket.getInputStream().read(new byte[1024]);

        // 发送响应
        String response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: " + responseContent.length() + "\r\n" +
                "Connection: close\r\n" +
                "\r\n" +
                responseContent;

        try (OutputStream out = socket.getOutputStream()) {
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }
}
