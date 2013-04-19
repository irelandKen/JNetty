/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ireland.jnetty.jsp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jasper.Constants;
import org.apache.jasper.EmbeddedServletOptions;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.security.SecurityUtil;
import org.apache.jasper.servlet.JspServletWrapper;
import org.apache.jasper.util.ExceptionUtils;

import org.apache.tomcat.PeriodicEventListener;

/**
 * 由于org.apache.jasper.servlet.JspServlet只能支持单个Jsp页面,故 用组合模式设计了此Servlet,JspServletComposite
 * 
 * 可处理多个JSP页面,可搭配*.jsp来使用,表示拦截所有*.jsp的请求
 * 
 * @author KEN
 * 
 */
public class JspServletComposite extends HttpServlet implements PeriodicEventListener
{

	private static final long serialVersionUID = 1L;

	// Logger
	private final transient Log log = LogFactory.getLog(JspServletComposite.class);

	private transient ServletContext context;

	private ServletConfig config;

	//Jsp相关参数
	private transient Options options;

	
	//存放了所有JSP页面编译后的JspServletWrapper
	private transient JspRuntimeContext rctxt;

	


	/*
	 * Initializes this JspServletComposite.
	 */
	@Override
	public void init(ServletConfig config) throws ServletException
	{

		super.init(config);
		this.config = config;
		this.context = config.getServletContext();

		// Initialize the JSP Runtime Context
		// Check for a custom Options implementation
		String engineOptionsName = config.getInitParameter("engineOptionsClass");			//加载自定义
		if (engineOptionsName != null)																
		{
			// Instantiate the indicated Options implementation
			try
			{
				ClassLoader loader = Thread.currentThread().getContextClassLoader();
				Class<?> engineOptionsClass = loader.loadClass(engineOptionsName);
				Class<?>[] ctorSig = { ServletConfig.class, ServletContext.class };
				Constructor<?> ctor = engineOptionsClass.getConstructor(ctorSig);
				Object[] args = { config, context };
				options = (Options) ctor.newInstance(args);
			}
			catch (Throwable e)
			{
				e = ExceptionUtils.unwrapInvocationTargetException(e);
				ExceptionUtils.handleThrowable(e);
				// Need to localize this.
				log.warn("Failed to load engineOptionsClass", e);
				// Use the default Options implementation
				options = new EmbeddedServletOptions(config, context);
			}
		}
		else
		{
			// Use the default Options implementation
			options = new EmbeddedServletOptions(config, context);
		}

		
		rctxt = new JspRuntimeContext(context, options);
		

		if (log.isDebugEnabled())
		{
			log.debug(Localizer.getMessage("jsp.message.scratch.dir.is", options.getScratchDir().toString()));
			log.debug(Localizer.getMessage("jsp.message.dont.modify.servlets"));
		}
	}

	/**
	 * Returns the number of JSPs for which JspServletWrappers exist, i.e., the number of JSPs that have been loaded
	 * into the webapp with which this JspServlet is associated.
	 * 
	 * <p>
	 * This info may be used for monitoring purposes.
	 * 
	 * @return The number of JSPs that have been loaded into the webapp with which this JspServlet is associated
	 */
	public int getJspCount()
	{
		return this.rctxt.getJspCount();
	}

	/**
	 * Resets the JSP reload counter.
	 * 
	 * @param count
	 *            Value to which to reset the JSP reload counter
	 */
	public void setJspReloadCount(int count)
	{
		this.rctxt.setJspReloadCount(count);
	}

	/**
	 * Gets the number of JSPs that have been reloaded.
	 * 
	 * <p>
	 * This info may be used for monitoring purposes.
	 * 
	 * @return The number of JSPs (in the webapp with which this JspServlet is associated) that have been reloaded
	 */
	public int getJspReloadCount()
	{
		return this.rctxt.getJspReloadCount();
	}

	/**
	 * Gets the number of JSPs that are in the JSP limiter queue
	 * 
	 * <p>
	 * This info may be used for monitoring purposes.
	 * 
	 * @return The number of JSPs (in the webapp with which this JspServlet is associated) that are in the JSP limiter
	 *         queue
	 */
	public int getJspQueueLength()
	{
		return this.rctxt.getJspQueueLength();
	}

	/**
	 * Gets the number of JSPs that have been unloaded.
	 * 
	 * <p>
	 * This info may be used for monitoring purposes.
	 * 
	 * @return The number of JSPs (in the webapp with which this JspServlet is associated) that have been unloaded
	 */
	public int getJspUnloadCount()
	{
		return this.rctxt.getJspUnloadCount();
	}

	/**
	 *<p> 
	 *判断是否只是预编译JSP文件为Servlet
	 *</p>
	 * <p>
	 * Look for a <em>precompilation request</em> as described in Section 8.4.2 of the JSP 1.2 Specification.
	 * <strong>WARNING</strong> - we cannot use <code>request.getParameter()</code> for this, because that will trigger
	 * parsing all of the request parameters, and not give a servlet the opportunity to call
	 * <code>request.setCharacterEncoding()</code> first.
	 * </p>
	 * 
	 * @param request
	 *            The servlet request we are processing
	 * 
	 * @exception ServletException
	 *                if an invalid parameter value for the <code>jsp_precompile</code> parameter name is specified
	 */
	boolean isPreCompile(HttpServletRequest request) throws ServletException
	{

		String queryString = request.getQueryString();
		if (queryString == null)
		{
			return (false);
		}
		int start = queryString.indexOf(Constants.PRECOMPILE);
		if (start < 0)
		{
			return (false);
		}
		queryString = queryString.substring(start + Constants.PRECOMPILE.length());
		if (queryString.length() == 0)
		{
			return (true); // ?jsp_precompile
		}
		if (queryString.startsWith("&"))
		{
			return (true); // ?jsp_precompile&foo=bar...
		}
		if (!queryString.startsWith("="))
		{
			return (false); // part of some other name or value
		}
		int limit = queryString.length();
		int ampersand = queryString.indexOf("&");
		if (ampersand > 0)
		{
			limit = ampersand;
		}
		String value = queryString.substring(1, limit);
		if (value.equals("true"))
		{
			return (true); // ?jsp_precompile=true
		}
		else if (value.equals("false"))
		{
			// Spec says if jsp_precompile=false, the request should not
			// be delivered to the JSP page; the easiest way to implement
			// this is to set the flag to true, and precompile the page anyway.
			// This still conforms to the spec, since it says the
			// precompilation request can be ignored.
			return (true); // ?jsp_precompile=false
		}
		else
		{
			throw new ServletException("Cannot have request parameter " + Constants.PRECOMPILE + " set to " + value);
		}

	}

	@Override
	public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		String jspUri = request.getRequestURI();

		if (jspUri == null)
		{
			// JSP specified via <jsp-file> in <servlet> declaration and supplied through
			// custom servlet container code
			jspUri = (String) request.getAttribute(Constants.JSP_FILE);
		}
		if (jspUri == null)
		{
			/*
			 * Check to see if the requested JSP has been the target of a RequestDispatcher.include()
			 */
			jspUri = (String) request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
			if (jspUri != null)
			{
				/*
				 * Requested JSP has been target of RequestDispatcher.include(). Its path is assembled from the relevant
				 * javax.servlet.include.* request attributes
				 */
				String pathInfo = (String) request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
				if (pathInfo != null)
				{
					jspUri += pathInfo;
				}
			}
			else
			{
				/*
				 * Requested JSP has not been the target of a RequestDispatcher.include(). Reconstruct its path from the
				 * request's getServletPath() and getPathInfo()
				 */
				jspUri = request.getServletPath();
				String pathInfo = request.getPathInfo();
				if (pathInfo != null)
				{
					jspUri += pathInfo;
				}
			}
		}

		if (log.isDebugEnabled())
		{
			log.debug("JspEngine --> " + jspUri);
			log.debug("\t     ServletPath: " + request.getServletPath());
			log.debug("\t        PathInfo: " + request.getPathInfo());
			log.debug("\t        RealPath: " + context.getRealPath(jspUri));
			log.debug("\t      RequestURI: " + request.getRequestURI());
			log.debug("\t     QueryString: " + request.getQueryString());
		}

		try
		{
			boolean isPreCompile = isPreCompile(request);		//判断是否只是预编译JSP文件为Servlet
			
			serviceJspFile(request, response, jspUri, isPreCompile);
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (ServletException e)
		{
			throw e;
		}
		catch (IOException e)
		{
			throw e;
		}
		catch (Throwable e)
		{
			ExceptionUtils.handleThrowable(e);
			throw new ServletException(e);
		}

	}

	@Override
	public void destroy()
	{
		if (log.isDebugEnabled())
		{
			log.debug("JspServlet.destroy()");
		}

		rctxt.destroy();
	}

	@Override
	public void periodicEvent()
	{
		rctxt.checkUnload();
		rctxt.checkCompile();
	}

	// -------------------------------------------------------- Private Methods

	/**
	 * 执行jsp的编译后的 JspServletWrapper 的 service() 方法
	 * @param request
	 * @param response
	 * @param jspUri
	 * @param isPreCompile
	 * @throws ServletException
	 * @throws IOException
	 */
	private void serviceJspFile(HttpServletRequest request, HttpServletResponse response, String jspUri, boolean isPreCompile) throws ServletException,
			IOException
	{

		JspServletWrapper wrapper = rctxt.getWrapper(jspUri);
		if (wrapper == null)
		{
			synchronized (this)
			{
				wrapper = rctxt.getWrapper(jspUri);
				if (wrapper == null)
				{
					// Check if the requested JSP page exists, to avoid
					// creating unnecessary directories and files.
					if (null == context.getResource(jspUri))
					{
						handleMissingResource(request, response, jspUri);				//找不到jsp文件
						return;
					}
					wrapper = new JspServletWrapper(config, options, jspUri, rctxt);
					rctxt.addWrapper(jspUri, wrapper);
				}
			}
		}

		try
		{
			wrapper.service(request, response, isPreCompile);
		}
		catch (FileNotFoundException fnfe)
		{
			handleMissingResource(request, response, jspUri);
		}

	}

	
	/**
	 * 无法找到jsp文件
	 * @param request
	 * @param response
	 * @param jspUri
	 * @throws ServletException
	 * @throws IOException
	 */
	private void handleMissingResource(HttpServletRequest request, HttpServletResponse response, String jspUri) throws ServletException, IOException
	{

		String includeRequestUri = (String) request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI);

		if (includeRequestUri != null)
		{
			// This file was included. Throw an exception as
			// a response.sendError() will be ignored
			String msg = Localizer.getMessage("jsp.error.file.not.found", jspUri);
			// Strictly, filtering this is an application
			// responsibility but just in case...
			throw new ServletException(SecurityUtil.filter(msg));
		}
		else
		{
			try		//生成404响应
			{
				response.sendError(HttpServletResponse.SC_NOT_FOUND, request.getRequestURI());
			}
			catch (IllegalStateException ise)
			{
				log.error(Localizer.getMessage("jsp.error.file.not.found", jspUri));
			}
		}
		return;
	}

}
