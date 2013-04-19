//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.ireland.jnetty.http;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.IllegalSelectorException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.util.ByteArrayISO8859Writer;
import org.eclipse.jetty.util.StringUtil;


import org.ireland.jnetty.util.http.ServletServerCookieEncoder;

/**
 * <p>
 * {@link HttpServletResponseImpl} provides the implementation for {@link HttpServletResponse}.
 * </p>
 */
public class HttpServletResponseImpl implements HttpServletResponse
{
	/**
	 * Logger available to subclasses.
	 */
	protected static final Log LOG = LogFactory.getLog(HttpServletResponseImpl.class);

	// ----------------------------------------------------- Class Variables

	/**
	 * Default locale as mandated by the spec.
	 */
	private static Locale DEFAULT_LOCALE = Locale.getDefault();
	
	public static final String DEFAULT_CHARACTER_ENCODING="ISO-8859-1";


	private ServletContext servletContext;

	// Netty
	private final SocketChannel socketChannel;

	private final ChannelHandlerContext ctx;


	private final FullHttpRequest request;
	
	// response
	private final FullHttpResponse response;

	// response header
	private final HttpHeaders headers;

	// response body
	private final LastHttpContent body;

	// response end

	/**
	 * Using output stream flag.
	 */
	protected boolean usingOutputStream = false;

	/**
	 * The associated output stream.
	 */
	protected ServletOutputStream outputStream;

	/**
	 * Using writer flag.
	 */
	protected boolean usingWriter = false;

	/**
	 * Committed flag.
	 */
	protected boolean commited = false;

	private List<Cookie> _cookiesOut;

	private final AtomicInteger _include = new AtomicInteger();

	/* Headers--------------------------------------- */
	/**
	 * Status code.
	 */
	protected int _status = SC_OK;

	private String _reason;

	private Locale _locale;
	private String contentLanguage = null;

	private MimeTypes.Type _mimeType;

	/**
	 * The characterEncoding flag(eplicitly set flag)
	 */
	private boolean isCharacterEncodingSet = false;

	private String _characterEncoding;

	private String _contentType;
	private PrintWriter _writer;
	private long _contentLength = -1;

	public HttpServletResponseImpl(SocketChannel socketChannel, ChannelHandlerContext ctx,
												FullHttpResponse response, FullHttpRequest request)
	{
		this.socketChannel = socketChannel;
		this.ctx = ctx;
		
		this.request = request;
		
		//Response
		this.response = response;
		
		//Response Header
		this.headers = response.headers();
		
		//Response Body
		this.body = response;

	}

	protected SocketChannel getHttpChannel()
	{
		return socketChannel;
	}

	protected void recycle()
	{
		_status = SC_OK;
		_reason = null;
		_locale = null;
		_characterEncoding = null;
		_contentType = null;
		_contentLength = -1;
		headers.clear();
	}

	public HttpContent getResponseBody()
	{
		return body;
	}

	public boolean isIncluding()
	{
		return _include.get() > 0;
	}

	public void include()
	{
		_include.incrementAndGet();
	}

	public void included()
	{
		_include.decrementAndGet();
		// body.reopen();
	}

	@Override
	// OK
	public void addCookie(Cookie cookie)
	{
		// Ignore any call from an included servlet
		if (isIncluding() || isCommitted())
		{
			return;
		}

		if (_cookiesOut == null)
			_cookiesOut = new ArrayList<Cookie>();

		_cookiesOut.add(cookie);

		// if we reached here, no exception, cookie is valid
		// the header name is Set-Cookie for both "old" and v.1 ( RFC2109 )
		// RFC2965 is not supported by browsers and the Servlet spec
		// asks for 2109.
		headers.add("Set-Cookie",
				ServletServerCookieEncoder.encode(_cookiesOut));

	}

	@Override
	// OK
	public boolean containsHeader(String name)
	{
		if (name == null || name.length() == 0)
		{
			return false;
		}

		// Need special handling for Content-Type and Content-Length due to
		// special handling of these in coyoteResponse
		char cc = name.charAt(0);
		if (cc == 'C' || cc == 'c')
		{
			if (name.equalsIgnoreCase(HttpHeaders.Names.CONTENT_TYPE))
			{
				// Will return null if this has not been set
				return (_contentType != null);
			}
			if (name.equalsIgnoreCase(HttpHeaders.Names.CONTENT_LENGTH))
			{
				// -1 means not known and is not sent to client
				return (_contentLength != -1);
			}
		}

		// Need special handling for Set-Cookie
		if (cc == 'S' || cc == 's')
		{
			if (name.equalsIgnoreCase(HttpHeaders.Names.SET_COOKIE))
			{
				return (_cookiesOut != null) && (_cookiesOut.size() > 0);
			}
		}

		return headers.contains(name);
	}

	@Override
	// TODO
	public String encodeURL(String url)
	{

		/*
		 * SessionManager sessionManager = httpServletRequest.getSessionManager(); if
		 * (sessionManager == null) return url;
		 * 
		 * HttpURI uri = null; if
		 * (sessionManager.isCheckingRemoteSessionIdEncoding() &&
		 * URIUtil.hasScheme(url)) { uri = new HttpURI(url); String path =
		 * uri.getPath(); path = (path == null ? "" : path); int port =
		 * uri.getPort(); if (port < 0) port =
		 * HttpScheme.HTTPS.asString().equalsIgnoreCase(uri.getScheme()) ? 443 :
		 * 80; if (!httpServletRequest.getServerName().equalsIgnoreCase(uri.getHost()) ||
		 * httpServletRequest.getServerPort() != port ||
		 * !path.startsWith(httpServletRequest.getContextPath())) //TODO the root context
		 * path is "", with which every non null string starts return url; }
		 * 
		 * String sessionURLPrefix =
		 * sessionManager.getSessionIdPathParameterNamePrefix(); if
		 * (sessionURLPrefix == null) return url;
		 * 
		 * if (url == null) return null;
		 * 
		 * // should not encode if cookies in evidence if
		 * (httpServletRequest.isRequestedSessionIdFromCookie()) { int prefix =
		 * url.indexOf(sessionURLPrefix); if (prefix != -1) { int suffix =
		 * url.indexOf("?", prefix); if (suffix < 0) suffix = url.indexOf("#",
		 * prefix);
		 * 
		 * if (suffix <= prefix) return url.substring(0, prefix); return
		 * url.substring(0, prefix) + url.substring(suffix); } return url; }
		 * 
		 * // get session; HttpSession session = httpServletRequest.getSession(false);
		 * 
		 * // no session if (session == null) return url;
		 * 
		 * // invalid session if (!sessionManager.isValid(session)) return url;
		 * 
		 * String id = sessionManager.getNodeId(session);
		 * 
		 * if (uri == null) uri = new HttpURI(url);
		 * 
		 * 
		 * // Already encoded int prefix = url.indexOf(sessionURLPrefix); if
		 * (prefix != -1) { int suffix = url.indexOf("?", prefix); if (suffix <
		 * 0) suffix = url.indexOf("#", prefix);
		 * 
		 * if (suffix <= prefix) return url.substring(0, prefix +
		 * sessionURLPrefix.length()) + id; return url.substring(0, prefix +
		 * sessionURLPrefix.length()) + id + url.substring(suffix); }
		 * 
		 * // edit the session int suffix = url.indexOf('?'); if (suffix < 0)
		 * suffix = url.indexOf('#'); if (suffix < 0) { return url +
		 * ((HttpScheme.HTTPS.is(uri.getScheme()) ||
		 * HttpScheme.HTTP.is(uri.getScheme())) && uri.getPath() == null ? "/" :
		 * "") + //if no path, insert the root path sessionURLPrefix + id; }
		 * 
		 * 
		 * return url.substring(0, suffix) +
		 * ((HttpScheme.HTTPS.is(uri.getScheme()) ||
		 * HttpScheme.HTTP.is(uri.getScheme())) && uri.getPath() == null ? "/" :
		 * "") + //if no path so insert the root path sessionURLPrefix + id +
		 * url.substring(suffix);
		 */
		return url;
	}

	@Override
	public String encodeRedirectURL(String url)
	{
		return encodeURL(url);
	}

	@Override
	@Deprecated
	public String encodeUrl(String url)
	{
		return encodeURL(url);
	}

	@Override
	@Deprecated
	public String encodeRedirectUrl(String url)
	{
		return encodeRedirectURL(url);
	}

	@Override
	public void sendError(int sc) throws IOException
	{
		if (sc == 102)
			sendProcessing();
		else
			sendError(sc, null);
	}

	@Override
	public void sendError(int code, String message) throws IOException
	{
		if (isIncluding())
			return;

		if (isCommitted())
			LOG.warn("Committed before " + code + " " + message);

		resetBuffer();
		_characterEncoding = null;
		setHeader(HttpHeaders.Names.EXPIRES, null);
		setHeader(HttpHeaders.Names.LAST_MODIFIED, null);
		setHeader(HttpHeaders.Names.CACHE_CONTROL, null);
		setHeader(HttpHeaders.Names.CONTENT_TYPE, null);
		setHeader(HttpHeaders.Names.CONTENT_LENGTH, null);

		usingOutputStream = false;
		usingWriter = false;
		
		setStatus(code);
		_reason = message;

		if (message == null)
			message = HttpResponseStatus.valueOf(code).reasonPhrase();

		// If we are allowed to have a body
		if (code != SC_NO_CONTENT && code != SC_NOT_MODIFIED
				&& code != SC_PARTIAL_CONTENT && code >= SC_OK)
		{

			setHeader(HttpHeaders.Names.CACHE_CONTROL,
					"must-revalidate,no-cache,no-store");
			setContentType("text/html;charset=ISO-8859-1");
			ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer(2048);
			if (message != null)
			{
				message = StringUtil.replace(message, "&", "&amp;");
				message = StringUtil.replace(message, "<", "&lt;");
				message = StringUtil.replace(message, ">", "&gt;");
			}
			String uri = request.getUri();
			if (uri != null)
			{
				uri = StringUtil.replace(uri, "&", "&amp;");
				uri = StringUtil.replace(uri, "<", "&lt;");
				uri = StringUtil.replace(uri, ">", "&gt;");
			}

			writer.write("<html>\n<head>\n<meta http-equiv=\"Content-Type\" content=\"text/html;charset=ISO-8859-1\"/>\n");
			writer.write("<title>Error ");
			writer.write(Integer.toString(code));
			writer.write(' ');

			if (message == null)
				message = HttpResponseStatus.valueOf(code).reasonPhrase();

			writer.write(message);
			writer.write("</title>\n</head>\n<body>\n<h2>HTTP ERROR: ");
			writer.write(Integer.toString(code));
			writer.write("</h2>\n<p>Problem accessing ");
			writer.write(uri);
			writer.write(". Reason:\n<pre>    ");
			writer.write(message);
			writer.write("</pre>");
			writer.write("</p>\n<hr /><i><small>Powered by JNetty(IrelandKen)://</small></i>");
			writer.write("\n</body>\n</html>\n");

			writer.flush();
			setContentLength(writer.size());
			writer.writeTo(getOutputStream());
			writer.destroy();
		}

		complete();
	}

	/**
	 * Sends a 102-Processing response. If the connection is a HTTP connection,
	 * the version is 1.1 and the httpServletRequest has a Expect header starting with 102,
	 * then a 102 response is sent. This indicates that the httpServletRequest still be
	 * processed and real response can still be sent. This method is called by
	 * sendError if it is passed 102.
	 * 
	 * @see javax.servlet.http.HttpServletResponse#sendError(int)
	 */
	public void sendProcessing() throws IOException
	{
		/*
		 * if (socketChannel.isExpecting102Processing() && !isCommitted()) {
		 * socketChannel.commitResponse(HttpGenerator.PROGRESS_102_INFO, null,
		 * true); }
		 */
	}

	@Override
	// TODO
	public void sendRedirect(String location) throws IOException
	{
		/*
		 * if (isIncluding()) return;
		 * 
		 * if (location == null) throw new IllegalArgumentException();
		 * 
		 * if (!URIUtil.hasScheme(location)) { StringBuilder buf =
		 * httpServletRequest.getRootURL(); if (location.startsWith("/"))
		 * buf.append(location); else { String path = httpServletRequest.getRequestURI();
		 * String parent = (path.endsWith("/")) ? path :
		 * URIUtil.parentPath(path); location = URIUtil.addPaths(parent,
		 * location); if (location == null) throw new
		 * IllegalStateException("path cannot be above root"); if
		 * (!location.startsWith("/")) buf.append('/'); buf.append(location); }
		 * 
		 * location = buf.toString(); HttpURI uri = new HttpURI(location);
		 * String path = uri.getDecodedPath(); String canonical =
		 * URIUtil.canonicalPath(path); if (canonical == null) throw new
		 * IllegalArgumentException(); if (!canonical.equals(path)) { buf =
		 * socketChannel.getRequest().getRootURL();
		 * buf.append(URIUtil.encodePath(canonical)); String
		 * param=uri.getParam(); if (param!=null) { buf.append(';');
		 * buf.append(param); } String query=uri.getQuery(); if (query!=null) {
		 * buf.append('?'); buf.append(query); } String
		 * fragment=uri.getFragment(); if (fragment!=null) { buf.append('#');
		 * buf.append(fragment); } location = buf.toString(); } }
		 * 
		 * resetBuffer(); setHeader(HttpHeaders.Names.LOCATION, location);
		 * setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY); complete();
		 */
	}

	@Override
	// OK
	public void setDateHeader(String name, long date)
	{
		if (name == null || name.length() == 0)
		{
			return;
		}

		if (isCommitted())
		{
			return;
		}

		// Ignore any call from an included servlet
		if (isIncluding())
		{
			return;
		}

		HttpHeaders.setDateHeader(response, name, new Date(date));
	}

	@Override
	// OK
	public void addDateHeader(String name, long date)
	{
		if (name == null || name.length() == 0)
		{
			return;
		}

		if (isCommitted())
		{
			return;
		}

		// Ignore any call from an included servlet
		if (isIncluding())
		{
			return;
		}

		HttpHeaders.addDateHeader(response, name, new Date(date));
	}

	/*
	 * public void setHeader(HttpHeader name, String value) { if (name == null
	 * || name.length() == 0 || value == null) { return; }
	 * 
	 * if (isCommitted()) { return; }
	 * 
	 * // Ignore any call from an included servlet if (isIncluding()) { return;
	 * }
	 * 
	 * char cc=name.charAt(0); if (cc=='C' || cc=='c') {
	 * 
	 * if (HttpHeaders.Names.CONTENT_TYPE.equalsIgnoreCase(name))
	 * setContentType(value); else { if (isIncluding()) return;
	 * 
	 * if (HttpHeaders.Names.CONTENT_LENGTH.equalsIgnoreCase(name)) { if (value
	 * == null) _contentLength = -1l; else _contentLength =
	 * Long.parseLong(value); } }
	 * 
	 * }
	 * 
	 * //what about setCookie?
	 * 
	 * HttpHeaders.setHeader(response, name, value); }
	 */

	@Override
	// OK
	public void setHeader(String name, String value)
	{
		if (name == null || name.length() == 0 || value == null)
		{
			return;
		}

		if (isCommitted())
		{
			return;
		}

		// Ignore any call from an included servlet
		if (isIncluding())
		{
			return;
		}

		char cc = name.charAt(0);
		if (cc == 'C' || cc == 'c')
		{

			if (HttpHeaders.Names.CONTENT_TYPE.equalsIgnoreCase(name))
				setContentType(value);
			else
			{
				if (HttpHeaders.Names.CONTENT_LENGTH.equalsIgnoreCase(name))
				{
					if (value == null)
						_contentLength = -1l;
					else
						_contentLength = Long.parseLong(value);
				}
			}

		}

		// what about setCookie?

		HttpHeaders.setHeader(response, name, value);
	}

	@Override
	// OK
	public Collection<String> getHeaderNames()
	{
		return headers.names();
	}

	@Override
	// OK
	public String getHeader(String name)
	{
		return headers.get(name);
	}

	@Override
	// OK
	public Collection<String> getHeaders(String name)
	{
		return headers.getAll(name);
	}

	@Override
	// ok
	public void addHeader(String name, String value)
	{
		if (name == null || name.length() == 0 || value == null)
		{
			return;
		}

		if (isCommitted())
		{
			return;
		}

		// Ignore any call from an included servlet
		if (isIncluding())
		{
			return;
		}

		char cc = name.charAt(0);
		if (cc == 'C' || cc == 'c')
		{

			if (HttpHeaders.Names.CONTENT_TYPE.equalsIgnoreCase(name))
				setContentType(value);
			else
			{
				if (HttpHeaders.Names.CONTENT_LENGTH.equalsIgnoreCase(name))
				{
					if (value == null)
						_contentLength = -1l;
					else
						_contentLength = Long.parseLong(value);
				}
			}
		}

		HttpHeaders.addHeader(response, name, value);
	}

	@Override
	// OK
	public void setIntHeader(String name, int value)
	{
		if (name == null || name.length() == 0)
		{
			return;
		}

		if (isCommitted())
		{
			return;
		}

		// Ignore any call from an included servlet
		if (isIncluding())
		{
			return;
		}

		char cc = name.charAt(0);
		if (cc == 'C' || cc == 'c')
		{
			if (HttpHeaders.Names.CONTENT_LENGTH.equalsIgnoreCase(name))
			{
				if (value < 0)
					_contentLength = -1l;
				else
					_contentLength = value;
			}
		}

		HttpHeaders.setHeader(response, name, value);
	}

	@Override
	// ok
	public void addIntHeader(String name, int value)
	{
		if (name == null || name.length() == 0)
		{
			return;
		}

		if (isCommitted())
		{
			return;
		}

		// Ignore any call from an included servlet
		if (isIncluding())
		{
			return;
		}

		char cc = name.charAt(0);
		if (cc == 'C' || cc == 'c')
		{
			if (HttpHeaders.Names.CONTENT_LENGTH.equalsIgnoreCase(name))
			{
				if (value < 0)
					_contentLength = -1l;
				else
					_contentLength = value;
			}
		}

		HttpHeaders.addHeader(response, name, value);
	}

	@Override
	// OK
	public void setStatus(int sc)
	{
		setStatus(sc, null);
	}

	@Override
	@Deprecated
	// ok
	public void setStatus(int sc, String sm)
	{
		if (isCommitted())
		{
			return;
		}

		// Ignore any call from an included servlet
		if (isIncluding())
		{
			return;
		}

		if (sc <= 0)
			throw new IllegalArgumentException();
		if (!isIncluding())
		{
			_status = sc;
			_reason = sm;
		}
	}

	@Override// ok
	public String getCharacterEncoding()
	{
		if (_characterEncoding == null)
		{
			/* get encoding from Content-Type header */
			if (_characterEncoding == null)
			{
				_characterEncoding = MimeTypes.inferCharsetFromContentType(getContentType());
				if (_characterEncoding == null)
					_characterEncoding = StringUtil.__ISO_8859_1;
			}
		}
			
		return _characterEncoding;
	}

	/**
	 * @see javax.servlet.ServletResponse.getContentType() for example,
	 *      text/html; charset=UTF-8, or null
	 */
	@Override	//OK
	public String getContentType()
	{
		return _contentType;
	}

	@Override// OK
	public ServletOutputStream getOutputStream() throws IOException
	{
		if (usingWriter)
		{
			throw new IllegalStateException("Already using Writer");
		}

		usingOutputStream = true;

		if (outputStream == null)
		{
			outputStream = new ByteBufServletOutputStream(this, body.data());
		}

		return outputStream;
	}
	
	private ServletOutputStream getOutputStreamWithoutCheck() throws IOException
	{
		if (outputStream == null)
		{
			outputStream = new ByteBufServletOutputStream(this, body.data());
		}

		return outputStream;
	}

	public boolean isWriting()
	{
		return usingWriter;
	}

	@Override
	public PrintWriter getWriter() throws IOException
	{
		if (usingOutputStream)
		{
			throw new IllegalStateException("Already using OutputStream");
		}

/*		
		 * If the response's character encoding has not been specified as
		 * described in <code>getCharacterEncoding</code> (i.e., the method just
		 * returns the default value <code>ISO-8859-1</code>),
		 * <code>getWriter</code> updates it to <code>ISO-8859-1</code> (with
		 * the effect that a subsequent call to getContentType() will include a
		 * charset=ISO-8859-1 component which will also be reflected in the
		 * Content-Type response header, thereby satisfying the Servlet spec
		 * requirement that containers must communicate the character encoding
		 * used for the servlet response's writer to the client).
		 
		setCharacterEncoding(getCharacterEncoding());*/

		
		if (_writer == null)
		{
			
			/* get encoding from Content-Type header */
			String encoding = _characterEncoding;
			if (encoding == null)
			{
				encoding = MimeTypes.inferCharsetFromContentType(_contentType);
				if (encoding == null)
					encoding = StringUtil.__ISO_8859_1;
				setCharacterEncoding(encoding);
			}

			
			if (StringUtil.__ISO_8859_1.equalsIgnoreCase(encoding))
				_writer = new ResponseWriter(new Iso88591HttpWriter(getOutputStreamWithoutCheck()),
						encoding);
			else if (StringUtil.__UTF8.equalsIgnoreCase(encoding))
				_writer = new ResponseWriter(new Utf8HttpWriter(getOutputStreamWithoutCheck()),
						encoding);
			else
				_writer = new ResponseWriter(new EncodingHttpWriter(getOutputStreamWithoutCheck(), encoding),encoding);

		}
		
		// Set the output type at the end, because setCharacterEncoding()
		// checks for it
		usingWriter = true;
		
		return _writer;
	}

	@Override
	// OK
	public void setContentLength(int len)
	{
		// Protect from setting after committed as default handling
		// of a servlet HEAD httpServletRequest ALWAYS sets _content length, even
		// if the getHandling committed the response!
		if (isCommitted() || isIncluding())
			return;

		_contentLength = len;
		headers.set(HttpHeaders.Names.CONTENT_LENGTH, len);
	}

	public long getLongContentLength()
	{
		return _contentLength;
	}

	public void setLongContentLength(long len)
	{
		// Protect from setting after committed as default handling
		// of a servlet HEAD httpServletRequest ALWAYS sets _content length, even
		// if the getHandling committed the response!
		if (isCommitted() || isIncluding())
			return;

		_contentLength = len;
		headers.set(HttpHeaders.Names.CONTENT_LENGTH.toString(), len);
	}

	@Override//OK
	public void setCharacterEncoding(String encoding)
	{
        if (encoding == null)
            return;
        
        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (isIncluding()) {
            return;
        }

        // Ignore any call made after the getWriter has been invoked
        // The default should be used
        if (usingWriter) {
            return;
        }

        _characterEncoding = encoding;
        
        isCharacterEncodingSet = true;
	}

	@Override
	// OK
	public void setContentType(String contentType)
	{
		if (isCommitted() || isIncluding())
			return;

		if (contentType == null) // clear the contentType
		{
			if (isWriting() && _characterEncoding != null)
				throw new IllegalSelectorException();

			if (_locale == null)
				_characterEncoding = null;
			_mimeType = null;
			_contentType = null;
			headers.remove(HttpHeaders.Names.CONTENT_TYPE);
		} else
		{
			_contentType = contentType;
			_mimeType = MimeTypes.CACHE.get(contentType);
			String charset;
			if (_mimeType != null && _mimeType.getCharset() != null)
				charset = _mimeType.getCharset().toString();
			else
				charset = MimeTypes.getCharsetFromContentType(contentType);

			if (charset == null)
			{
				if (_characterEncoding != null)
				{
					_contentType = contentType + ";charset="
							+ _characterEncoding;
					_mimeType = null;
				}
			} else if (isWriting() && !charset.equals(_characterEncoding))
			{
				// too late to change the character encoding;
				_mimeType = null;
				_contentType = MimeTypes
						.getContentTypeWithoutCharset(_contentType);
				if (_characterEncoding != null)
					_contentType = _contentType + ";charset="
							+ _characterEncoding;
			} else
			{
				_characterEncoding = charset;
			}

			headers.set(HttpHeaders.Names.CONTENT_TYPE, _contentType);
		}
	}

	/**
	 * Must be called before any response body content is written;
	 * 
	 */
	@Override
	// OK
	public void setBufferSize(int size)
	{
		if (isCommitted() || getContentCount() > 0)
			throw new IllegalStateException("Committed or content written");

		body.data().capacity(size);
	}

	@Override
	// OK
	public int getBufferSize()
	{
		return body.data().capacity();
	}

	/**
	 * Flush the buffer and commit this response.
	 * 
	 */
	@Override//OK
	public void flushBuffer() throws IOException
	{
		if (isCommitted()) // committed,need not to do again
			return;

		//we sure that getOutputStream() and getWriter() 不会缓存有数据,所有数据已经写到body里
		
		writeResponse(ctx, request, response);
		
		commited = true;
	}

	/**
	 * 向客户端返回响应
	 * @param ctx
	 * @param request
	 * @param response
	 */
	private void writeResponse(ChannelHandlerContext ctx,FullHttpRequest request, FullHttpResponse response)
	{
		boolean keepAlive = true;
		
		if(headers.get(HttpHeaders.Names.CONNECTION) != null)					
		{
			keepAlive = HttpHeaders.isKeepAlive(response);					//用户显式设置KeepAlive
		}
		else//用户未设置CONNECTION响应头
		{
			// Decide whether to close the connection or not.
			keepAlive = HttpHeaders.isKeepAlive(request);			//用户未设置CONNECTION,则保持未请求头的CONNECTION一致
	
			// TODO:think about how to decide keepAlive or not
			if (keepAlive)
			{
	            // Add 'Content-Length' header only for a keep-alive connection.
	            response.headers().set(CONTENT_LENGTH, response.data().readableBytes());
				// Add keep alive header as per:
				// http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
				response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
			}
			else
			{
				response.headers().set(CONTENT_LENGTH, response.data().readableBytes());
				 
				response.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
			}
		}

		// Write the response.
		ctx.nextOutboundMessageBuffer().add(response);


		//if CONNECTION == "close" Close the non-keep-alive connection after the write operation is done.
		if (keepAlive)
		{
			ctx.flush();
		}
		else
		{
			ctx.flush().addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	// OK
	public void reset()
	{
		if (isIncluding())
		{
			return; // Ignore any call from an included servlet
		}

		if (isCommitted())
			throw new IllegalStateException("Committed");

		resetHeader();
		resetBuffer();
		usingOutputStream = false;
		usingWriter = false;

	}

	/*
	 * public void reset(boolean preserveCookies) { if (!preserveCookies)
	 * reset(); else { ArrayList<String> cookieValues = new
	 * ArrayList<String>(5); Enumeration<String> vals =
	 * headers.getValues(HttpHeader.SET_COOKIE.asString()); while
	 * (vals.hasMoreElements()) cookieValues.add(vals.nextElement()); reset();
	 * for (String v:cookieValues) headers.add(HttpHeader.SET_COOKIE, v); } }
	 */

	/*
	 * public void resetForForward() { resetBuffer(); usingOutputStream = false;
	 * usingWriter = false; }
	 */

	// ok
	/**
     * 
     */
	public void resetHeader()
	{
		if (isIncluding())
		{
			return; // Ignore any call from an included servlet
		}

		if (isCommitted())
			throw new IllegalStateException("Committed");

		// Reset the headers only if this is the main httpServletRequest,
		// not for included
		_contentType = null;
		_locale = DEFAULT_LOCALE;

		_characterEncoding = "ISO-8859-1";//Constants.DEFAULT_CHARACTER_ENCODING;

		_contentLength = -1;

		_status = 200;
		_reason = null;

		headers.clear();
	}

	@Override
	// OK
	public void resetBuffer()
	{
		if (isCommitted())
			throw new IllegalStateException("Committed");

		body.data().clear();
	}

	/*
	 * protected ResponseInfo newResponseInfo() { if (_status ==
	 * HttpStatus.NOT_SET_000) _status = HttpStatus.OK_200; return new
	 * ResponseInfo(socketChannel.getRequest().getHttpVersion(), headers,
	 * getLongContentLength(), getStatus(), getReason(),
	 * socketChannel.getRequest().isHead()); }
	 */

	@Override
	public boolean isCommitted()
	{
		return commited;
	}

	@Override
	// OK WITH TODO
	public void setLocale(Locale locale)
	{
		if (locale == null || isCommitted())
		{
			return;
		}

		// Ignore any call from an included servlet
		if (isIncluding())
		{
			return;
		}

		// Save the locale for use by getLocale()
		_locale = locale;

		// Set the contentLanguage for header output
		contentLanguage = locale.getLanguage();
		if ((contentLanguage != null) && (contentLanguage.length() > 0))
		{
			String country = locale.getCountry();
			StringBuilder value = new StringBuilder(contentLanguage);
			if ((country != null) && (country.length() > 0))
			{
				value.append('-');
				value.append(country);
			}
			contentLanguage = value.toString();
		}

		// Ignore any call made after the getWriter has been invoked.
		// The default should be used
		if (usingWriter)
		{
			return;
		}

		if (isCharacterEncodingSet)
		{
			return;
		}

		// :TODO
		/*
		 * String charset = servletContext.getCharset(locale); if (charset !=
		 * null) { coyoteResponse.setCharacterEncoding(charset); }
		 */
	}

	@Override
	public Locale getLocale()
	{
		if (_locale == null)
			return Locale.getDefault();
		return _locale;
	}

	@Override
	// ok
	public int getStatus()
	{
		return _status;
	}

	public String getReason()
	{
		return _reason;
	}

	public void complete() throws IOException
	{
	}

	public long getContentCount()
	{
		return body.data().writerIndex();
	}

	@Override
	// OK
	public String toString()
	{
		return String.format("%s %d %s%n%s", request.getProtocolVersion(),
				_status, _reason == null ? "" : _reason, headers);
	}

	private  class ResponseWriter extends PrintWriter
	{
		private final String _encoding;
		private final HttpWriter _httpWriter;

		public ResponseWriter(HttpWriter httpWriter, String encoding)
		{
			super(httpWriter);
			_httpWriter = httpWriter;
			_encoding = encoding;
		}

		public boolean isFor(String encoding)
		{
			return _encoding.equalsIgnoreCase(encoding);
		}

		protected void reopen()
		{
			super.clearError();
			out = _httpWriter;
		}
	}

	/**
	 * Returns a channel where the I/O operation associated with this response
	 * takes place.
	 */
	public Channel channel()
	{
		return this.socketChannel;
	}

	/**
	 * invoke before forward
	 */
	public void resetForForward()
	{
        resetBuffer();
        usingOutputStream = false;
        usingWriter = false;
	}
}
