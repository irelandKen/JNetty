/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.ireland.jnetty.util.http;

import io.netty.handler.codec.http.HttpConstants;


import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.ireland.jnetty.util.http.CookieEncoderUtil.*;

import javax.servlet.http.Cookie;




/**
 * Encodes server-side {@link Cookie}s into HTTP header values.  This encoder can encode
 * the HTTP cookie version 0, 1, and 2.
 * <pre>
 * // Example
 * {@link HttpRequest} req = ...;
 * res.setHeader("Set-Cookie", {@link ServletServerCookieEncoder}.encode("JSESSIONID", "1234"));
 * </pre>
 *
 * this is the special version of ServerCookieEncoder that can handle javax.servlet.http.Cookie directly.
 *
 * @see io.netty.handler.codec.http.CookieDecoder
 * @see io.netty.handler.codec.http.ServerCookieEncoder
 * 
 * 
 */
public final class ServletServerCookieEncoder {

    /**
     * Encodes the specified cookie into an HTTP header value.
     */
    public static String encode(String name, String value) {
        return encode(new Cookie(name, value));
    }

    public static String encode(Cookie cookie) {
        if (cookie == null) {
            throw new NullPointerException("cookie");
        }

        StringBuilder buf = new StringBuilder();

        add(buf, cookie.getName(), cookie.getValue());

        if (cookie.getMaxAge() != Long.MIN_VALUE) {
            if (cookie.getVersion() == 0) {
                addUnquoted(buf, CookieHeaderNames.EXPIRES,
                        new HttpHeaderDateFormat().format(
                                new Date(System.currentTimeMillis() +
                                         cookie.getMaxAge() * 1000L)));
            } else {
                add(buf, CookieHeaderNames.MAX_AGE, cookie.getMaxAge());
            }
        }

        if (cookie.getPath() != null) {
            if (cookie.getVersion() > 0) {
                add(buf, CookieHeaderNames.PATH, cookie.getPath());
            } else {
                addUnquoted(buf, CookieHeaderNames.PATH, cookie.getPath());
            }
        }

        if (cookie.getDomain() != null) {
            if (cookie.getVersion() > 0) {
                add(buf, CookieHeaderNames.DOMAIN, cookie.getDomain());
            } else {
                addUnquoted(buf, CookieHeaderNames.DOMAIN, cookie.getDomain());
            }
        }
        if (cookie.getSecure()) {
            buf.append(CookieHeaderNames.SECURE);
            buf.append((char) HttpConstants.SEMICOLON);
            buf.append((char) HttpConstants.SP);
        }
        if (cookie.isHttpOnly()) {
            buf.append(CookieHeaderNames.HTTPONLY);
            buf.append((char) HttpConstants.SEMICOLON);
            buf.append((char) HttpConstants.SP);
        }
        if (cookie.getVersion() >= 1) {
            if (cookie.getComment() != null) {
                add(buf, CookieHeaderNames.COMMENT, cookie.getComment());
            }

            add(buf, CookieHeaderNames.VERSION, 1);

            if (cookie.getPath() != null) {
                addQuoted(buf, CookieHeaderNames.COMMENTURL, cookie.getPath());
            }

/*            if (!cookie.getPorts().isEmpty()) {
                buf.append(CookieHeaderNames.PORT);
                buf.append((char) HttpConstants.EQUALS);
                buf.append((char) HttpConstants.DOUBLE_QUOTE);
                for (int port: cookie.getPorts()) {
                    buf.append(port);
                    buf.append((char) HttpConstants.COMMA);
                }
                buf.setCharAt(buf.length() - 1, (char) HttpConstants.DOUBLE_QUOTE);
                buf.append((char) HttpConstants.SEMICOLON);
                buf.append((char) HttpConstants.SP);
            }*/
/*            if (cookie.isDiscard()) {
                buf.append(CookieHeaderNames.DISCARD);
                buf.append((char) HttpConstants.SEMICOLON);
                buf.append((char) HttpConstants.SP);
            }*/
        }

        return stripTrailingSeparator(buf);
    }

    public static List<String> encode(Cookie... cookies) {
        if (cookies == null) {
            throw new NullPointerException("cookies");
        }

        List<String> encoded = new ArrayList<String>(cookies.length);
        for (Cookie c: cookies) {
            if (c == null) {
                break;
            }
            encoded.add(encode(c));
        }
        return encoded;
    }

    public static List<String> encode(Collection<Cookie> cookies) {
        if (cookies == null) {
            throw new NullPointerException("cookies");
        }

        List<String> encoded = new ArrayList<String>(cookies.size());
        for (Cookie c: cookies) {
            if (c == null) {
                break;
            }
            encoded.add(encode(c));
        }
        return encoded;
    }

    public static List<String> encode(Iterable<Cookie> cookies) {
        if (cookies == null) {
            throw new NullPointerException("cookies");
        }

        List<String> encoded = new ArrayList<String>();
        for (Cookie c: cookies) {
            if (c == null) {
                break;
            }
            encoded.add(encode(c));
        }
        return encoded;
    }

    private ServletServerCookieEncoder() {
        // Unused
    }
    
    

}

/**
 * @see io.netty.handler.codec.http.CookieHeaderNames
 * @author KEN
 *
 */
final class CookieHeaderNames {
    static final String PATH = "Path";

    static final String EXPIRES = "Expires";

    static final String MAX_AGE = "Max-Age";

    static final String DOMAIN = "Domain";

    static final String SECURE = "Secure";

    static final String HTTPONLY = "HTTPOnly";

    static final String COMMENT = "Comment";

    static final String COMMENTURL = "CommentURL";

    static final String DISCARD = "Discard";

    static final String PORT = "Port";

    static final String VERSION = "Version";

    private CookieHeaderNames() {
        // Unused.
    }
}



/**
 * @see io.netty.handler.codec.http.HttpHeaderDateFormat
 * @author KEN
 *
 */
final class HttpHeaderDateFormat extends SimpleDateFormat {
    private static final long serialVersionUID = -925286159755905325L;

    private final SimpleDateFormat format1 = new HttpHeaderDateFormatObsolete1();
    private final SimpleDateFormat format2 = new HttpHeaderDateFormatObsolete2();

    /**
     * Standard date format<p>
     * Sun, 06 Nov 1994 08:49:37 GMT -> E, d MMM yyyy HH:mm:ss z
     */
    HttpHeaderDateFormat() {
        super("E, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    @Override
    public Date parse(String text, ParsePosition pos) {
        Date date = super.parse(text, pos);
        if (date == null) {
            date = format1.parse(text, pos);
        }
        if (date == null) {
            date = format2.parse(text, pos);
        }
        return date;
    }

    /**
     * First obsolete format<p>
     * Sunday, 06-Nov-94 08:49:37 GMT -> E, d-MMM-y HH:mm:ss z
     */
    private static final class HttpHeaderDateFormatObsolete1 extends SimpleDateFormat {
        private static final long serialVersionUID = -3178072504225114298L;

        HttpHeaderDateFormatObsolete1() {
            super("E, dd-MMM-y HH:mm:ss z", Locale.ENGLISH);
            setTimeZone(TimeZone.getTimeZone("GMT"));
        }
    }

    /**
     * Second obsolete format
     * <p>
     * Sun Nov 6 08:49:37 1994 -> EEE, MMM d HH:mm:ss yyyy
     */
    private static final class HttpHeaderDateFormatObsolete2 extends SimpleDateFormat {
        private static final long serialVersionUID = 3010674519968303714L;

        HttpHeaderDateFormatObsolete2() {
            super("E MMM d HH:mm:ss yyyy", Locale.ENGLISH);
            setTimeZone(TimeZone.getTimeZone("GMT"));
        }
    }
}

/**
 * copy as io.netty.handler.codec.http.CookieEncoderUtil
 * @see io.netty.handler.codec.http.CookieEncoderUtil

 * @author KEN
 *
 */
final class CookieEncoderUtil {

    static String stripTrailingSeparator(StringBuilder buf) {
        if (buf.length() > 0) {
            buf.setLength(buf.length() - 2);
        }
        return buf.toString();
    }

    static void add(StringBuilder sb, String name, String val) {
        if (val == null) {
            addQuoted(sb, name, "");
            return;
        }

        for (int i = 0; i < val.length(); i ++) {
            char c = val.charAt(i);
            switch (c) {
            case '\t': case ' ': case '"': case '(':  case ')': case ',':
            case '/':  case ':': case ';': case '<':  case '=': case '>':
            case '?':  case '@': case '[': case '\\': case ']':
            case '{':  case '}':
                addQuoted(sb, name, val);
                return;
            }
        }

        addUnquoted(sb, name, val);
    }

    static void addUnquoted(StringBuilder sb, String name, String val) {
        sb.append(name);
        sb.append((char) HttpConstants.EQUALS);
        sb.append(val);
        sb.append((char) HttpConstants.SEMICOLON);
        sb.append((char) HttpConstants.SP);
    }

    static void addQuoted(StringBuilder sb, String name, String val) {
        if (val == null) {
            val = "";
        }

        sb.append(name);
        sb.append((char) HttpConstants.EQUALS);
        sb.append((char) HttpConstants.DOUBLE_QUOTE);
        sb.append(val.replace("\\", "\\\\").replace("\"", "\\\""));
        sb.append((char) HttpConstants.DOUBLE_QUOTE);
        sb.append((char) HttpConstants.SEMICOLON);
        sb.append((char) HttpConstants.SP);
    }

    static void add(StringBuilder sb, String name, long val) {
        sb.append(name);
        sb.append((char) HttpConstants.EQUALS);
        sb.append(val);
        sb.append((char) HttpConstants.SEMICOLON);
        sb.append((char) HttpConstants.SP);
    }

    private CookieEncoderUtil() {
        // Unused
    }
}
