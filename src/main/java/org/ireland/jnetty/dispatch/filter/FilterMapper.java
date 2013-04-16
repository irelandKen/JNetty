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

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.ireland.jnetty.config.ConfigException;
import org.ireland.jnetty.dispatch.Invocation;
import org.ireland.jnetty.dispatch.filterchain.FilterFilterChain;

import java.util.ArrayList;
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

	private final ServletContext _servletContext;

	private final FilterManager _filterManager;

	//FilterMapping按web.xml中的<filter-mapping>顺序排列
	private ArrayList<FilterMapping> _filterMappings = new ArrayList<FilterMapping>();


	public FilterMapper(ServletContext servletContext,FilterManager filterManager)
	{
		_servletContext = servletContext;
		_filterManager = filterManager;
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
	 */
	public void addFilterMapping(FilterMapping mapping) throws ServletException
	{
		try
		{
			String filterName = mapping.getFilterConfig().getFilterName();

			if (filterName == null)
				filterName = mapping.getFilterConfig().getFilterClassName();

			
			//如果FilterMapping不存在,则添加到FilterMapping中
			if (mapping.getFilterConfig().getFilterClassName() != null && _filterManager.getFilter(filterName) == null)
			{
				_filterManager.addFilter(mapping.getFilterConfig());
			}

			if (_filterManager.getFilter(filterName) == null)
				throw new ConfigException(L.l("'{0}' is an unknown filter-name.  filter-mapping requires that the named filter be defined in a <filter> configuration before the <filter-mapping>.",filterName));

			_filterMappings.add(mapping);

			log.fine("filter-mapping " + mapping + " -> " + filterName);
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
	 * @param invocation
	 * @param chain
	 * @return
	 * @throws ServletException
	 */
	public FilterChain buildDispatchChain(Invocation invocation, FilterChain chain) throws ServletException
	{
		//TODO: why first matche the ServletName and the Match the URI? why not match the FilterMapping'ServletName and  FilterMapping'urlPattern as the same time?
		
		//根据ServletName去查找匹配的FilterMapping,并将其Filter实例 添加到FilterChain中
		synchronized (_filterMappings)
		{
			for (int i = _filterMappings.size() - 1; i >= 0; i--)
			{
				FilterMapping filterMapping = _filterMappings.get(i);

				if (filterMapping.isMatch(invocation.getServletName()))
				{
					FilterConfigImpl config = filterMapping.getFilterConfig();
					
					Filter filter = config.getInstance();

					if (!config.isAsyncSupported())
						invocation.clearAsyncSupported();

					chain = addFilter(chain, filter);
				}
			}
		}

		//根据uri去查找匹配的FilterMapping,并将其Filter实例 添加到FilterChain中
		synchronized (_filterMappings)
		{
			for (int i = _filterMappings.size() - 1; i >= 0; i--)
			{
				FilterMapping filterMapping = _filterMappings.get(i);

				if (filterMapping.isMatch(invocation))
				{
					FilterConfigImpl config = filterMapping.getFilterConfig();
					
					Filter filter = config.getInstance();

					if (!config.isAsyncSupported())
						invocation.clearAsyncSupported();

					chain = addFilter(chain, filter);
				}
			}
		}


		invocation.setFilterChain(chain);

		return chain;
	}

	/**
	 * Fills in the invocation.
	 */
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

	private FilterChain addFilter(FilterChain chain, Filter filter)
	{
		return new FilterFilterChain(chain, filter);
	}
}
