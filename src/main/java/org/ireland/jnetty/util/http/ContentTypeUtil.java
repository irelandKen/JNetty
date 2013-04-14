/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.ireland.jnetty.util.http;


/**
 * Useful methods for Content-Type processing
 *
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author Harish Prabandham
 * @author costin@eng.sun.com
 * 
 * @see org.apache.tomcat.util.http.ContentType
 */
public class ContentTypeUtil {

    /**
     * Parse the character encoding from the specified content type header.
     * If the content type is null, or there is no explicit character encoding,
     * <code>null</code> is returned.
     *
     * @param contentType a content type header
     */
    public static String getCharsetFromContentType(String contentType) {

        if (contentType == null) {
            return (null);
        }
        int start = contentType.indexOf("charset=");
        if (start < 0) {
            return (null);
        }
        String encoding = contentType.substring(start + 8);
        int end = encoding.indexOf(';');
        if (end >= 0) {
            encoding = encoding.substring(0, end);
        }
        encoding = encoding.trim();
        if ((encoding.length() > 2) && (encoding.startsWith("\""))
            && (encoding.endsWith("\""))) {
            encoding = encoding.substring(1, encoding.length() - 1);
        }
        return (encoding.trim());

    }


    /**
     * Returns true if the given content type contains a charset component,
     * false otherwise.
     *
     * @param contentType Content type
     * @return true if the given content type contains a charset component,
     * false otherwise
     */
    public static boolean hasCharset(String contentType) {

        boolean hasCharset = false;

        int len = contentType.length();
        int index = contentType.indexOf(';');
        while (index != -1) {
            index++;
            while (index < len && Character.isSpace(contentType.charAt(index))) {
                index++;
            }
            if (index+8 < len
                    && contentType.charAt(index) == 'c'
                    && contentType.charAt(index+1) == 'h'
                    && contentType.charAt(index+2) == 'a'
                    && contentType.charAt(index+3) == 'r'
                    && contentType.charAt(index+4) == 's'
                    && contentType.charAt(index+5) == 'e'
                    && contentType.charAt(index+6) == 't'
                    && contentType.charAt(index+7) == '=') {
                hasCharset = true;
                break;
            }
            index = contentType.indexOf(';', index);
        }

        return hasCharset;
    }

    /**
     * Returns the contentType without Charset
     * 
     * EX: "text/css;charset=UTF-8" ==> "text/css"
     *
     * @param type Content type
     * @return the contentType without Charset
     */
    public static String getContentTypeWithoutCharset(String contentType) {


        int index = contentType.indexOf(';');

        if(index < 0)
        	return contentType;
        else
        	return ( (contentType.substring(0, index)).trim() );
    }
}
