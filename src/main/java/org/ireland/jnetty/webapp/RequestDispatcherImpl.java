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

package org.ireland.jnetty.webapp;


import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ireland.jnetty.dispatch.HttpInvocation;
import org.ireland.jnetty.http.HttpServletRequestImpl;
import org.ireland.jnetty.http.HttpServletResponseImpl;
import org.ireland.jnetty.http.wrapper.ErrorRequest;
import org.ireland.jnetty.http.wrapper.ForwardRequest;
import org.ireland.jnetty.http.wrapper.IncludeRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 
 * 对于每一个请求,要生成一个与之对应的RequestDispatcher
 * 
 * 对于相同的rawContextURI的多个不同的请求,其生成的RequestDispatcherImpl是一样的, 故可以根据rawContextURI来缓存RequestDispatcherImpl
 * 
 * 只要rawContextURI相同(即包括参数也相同),并发的情况下也是可重用的和共享的,像单例一般的重用
 * 
 * @author KEN
 * 
 */
public class RequestDispatcherImpl implements RequestDispatcher
{
	private final static Log log = LogFactory.getLog(RequestDispatcherImpl.class.getName());

	static final int MAX_DEPTH = 64;

	// WebApp the request dispatcher was called from
	private final WebApp _webApp;

	private final String _rawContextURI;

	private HttpInvocation _dispatchInvocation;
	private HttpInvocation _forwardInvocation;
	private HttpInvocation _includeInvocation;
	private HttpInvocation _errorInvocation;
	

	// private HttpInvocation _asyncInvocation;

	public RequestDispatcherImpl(WebApp webApp, String rawContextURI)
	{
		_webApp = webApp;

		_rawContextURI = rawContextURI;
	}

	/**
	 * This method sets the dispatcher type of the given request to DispatcherType.REQUEST.
	 * 
	 * 通常,DispatcherType.REQUEST是第一次被处理的类型.
	 * 
	 * @param request
	 * @param response
	 * @throws ServletException
	 * @throws IOException
	 */
	public void dispatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		// jsp/15m8
		if (response.isCommitted())
			throw new IllegalStateException("dispatch() not allowed after buffer has committed.");

		// build invocation,if not exist
		if (_dispatchInvocation == null)
		{
			_dispatchInvocation = buildDispatchInvocation(_rawContextURI);
		}

		doDispatch(request, response, _dispatchInvocation);
	}

	private void doDispatch(HttpServletRequest request, HttpServletResponse response, HttpInvocation invocation) throws ServletException, IOException
	{

		// 到这里,response的buffer一定为空的,TODO: need resetBuffer()?
		response.resetBuffer();

		// Set the invocation into HttpServlerRequestImpl
		if (request instanceof HttpServletRequestImpl)
		{
			((HttpServletRequestImpl) request).setInvocation(_dispatchInvocation);
			((HttpServletRequestImpl) request).setDispatcherType(DispatcherType.REQUEST);
		}

		boolean isValid = false;

		try
		{

			invocation.getFilterChainInvocation().service(request, response);
			isValid = true;
		}
		finally
		{
			if (request.getAsyncContext() != null)
			{
				// An async request was started during the forward, don't close the
				// response as it may be written to during the async handling
				return;
			}

			// server/106r, ioc/0310
			if (isValid)
			{
				finishResponse(response);
			}
		}
	}

	@Override
	public void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException
	{
		// jsp/15m8
		if (response.isCommitted())
			throw new IllegalStateException("forward() not allowed after buffer has committed.");

		// build invocation,if not exist
		if (_forwardInvocation == null)
		{
			_forwardInvocation = buildForwardInvocation(_rawContextURI);
		}

		doForward((HttpServletRequest) request, (HttpServletResponse) response, _forwardInvocation);
	}

	private void doForward(HttpServletRequest request, HttpServletResponse response, HttpInvocation invocation) throws ServletException, IOException
	{

		// Reset any output that has been buffered, but keep headers/cookies
		response.resetBuffer(); // Servlet-3_1-PFD 9.4

		//Wrap the request
		ForwardRequest wrequest = new ForwardRequest(request, response, invocation);

		// If we have already been forwarded previously, then keep using the established
		// original value. Otherwise, this is the first forward and we need to establish the values.
		// Note: the established value on the original request for pathInfo and
		// for queryString is allowed to be null, but cannot be null for the other values.
		if (request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) == null)
		{
			// 只有在第一次请求转发,记下最开始的请求的相关属性
			wrequest.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI, request.getRequestURI());

			wrequest.setAttribute(RequestDispatcher.FORWARD_CONTEXT_PATH, request.getContextPath());
			wrequest.setAttribute(RequestDispatcher.FORWARD_SERVLET_PATH, request.getServletPath());
			wrequest.setAttribute(RequestDispatcher.FORWARD_PATH_INFO, request.getPathInfo());
			wrequest.setAttribute(RequestDispatcher.FORWARD_QUERY_STRING, request.getQueryString());
		}

		boolean isValid = false;

		try
		{

			invocation.getFilterChainInvocation().service(wrequest, response);

			isValid = true;
		}
		finally
		{
			if (request.getAsyncContext() != null)
			{
				// An async request was started during the forward, don't close the
				// response as it may be written to during the async handling
				return;
			}

			// server/106r, ioc/0310
			if (isValid)
			{
				finishResponse(response);
			}
		}
	}



	@Override
	public void include(ServletRequest request, ServletResponse response) throws ServletException, IOException
	{
		// jsp/15m8
		if (response.isCommitted())
			throw new IllegalStateException("include() not allowed after buffer has committed.");

		// build invocation,if not exist
		if (_includeInvocation == null)
		{
			_includeInvocation = buildIncludeInvocation(_rawContextURI);
		}

		doInclude((HttpServletRequest) request, (HttpServletResponse) response, _forwardInvocation);
	}

	private void doInclude(HttpServletRequest request, HttpServletResponse response, HttpInvocation invocation) throws ServletException, IOException
	{

		//Wrap the request
		IncludeRequest wrequest = new IncludeRequest(request, response, invocation);

		// If we have already been include previously, then keep using the established
		// original value. Otherwise, this is the first include and we need to establish the values.
		// Note: the established value on the original request for pathInfo and
		// for queryString is allowed to be null, but cannot be null for the other values.
		if (request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) == null)
		{
			// 只有在第一次Include请求,记下最开始的请求的相关属性
			wrequest.setAttribute(RequestDispatcher.INCLUDE_REQUEST_URI, request.getRequestURI());

			wrequest.setAttribute(RequestDispatcher.INCLUDE_CONTEXT_PATH, request.getContextPath());
			wrequest.setAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH, request.getServletPath());
			wrequest.setAttribute(RequestDispatcher.INCLUDE_PATH_INFO, request.getPathInfo());
			wrequest.setAttribute(RequestDispatcher.INCLUDE_QUERY_STRING, request.getQueryString());
		}

		boolean isValid = false;

		try
		{

			invocation.getFilterChainInvocation().service(wrequest, response);

			isValid = true;
		}
		finally
		{
			if (request.getAsyncContext() != null)
			{
				// An async request was started during the forward, don't close the
				// response as it may be written to during the async handling
				return;
			}

			// server/106r, ioc/0310
			if (isValid)
			{
				finishResponse(response);
			}
		}
	}

	
	/**
	 * This method Wrap the dispatcher type of the given request to DispatcherType.ERROR.
	 * 
	 * @param request
	 * @param response
	 * @throws ServletException
	 * @throws IOException
	 */
	public void error(ServletRequest request, ServletResponse response) throws ServletException, IOException
	{
		// jsp/15m8
		if (response.isCommitted())
			throw new IllegalStateException("error() not allowed after buffer has committed.");

		// build invocation,if not exist
		if (_errorInvocation == null)
		{
			_errorInvocation = buildErrorInvocation(_rawContextURI);
		}

		doError((HttpServletRequest) request, (HttpServletResponse) response, _errorInvocation);
	}

	private void doError(HttpServletRequest request, HttpServletResponse response, HttpInvocation invocation) throws ServletException, IOException
	{

		// Reset any output that has been buffered, but keep headers/cookies
		response.resetBuffer(); // Servlet-3_1-PFD 9.4

		
		//Wrap the request
		ErrorRequest wrequest = new ErrorRequest(request, response, invocation);


		boolean isValid = false;

		try
		{

			invocation.getFilterChainInvocation().service(wrequest, response);

			isValid = true;
		}
		finally
		{
			if (request.getAsyncContext() != null)
			{
				// An async request was started during the forward, don't close the
				// response as it may be written to during the async handling
				return;
			}

			// server/106r, ioc/0310
			if (isValid)
			{
				finishResponse(response);
			}
		}
	}
	
	// -----------------------------------------------------------------------------------

	/**
	 * Fills the invocation with uri.
	 * 
	 * @throws IOException
	 */
	private HttpInvocation buildDispatchInvocation(String rawContextURI) throws ServletException
	{

		return _webApp.buildDispatchInvocation(rawContextURI);
	}

	/**
	 * Fills the invocation for a forward request.
	 * 
	 * @throws IOException
	 */
	private HttpInvocation buildForwardInvocation(String rawContextURI) throws ServletException
	{

		return _webApp.buildForwardInvocation(rawContextURI);
	}

	/**
	 * Fills the invocation for an include request.
	 * 
	 * @throws IOException
	 */
	private HttpInvocation buildIncludeInvocation(String rawContextURI) throws ServletException
	{

		return _webApp.buildIncludeInvocation(rawContextURI);
	}

	/**
	 * Fills the invocation for an error request.
	 * 
	 * @throws IOException
	 */
	private HttpInvocation buildErrorInvocation(String rawContextURI) throws ServletException
	{

		return _webApp.buildErrorInvocation(rawContextURI);
	}

	// ------------------------------------------------------------------------------------
	private void finishResponse(ServletResponse res) throws ServletException, IOException
	{

		if (res instanceof HttpServletResponseImpl)
		{
			res.flushBuffer(); // we sure that all data has already put to the ByteBuf?
		}
		else
		{
			try
			{
				OutputStream os = res.getOutputStream();
				os.flush();
				// os.close();
			}
			catch (Exception e)
			{
			}

			try
			{
				PrintWriter out = res.getWriter();
				out.flush();
				// out.close();
			}
			catch (Exception e)
			{
			}
		}
	}

	@Override
	public String toString()
	{
		return (getClass().getSimpleName() + "[" + _dispatchInvocation.getRawContextURI() + "]");
	}

	// Util------------------------------------------------------------

	/**
	 * 将reques解包装,返回未包装的ServletRequest
	 * 
	 * @param request
	 * @return
	 */
	private static ServletRequest unwarp(ServletRequest request)
	{

		ServletRequest _request = request;

		while (_request instanceof ServletRequestWrapper)
		{
			_request = ((ServletRequestWrapper) _request).getRequest();
		}

		return _request;
	}

	/**
	 * 将response解包装,返回未包装的ServletResponse
	 * 
	 * @param response
	 * @return
	 */
	private static ServletResponse unwarp(ServletResponse response)
	{

		ServletResponse _response = response;

		while (_response instanceof ServletResponseWrapper)
		{
			_response = ((ServletResponseWrapper) _response).getResponse();
		}

		return _response;
	}
}
