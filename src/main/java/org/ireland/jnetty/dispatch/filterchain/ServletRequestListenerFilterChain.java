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

package org.ireland.jnetty.dispatch.filterchain;



import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.ServletResponse;

import org.ireland.jnetty.webapp.WebApp;

import java.io.IOException;
import java.util.List;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

/**
 * 
 * 为回调ServletRequestListener相关事件的FilterChain 
 * 
 */
public class ServletRequestListenerFilterChain implements FilterChain
{
	private static final Log log = LogFactory.getLog(ServletRequestListenerFilterChain.class.getName());

	// Next filter chain
	private FilterChain _next;

	// app
	private WebApp _webApp;

	private List<ServletRequestListener> _requestListeners;

	/**
	 * Creates a new FilterChainFilter.
	 * 
	 * @param next
	 *            the next filterChain
	 * @param filter
	 *            the user's filter
	 */
	public ServletRequestListenerFilterChain(FilterChain next, WebApp webApp, List<ServletRequestListener> requestListeners)
	{
		_next = next;
		_webApp = webApp;
		_requestListeners = requestListeners;
	}

	/**
	 * Returns true if cacheable.
	 */
	public FilterChain getNext()
	{
		return _next;
	}

	/**
	 * Invokes the next filter in the chain or the final servlet at the end of the chain.
	 * 
	 * @param request
	 *            the servlet request
	 * @param response
	 *            the servlet response
	 * @since Servlet 2.3
	 */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response) throws ServletException, IOException
	{
		try
		{
			//触发ServletRequestListener#requestInitialized事件
			for (ServletRequestListener listener : _requestListeners)
			{
				ServletRequestEvent event = new ServletRequestEvent(_webApp, request);

				listener.requestInitialized(event);
			}

			_next.doFilter(request, response);
		}
		finally
		{
			//触发ServletRequestListener#requestDestroyed事件
			for (int i = _requestListeners.size() - 1; i >= 0; i--)
			{
				try
				{
					ServletRequestEvent event = new ServletRequestEvent(_webApp, request);

					_requestListeners.get(i).requestDestroyed(event);
				}
				catch (Throwable e)
				{
					log.warn( e.toString(), e);
				}
			}
		}
	}

	@Override
	public String toString()
	{
		return (getClass().getSimpleName() + "[" + _webApp.getURL() + ", next=" + _next + "]");
	}
}
