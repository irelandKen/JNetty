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

package org.ireland.jnetty.dispatch.servlet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;

import org.ireland.jnetty.config.ConfigException;
import org.ireland.jnetty.dispatch.ServletInvocation;
import org.ireland.jnetty.dispatch.filterchain.ErrorFilterChain;
import org.ireland.jnetty.util.http.UrlMap;
import org.ireland.jnetty.webapp.WebApp;

import com.caucho.util.L10N;

/**
 * Manages dispatching: servlets and filters.
 * :TODO: rename "ServletMapper" TO "ServletMatcher"
 * 
 * Servlet匹配器
 * 
 */
public class ServletMapper
{
	private static final Logger LOG = Logger.getLogger(ServletMapper.class.getName());
	
	private static final L10N L = new L10N(ServletMapper.class);


	private WebApp _webApp;

	private ServletManager _servletManager;

	
	//<urlPattern, ServletMapping>
	private UrlMap<ServletMapping> _servletMappings = new UrlMap<ServletMapping>();


	
	//记录 ServletName 到 urlPattern 之间的映射关系 
	// Servlet 3.0 maps serletName to urlPattern   <serletName,Set<urlPattern>>
	private Map<String, Set<String>> _urlPatterns = new HashMap<String, Set<String>>();

	
	
	//记录 urlPattern 到 <servlet-mapping>的映射关系
	// Servlet 3.0 urlPattern to servletName  <urlPattern,ServletMapping>
	private Map<String, ServletMapping> _servletNamesMap = new HashMap<String, ServletMapping>();

	
	
	//默认的Servlet(urlPattern为"/",当无法找到匹配的Servlet或jsp时,则默认匹配的Servlet)
	private String _defaultServlet;
	
	
	
	public ServletMapper(WebApp webApp)
	{
		_webApp = webApp;
	}

//Getter and Setter---------------------------------------------------	
	/**
	 * Gets the servlet context.
	 */
	public WebApp getWebApp()
	{
		return _webApp;
	}

	/**
	 * Returns the servlet manager.
	 */
	public ServletManager getServletManager()
	{
		return _servletManager;
	}

	/**
	 * Sets the servlet manager.
	 */
	public void setServletManager(ServletManager manager)
	{
		_servletManager = manager;
	}
//Getter and Setter---------------------------------------------------	
	
	

	/**
	 * Adds a servlet mapping
	 * 
	 * 增加  urlPattern + " -> " + servletName 的映射关系
	 * 
	 */
	void addUrlMapping(final String urlPattern, String servletName,ServletMapping mapping, boolean ifAbsent) throws ServletException
	{
		try
		{
			boolean isIgnore = false;


			if (servletName == null)
			{
				throw new ConfigException(L.l("servlets need a servlet-name."));
			}
			else if (_servletManager.getServlet(servletName) == null)
				throw new ConfigException(L.l("'{0}' is an unknown servlet-name.  servlet-mapping requires that the named servlet be defined in a <servlet> configuration before the <servlet-mapping>.",
								servletName));

			
			if ("/".equals(urlPattern))			// "/":无法找到匹配时,默认匹配的Servlet
			{
				_defaultServlet = servletName;
			} 
			else if (mapping.isStrictMapping())
			{
				_servletMappings.addStrictMap(urlPattern, null, mapping);
			} 
			else
				_servletMappings.addMap(urlPattern, mapping, isIgnore, ifAbsent);

			Set<String> patterns = _urlPatterns.get(servletName);

			if (patterns == null)
			{
				patterns = new HashSet<String>();

				_urlPatterns.put(servletName, patterns);
			}

			_servletNamesMap.put(urlPattern, mapping);

			patterns.add(urlPattern);

			LOG.config("servlet-mapping " + urlPattern + " -> " + servletName);
		} catch (ServletException e)
		{
			throw e;
		} catch (RuntimeException e)
		{
			throw e;
		} catch (Exception e)
		{
			throw ConfigException.create(e);
		}
	}

	public Set<String> getUrlPatterns(String servletName)
	{
		return _urlPatterns.get(servletName);
	}

	/**
	 * Sets the default servlet.
	 * 4. 如果前三个规则都没有产生一个servlet匹配，容器将试图为请求资源提供相关的内容。如果应用中定义了一个“default”servlet，它将被使用。许多容器提供了一种隐式的default servlet用于提供内容。
	 */
	public void setDefaultServlet(String servletName) throws ServletException
	{
		_defaultServlet = servletName;
	}

	
	/**
	 * 查找 ServletInvocation 所匹配 的 Servlet,并返回生成的FilterChain

用于映射到Servlet的路径是请求对象的请求URL减去上下文和路径参数部分。下面的URL路径映射规则按顺序使用。使用第一个匹配成功的且不会进一步尝试匹配：

1. 容器将尝试找到一个请求路径到servlet路径的精确匹配。成功匹配则选择该servlet。

2. 容器将递归地尝试匹配最长路径前缀。这是通过一次一个目录的遍历路径树完成的，使用‘/’字符作为路径分隔符。最长匹配确定选择的servlet。

3. 如果URL最后一部分包含一个扩展名（如 .jsp），servlet容器将视图匹配为扩展名处理请求的Servlet。扩展名定义在最后一部分的最后一个‘.’字符之后。

4. 如果前三个规则都没有产生一个servlet匹配，容器将试图为请求资源提供相关的内容。如果应用中定义了一个“default”servlet，它将被使用。许多容器提供了一种隐式的default servlet用于提供内容。

	 * @param invocation
	 * @return
	 * @throws ServletException
	 */
	public FilterChain mapServlet(ServletInvocation invocation)throws ServletException
	{
		String contextURI = invocation.getContextURI();

		String servletName = null;
		
		ArrayList<String> vars = new ArrayList<String>();

		ServletConfigImpl config = null;

		
		//1:查找是否存在和url相匹配的Servlet
		if (_servletMappings != null)
		{
			ServletMapping servletMapping = _servletMappings.map(contextURI);

			if (servletMapping != null && servletMapping.getServletConfig().isServletConfig())
				config = servletMapping.getServletConfig();

			if (servletMapping != null)
			{
				servletName = servletMapping.getServletConfig().getServletName();
			}
		}



		//2:默认的Servlet(urlPattern为"/",当无法找到匹配的Servlet或jsp时,则默认匹配的Servlet)
		if (servletName == null)
		{
			servletName = _defaultServlet;
			vars.clear();

			vars.add(contextURI);
		}

		//3:无法找到合适的Servlet,返回404
		if (servletName == null)
		{
			LOG.fine(L.l("'{0}' has no default servlet defined", contextURI));

			return new ErrorFilterChain(404);
		}

		String servletPath = contextURI; //TODO: how to decide ?

		invocation.setServletPath(servletPath);

		if (servletPath.length() < contextURI.length())
			invocation.setPathInfo(contextURI.substring(servletPath.length()));
		else
			invocation.setPathInfo(null);


		invocation.setServletName(servletName);

		if (LOG.isLoggable(Level.FINER))
		{
			LOG.finer(_webApp + " map (uri:" + contextURI + " -> " + servletName + ")");
		}

		
		//创建FilterChain
		FilterChain chain;
		if(config != null)
			chain = _servletManager.createServletChain(config, invocation);
		else
			chain = _servletManager.createServletChain(servletName,invocation);

		
		//JSP
/*		if (chain instanceof PageFilterChain)
		{
			PageFilterChain pageChain = (PageFilterChain) chain;

			chain = PrecompilePageFilterChain.create(invocation, pageChain);
		}*/

		return chain;
	}



	

	public String getServletPattern(String uri)
	{
		ArrayList<String> vars = new ArrayList<String>();

		Object value = null;

		if (_servletMappings != null)
			value = _servletMappings.map(uri, vars);

		if (value != null)
			return uri;
		else
			return null;
	}

	public String getServletClassByUri(String uri)
	{

		ServletMapping value = null;

		if (_servletMappings != null)
			value = _servletMappings.map(uri);

		if (value != null)
		{
			Class<?> servletClass = value.getServletConfig().getServletClass();

			if (servletClass != null)
				return servletClass.getName();
			else
			{
				String servletName = value.getServletConfig().getServletName();

				ServletConfigImpl config = _servletManager
						.getServlet(servletName);

				if (config != null)
					return config.getServletClassName();
				else
					return servletName;
			}
		} else
			return null;
	}

	/**
	 * Returns the servlet matching patterns.
	 */
	public ArrayList<String> getURLPatterns()
	{
		ArrayList<String> patterns = _servletMappings.getURLPatterns();

		return patterns;
	}

	public ServletMapping getServletMapping(String pattern)
	{
		return _servletNamesMap.get(pattern);
	}

	

	private void addServlet(String servletName) throws ServletException
	{
		if (_servletManager.getServlet(servletName) != null)
			return;

		ServletConfigImpl config = new ServletConfigImpl();
		config.setServletContext(_webApp);
		config.setServletName(servletName);

		try
		{
			config.setServletClass(servletName);
		} catch (RuntimeException e)
		{
			throw e;
		} catch (Exception e)
		{
			throw new ServletException(e);
		}

		config.init();

		_servletManager.addServlet(config);
	}

	public void destroy()
	{
		_servletManager.destroy();
	}

}
