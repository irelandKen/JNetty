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

package org.ireland.jnetty.dispatch.filter;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.FilterRegistration;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;

import org.ireland.jnetty.config.ConfigException;
import org.ireland.jnetty.dispatch.servlet.ServletConfigImpl;
import org.ireland.jnetty.webapp.WebApp;

import com.caucho.util.L10N;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuration for a filter.
 * 
 * 表示一个Filter的所有配置(代表一个web.xml的<filter>标签)
 	<filter>
		<description>this is EncodeFilter</description>
		<display-name>EncodeFilter</display-name>
		
		<filter-name>EncodeFilter</filter-name>
		<filter-class>com.ireland.filters.EncodeFilter</filter-class>
		<async-supported>true</async-supported>
		
		<init-param>
			<param-name>name</param-name>
			<param-value>jack</param-value>
		</init-param>
		<init-param>
			<param-name>pwd</param-name>
			<param-value>1234</param-value>
		</init-param>
	</filter>

 */
public class FilterConfigImpl implements FilterConfig, FilterRegistration.Dynamic
{
	static L10N L = new L10N(ServletConfigImpl.class);
	protected static final Logger log = Logger.getLogger(FilterConfigImpl.class.getName());
	
	private WebApp _webApp;

	private ServletContext _servletContext;
	
	private FilterManager _filterManager;
	
	
	//web.xml 配置
	private String _filterName;

	private String _filterClassName;

	private Class<? extends Filter> _filterClass;

	private String _displayName;
	
	private HashMap<String, String> _initParams = new HashMap<String, String>();
	
	private boolean _isAsyncSupported;
	
	

	
	//Filter的实例(单例)
	private Filter _filter;
	

	/**
	 * Creates a new filter configuration object.
	 */
	public FilterConfigImpl()
	{
	}

	/**
	 * Sets the filter name.
	 */
	public void setFilterName(String name)
	{
		_filterName = name;
	}

	/**
	 * Gets the filter name.
	 */
	@Override
	public String getFilterName()
	{
		return _filterName;
	}

	/**
	 * Sets the filter class.
	 */
	public void setFilterClass(String filterClassName) throws ClassNotFoundException
	{
		_filterClassName = filterClassName;

		_filterClass = (Class<? extends Filter>) _webApp.getClassLoader().loadClass(filterClassName);

	}

	public void setFilterClass(Class<? extends Filter> filterClass)
	{
		_filterClass = filterClass;
	}

	/**
	 * Gets the filter name.
	 */
	public Class<? extends Filter> getFilterClass()
	{
		return _filterClass;
	}

	/**
	 * Gets the filter name.
	 */
	public String getFilterClassName()
	{
		return _filterClassName;
	}


	public void setFilter(Filter filter)
	{
		_filter = filter;
	}

	/**
	 * Instantiates a Filter given its configuration.
	 * 
	 * 返回Filter的实例(单例的,多次调用返回同一个实例)
	 * 
	 * 1:如果Filter实例不存在,则创建一个Filter实例,
	 *   并初始化它(调用Filter#init(FilterConfig config)
	 * 
	 * 2:返回已初始化的Filter实例.
	 * 
	 * 
	 * @return the initialized servlet.
	 */
	public Filter getInstance() throws Exception
	{
		// server/102e
		if (_filter != null)
			return _filter;

		_filter = createFilterAndInit();
		
		return _filter;
	}

	/*
	 * 
	 * 
	 * 实例化一个Filter,并初始化它
	 * 
	 * Filter.init(this);
	 */
	private Filter createFilterAndInit() throws Exception
	{

		Class<? extends Filter> filterClass = getFilterClass();

		Filter filter;

		if (filterClass == null)
			throw new NullPointerException(L.l("Null servlet class for '{0}'.", _filterName));

		
		try
		{
			filter = filterClass.newInstance();
		}
		catch (Exception e)
		{
			throw new ServletException(e);
		}
			

		// 配置Filter
		configureFilter(filter);

		//初始化
		filter.init(this);

		if (log.isLoggable(Level.FINE))
			log.finer("Filter[" + _filterName + "] instantiated and inited");
		
		return filter;
	}

	/**
	 * Configure the servlet (everything that is done after instantiation but before servlet.init()
	 */
	void configureFilter(Object servlet)
	{

	}
	
	/**
	 * Sets an init-param
	 */
	public void addInitParam(String param, String value)
	{
		_initParams.put(param, value);
	}

	/**
	 * Gets the init params
	 */
	public Map<String, String> getInitParamMap()
	{
		return _initParams;
	}

	/**
	 * Gets the init params
	 */
	@Override
	public String getInitParameter(String name)
	{
		return _initParams.get(name);
	}

	/**
	 * Gets the init params
	 */
	@Override
	public Enumeration<String> getInitParameterNames()
	{
		return Collections.enumeration(_initParams.keySet());
	}

	public void setWebApp(WebApp webApp)
	{
		_webApp = webApp;
	}

	/**
	 * Returns the servlet context.
	 */
	public ServletContext getServletContext()
	{
		return _servletContext;
	}

	/**
	 * Sets the servlet context.
	 */
	public void setServletContext(ServletContext app)
	{
		_servletContext = app;
	}

	public FilterManager getFilterManager()
	{
		return _filterManager;
	}

	public void setFilterManager(FilterManager filterManager)
	{
		_filterManager = filterManager;
	}

	/**
	 * Sets the display name
	 */
	public void setDisplayName(String displayName)
	{
		_displayName = displayName;
	}

	/**
	 * Gets the display name
	 */
	public String getDisplayName()
	{
		return _displayName;
	}

	/**
	 * 添加基于ServletName的映射关系
	<filter-mapping>
		<filter-name>EncodeFilter</filter-name>
		<servlet-name>FirstServlet</servlet-name>
		<servlet-name>SecondServlet</servlet-name>
		<dispatcher>FORWARD</dispatcher>
		<dispatcher>REQUEST</dispatcher>
	</filter-mapping>
	 */
	public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... servletNames)
	{

		try
		{
			FilterMapping mapping = new FilterMapping(this);

			if (dispatcherTypes != null)
			{
				for (DispatcherType dispatcherType : dispatcherTypes)
				{
					mapping.addDispatcherType(dispatcherType);
				}
			}

			for (String servletName : servletNames)
				mapping.addServletName(servletName);

			_webApp.addFilterMapping(mapping);
		}
		catch (Exception e)
		{
			// XXX: needs better exception handling
			throw new ConfigException(e.getMessage(), e);
		}
	}

	public Collection<String> getServletNameMappings()
	{
		Set<String> names = _filterManager.getServletNameMappings(_filterName);

		if (names == null)
			return Collections.EMPTY_SET;

		return Collections.unmodifiableSet(names);
	}

	/**
	 * 添加基于URL的映射关系
	 <filter-mapping>
		<filter-name>EncodeFilter</filter-name>
		<url-pattern>/FirstServlet</url-pattern>
		<url-pattern>/*</url-pattern>
		<dispatcher>REQUEST</dispatcher>
		<dispatcher>ASYNC</dispatcher>
		<dispatcher>FORWARD</dispatcher>
	 </filter-mapping>
	
	 */
	public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... urlPatterns)
	{

		try
		{
			FilterMapping mapping = new FilterMapping(this);

			if (dispatcherTypes != null)
			{
				for (DispatcherType dispatcherType : dispatcherTypes)
				{
					mapping.addDispatcherType(dispatcherType);
				}
			}

			FilterMapping.URLPattern urlPattern = mapping.createUrlPattern();

			for (String pattern : urlPatterns)
			{
				urlPattern.addText(pattern);
			}

			urlPattern.init();

			_webApp.addFilterMapping(mapping); 
		}
		catch (Exception e)
		{
			// XXX: needs better exception handling
			throw new ConfigException(e.getMessage(), e);
		}
	}

	public Collection<String> getUrlPatternMappings()
	{
		Set<String> patterns = _filterManager.getUrlPatternMappings(_filterName);

		if (patterns == null)
			return Collections.EMPTY_SET;

		return Collections.unmodifiableSet(patterns);
	}

	public String getName()
	{
		return _filterName;
	}

	public String getClassName()
	{
		return _filterClassName;
	}

	public boolean setInitParameter(String name, String value)
	{

		if (_initParams.containsKey(name))
			return false;

		_initParams.put(name, value);

		return true;
	}

	public Set<String> setInitParameters(Map<String, String> initParameters)
	{

		Set<String> conflicts = new HashSet<String>();

		for (Map.Entry<String, String> parameter : initParameters.entrySet())
		{
			if (_initParams.containsKey(parameter.getKey()))
				conflicts.add(parameter.getKey());
			else
				_initParams.put(parameter.getKey(), parameter.getValue());
		}

		return conflicts;
	}

	public Map<String, String> getInitParameters()
	{
		return _initParams;
	}

	public void setAsyncSupported(boolean isAsyncSupported)
	{
		_isAsyncSupported = isAsyncSupported;
	}

	public boolean isAsyncSupported()
	{
		return _isAsyncSupported;
	}

	/**
	 * Sets the description
	 */
	public void setDescription(String description)
	{
	}

	/**
	 * Sets the icon
	 */
	public void setIcon(String icon)
	{
	}

	/**
	 * Returns a printable representation of the filter config object.
	 */
	public String toString()
	{
		return "FilterConfigImpl[name=" + _filterName + ",class=" + _filterClass + "]";
	}


}
