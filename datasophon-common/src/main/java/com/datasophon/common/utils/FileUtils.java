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

package com.datasophon.common.utils;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

import com.google.common.io.CharStreams;
import com.google.common.io.LineProcessor;

/**
 * 基本文件的特殊操作，文件MD5，从 targz 压缩包不解压读取一个文本文件，读取一个文件的第一行 等
 *
 * <pre>
 *
 * Created by zhenqin.
 * User: zhenqin
 * Date: 2023/4/21
 * Time: 下午9:58
 *
 * </pre>
 *
 * @author zhenqin
 */
public class FileUtils {
    
    /**
     * 获取一个文件的md5值(可处理大文件)
     *
     * @return md5 value
     */
    public static String md5(File file) {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fileInputStream.read(buffer)) != -1) {
                md5.update(buffer, 0, length);
            }
            return new String(Hex.encodeHex(md5.digest()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
    
    public static String md5(Iterator<File> iterator, Consumer<Integer> consumer) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
            while (iterator.hasNext()) {
                File file = iterator.next();
                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = fileInputStream.read(buffer)) != -1) {
                        md5.update(buffer, 0, length);
                        consumer.accept(length);
                    }
                }
            }
            return new String(Hex.encodeHex(md5.digest()));
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException(e);
        }
    }
    
    /**
     * 从 tar.gz 的压缩包内读取一个 文本文件
     *
     * @param targz
     * @param name
     * @return
     * @throws IOException
     */
    public static String readTargzTextFile(File targz, String name, Charset charset) throws IOException {
        String content = null;
        TarEntry tarEntry = null;
        try (
                TarInputStream tarInputStream = new TarInputStream(new GZIPInputStream(new FileInputStream(targz)));
                BufferedReader reader = new BufferedReader(new InputStreamReader(tarInputStream, charset));) {
            boolean hasNext = reader.readLine() != null;
            if (hasNext) {
                return null;
            }
            while ((tarEntry = tarInputStream.getNextEntry()) != null) {
                String entryName = tarEntry.getName();
                if (tarEntry.isDirectory()) {
                    // 如果是文件夹,创建文件夹并加速循环
                    continue;
                }
                if (entryName.endsWith(name)) {
                    // 找到第一个文件就结束
                    content = CharStreams.toString(reader);
                    break;
                }
            }
        }
        return content;
    }
    
    /**
     * 读取文件第一行，第一行的非空行
     *
     * @param file
     * @return
     * @throws Exception
     */
    public static String readFirstLine(File file) throws Exception {
        final String firstLine = CharStreams.readLines(new FileReader(file), new LineProcessor<String>() {
            
            String firstLine = null;
            
            @Override
            public boolean processLine(String line) throws IOException {
                this.firstLine = line;
                // 第一行非空则返回
                return StringUtils.trimToNull(line) == null;
            }
            
            @Override
            public String getResult() {
                return firstLine;
            }
        });
        return firstLine;
    }
    
    /**
     * 连接路径
     *
     * @param paths
     * @return
     */
    public static String concatPath(String... paths) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            if (StringUtils.isBlank(path)) {
                continue;
            }
            path = StringUtils.appendIfMissing(path, "/");
            if (i != 0) {
                path = StringUtils.removeStart(path, "/");
            }
            if (i == paths.length - 1) {
                path = StringUtils.removeEnd(path, "/");
            }
            stringBuilder.append(path);
        }
        return StringUtils.removeEnd(stringBuilder.toString(), "/");
    }
}
