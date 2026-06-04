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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.common.net.InetAddresses;

/**
 * 读取hosts文件
 *
 * @author gaodayu
 */
public enum HostUtils {
    ;
    
    public static final Pattern HOST_NAME_STR = Pattern.compile("[0-9a-zA-Z-.]{1,64}");
    
    public static boolean checkIP(String ipStr) {
        return InetAddresses.isInetAddress(ipStr);
    }
    
    private static void checkIPThrow(String ipStr, Map<String, String> ipHost) {
        if (!checkIP(ipStr)) {
            throw new RuntimeException("Invalid IP in file /etc/hosts, IP：" + ipStr);
        }
        if (ipHost.containsKey(ipStr)) {
            throw new RuntimeException("Duplicate ip in file /etc/hosts, IP：" + ipStr);
        }
    }
    
    public static boolean checkHostname(String hostname) {
        if (!HOST_NAME_STR.matcher(hostname).matches()) {
            return false;
        }
        return !hostname.startsWith("-") && !hostname.endsWith("-");
    }
    
    private static void validHostname(String hostname) {
        if (!checkHostname(hostname)) {
            throw new RuntimeException("Invalid hostname in file /etc/hosts, hostname：" + hostname);
        }
    }
    
    public static String findIp(String hostname) {
        validHostname(hostname);
        String ip = getIp(hostname);
        return ip;
    }
    
    public static String getHostName(String hostOrIp) {
        try {
            InetAddress byName = InetAddress.getByName(hostOrIp);
            return byName.getCanonicalHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static String getIp(String hostName) {
        try {
            InetAddress byName = InetAddress.getByName(hostName);
            return byName.getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static String getLocalIp() {
        try {
            InetAddress ip = InetAddress.getLocalHost();
            return ip.getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static String getLocalHostName() {
        try {
            InetAddress ip = InetAddress.getLocalHost();
            return ip.getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
    
}
