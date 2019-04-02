package com.android.server.util;

import android.net.wifi.WifiConfiguration;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class NetworkCheckUtils {
    public static final int HTTP_RES_CODE_MOVED_PERMANENTLY = 301;

    public static final int HTTP_RES_CODE_FOUND = 302;

    public static final int HTTP_RES_CODE_SEE_OTHER = 303;

    public static final int HTTP_RES_CODE_TEMPORARY_REDIRECT = 307;

    public static final int HTTP_RES_CODE_BAD_REQUEST = 400;

    public static final int HTTP_RES_CODE_CLIENT_ERRORS_MAX = 499;

    public static final int X_HWCLOUD_REQID_LEN = 32;

    public static final String HTML_TITLE_HTTP_EN = "http://";

    public static final String HTML_TITLE_HTTPS_EN = "https://";

    public static final String KEY_WORDS_REDIRECTION = "location.replace";

    public static final int NONE = 0;

    public static final int WPA_PSK = 1;

    public static final int WPA2_PSK = 4;

    private NetworkCheckUtils() {
    }

    public static boolean isRedirectedRespCode(int respCode) {
        return ((respCode == HTTP_RES_CODE_MOVED_PERMANENTLY)
                || (respCode == HTTP_RES_CODE_FOUND)
                || (respCode == HTTP_RES_CODE_SEE_OTHER)
                || (respCode == HTTP_RES_CODE_TEMPORARY_REDIRECT));
    }

    public static boolean isClientErrorRespCode(int respCode) {
        return ((respCode >= HTTP_RES_CODE_BAD_REQUEST)
                && (respCode == HTTP_RES_CODE_CLIENT_ERRORS_MAX));
    }

    public static String parseHostByUrlLocation(String requestUrl) {
        if (requestUrl != null) {
            int start = 0;
            int end = 0;
            if (requestUrl.startsWith(HTML_TITLE_HTTP_EN)) {
                start = HTML_TITLE_HTTP_EN.length();
            } else if (requestUrl.startsWith(HTML_TITLE_HTTPS_EN)) {
                start = HTML_TITLE_HTTPS_EN.length();
            }
            final String tag = "/";
            end = requestUrl.indexOf(tag, start);
            if ((end != -1) && ((end + tag.length()) <= requestUrl.length())) {
                String tmpHost = requestUrl.substring(start, end);
                int tmpEnd = tmpHost.indexOf("?", 0);
                end = ((tmpEnd != -1) ? (tmpEnd + start) : end);
            } else {
                end = requestUrl.indexOf("?", start);
            }
            start = 0;
            if (end == -1) {
                return requestUrl;
            } else if ((start <= end) && ((end + tag.length()) <= requestUrl.length())) {
                return requestUrl.substring(start, end);
            }
        }
        return requestUrl;
    }

    //NetworkUtils.intToInetAddress UnsupportedAppUsage
    public static Inet4Address intToInetAddress(int hostAddress) {
        int tmpHostAddress = Integer.reverseBytes(hostAddress);
        byte[] addressBytes = {(byte) (0xff & (tmpHostAddress >> 24)),
            (byte) (0xff & (tmpHostAddress >> 16)),
            (byte) (0xff & (tmpHostAddress >> 8)),
            (byte) (0xff & tmpHostAddress)};

        try {
            return (Inet4Address) InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            throw new AssertionError();
        }
    }

    public static boolean isWpaOrWpa2(WifiConfiguration config) {
        if (config != null) {
            if (config.allowedKeyManagement.cardinality() > 1) {
                return false;
            }
            return ((config.allowedKeyManagement.get(WPA_PSK))
                    || (config.allowedKeyManagement.get(WPA2_PSK)));
        }
        return false;
    }
}
