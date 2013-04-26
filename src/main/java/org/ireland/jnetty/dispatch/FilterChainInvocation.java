/*
 * Copyright (c) 1998-2012 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package org.ireland.jnetty.dispatch;

import javax.servlet.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ireland.jnetty.webapp.WebApp;

import java.io.IOException;

/**
 * A repository for request information gleaned from the uri.
 * 
 * and the FilterChain that match the URI
 * 
 * A HttpInvocation include the URI information and the FilterChain that match the URI
 * 
 * FilterChainInvocation 是一个面向特定ContextURI的 FilterChain + Servlet 的调用
 * 
 * 对于不同的请求,如果ContextURI相同,则生成的FilterChainInvocation必定相同,所以FilterChainInvocation是是重用的和共享的,也可以根据ContextURI来缓存
 * 
 * 并发的情况下也是可重用的和共享的,像单例一般的重用.
 * 
 * 由于对一个新的ContextURI,要匹配多次才可以生成与其匹配的FilterChain和Servlet,所以对HttpInvocation作缓存的意义较大
 * 
 * 
 */
public class FilterChainInvocation
{
	private static final Log log = LogFactory.getLog(FilterChainInvocation.class.getName());

	private static final boolean debug = log.isDebugEnabled();

	protected final WebApp _webApp;

	// Servlet在这FilterChain末端
	private FilterChain _filterChain;

	//RequestURI = ContextPath + (ServletPath + PathInfo) = ContextPath + ContextURI;
	private final String _requestURI;
	
	// ContextURI = ServletPath + PathInfo
	private final String _contextURI;

	private String _servletPath;

	private String _pathInfo;

	private String _servletName;

	private boolean _isAsyncSupported = true;

	private MultipartConfigElement _multipartConfig;

	/**
	 * Creates a new invocation
	 * 
	 * @param contextURI
	 */
	public FilterChainInvocation(WebApp webApp, String contextURI)
	{
		_webApp = webApp;
		
		_requestURI = webApp.getContextPath() + contextURI;

		_contextURI = contextURI;

		//默认情况下,ServletPath = ContextURI
		_servletPath = contextURI;
	}

	/**
	 * Returns the mapped webApp.
	 */
	public final WebApp getWebApp()
	{
		return _webApp;
	}

    /**
     * Returns the part of this request's URL from the protocol name up to the
     * query string in the first line of the HTTP request. The web container
     * does not decode this String. For example:
     * <table summary="Examples of Returned Values">
     * <tr align=left>
     * <th>First line of HTTP request</th>
     * <th>Returned Value</th>
     * <tr>
     * <td>POST /some/path.html HTTP/1.1
     * <td>
     * <td>/some/path.html
     * <tr>
     * <td>GET http://foo.bar/a.html HTTP/1.0
     * <td>
     * <td>/a.html
     * <tr>
     * <td>HEAD /xyz?a=b HTTP/1.1
     * <td>
     * <td>/xyz
     * </table>
     * <p>
     * To reconstruct an URL with a scheme and host, use
     * {@link #getRequestURL}.
     * 
     * @return a <code>String</code> containing the part of the URL from the
     *         protocol name up to the query string
     * @see #getRequestURL
     * 
     * RequestURI = ContextPath + ServletPath + PathInfo;
     * <br>       = ContextPath + ContextURI;
     */
	public String getRequestURI()
	{
		return _requestURI;
	}

	/**
	 * Returns the mapped context-path.
	 */
	public final String getContextPath()
	{
		return _webApp.getContextPath();
	}

	/**
	 * Returns the URI tail, i.e. everything after the context path.
	 * 
	 * e.g:<br>
	 * 
	 * URI: "/myapp/blog/page1.jsp"<br>
	 * 
	 * contextPath: "/myapp"<br>
	 * 
	 * servletPath: "/blog"
	 * 
	 * pathInfo: "/page1.jsp"
	 * 
	 * ContextURI: "/blog/page1.jsp"<br>
	 * 
	 * 
	 * ContextURI = ServletPath + PathInfo;
	 * 
	 */
	public final String getContextURI()
	{
		return _contextURI;
	}

	/**
	 * Returns the mapped servlet path.
	 */
	public final String getServletPath()
	{
		return _servletPath;
	}

	/**
	 * Sets the mapped servlet path.
	 */
	public void setServletPath(String servletPath)
	{
		_servletPath = servletPath;
	}

	/**
	 * Returns the mapped path info.
	 */
	public final String getPathInfo()
	{
		return _pathInfo;
	}

	/**
	 * Sets the mapped path info
	 */
	public void setPathInfo(String pathInfo)
	{
		_pathInfo = pathInfo;
	}

	/**
	 * Gets the class loader.
	 */
	public ClassLoader getClassLoader()
	{
		return _webApp.getClassLoader();
	}

	/**
	 * Sets the servlet name
	 */
	public void setServletName(String servletName)
	{
		_servletName = servletName;
	}

	/**
	 * Gets the servlet name
	 */
	public String getServletName()
	{
		return _servletName;
	}

	/**
	 * Sets the filter chain
	 */
	public void setFilterChain(FilterChain chain)
	{
		_filterChain = chain;
	}

	/**
	 * Gets the filter chain
	 */
	public FilterChain getFilterChain()
	{
		return _filterChain;
	}

	/**
	 * True if the invocation chain supports async (comet) requets.
	 */
	public boolean isAsyncSupported()
	{
		return _isAsyncSupported;
	}

	/**
	 * Mark the invocation chain as not supporting async.
	 */
	public void clearAsyncSupported()
	{
		_isAsyncSupported = false;
	}

	public MultipartConfigElement getMultipartConfig()
	{
		return _multipartConfig;
	}

	public void setMultipartConfig(MultipartConfigElement multipartConfig)
	{
		_multipartConfig = multipartConfig;
	}

	/**
	 * Service a request.
	 * 
	 * @param request
	 *            the servlet request
	 * @param response
	 *            the servlet response
	 */
	public void service(ServletRequest request, ServletResponse response) throws IOException, ServletException
	{
		if (debug)
			log.debug("Dispatch '" + _contextURI + "' to " + _filterChain);

		_filterChain.doFilter(request, response);
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();

		sb.append(getClass().getSimpleName());
		sb.append("[");
		sb.append(_contextURI);

		sb.append("]");

		return sb.toString();
	}
}
