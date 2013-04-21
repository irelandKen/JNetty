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

import com.caucho.util.L10N;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.ireland.jnetty.config.ConfigException;
import org.ireland.jnetty.dispatch.Invocation;
import org.ireland.jnetty.dispatch.filterchain.FilterFilterChain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manages dispatching: servlets and filters.
 
  :TODO: rename "FilterMapper" TO "FilterMatcher"
  
  通常情况下,一个FilterMapper里存放FilterMappings会支持同一种Dispatcher.Type
  对于org.ireland.jnetty.webapp.WebApp里,对于每一个Dispatcher.Type都会有对应的独立的FilterMapper
  	
  	// The dispatch filter mapper                  (DispatcherType#REQUEST)
	private FilterMapper _dispatchFilterMapper;

	// The include filter mapper                   (DispatcherType#INCLUDE)
	private FilterMapper _includeFilterMapper;

	// The forward filter mapper                   (DispatcherType#FORWARD)
	private FilterMapper _forwardFilterMapper;

	// The error filter mapper                     (DispatcherType#ERROR)
	private FilterMapper _errorFilterMapper;
	
 * 
 * Filter匹配器
 */
public class FilterMapper
{
	private static final Logger log = Logger.getLogger(FilterMapper.class.getName());
	private static final L10N L = new L10N(FilterMapper.class);

	//本FilterMapper支持的匹配的DispatcherType
	private final DispatcherType _sameDispatcherType;
	
	private final ServletContext _servletContext;

	private final FilterManager _filterManager;

	//FilterMapping按web.xml中的<filter-mapping>顺序排列
	@Deprecated
	private ArrayList<FilterMapping> _filterMappings = new ArrayList<FilterMapping>();

	//持有<url-pattern>的<filter-mapping>对象
    private List<FilterMapping> _filterMappingsWithUrl = new ArrayList<FilterMapping>();;
    
    //持有<servlet-name>的<filter-mapping>对象, KEY: servlet-name,VALUE: List<FilterMapping>
    private Map<String,List<FilterMapping>> _filterMappingsWithServletName = new HashMap<String,List<FilterMapping>>();

	public FilterMapper(ServletContext servletContext,FilterManager filterManager,DispatcherType sameDispatcherType)
	{
		_servletContext = servletContext;
		_filterManager = filterManager;
		_sameDispatcherType = sameDispatcherType;
	}

	//getter & setter------------------------------------------------------------------
	/**
	 * Gets the servlet context.
	 */
	public ServletContext getServletContext()
	{
		return _servletContext;
	}

	/**
	 * Returns the filter manager.
	 */
	public FilterManager getFilterManager()
	{
		return _filterManager;
	}
	//getter & setter------------------------------------------------------------------

	
	

	//---------------------------------------------------------------------------------
	/**
	 * Adds a filter mapping
	 * 
	 * 被添加的FilterMapping必需持有共同的DispatcherType.
	 * 
	 */
	public void addFilterMapping(FilterMapping filterMapping) throws ServletException
	{
		try
		{
			if(!filterMapping.matchDispatcherType(_sameDispatcherType))
				throw new IllegalArgumentException("The FilterMapping to be added should match "+_sameDispatcherType);
			
			FilterConfigImpl filterConfig = filterMapping.getFilterConfig();
			
			String filterName = filterConfig.getFilterName();

			if (filterName == null)
				filterName = filterConfig.getFilterClassName();

			
			//如果FilterMapping不存在,则添加到FilterMapping中
			if (filterConfig.getFilterClassName() != null && _filterManager.getFilter(filterName) == null)
			{
				_filterManager.addFilter(filterConfig);
			}

			if (_filterManager.getFilter(filterName) == null)
				throw new ConfigException(L.l("'{0}' is an unknown filter-name.  filter-mapping requires that the named filter be defined in a <filter> configuration before the <filter-mapping>.",filterName));

			//持有<url-pattern>元素,添加至_filterMappingsWithUrl
			if(filterMapping.getURLPatterns() != null && !filterMapping.getURLPatterns().isEmpty())
			{
				_filterMappingsWithUrl.add(filterMapping);
			}
			
			//持有<servlet-name>元素,添加至_filterMappingsWithUrl
			if(filterMapping.getServletNames() != null && !filterMapping.getServletNames().isEmpty())
			{
				for(String servletName : filterMapping.getServletNames())
				{
					List<FilterMapping> list = _filterMappingsWithServletName.get(servletName);
					
					if(list == null)
					{
						list = new ArrayList<FilterMapping>();
						
						_filterMappingsWithServletName.put(servletName, list);
					}
					
					list.add(filterMapping);
				}
			}
			

			log.fine("filter-mapping " + filterMapping + " -> " + filterName);
		}
		catch (Exception e)
		{
			throw new ServletException(e);
		}
	}



	/**
	 * 
	 * Fills in the invocation.
	 * 
	 * 容器使用的用于构建应用到一个特定请求URI的过滤器链的顺序如下所示：
	 * 1. 首先，    <url-pattern>按照在部署描述符中的出现顺序匹配过滤器映射。
     * 2. 接下来，<servlet-name>按照在部署描述符中的出现顺序匹配过滤器映射。
	 * @param invocation
	 * @param chain
	 * @return
	 * @throws ServletException
	 */
	public FilterChain buildDispatchChain(Invocation invocation, FilterChain chain) throws ServletException
	{
		//TODO: why first matche the ServletName and the Match the URI? why not match the FilterMapping'ServletName and  FilterMapping'urlPattern as the same time?
		
		//根据<servlet-name>去查找匹配的FilterMapping,并将其Filter实例 添加到FilterChain中
		if(_filterMappingsWithServletName.size() > 0)
		{
			synchronized(_filterMappingsWithServletName)
			{
				//<servlet-name>*</servlet-name>会匹配所有Servlet
				List<FilterMapping> mappings = 	_filterMappingsWithServletName.get("*");
				
				if(mappings != null)
				{
					for (int i = mappings.size() - 1; i >= 0; i--)
					{
						FilterMapping filterMapping = mappings.get(i);
		
						chain = addFilter(invocation, chain, filterMapping);
					}
				}
				
				
				//查找 指定servletName匹配的FilterMapping,并将其Filter实例 添加到FilterChain中
				String servletName = invocation.getServletName();
				
				mappings = 	_filterMappingsWithServletName.get(servletName);
				
				if(mappings != null)
				{
					for (int i = mappings.size() - 1; i >= 0; i--)
					{
						FilterMapping filterMapping = mappings.get(i);
		
						chain = addFilter(invocation, chain, filterMapping);
					}
				}
			}
		}

		//根据<url-pattern>去查找匹配的FilterMapping,并将其Filter实例 添加到FilterChain中
		if(_filterMappingsWithUrl.size() > 0)
		{
			synchronized (_filterMappingsWithUrl)
			{
				for (int i = _filterMappingsWithUrl.size() - 1; i >= 0; i--)
				{
					FilterMapping filterMapping = _filterMappingsWithUrl.get(i);
	
					if (filterMapping.isMatch(invocation))
					{
						chain = addFilter(invocation, chain, filterMapping);
					}
				}
			}
		}
			

		invocation.setFilterChain(chain);

		return chain;
	}
	


	/**
	 * Fills in the invocation.
	 */
	@Deprecated
	public FilterChain buildFilterChain(FilterChain chain, String servletName) throws ServletException
	{
		//根据ServletName去查找匹配的FilterMapping,并将其Filter实例 添加到FilterChain中
		synchronized (_filterMappings)
		{
			for (int i = _filterMappings.size() - 1; i >= 0; i--)
			{
				FilterMapping filterMapping = _filterMappings.get(i);

				if (filterMapping.isMatch(servletName))
				{
					FilterConfigImpl config = filterMapping.getFilterConfig();
					
					Filter filter = config.getInstance();

					chain = addFilter(chain, filter);
				}
			}
		}

		return chain;
	}

	/**
	 * 
	 * @param invocation
	 * @param chain
	 * @param filterMapping
	 * @return	增加了FilterChain节点的新的FilterChain
	 * @throws ServletException
	 */
	private FilterChain addFilter(Invocation invocation,FilterChain chain, FilterMapping filterMapping) throws ServletException
	{
		FilterConfigImpl config = filterMapping.getFilterConfig();
		
		Filter filter = config.getInstance();

		if (!config.isAsyncSupported())
			invocation.clearAsyncSupported();
		
		return addFilter(chain, filter);
	}
	
	private FilterChain addFilter(FilterChain chain, Filter filter)
	{
		return new FilterFilterChain(chain, filter);
	}

	@Override
	public String toString()
	{
		return "FilterMapper[" + _sameDispatcherType + "]";
	}
}
