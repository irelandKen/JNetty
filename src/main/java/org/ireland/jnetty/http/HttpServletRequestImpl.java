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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import java.security.Principal;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.ServletResponse;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;

import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.UrlEncoded;

import org.ireland.jnetty.dispatch.Invocation;
import org.ireland.jnetty.http.io.ByteBufServletInputStream;
import org.ireland.jnetty.server.session.HttpSessionImpl;
import org.ireland.jnetty.server.session.SessionManager;
import org.ireland.jnetty.util.StringParser;
import org.ireland.jnetty.util.http.ContentTypeUtil;
import org.ireland.jnetty.webapp.WebApp;


/* ------------------------------------------------------------ */
/**
 * Jetty HttpServletRequestImpl.
 * <p>
 * Implements {@link javax.servlet.http.HttpServletRequest} from the <code>javax.servlet.http</code> package.
 * </p>
 * <p>
 * request object to be as lightweight as possible and not actually implement any significant behavior. For example
 * <ul>
 * 
 * <li>The {@link HttpServletRequestImpl#getContextPath()} method will return null, until the request has been passed to
 * a {@link ContextHandler} which matches the {@link HttpServletRequestImpl#getPathInfo()} with a context path and calls
 * {@link HttpServletRequestImpl#setContextPath(String)} as a result.</li>
 * 
 * <li>the HTTP session methods will all return null sessions until such time as a request has been passed to a
 * {@link org.eclipse.jetty.server.session.SessionHandler} which checks for session cookies and enables the ability to
 * create new sessions.</li>
 * 
 * <li>The {@link HttpServletRequestImpl#getServletPath()} method will return null until the request has been passed to
 * a <code>org.eclipse.jetty.servlet.ServletHandler</code> and the pathInfo matched against the servlet URL patterns and
 * {@link HttpServletRequestImpl#setServletPath(String)} called as a result.</li>
 * </ul>
 * 
 * A request instance is created for each connection accepted by the server and recycled for each HTTP request received
 * via that connection. An effort is made to avoid reparsing headers and cookies that are likely to be the same for
 * requests from the same connection.
 * 
 * <p>
 * The form content that a request can process is limited to protect from Denial of Service attacks. The size in bytes
 * is limited by {@link ContextHandler#getMaxFormContentSize()} or if there is no context then the
 * "org.eclipse.jetty.server.Request.maxFormContentSize" {@link Server} attribute. The number of parameters keys is
 * limited by {@link ContextHandler#getMaxFormKeys()} or if there is no context then the
 * "org.eclipse.jetty.server.Request.maxFormKeys" {@link Server} attribute.
 * 
 * 
 */
public class HttpServletRequestImpl implements HttpServletRequest
{

	/**
	 * Logger available to subclasses.
	 */
	protected static final Log LOG = LogFactory.getLog(HttpServletRequestImpl.class);

	/**
	 * The default Locale if none are specified.
	 */
	protected static final Locale defaultLocale = Locale.getDefault();

	private static final Collection<Locale> __defaultLocale = Collections.singleton(Locale.getDefault());

	// Util

	/**
	 * The string parser we will use for parsing request lines.
	 */
	private StringParser parser;
	//

	private final ServletContext servletContext;
	
	//the Invocation of this Request()
	private Invocation _invocation;

	// Netty
	private final SocketChannel socketChannel;

	private final ChannelHandlerContext ctx;

	private final FullHttpResponse response;

	private final HttpServletResponseImpl _httpResponse;

	// request
	private final FullHttpRequest request;

	// request-header
	private final HttpHeaders headers;

	// request-body
	private final HttpContent body;

	// the content of HttpServletRequestImpl Body,as byte array. normaly,please use HttpContent body
	private byte[] bodyContent;

	// Netty<<

	/**
	 * ServletInputStream.
	 */
	protected ServletInputStream inputStream;

	/**
	 * @see javax.servlet.ServletRequest#getInputStream() Using body as a stream flag.
	 *      将请求体用作InputStream使用的标志,不能同时将请求体作为Reader使用
	 */
	protected boolean usingInputStream = false;

	/**
	 * @see javax.servlet.ServletRequest#getReader() Using body as reader flag.
	 *      将请求体用作Reader使用的标志,不能同时将请求体作为InputStream使用
	 */
	protected boolean usingReader = false;

	private List<ServletRequestAttributeListener> _requestAttributeListeners;

	private boolean _secure;
	private boolean _asyncSupported = true;
	private boolean _newContext;
	private boolean _cookiesExtracted = false;
	private boolean _handled = false;
	private boolean _paramsExtracted;

	/**
	 * The attributes associated with this HttpServletRequestImpl, keyed by attribute name.
	 */
	protected Map<String, Object> _attributes;

	private String _contentType;
	private String _characterEncoding;

	/**
	 * Cookies parsed flag.
	 */
	protected boolean cookiesParsed = false;

	// rawUri = contextPath + servletPath + pathInfo +?+ queryString

	// 如: /myweb
	private String _contextPath = "";

	// 如: /myservlet
	private String _servletPath;

	// 如: /page.do 或 null
	private String _pathInfo = null;

	// 如: name=jack&pwd=123
	private String _queryString;

	/**
	 * Request QueryString Extracted flag.
	 */
	private boolean _queryStringExtracted;

	private DispatcherType _dispatcherType;

	private HttpMethod _httpMethod;

	// Parameters from query string and form HttpServletRequestImpl Body(application/x-www-form-urlencoded [POST |PUT])
	private Map<String, List<String>> _parameters;

	private int _port;
	private HttpVersion _httpVersion = HttpVersion.HTTP_1_1;
	private String _queryEncoding;

	private BufferedReader _reader;

	private String _readerEncoding;

	private InetSocketAddress _remote;

	private String _requestedSessionId;

	private String _requestURI;

	private String _scheme = "http";

	private String _serverName;

	private HttpSessionImpl _session;
	private SessionManager _sessionManager;
	
	//true: has try to Extracte the sessionid
	private boolean _sessionIdExtracted;
	
	private boolean _isSessionIdFromCookie;

	private long _timeStamp;
	private long _dispatchTime;

	/**
	 * The set of cookies associated with this HttpServletRequestImpl.
	 */
	protected Cookie[] cookies = null;

	/**
	 * The preferred Locales associated with this HttpServletRequestImpl.
	 */
	protected List<Locale> _locales;

	/**
	 * Parse _locales.
	 */
	protected boolean localesParsed = false;

	/* ------------------------------------------------------------ */
	public HttpServletRequestImpl(WebApp webApp, ServletContext servletContext, SocketChannel socketChannel, ChannelHandlerContext ctx,
			FullHttpResponse response, FullHttpRequest request, HttpServletResponseImpl httpResponse)
	{
		this.servletContext = servletContext;

		this._sessionManager = webApp.getSessionManager();

		this.socketChannel = socketChannel;
		this.ctx = ctx;
		this.response = response;
		this.request = request;

		_httpResponse = httpResponse;

		this.headers = request.headers();
		this.body = request;

	}

	/* ------------------------------------------------------------ */
	public void addEventListener(final EventListener listener)
	{
		if (listener instanceof ServletRequestAttributeListener)
		{
			if (_requestAttributeListeners == null)
				_requestAttributeListeners = new ArrayList<ServletRequestAttributeListener>();

			_requestAttributeListeners.add((ServletRequestAttributeListener) listener);
		}

		if (listener instanceof AsyncListener)
			throw new IllegalArgumentException(listener.getClass().toString());
	}

	/* ------------------------------------------------------------ */
	/**
	 * Extract Parameters from query string and form HttpServletRequestImpl Body(application/x-www-form-urlencoded [POST
	 * | PUT])
	 */
	public void extractParameters()
	{

		if (_paramsExtracted)
			return;

		_paramsExtracted = true;

		if(_parameters == null)
			_parameters = new HashMap<String, List<String>>();
		
		// Handle query string

		if (_queryEncoding == null)
		{
			QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
			
			_parameters.putAll(queryStringDecoder.parameters());

		}
		else
		{
			try
			{
				QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri(), Charset.forName(_queryEncoding));

				_parameters.putAll(queryStringDecoder.parameters());

			}
			catch (UnsupportedCharsetException e)
			{
				if (LOG.isDebugEnabled())
					LOG.warn(e);
				else
					LOG.warn(e.toString());
			}
		}

		// handle form _content (application/x-www-form-urlencoded)
		String encoding = getCharacterEncoding();
		String content_type = getContentType();

		if (content_type != null && content_type.length() > 0)
		{
			content_type = ContentTypeUtil.getContentTypeWithoutCharset(content_type);

			// application/x-www-form-urlencoded( POST or PUT )
			if (HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED.equals(content_type)
					&& (HttpMethod.POST.name().equals(getMethod()) || HttpMethod.PUT.name().equals(getMethod())))
			{
				int content_length = getContentLength();
				if (content_length > 0)
				{
					try
					{
						Charset bodyCharset = Charset.forName(encoding);
						String bodyContent = new String(getRowBodyContent(), bodyCharset);

						// Add form params to query params
						QueryStringDecoder queryStringDecoder = new QueryStringDecoder(bodyContent, bodyCharset,false);

						if (_parameters == null)
							_parameters = queryStringDecoder.parameters();
						else//merge
						{
							Map<String, List<String>> map = queryStringDecoder.parameters();

							for (Entry<String, List<String>> e : map.entrySet())
							{
								if (!_parameters.containsKey(e.getKey()))
								{
									_parameters.put(e.getKey(), e.getValue());
								}
								else// parameter with the same name exist,merge
								{
									List<String> value = _parameters.get(e.getKey());

									if (value == null)
										_parameters.put(e.getKey(), e.getValue());
									else
									{
										value.addAll(e.getValue()); // merge
										_parameters.put(e.getKey(), value);
									}
								}
							}
						}
					}
					catch (Exception e)
					{
						if (LOG.isDebugEnabled())
							e.printStackTrace();
						else
							LOG.warn(e.toString());
					}
				}
			}
		}
	}

	/**
	 * 
	 * @return the content of HttpServletRequestImpl Body,as byte array. this method will not change the read / write
	 *         index of body
	 */
	private byte[] getRowBodyContent()
	{
		// get [0,writerIndex) of body.date();
		if (bodyContent == null)
		{
			int content_length = body.data().writerIndex(); // [0,writerIndex)

			int old_readerIndex = body.data().readerIndex();// resver the old_readrIndex

			byte[] data = new byte[content_length];

			body.data().readerIndex(0);

			body.data().readBytes(data, 0, content_length);

			body.data().readerIndex(old_readerIndex); // recover the readerIndex
			
			bodyContent = data;
		}

		return bodyContent;
	}

	/* ------------------------------------------------------------ */
	@Override
	public AsyncContext getAsyncContext()
	{
		/*
		 * HttpChannelState continuation = getHttpChannelState(); if (continuation.isInitial() &&
		 * !continuation.isAsync()) throw new IllegalStateException(continuation.getStatusString()); return
		 * continuation;
		 */
		// throw new UnsupportedOperationException();
		return null;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getAttribute(java.lang.String)
	 */
	@Override
	// OK
	public Object getAttribute(String name)
	{
		return (_attributes == null) ? null : _attributes.get(name);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getAttributeNames()
	 */
	@Override
	// OK
	public Enumeration<String> getAttributeNames()
	{
		// Take a copy to prevent ConncurrentModificationExceptions if used to
		// remove attributes
		Set<String> names = new HashSet<String>();
		names.addAll(_attributes.keySet());
		return Collections.enumeration(names);
	}

	/* ------------------------------------------------------------ */
	/*
     */
	public Map<String, Object> getAttributes()
	{
		if (_attributes == null)
			_attributes = new HashMap();
		return _attributes;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getAuthType()
	 */
	@Override
	public String getAuthType()
	{
		return null;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getCharacterEncoding()
	 */
	@Override
	// OK
	public String getCharacterEncoding()
	{
		if (_characterEncoding == null)
		{
			_characterEncoding = ContentTypeUtil.getCharsetFromContentType(getContentType());
		}

		return _characterEncoding;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @return Returns the connection.
	 */
	public SocketChannel getHttpChannel()
	{
		return socketChannel;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getContentLength()
	 */
	@Override
	// OK
	public int getContentLength()
	{
		long length = HttpHeaders.getContentLength(request, -1);

		return (int) ((length <= Integer.MAX_VALUE) ? length : -1);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getContentType()
	 * 
	 * example: "text/css;charset=UTF-8"
	 */
	@Override
	// OK
	public String getContentType()
	{
		if (_contentType == null)
			_contentType = getHeader(HttpHeaders.Names.CONTENT_TYPE);

		return _contentType;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getContextPath()
	 */
	@Override
	public String getContextPath()
	{
		if (servletContext == null)
			return "";

		return servletContext.getContextPath();
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getCookies()
	 */
	@Override
	// OK
	public Cookie[] getCookies()
	{
		if (!_cookiesExtracted)
			extracteCookie();

		return cookies;
	}

	/**
	 * Extracte cookies.
	 */
	protected void extracteCookie()
	{
		_cookiesExtracted = true;

		// Decode the cookie.
		String cookieString = headers.get(HttpHeaders.Names.COOKIE);
		if (cookieString != null)
		{
			Set<io.netty.handler.codec.http.Cookie> _cookies = CookieDecoder.decode(cookieString);

			this.cookies = new Cookie[_cookies.size()];

			int i = 0;

			// Convent netty's Cookie to Servlet's Cookie
			for (io.netty.handler.codec.http.Cookie c : _cookies)
			{
				Cookie cookie = new Cookie(c.getName(), c.getValue());

				cookie.setComment(c.getComment());
				
				if(c.getDomain() != null)
					cookie.setDomain(c.getDomain());
				
				cookie.setHttpOnly(c.isHttpOnly());
				cookie.setMaxAge((int) c.getMaxAge());
				cookie.setPath(c.getPath());
				cookie.setSecure(c.isSecure());
				cookie.setVersion(c.getVersion());

				this.cookies[i] = cookie;
				i++;
			}
		}
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getDateHeader(java.lang.String)
	 */
	@Override
	// OK
	public long getDateHeader(String name)
	{
		Date date = HttpHeaders.getDateHeader(request, name, null);

		return (date == null) ? -1 : date.getTime();
	}

	/* ------------------------------------------------------------ */
	@Override
	// OK
	public DispatcherType getDispatcherType()
	{
		return _dispatcherType;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getHeader(java.lang.String)
	 */
	@Override
	// ok
	public String getHeader(String name)
	{
		return headers.get(name);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getHeaderNames()
	 */
	@Override
	// OK
	public Enumeration<String> getHeaderNames()
	{
		return Collections.enumeration(headers.names());
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getHeaders(java.lang.String)
	 */
	@Override
	// OK
	public Enumeration<String> getHeaders(String name)
	{
		List<String> e = headers.getAll(name);
		if (e == null)
			return Collections.enumeration(Collections.<String> emptyList());

		return Collections.enumeration(e);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getInputStream()
	 */
	@Override
	// OK
	public ServletInputStream getInputStream() throws IOException
	{
		if (usingReader)
		{
			throw new IllegalStateException("Already using Reader");
		}

		usingInputStream = true;
		if (inputStream == null)
		{
			inputStream = new ByteBufServletInputStream(body.data());
		}
		return inputStream;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getIntHeader(java.lang.String)
	 */
	@Override
	// OK
	public int getIntHeader(String name)
	{
		return HttpHeaders.getIntHeader(request, name, -1);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getLocale()
	 */
	@Override
	public Locale getLocale()
	{

		if (!localesParsed)
		{
			parseLocales();
		}

		if (_locales.size() > 0)
		{
			return _locales.get(0);
		}

		return defaultLocale;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getLocales()
	 */
	@Override
	public Enumeration<Locale> getLocales()
	{

		if (!localesParsed)
		{
			parseLocales();
		}

		if (_locales.size() > 0)
		{
			return Collections.enumeration(_locales);
		}
		List<Locale> results = new LinkedList<Locale>();
		results.add(defaultLocale);
		return Collections.enumeration(results);

	}

	/**
	 * Parse request _locales.
	 */
	protected void parseLocales()
	{

		localesParsed = true;

		Enumeration<String> values = getHeaders(HttpHeaders.Names.ACCEPT_LANGUAGE);

		while (values.hasMoreElements())
		{
			String value = values.nextElement();
			parseLocalesHeader(value);
		}

	}

	/**
	 * Parse accept-language header value.
	 */
	protected void parseLocalesHeader(String value)
	{
		if (parser == null)
			parser = new StringParser();

		// Store the accumulated languages that have been requested in
		// a local collection, sorted by the quality value (so we can
		// add Locales in descending order). The values will be ArrayLists
		// containing the corresponding Locales to be added
		TreeMap<Double, ArrayList<Locale>> locales = new TreeMap<Double, ArrayList<Locale>>();

		// Preprocess the value to remove all whitespace
		int white = value.indexOf(' ');
		if (white < 0)
		{
			white = value.indexOf('\t');
		}
		if (white >= 0)
		{
			StringBuilder sb = new StringBuilder();
			int len = value.length();
			for (int i = 0; i < len; i++)
			{
				char ch = value.charAt(i);
				if ((ch != ' ') && (ch != '\t'))
				{
					sb.append(ch);
				}
			}
			parser.setString(sb.toString());
		}
		else
		{
			parser.setString(value);
		}

		// Process each comma-delimited language specification
		int length = parser.getLength();
		while (true)
		{

			// Extract the next comma-delimited entry
			int start = parser.getIndex();
			if (start >= length)
			{
				break;
			}
			int end = parser.findChar(',');
			String entry = parser.extract(start, end).trim();
			parser.advance(); // For the following entry

			// Extract the quality factor for this entry
			double quality = 1.0;
			int semi = entry.indexOf(";q=");
			if (semi >= 0)
			{
				try
				{
					String strQuality = entry.substring(semi + 3);
					if (strQuality.length() <= 5)
					{
						quality = Double.parseDouble(strQuality);
					}
					else
					{
						quality = 0.0;
					}
				}
				catch (NumberFormatException e)
				{
					quality = 0.0;
				}
				entry = entry.substring(0, semi);
			}

			// Skip entries we are not going to keep track of
			if (quality < 0.00005)
			{
				continue; // Zero (or effectively zero) quality factors
			}
			if ("*".equals(entry))
			{
				continue; // FIXME - "*" entries are not handled
			}

			// Extract the language and country for this entry
			String language = null;
			String country = null;
			String variant = null;
			int dash = entry.indexOf('-');
			if (dash < 0)
			{
				language = entry;
				country = "";
				variant = "";
			}
			else
			{
				language = entry.substring(0, dash);
				country = entry.substring(dash + 1);
				int vDash = country.indexOf('-');
				if (vDash > 0)
				{
					String cTemp = country.substring(0, vDash);
					variant = country.substring(vDash + 1);
					country = cTemp;
				}
				else
				{
					variant = "";
				}
			}
			if (!isAlpha(language) || !isAlpha(country) || !isAlpha(variant))
			{
				continue;
			}

			// Add a new Locale to the list of Locales for this quality level
			Locale locale = new Locale(language, country, variant);
			Double key = new Double(-quality); // Reverse the order
			ArrayList<Locale> values = locales.get(key);
			if (values == null)
			{
				values = new ArrayList<Locale>();
				locales.put(key, values);
			}
			values.add(locale);

		}

		// Process the quality values in highest->lowest order (due to
		// negating the Double value when creating the key)
		for (ArrayList<Locale> list : locales.values())
		{
			for (Locale locale : list)
			{
				addLocale(locale);
			}
		}

	}

	protected static final boolean isAlpha(String value)
	{
		for (int i = 0; i < value.length(); i++)
		{
			char c = value.charAt(i);
			if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Add a Locale to the set of preferred Locales for this HttpServletRequestImpl. The first added Locale will be the
	 * first one returned by getLocales().
	 * 
	 * @param locale
	 *            The new preferred Locale
	 */
	public void addLocale(Locale locale)
	{
		if (_locales == null)
			_locales = new LinkedList<Locale>();
		_locales.add(locale);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getLocalAddr()
	 */
	@Override
	// OK
	public String getLocalAddr()
	{
		InetSocketAddress local = socketChannel.localAddress();
		if (local == null)
			return "";
		InetAddress address = local.getAddress();
		if (address == null)
			return local.getHostString();
		return address.getHostAddress();
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getLocalName()
	 */
	@Override
	// ok
	public String getLocalName()
	{
		InetSocketAddress local = socketChannel.localAddress();
		return local.getHostString();
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getLocalPort()
	 */
	@Override
	// OK
	public int getLocalPort()
	{
		InetSocketAddress local = socketChannel.localAddress();
		return local.getPort();
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getMethod()
	 */
	@Override
	// OK
	public String getMethod()
	{
		return request.getMethod().toString();
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getParameter(java.lang.String)
	 */
	@Override
	// OK
	public String getParameter(String name)
	{
		if (!_paramsExtracted)
			extractParameters();
		List<String> list = _parameters.get(name);

		if (list == null || list.isEmpty())
			return null;

		return list.get(0);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getParameterMap()
	 */
	@Override
	// OK
	public Map<String, String[]> getParameterMap()
	{
		if (!_paramsExtracted)
			extractParameters();

		HashMap<String, String[]> map = new HashMap<String, String[]>(_parameters.size() * 3 / 2);

		for (Map.Entry<String, List<String>> entry : _parameters.entrySet())
		{
			String[] a = null;
			if (entry.getValue() != null)
			{
				a = new String[entry.getValue().size()];
				a = entry.getValue().toArray(a);
			}
			map.put(entry.getKey(), a);
		}

		return Collections.unmodifiableMap(map);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getParameterNames()
	 */
	@Override
	// OK
	public Enumeration<String> getParameterNames()
	{
		if (!_paramsExtracted)
			extractParameters();
		return Collections.enumeration(_parameters.keySet());
	}

	/* ------------------------------------------------------------ */
	/**
	 * @return Returns the parameters.
	 */
	/*
	 * public MultiMap<String> getParameters() { return _parameters; }
	 */

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getParameterValues(java.lang.String)
	 */
	@Override
	// ok
	public String[] getParameterValues(String name)
	{
		if (!_paramsExtracted)
			extractParameters();
		List<String> vals = _parameters.get(name);
		if (vals == null)
			return null;
		return vals.toArray(new String[vals.size()]);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getPathInfo()
	 */
	@Override
	public String getPathInfo()
	{
	    if (_invocation != null)
	        return _invocation.getPathInfo();
	      else
	        return null;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getPathTranslated()
	 */
	@Override
	public String getPathTranslated()
	{
		if (_pathInfo == null || servletContext == null)
			return null;
		return servletContext.getRealPath(_pathInfo);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getProtocol()
	 */
	@Override
	// OK
	public String getProtocol()
	{
		return _httpVersion.toString();
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getProtocol()
	 */
	public HttpVersion getHttpVersion()
	{
		return _httpVersion;
	}

	/* ------------------------------------------------------------ */
	public String getQueryEncoding()
	{
		return _queryEncoding;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getQueryString()
	 */
	@Override
	public String getQueryString()
	{
	    if (_invocation != null)
	        return _invocation.getQueryString();
	      else
	        return null;
	}

	/**
	 * 从原生的uri中分离出不带参数的RequestURI和参数QueryString
	 * 
	 * @return
	 */
	boolean parseRequestURIAndQueryString()
	{
		if (_queryString == null || _requestURI == null)
		{
			if (_queryEncoding == null)
			{
				String uri = request.getUri();

				int p = uri.indexOf('?');

				if (p != -1)
					_queryString = uri.substring(p + 1);

				// 同时也设置requestURI
				if (p == -1)
					_requestURI = uri;
				else
					_requestURI = uri.substring(0, p);
			}
			else
			{
				// TODO: what about other queryEncoding?
			}
		}

		_queryStringExtracted = true;

		return true;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getReader()
	 */
	@Override
	// ok
	public BufferedReader getReader() throws IOException
	{
		if (usingInputStream)
		{
			throw new IllegalStateException("using InputStream already");
		}

		usingReader = true;

		String encoding = getCharacterEncoding();

		if (encoding == null)
			encoding = "ISO-8859-1";

		if (_reader == null || !encoding.equalsIgnoreCase(_readerEncoding))
		{
			final ServletInputStream in = getInputStream();
			_readerEncoding = encoding;
			_reader = new BufferedReader(new InputStreamReader(in, encoding))
			{
				@Override
				public void close() throws IOException
				{
					in.close();
				}
			};
		}

		return _reader;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getRealPath(java.lang.String)
	 */
	@Deprecated
	@Override
	public String getRealPath(String path)
	{
		if (servletContext == null)
			return null;
		return servletContext.getRealPath(path);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getRemoteAddr()
	 */
	@Override
	// OK
	public String getRemoteAddr()
	{
		InetSocketAddress remote = _remote;
		if (remote == null)
			remote = socketChannel.remoteAddress();

		if (remote == null)
			return "";

		InetAddress address = remote.getAddress();
		if (address == null)
			return remote.getHostString();

		return address.getHostAddress();
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getRemoteHost()
	 */
	@Override
	// OK
	public String getRemoteHost()
	{
		InetSocketAddress remote = _remote;
		if (remote == null)
			remote = socketChannel.remoteAddress();
		return remote == null ? "" : remote.getHostString();
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getRemotePort()
	 */
	@Override
	// OK
	public int getRemotePort()
	{
		InetSocketAddress remote = _remote;

		if (remote == null)
			remote = socketChannel.remoteAddress();

		return remote == null ? 0 : remote.getPort();
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getRemoteUser()
	 */
	@Override
	// ok
	public String getRemoteUser()
	{
		Principal p = getUserPrincipal();
		if (p == null)
			return null;
		return p.getName();
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getRequestDispatcher(java.lang.String)
	 */
	@Override
	public RequestDispatcher getRequestDispatcher(String path)
	{
		if (path == null || servletContext == null)
			return null;

		// handle relative path
		if (!path.startsWith("/"))
		{
			String relTo = URIUtil.addPaths(_servletPath, _pathInfo);
			int slash = relTo.lastIndexOf("/");
			if (slash > 1)
				relTo = relTo.substring(0, slash + 1);
			else
				relTo = "/";
			path = URIUtil.addPaths(relTo, path);
		}

		return servletContext.getRequestDispatcher(path);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getRequestedSessionId()
	 */
	@Override
	public String getRequestedSessionId()
	{
		return _requestedSessionId;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getRequestURI()
	 */
	@Override
	public String getRequestURI()
	{
	    if (_invocation != null)
	        return _invocation.getRawURI();
	      else
	        return "";
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getRequestURL()
	 */
	@Override
	public StringBuffer getRequestURL()
	{
		final StringBuffer url = new StringBuffer(48);
		String scheme = getScheme();
		int port = getServerPort();

		url.append(scheme);
		url.append("://");
		url.append(getServerName());
		if (_port > 0 && ((scheme.equalsIgnoreCase(URIUtil.HTTP) && port != 80) || (scheme.equalsIgnoreCase(URIUtil.HTTPS) && port != 443)))
		{
			url.append(':');
			url.append(_port);
		}

		url.append(getRequestURI());
		return url;
	}

	/* ------------------------------------------------------------ */
	/**
	 * Reconstructs the URL the client used to make the request. The returned URL contains a protocol, server name, port
	 * number, and, but it does not include a path.
	 * <p>
	 * Because this method returns a <code>StringBuffer</code>, not a string, you can modify the URL easily, for
	 * example, to append path and query parameters.
	 * 
	 * This method is useful for creating redirect messages and for reporting errors.
	 * 
	 * @return "scheme://host:port"
	 */
	public StringBuilder getRootURL()
	{
		StringBuilder url = new StringBuilder(48);
		String scheme = getScheme();
		int port = getServerPort();

		url.append(scheme);
		url.append("://");
		url.append(getServerName());

		if (port > 0 && ((scheme.equalsIgnoreCase("http") && port != 80) || (scheme.equalsIgnoreCase("https") && port != 443)))
		{
			url.append(':');
			url.append(port);
		}
		return url;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getScheme()
	 */
	@Override
	public String getScheme()
	{
		return _scheme;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getServerName()
	 */
	@Override
	// OK
	public String getServerName()
	{
		if (_serverName == null)
		{
			parseServerNameAndServerPort();
		}

		return _serverName;
	}

	/**
	 * 1:Return host from header field "Host"
	 * 
	 * OR:2:Return host from connection
	 * 
	 * 3:Return the local host
	 */
	private void parseServerNameAndServerPort()
	{
		// Return host from header field
		String hostPort = getHeader(HttpHeaders.Names.HOST);
		if (hostPort != null)
		{
			loop: for (int i = hostPort.length(); i-- > 0;)
			{
				char ch = (char) (0xff & hostPort.charAt(i));
				switch (ch)
				{
				case ']':
					break loop;

				case ':':
					_serverName = hostPort.substring(0, i);
					try
					{
						_port = Integer.parseInt(hostPort.substring(i + 1));
					}
					catch (NumberFormatException e)
					{
						LOG.warn(e);
					}
					return;
				}
			}

			if (_serverName == null || _port < 0)
			{
				_serverName = hostPort;
				_port = 0;
			}

			return;
		}

		// Return host from connection
		if (socketChannel != null)
		{
			_serverName = getLocalName();
			_port = getLocalPort();
			if (_serverName != null && !"0.0.0.0".equals(_serverName))
				return;
		}

		// Return the local host
		try
		{
			_serverName = InetAddress.getLocalHost().getHostAddress();
		}
		catch (java.net.UnknownHostException e)
		{
			LOG.debug(e);
		}
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getServerPort()
	 */
	@Override
	public int getServerPort()
	{
		if (_port <= 0)
		{
			if (_serverName == null)
				parseServerNameAndServerPort();

			if (_port <= 0)
			{
				InetSocketAddress local = socketChannel.localAddress();
				_port = local == null ? 0 : local.getPort();
			}
		}

		if (_port <= 0)
		{
			if (getScheme().equalsIgnoreCase("https"))
				return 443;
			return 80;
		}
		return _port;
	}

	/* ------------------------------------------------------------ */
	@Override
	// ok
	public ServletContext getServletContext()
	{
		return servletContext;
	}

	/* ------------------------------------------------------------ */
	/*
     */
	/*
	 * public String getServletName() { if (_scope != null) return _scope.getName(); return null; }
	 */
	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getServletPath()
	 */
	@Override
	public String getServletPath()
	{
	    if (_invocation != null)
	        return _invocation.getServletPath();
	      else
	        return "";
	}

	/* ------------------------------------------------------------ */

	/**
	 * 从请求的cookie或URL里提取SessionId
	 * @return
	 */
	protected String extracteSessionId()
	{
		String sessionId = findSessionIdFromCookie();
	
		_sessionIdExtracted = true;
		return sessionId;
	}
	

	/**
	 * Returns the session id in the HTTP request cookies. Because the webApp might use the cookie to change the page
	 * contents, the caching sets vary: JSESSIONID.
	 */
	protected String findSessionIdFromCookie()
	{
		Cookie cookie = getCookie("JSESSIONID");

		if (cookie != null)
		{
			_isSessionIdFromCookie = true;
			return cookie.getValue();
		}
		else
			return null;
	}

	/**
	 * 查找特定名称的cookie
	 * Returns the named cookie from the browser
	 */
	public Cookie getCookie(String name)
	{
		Cookie[] cookies = getCookies();

		if (cookies == null)
			return null;

		for (Cookie cookie : cookies)
		{
			if (cookie.getName().equals(name))
			{
				return cookie;
			}
		}

		return null;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getSession()
	 */
	@Override
	public HttpSession getSession()
	{
		return getSession(true);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getSession(boolean)
	 */
	@Override
	public HttpSession getSession(boolean create)
	{
		if (_session != null)
		{
			if (_sessionManager.isValid(_session))
				return _session;
			else
			// Session无效
			{
				_session.invalidate();
				_session = null;
			}
		}
		
		
		//如果未从COOKIE或URL中解释SESSIONID,则try to extract sessionId from cookie OR URL
		if(!_sessionIdExtracted)
		{
			String sessionId = extracteSessionId();
			
			if(sessionId != null)
			{
				HttpSessionImpl session = _sessionManager.getSession(sessionId);
				
				if (session != null && _sessionManager.isValid(session))
				{
					_session = session;
					return _session;
				}
			}
		}

		if (!create)
			return null;

		// 创建一个Session
		if (_sessionManager == null)
			throw new IllegalStateException("No SessionManager");

		_session = _sessionManager.createNewSession(this);

		Cookie cookie = _sessionManager.getSessionCookie(_session, getContextPath(), isSecure());

		if (cookie != null)
		{
			_httpResponse.addCookie(cookie);
		}

		return _session;

	}

	/* ------------------------------------------------------------ */
	/**
	 * @return Returns the sessionManager.
	 */
	public SessionManager getSessionManager()
	{
		return _sessionManager;
	}

	/* ------------------------------------------------------------ */
	/**
	 * Get HttpServletRequestImpl TimeStamp
	 * 
	 * @return The time that the request was received.
	 */
	public long getTimeStamp()
	{
		return _timeStamp;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#getUserPrincipal()
	 */
	@Override
	public Principal getUserPrincipal()
	{
		/*
		 * if (_authentication instanceof Authentication.Deferred)
		 * setAuthentication(((Authentication.Deferred)_authentication).authenticate(this));
		 * 
		 * if (_authentication instanceof Authentication.User) { UserIdentity user =
		 * ((Authentication.User)_authentication).getUserIdentity(); return user.getUserPrincipal(); }
		 * 
		 * return null;
		 */
		return null;
	}

	/* ------------------------------------------------------------ */
	/**
	 * Get timestamp of the request dispatch
	 * 
	 * @return timestamp
	 */
	public long getDispatchTime()
	{
		return _dispatchTime;
	}

	/* ------------------------------------------------------------ */
	public boolean isHandled()
	{
		return _handled;
	}

	@Override
	public boolean isAsyncStarted()
	{
		//TODO:NOT support now
		return false;
	}

	/* ------------------------------------------------------------ */
	@Override
	public boolean isAsyncSupported()
	{
		return _asyncSupported;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromCookie()
	 */
	@Override
	public boolean isRequestedSessionIdFromCookie()
	{
		return _requestedSessionId != null && _isSessionIdFromCookie;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromUrl()
	 */
	@Override
	public boolean isRequestedSessionIdFromUrl()
	{
		return _requestedSessionId != null && !_isSessionIdFromCookie;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromURL()
	 */
	@Override
	public boolean isRequestedSessionIdFromURL()
	{
		return _requestedSessionId != null && !_isSessionIdFromCookie;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdValid()
	 */
	@Override
	public boolean isRequestedSessionIdValid()
	{
		if (_requestedSessionId == null)
			return false;

		HttpSession session = getSession(false);
		return (session != null);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#isSecure()
	 */
	@Override
	public boolean isSecure()
	{
		return _secure;
	}

	/* ------------------------------------------------------------ */
	public void setSecure(boolean secure)
	{
		_secure = secure;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.http.HttpServletRequest#isUserInRole(java.lang.String)
	 */
	@Override
	public boolean isUserInRole(String role)
	{
		/*
		 * if (_authentication instanceof Authentication.Deferred)
		 * setAuthentication(((Authentication.Deferred)_authentication).authenticate(this));
		 * 
		 * if (_authentication instanceof Authentication.User) return
		 * ((Authentication.User)_authentication).isUserInRole(_scope,role); return false;
		 */
		//TODO: 
		return false;
	}



	/* ------------------------------------------------------------ */
	protected void recycle()
	{
		_asyncSupported = true;
		_handled = false;
		if (servletContext != null)
			throw new IllegalStateException("HttpServletRequestImpl in context!");
		if (_attributes != null)
			_attributes.clear();
		_characterEncoding = null;
		_contextPath = null;

		_cookiesExtracted = false;
		_serverName = null;
		_pathInfo = null;
		_port = 0;
		_httpVersion = HttpVersion.HTTP_1_1;
		_queryEncoding = null;
		_queryString = null;
		_requestedSessionId = null;
		_isSessionIdFromCookie = false;
		_session = null;
		_sessionManager = null;
		_requestURI = null;
		// _scope = null;
		_scheme = URIUtil.HTTP;
		_servletPath = null;
		_timeStamp = 0;
		// _uri = null;
		if (_parameters != null)
			_parameters.clear();
		_parameters = null;
		_paramsExtracted = false;


		_remote = null;
		headers.clear();
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#removeAttribute(java.lang.String)
	 */
	@Override
	// OK
	public void removeAttribute(String name)
	{
		if (_attributes == null)
			return;

		//
		boolean found = _attributes.containsKey(name);
		if (found)
		{
			Object value = _attributes.get(name);
			_attributes.remove(name);

			// Notify interested application event listeners
			notifyAttributeRemoved(name, value);
		}
	}

	/* ------------------------------------------------------------ */
	public void removeEventListener(final EventListener listener)
	{
		if (_requestAttributeListeners != null)
			_requestAttributeListeners.remove(listener);
	}



	/* ------------------------------------------------------------ */
	public void setAsyncSupported(boolean supported)
	{
		_asyncSupported = supported;
	}

	/* ------------------------------------------------------------ */
	/*
	 * Set a request attribute. if the attribute name is "org.eclipse.jetty.server.server.Request.queryEncoding" then
	 * the value is also passed in a call to {@link #setQueryEncoding}. <p> if the attribute name is
	 * "org.eclipse.jetty.server.server.ResponseBuffer", then the response buffer is flushed with @{link
	 * #flushResponseBuffer} <p> if the attribute name is "org.eclipse.jetty.io.EndPoint.maxIdleTime", then the value is
	 * passed to the associated {@link EndPoint#setIdleTimeout}.
	 * 
	 * @see javax.servlet.ServletRequest#setAttribute(java.lang.String, java.lang.Object)
	 */
	@Override
	// OK
	public void setAttribute(String name, Object value)
	{
		// Name cannot be null
		if (name == null)
		{
			throw new IllegalArgumentException("Name cannot be null");
		}

		// Null value is the same as removeAttribute()
		if (value == null)
		{
			removeAttribute(name);
			return;
		}

		if (_attributes == null)
			_attributes = new HashMap<String, Object>();

		Object oldValue = _attributes.put(name, value);

		// Notify interested ServletRequestAttributeListeners
		if (oldValue == null)
			notifyAttributeAdded(name, value);
		else
			notifyAttributeReplaced(name, oldValue);
	}

	/* ------------------------------------------------------------ */

	/**
	 * Notify interested ServletRequestAttributeListeners that attribute has been assigned a value.
	 * 
	 * 发布属性增加事件
	 * 
	 */
	private void notifyAttributeAdded(String name, Object value)
	{
		if (_requestAttributeListeners != null && !_requestAttributeListeners.isEmpty())
		{
			final ServletRequestAttributeEvent event = new ServletRequestAttributeEvent(servletContext, this, name, value);

			for (ServletRequestAttributeListener listener : _requestAttributeListeners)
			{
				listener.attributeAdded(event);
			}
		}
	}

	/**
	 * Notify interested ServletRequestAttributeListeners that attribute has been Replaced.
	 * 
	 * 发布属性被取代事件
	 */
	private void notifyAttributeReplaced(String name, Object oldValue)
	{
		if (_requestAttributeListeners != null && !_requestAttributeListeners.isEmpty())
		{
			final ServletRequestAttributeEvent event = new ServletRequestAttributeEvent(servletContext, this, name, oldValue);

			for (ServletRequestAttributeListener listener : _requestAttributeListeners)
			{
				listener.attributeReplaced(event);
			}
		}
	}

	/* ------------------------------------------------------------ */
	/**
	 * Notify interested listeners that attribute has been removed. 发布属性被删除事件
	 */
	private void notifyAttributeRemoved(String name, Object value)
	{
		if (_requestAttributeListeners != null && !_requestAttributeListeners.isEmpty())
		{
			final ServletRequestAttributeEvent event = new ServletRequestAttributeEvent(servletContext, this, name, value);

			for (ServletRequestAttributeListener listener : _requestAttributeListeners)
			{
				listener.attributeRemoved(event);
			}
		}
	}

	/* ------------------------------------------------------------ */
	/*
     */
	public void setAttributes(Map<String, Object> attributes)
	{
		_attributes = attributes;
	}

	/* ------------------------------------------------------------ */

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#setCharacterEncoding(java.lang.String)
	 */
	@Override
	// OK
	public void setCharacterEncoding(String encoding) throws UnsupportedEncodingException
	{
		if (usingReader)
		{
			return;
		}

		_characterEncoding = encoding;

		// check encoding is supported
		if (!"UTF-8".equalsIgnoreCase(encoding))
			Charset.forName(encoding);
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#setCharacterEncoding(java.lang.String)
	 */
	public void setCharacterEncodingUnchecked(String encoding)
	{
		_characterEncoding = encoding;
	}

	/* ------------------------------------------------------------ */
	/*
	 * @see javax.servlet.ServletRequest#getContentType()
	 */
	public void setContentType(String contentType)
	{
		headers.set(HttpHeaders.Names.CONTENT_TYPE, contentType);

	}

	/* ------------------------------------------------------------ */
	/**
	 * Set request context
	 * 
	 * @param context
	 *            context object
	 */
	/*
	 * public void setContext(DefalutServletContext context) { _newContext = servletContext != context; servletContext =
	 * context; }
	 */

	/* ------------------------------------------------------------ */
	/**
	 * @return True if this is the first call of {@link #takeNewContext()} since the last
	 *         {@link #setContext(org.eclipse.jetty.server.handler.ContextHandler.Context)} call.
	 */
	public boolean takeNewContext()
	{
		boolean nc = _newContext;
		_newContext = false;
		return nc;
	}

	/* ------------------------------------------------------------ */
	/**
	 * Sets the "context path" for this request
	 * 
	 * @see HttpServletRequest#getContextPath()
	 */
	public void setContextPath(String contextPath)
	{
		_contextPath = contextPath;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param cookies
	 *            The cookies to set.
	 */
	public void setCookies(Cookie[] cookies)
	{
		this.cookies = cookies;
	}

	/* ------------------------------------------------------------ */
	public void setDispatcherType(DispatcherType type)
	{
		_dispatcherType = type;
	}

	/* ------------------------------------------------------------ */
	public void setHandled(boolean h)
	{
		_handled = h;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param method
	 *            The method to set.
	 */
	public void setMethod(HttpMethod httpMethod)
	{
		_httpMethod = httpMethod;
	}

	/* ------------------------------------------------------------ */
	public boolean isHead()
	{
		return HttpMethod.HEAD == _httpMethod;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param parameters
	 *            The parameters to set.
	 */
	public void setParameters(Map<String, List<String>> parameters)
	{
		_parameters = (parameters == null) ? _parameters : parameters;
		if (_paramsExtracted && _parameters == null)
			throw new IllegalStateException();
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param pathInfo
	 *            The pathInfo to set.
	 */
	public void setPathInfo(String pathInfo)
	{
		_pathInfo = pathInfo;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param version
	 *            The protocol to set.
	 */
	public void setHttpVersion(HttpVersion version)
	{
		_httpVersion = version;
	}

	/* ------------------------------------------------------------ */
	/**
	 * Set the character encoding used for the query string. This call will effect the return of getQueryString and
	 * getParamaters. It must be called before any geParameter methods.
	 * 
	 * The request attribute "org.eclipse.jetty.server.server.Request.queryEncoding" may be set as an alternate method
	 * of calling setQueryEncoding.
	 * 
	 * @param queryEncoding
	 */
	public void setQueryEncoding(String queryEncoding)
	{
		_queryEncoding = queryEncoding;
		_queryString = null;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param queryString
	 *            The queryString to set.
	 */
	public void setQueryString(String queryString)
	{
		_queryString = queryString;
		_queryEncoding = null; // assume utf-8
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param addr
	 *            The address to set.
	 */
	public void setRemoteAddr(InetSocketAddress addr)
	{
		_remote = addr;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param requestedSessionId
	 *            The requestedSessionId to set.
	 */
	public void setRequestedSessionId(String requestedSessionId)
	{
		_requestedSessionId = requestedSessionId;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param requestedSessionIdCookie
	 *            The requestedSessionIdCookie to set.
	 */
	public void setRequestedSessionIdFromCookie(boolean requestedSessionIdCookie)
	{
		_isSessionIdFromCookie = requestedSessionIdCookie;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param requestURI
	 *            The requestURI to set.
	 */
	public void setRequestURI(String requestURI)
	{
		_requestURI = requestURI;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param scheme
	 *            The scheme to set.
	 */
	public void setScheme(String scheme)
	{
		_scheme = scheme;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param host
	 *            The host to set.
	 */
	public void setServerName(String host)
	{
		_serverName = host;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param port
	 *            The port to set.
	 */
	public void setServerPort(int port)
	{
		_port = port;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param servletPath
	 *            The servletPath to set.
	 */
	public void setServletPath(String servletPath)
	{
		_servletPath = servletPath;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param session
	 *            The session to set.
	 */
	public void setSession(HttpSessionImpl session)
	{
		_session = session;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param sessionManager
	 *            The sessionManager to set.
	 */
	public void setSessionManager(SessionManager sessionManager)
	{
		_sessionManager = sessionManager;
	}

	/* ------------------------------------------------------------ */
	public void setTimeStamp(long ts)
	{
		_timeStamp = ts;
	}

	/* ------------------------------------------------------------ */
	/**
	 * Set timetstamp of request dispatch
	 * 
	 * @param value
	 *            timestamp
	 */
	public void setDispatchTime(long value)
	{
		_dispatchTime = value;
	}

	/* ------------------------------------------------------------ */
	@Override
	public AsyncContext startAsync() throws IllegalStateException
	{
		/*
		 * if (!_asyncSupported) throw new IllegalStateException("!asyncSupported"); HttpChannelState state =
		 * getHttpChannelState(); state.startAsync(); return state;
		 */
		throw new UnsupportedOperationException();
	}

	/* ------------------------------------------------------------ */
	@Override
	public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException
	{
		/*
		 * if (!_asyncSupported) throw new IllegalStateException("!asyncSupported"); HttpChannelState state =
		 * getHttpChannelState(); state.startAsync(servletContext, servletRequest, servletResponse); return state;
		 */
		throw new UnsupportedOperationException();
	}

	/* ------------------------------------------------------------ */
	@Override
	public String toString()
	{
		return (_handled ? "[" : "(") + getMethod() + " " + request.getUri() + (_handled ? "]@" : ")@") + hashCode() + " " + super.toString();
	}

	/* ------------------------------------------------------------ */
	@Override
	public boolean authenticate(HttpServletResponse response) throws IOException, ServletException
	{
		throw new UnsupportedOperationException();
	}

	/* ------------------------------------------------------------ */
	@Override
	public Part getPart(String name) throws IOException, ServletException
	{
		/*
		 * if (getContentType() == null || !getContentType().startsWith("multipart/form-data")) throw new
		 * ServletException("Content-Type != multipart/form-data");
		 * 
		 * if (_multiPartInputStream == null) { MultipartConfigElement config =
		 * (MultipartConfigElement)getAttribute(__MULTIPART_CONFIG_ELEMENT);
		 * 
		 * if (config == null) throw new IllegalStateException("No multipart config for servlet");
		 * 
		 * _multiPartInputStream = new MultiPartInputStreamParser(getInputStream(), getContentType(),config,
		 * (servletContext != null?(File)servletContext.getAttribute("javax.servlet.context.tempdir"):null));
		 * setAttribute(__MULTIPART_INPUT_STREAM, _multiPartInputStream); setAttribute(__MULTIPART_CONTEXT,
		 * servletContext); Collection<Part> parts = _multiPartInputStream.getParts(); //causes parsing for (Part
		 * p:parts) { MultiPartInputStreamParser.MultiPart mp = (MultiPartInputStreamParser.MultiPart)p; if
		 * (mp.getContentDispositionFilename() == null && mp.getFile() == null) { //Servlet Spec 3.0 pg 23, parts
		 * without filenames must be put into init params String charset = null; if (mp.getContentType() != null)
		 * charset = MimeTypes.getCharsetFromContentType(mp.getContentType());
		 * 
		 * String content=new String(mp.getBytes(),charset==null?StringUtil.__UTF8:charset); getParameter(""); //cause
		 * params to be evaluated getParameters().add(mp.getName(), content); } } } return
		 * _multiPartInputStream.getPart(name);
		 */
		throw new UnsupportedOperationException();
	}

	/* ------------------------------------------------------------ */
	@Override
	public Collection<Part> getParts() throws IOException, ServletException
	{
		/*
		 * if (getContentType() == null || !getContentType().startsWith("multipart/form-data")) throw new
		 * ServletException("Content-Type != multipart/form-data");
		 * 
		 * if (_multiPartInputStream == null) { MultipartConfigElement config =
		 * (MultipartConfigElement)getAttribute(__MULTIPART_CONFIG_ELEMENT);
		 * 
		 * if (config == null) throw new IllegalStateException("No multipart config for servlet");
		 * 
		 * _multiPartInputStream = new MultiPartInputStreamParser(getInputStream(), getContentType(), config,
		 * (servletContext != null?(File)servletContext.getAttribute("javax.servlet.context.tempdir"):null));
		 * 
		 * setAttribute(__MULTIPART_INPUT_STREAM, _multiPartInputStream); setAttribute(__MULTIPART_CONTEXT,
		 * servletContext); Collection<Part> parts = _multiPartInputStream.getParts(); //causes parsing for (Part
		 * p:parts) { MultiPartInputStreamParser.MultiPart mp = (MultiPartInputStreamParser.MultiPart)p; if
		 * (mp.getContentDispositionFilename() == null && mp.getFile() == null) { //Servlet Spec 3.0 pg 23, parts
		 * without filenames must be put into init params String charset = null; if (mp.getContentType() != null)
		 * charset = MimeTypes.getCharsetFromContentType(mp.getContentType());
		 * 
		 * String content=new String(mp.getBytes(),charset==null?StringUtil.__UTF8:charset); getParameter(""); //cause
		 * params to be evaluated getParameters().add(mp.getName(), content); } } } return
		 * _multiPartInputStream.getParts();
		 */
		throw new UnsupportedOperationException();
	}

	/* ------------------------------------------------------------ */
	@Override
	public void login(String username, String password) throws ServletException
	{
		throw new UnsupportedOperationException();
	}

	/* ------------------------------------------------------------ */
	@Override
	public void logout() throws ServletException
	{
		throw new UnsupportedOperationException();
	}

	/* ------------------------------------------------------------ */
	/**
	 * Merge in a new query string. The query string is merged with the existing parameters and
	 * {@link #setParameters(MultiMap)} and {@link #setQueryString(String)} are called with the result. The merge is
	 * according to the rules of the servlet dispatch forward method.
	 * 
	 * @param query
	 *            The query string to merge into the request.
	 */
	/*
	 * public void mergeQueryString(String query) { // extract parameters from dispatch query MultiMap<String>
	 * parameters = new MultiMap<>(); UrlEncoded.decodeTo(query,parameters, StringUtil.__UTF8_CHARSET,-1); //have to
	 * assume UTF-8 because we can't know otherwise
	 * 
	 * boolean merge_old_query = false;
	 * 
	 * // Have we evaluated parameters if (!_paramsExtracted) extractParameters();
	 * 
	 * // Are there any existing parameters? if (_parameters != null && _parameters.size() > 0) { // Merge parameters;
	 * new parameters of the same name take precedence. merge_old_query = parameters.addAllValues(_parameters); }
	 * 
	 * if (_queryString != null && _queryString.length() > 0) { if (merge_old_query) { StringBuilder
	 * overridden_query_string = new StringBuilder(); MultiMap<String> overridden_old_query = new MultiMap<>();
	 * UrlEncoded.decodeTo(_queryString,overridden_old_query,getQueryEncoding(),-1);//decode using any queryencoding set
	 * for the request
	 * 
	 * 
	 * MultiMap<String> overridden_new_query = new MultiMap<>();
	 * UrlEncoded.decodeTo(query,overridden_new_query,StringUtil.__UTF8_CHARSET,-1); //have to assume utf8 as we cannot
	 * know otherwise
	 * 
	 * for(String name: overridden_old_query.keySet()) { if (!overridden_new_query.containsKey(name)) { List<String>
	 * values = overridden_old_query.get(name); for(String v: values) {
	 * overridden_query_string.append("&").append(name).append("=").append(v); } } }
	 * 
	 * query = query + overridden_query_string; } else { query = query + "&" + _queryString; } }
	 * 
	 * setParameters(parameters); setQueryString(query); }
	 */

	public FullHttpRequest getFullHttpRequest()
	{
		return request;
	}

	public Map<String, List<String>> getParameters()
	{
		return _parameters;
	}

	/* ------------------------------------------------------------ */
	/**
	 * Merge in a new query string. The query string is merged with the existing parameters and
	 * {@link #setParameters(MultiMap)} and {@link #setQueryString(String)} are called with the result. The merge is
	 * according to the rules of the servlet dispatch forward method.
	 * 
	 * @param query
	 *            The query string to merge into the request.
	 */
	public void mergeQueryString(String query)
	{
		/*
		 * // extract parameters from dispatch query MultiMap<String> parameters = new MultiMap<>();
		 * UrlEncoded.decodeTo(query,parameters, StringUtil.__UTF8_CHARSET,-1); //have to assume UTF-8 because we can't
		 * know otherwise
		 * 
		 * boolean merge_old_query = false;
		 * 
		 * // Have we evaluated parameters if (!_paramsExtracted) extractParameters();
		 * 
		 * // Are there any existing parameters? if (_parameters != null && _parameters.size() > 0) { // Merge
		 * parameters; new parameters of the same name take precedence. merge_old_query =
		 * parameters.addAllValues(_parameters); }
		 * 
		 * if (_queryString != null && _queryString.length() > 0) { if (merge_old_query) { StringBuilder
		 * overridden_query_string = new StringBuilder(); MultiMap<String> overridden_old_query = new MultiMap<>();
		 * UrlEncoded.decodeTo(_queryString,overridden_old_query,getQueryEncoding(),-1);//decode using any queryencoding
		 * set for the request
		 * 
		 * 
		 * MultiMap<String> overridden_new_query = new MultiMap<>();
		 * UrlEncoded.decodeTo(query,overridden_new_query,StringUtil.__UTF8_CHARSET,-1); //have to assume utf8 as we
		 * cannot know otherwise
		 * 
		 * for(String name: overridden_old_query.keySet()) { if (!overridden_new_query.containsKey(name)) { List<String>
		 * values = overridden_old_query.get(name); for(String v: values) {
		 * overridden_query_string.append("&").append(name).append("=").append(v); } } }
		 * 
		 * query = query + overridden_query_string; } else { query = query + "&" + _queryString; } }
		 * 
		 * setParameters(parameters); setQueryString(query);
		 */
	}

	public Invocation getInvocation()
	{
		return _invocation;
	}

	public void setInvocation(Invocation invocation)
	{
		this._invocation = invocation;
	}
}
