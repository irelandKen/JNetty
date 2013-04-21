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

import com.caucho.server.http.CauchoResponse;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ireland.jnetty.dispatch.Invocation;
import org.ireland.jnetty.http.HttpServletResponseImpl;
import org.ireland.jnetty.util.http.URIDecoder;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.logging.Logger;

/**
 * 
 * 对于每一个请求,要生成一个与之对应的RequestDispatcher
 * 
 * @author KEN
 * 
 */
public class RequestDispatcherImpl implements RequestDispatcher
{
	private final static Logger log = Logger.getLogger(RequestDispatcherImpl.class.getName());

	static final int MAX_DEPTH = 64;

	// WebApp the request dispatcher was called from
	private final WebApp _webApp;

	private final String _rawURI;

	private Invocation _includeInvocation;
	private Invocation _forwardInvocation;
	private Invocation _errorInvocation;
	private Invocation _dispatchInvocation;

	// private Invocation _asyncInvocation;

	public RequestDispatcherImpl(WebApp webApp, String rowURI, Invocation includeInvocation, Invocation forwardInvocation, Invocation errorInvocation,Invocation dispatchInvocation)
	{
		_webApp = webApp;

		_rawURI = rowURI;

		_includeInvocation = includeInvocation;
		_forwardInvocation = forwardInvocation;
		_errorInvocation = errorInvocation;
		_dispatchInvocation = dispatchInvocation;
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
			_dispatchInvocation = new Invocation();

			buildDispatchInvocation(_dispatchInvocation, _rawURI);
		}
		
		doDispatch(request, response,_dispatchInvocation);
	}
	
	private void doDispatch(HttpServletRequest request, HttpServletResponse response, Invocation invocation) throws ServletException, IOException
	{

		// 到这里,response的buffer一定为空的,TODO: need resetBuffer()?
		response.resetBuffer();

		boolean isValid = false;

		try
		{

			invocation.service(request, response);
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
		// build invocation,if not exist
		if (_forwardInvocation == null)
		{
			_forwardInvocation = new Invocation();

			buildForwardInvocation(_forwardInvocation, _rawURI);
		}

		doForward((HttpServletRequest) request, (HttpServletResponse) response, _forwardInvocation);
	}

	private void doForward(HttpServletRequest request, HttpServletResponse response, Invocation invocation) throws ServletException, IOException
	{
		// jsp/15m8
		if (response.isCommitted())
		{
			throw new IllegalStateException("forward() not allowed after buffer has committed.");
		}

		// Reset any output that has been buffered, but keep headers/cookies
		response.resetBuffer(); // Servlet-3_1-PFD 9.4

		ForwardRequest wrequest = new ForwardRequest(request, response, invocation);

		
		//If we have already been forwarded previously, then keep using the established
        //original value. Otherwise, this is the first forward and we need to establish the values.
        //Note: the established value on the original request for pathInfo and
        //for queryString is allowed to be null, but cannot be null for the other values.
		if (request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) == null)			
		{
			//只有在第一次请求转发,记下最开始的请求的相关属性
			wrequest.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI, request.getRequestURI());

			wrequest.setAttribute(RequestDispatcher.FORWARD_CONTEXT_PATH, request.getContextPath());
			wrequest.setAttribute(RequestDispatcher.FORWARD_SERVLET_PATH, request.getServletPath());
			wrequest.setAttribute(RequestDispatcher.FORWARD_PATH_INFO, request.getPathInfo());
			wrequest.setAttribute(RequestDispatcher.FORWARD_QUERY_STRING, request.getQueryString());
		}

		

		boolean isValid = false;

		try
		{

			invocation.service(wrequest, response);

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
	 * This method sets the dispatcher type of the given request to DispatcherType.ERROR.
	 * 
	 * @param request
	 * @param response
	 * @throws ServletException
	 * @throws IOException
	 */
	public void error(ServletRequest request, ServletResponse response) throws ServletException, IOException
	{
		// build invocation,if not exist
		if (_errorInvocation == null)
		{
			_errorInvocation = new Invocation();

			buildErrorInvocation(_errorInvocation, _rawURI);
		}

		doError(request, response, "error", _errorInvocation, DispatcherType.ERROR);
	}

	/**
	 * Forwards the request to the servlet named by the request dispatcher.
	 * 
	 * @param topRequest
	 *            the servlet request.
	 * @param topResponse
	 *            the servlet response.
	 * @param method
	 *            special to tell if from error.
	 */
	private void doError(ServletRequest topRequest, ServletResponse topResponse, String method, Invocation invocation, DispatcherType type)
			throws ServletException, IOException
	{
		CauchoResponse cauchoResp = null;

		boolean isAllowForwardAfterFlush = false;

		if (topResponse instanceof CauchoResponse)
		{
			cauchoResp = (CauchoResponse) topResponse;

			cauchoResp.setForwardEnclosed(!isAllowForwardAfterFlush);
		}

		// jsp/15m8
		if (topResponse.isCommitted() && method == null)
		{
			IllegalStateException exn;
			exn = new IllegalStateException("forward() not allowed after buffer has committed.");

			if (cauchoResp == null || !cauchoResp.hasError())
			{
				if (cauchoResp != null)
					cauchoResp.setHasError(true);
				throw exn;
			}

			_webApp.log(exn.getMessage(), exn);

			return;
		}
		else if ("error".equals(method) || (method == null))
		{
			// server/10yg

			topResponse.resetBuffer();

			if (cauchoResp != null)
			{
				// server/10yh
				// ServletResponse resp = cauchoRes.getResponse();
				ServletResponse resp = cauchoResp;

				while (resp != null)
				{
					if (isAllowForwardAfterFlush)// if (isAllowForwardAfterFlush && resp instanceof IncludeResponse)
					{
						// server/10yh
						break;
					}
					else if (resp instanceof CauchoResponse)
					{
						CauchoResponse cr = (CauchoResponse) resp;
						cr.resetBuffer();
						resp = cr.getResponse();
					}
					else
					{
						resp.resetBuffer();

						resp = null;
					}
				}
			}
		}

		HttpServletRequest parentReq;
		ServletRequestWrapper reqWrapper = null;

		if (topRequest instanceof ServletRequestWrapper)
		{

			ServletRequest request = topRequest;

			while (request instanceof ServletRequestWrapper)
			{
				reqWrapper = (ServletRequestWrapper) request;

				request = ((ServletRequestWrapper) request).getRequest();
			}

			parentReq = (HttpServletRequest) request;
		}
		else if (topRequest instanceof HttpServletRequest)
		{
			parentReq = (HttpServletRequest) topRequest;
		}
		else
		{
			throw new IllegalStateException("expected instance of ServletRequest at `{0}'");
		}

		HttpServletResponse parentRes;
		ServletResponseWrapper resWrapper = null;

		if (topResponse instanceof ServletResponseWrapper)
		{
			ServletResponse response = topResponse;

			while (response instanceof ServletResponseWrapper)
			{
				resWrapper = (ServletResponseWrapper) response;

				response = ((ServletResponseWrapper) response).getResponse();
			}

			parentRes = (HttpServletResponse) response;
		}
		else if (topResponse instanceof HttpServletResponse)
		{
			parentRes = (HttpServletResponse) topResponse;
		}
		else
		{
			throw new IllegalStateException("expected instance of ServletResponse at `{0}'");
		}

		ForwardRequest subRequest;

		if (type == DispatcherType.ERROR)
			subRequest = new ErrorRequest(parentReq, parentRes, invocation);
/*		else if (type == DispatcherType.REQUEST)
			subRequest = new DispatchRequest(parentReq, parentRes, invocation);*/
		else
			subRequest = new ForwardRequest(parentReq, parentRes, invocation);

		HttpServletResponse subResponse = subRequest.getResponse();

		if (reqWrapper != null)
		{
			reqWrapper.setRequest(subRequest);
		}
		else
		{
			topRequest = subRequest;
		}

		if (resWrapper != null)
		{
			resWrapper.setResponse(subResponse);
		}
		else
		{
			topResponse = subResponse;
		}

		boolean isValid = false;

		//subRequest.startRequest();

		try
		{

			invocation.service(topRequest, topResponse);

			isValid = true;
		}
		finally
		{
			if (reqWrapper != null)
				reqWrapper.setRequest(parentReq);

			if (resWrapper != null)
				resWrapper.setResponse(parentRes);

			//subRequest.finishRequest(isValid);

			// server/106r, ioc/0310
			if (isValid)
			{
				finishResponse(topResponse);
			}
		}
	}

	@Override
	public void include(ServletRequest request, ServletResponse response) throws ServletException, IOException
	{
		// build invocation,if not exist
		if (_includeInvocation == null)
		{
			_includeInvocation = new Invocation();

			buildIncludeInvocation(_includeInvocation, _rawURI);
		}

		doInclude(request, response, _includeInvocation, null);
	}

	/**
	 * Include a request into the current page.
	 */
	private void doInclude(ServletRequest topRequest, ServletResponse topResponse, Invocation invocation, String method) throws ServletException, IOException
	{

		HttpServletRequest parentReq;
		ServletRequestWrapper reqWrapper = null;

		if (topRequest instanceof ServletRequestWrapper)
		{
			ServletRequest request = topRequest;

			while (request instanceof ServletRequestWrapper)
			{
				reqWrapper = (ServletRequestWrapper) request;

				request = ((ServletRequestWrapper) request).getRequest();
			}

			parentReq = (HttpServletRequest) request;
		}
		else if (topRequest instanceof HttpServletRequest)
		{
			parentReq = (HttpServletRequest) topRequest;
		}
		else
		{
			throw new IllegalStateException("expected instance of ServletRequestWrapper at `{0}'");
		}

		HttpServletResponse parentRes;
		ServletResponseWrapper resWrapper = null;

		if (topResponse instanceof ServletResponseWrapper)
		{
			ServletResponse response = topResponse;

			while (response instanceof ServletResponseWrapper)
			{
				resWrapper = (ServletResponseWrapper) response;

				response = ((ServletResponseWrapper) response).getResponse();
			}

			parentRes = (HttpServletResponse) response;
		}
		else if (topResponse instanceof HttpServletResponse)
		{
			parentRes = (HttpServletResponse) topResponse;
		}
		else
		{
			throw new IllegalStateException("expected instance of ServletResponse at '{0}'");
		}

		IncludeRequest subRequest = new IncludeRequest(parentReq, parentRes, invocation);

		HttpServletResponse subResponse = subRequest.getResponse();

		if (reqWrapper != null)
		{
			reqWrapper.setRequest(subRequest);
		}
		else
		{
			topRequest = subRequest;
		}

		if (resWrapper != null)
		{
			resWrapper.setResponse(subResponse);
		}
		else
		{
			topResponse = subResponse;
		}

		// jsp/15lf, jsp/17eg - XXX: integrated with ResponseStream?
		// res.flushBuffer();

		subRequest.startRequest();

		try
		{
			invocation.service(topRequest, topResponse);
		}
		finally
		{
			if (reqWrapper != null)
				reqWrapper.setRequest(parentReq);

			if (resWrapper != null)
				resWrapper.setResponse(parentRes);

			subRequest.finishRequest();
		}
	}

	// -----------------------------------------------------------------------------------

	/**
	 * Fills the invocation with uri.
	 * 
	 * @throws IOException
	 */
	private void buildDispatchInvocation(Invocation invocation, String rawURI) throws ServletException, IOException
	{
		URIDecoder decoder = _webApp.getURIDecoder();

		decoder.splitQuery(invocation, rawURI);

		_webApp.buildDispatchInvocation(invocation);
	}

	/**
	 * Fills the invocation for a forward request.
	 * 
	 * @throws IOException
	 */
	private void buildForwardInvocation(Invocation invocation, String rawURI) throws ServletException, IOException
	{
		URIDecoder decoder = _webApp.getURIDecoder();

		decoder.splitQuery(invocation, rawURI);

		_webApp.buildForwardInvocation(invocation);
	}

	/**
	 * Fills the invocation for an include request.
	 * 
	 * @throws IOException
	 */
	private void buildIncludeInvocation(Invocation invocation, String rawURI) throws ServletException, IOException
	{
		URIDecoder decoder = _webApp.getURIDecoder();

		decoder.splitQuery(invocation, rawURI);

		_webApp.buildIncludeInvocation(invocation);
	}

	/**
	 * Fills the invocation for an error request.
	 * 
	 * @throws IOException
	 */
	private void buildErrorInvocation(Invocation invocation, String rawURI) throws ServletException, IOException
	{
		URIDecoder decoder = _webApp.getURIDecoder();

		decoder.splitQuery(invocation, rawURI);

		_webApp.buildErrorInvocation(invocation);
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
				os.close();
			}
			catch (Exception e)
			{
			}

			try
			{
				PrintWriter out = res.getWriter();
				out.close();
			}
			catch (Exception e)
			{
			}
		}
	}

	@Override
	public String toString()
	{
		return (getClass().getSimpleName() + "[" + _dispatchInvocation.getRawURI() + "]");
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
