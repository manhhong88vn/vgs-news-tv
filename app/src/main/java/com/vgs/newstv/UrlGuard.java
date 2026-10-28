package com.vgs.newstv;

import java.net.URL;
import java.util.Locale;

/**
 * Kiểm tra địa chỉ trước khi dùng. Dùng MỘT cách phân tích duy nhất (java.net.URL,
 * cũng là cách Jsoup và Glide dùng để kết nối), nên không bị lệch giữa chỗ kiểm tra
 * và chỗ tải thật.
 *
 * Chỉ nhận: HTTPS, không có user@ trong địa chỉ, cổng mặc định, tên miền nằm trong danh sách cho phép.
 */
final class UrlGuard {

    private static final int MAX_LEN = 2048;

    private UrlGuard() {}

    /** Link bài viết: chỉ vnexpress.net và miền con. */
    static boolean isArticle(String url) {
        return isTrusted(url, "vnexpress.net");
    }

    /** Ảnh: vnexpress.net hoặc vnecdn.net (CDN ảnh của VnExpress) và miền con. */
    static boolean isImage(String url) {
        return isTrusted(url, "vnexpress.net", "vnecdn.net");
    }

    static boolean isTrusted(String url, String... domains) {
        if (url == null || url.isEmpty() || url.length() > MAX_LEN) return false;
        try {
            URL u = new URL(url);
            if (!"https".equalsIgnoreCase(u.getProtocol())) return false;
            if (u.getUserInfo() != null) return false;
            int port = u.getPort();
            if (port != -1 && port != 443) return false;
            String host = u.getHost();
            if (host == null || host.isEmpty()) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String d : domains) {
                if (host.equals(d) || host.endsWith("." + d)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
