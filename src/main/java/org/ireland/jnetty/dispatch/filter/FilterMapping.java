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

import com.caucho.util.CharBuffer;
import com.caucho.util.L10N;

import javax.annotation.PostConstruct;
import javax.servlet.ServletException;
import javax.servlet.DispatcherType;

import org.ireland.jnetty.dispatch.ServletInvocation;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * Configuration for a filter. 代表一个<filter-mapping>元素
 	
 	<filter-mapping>
		<filter-name>EncodeFilter</filter-name>
		<url-pattern>/FirstServlet</url-pattern>
		<url-pattern>/*</url-pattern>
		<dispatcher>REQUEST</dispatcher>
		<dispatcher>ASYNC</dispatcher>
		<dispatcher>FORWARD</dispatcher>
	 </filter-mapping>
	 
 	<filter-mapping>
		<filter-name>EncodeFilter</filter-name>
		<servlet-name>FirstServlet</servlet-name>
		<servlet-name>SecondServlet</servlet-name>
		<dispatcher>FORWARD</dispatcher>
		<dispatcher>REQUEST</dispatcher>
	</filter-mapping>
	
 */
public class FilterMapping
{
	static L10N L = new L10N(FilterMapping.class);


	private final FilterConfigImpl filterConfig;

	private boolean isCaseInsensitive = true;

	
	//urlPatterns按<filter-mapping>里的顺序排列
	private final LinkedHashSet<String> _urlPatterns = new LinkedHashSet<String>();

	//servletNames按<filter-mapping>里的顺序排列(we sure ServletName不会重复)
	private final ArrayList<String> _servletNames = new ArrayList<String>();
	
	private HashSet<DispatcherType> _dispatcherTypes;

	

	/**
	 * Creates a new filter mapping object.
	 */
	public FilterMapping(FilterConfigImpl filterConfig)
	{
		Assert.notNull(filterConfig);
		
		this.filterConfig = filterConfig;
	}

	public FilterConfigImpl getFilterConfig()
	{
		return filterConfig;
	}



	/**
	 * Gets the url patterns
	 */

	public HashSet<String> getURLPatterns()
	{
		return _urlPatterns;
	}
	
	public void addURLPattern(String pattern)
	{
		_urlPatterns.add(pattern);
	}



	/**
	 * Sets the servlet name
	 */
	public void addServletName(String servletName)
	{
		if (servletName == null)
			throw new NullPointerException();

		_servletNames.add(servletName);
	}

	public ArrayList<String> getServletNames()
	{
		return _servletNames;
	}

	/**
	 * Adds a dispatcher.
	 */
	public void addDispatcherType(String dispatherType)
	{
		addDispatcherType(DispatcherType.valueOf(dispatherType));
	}

	public void addDispatcherType(DispatcherType dispatherType)
	{
		if (_dispatcherTypes == null)
			_dispatcherTypes = new HashSet<DispatcherType>();

		_dispatcherTypes.add(dispatherType);
	}

	/**
	 * True if the dispatcher is for REQUEST.(未指定dispatcherType时,默认是REQUEST)
	 */
	public boolean isRequest()
	{
		return _dispatcherTypes == null || _dispatcherTypes.contains(DispatcherType.REQUEST);
	}

	/**
	 * True if the dispatcher is for INCLUDE.
	 */
	public boolean isInclude()
	{
		return _dispatcherTypes != null && _dispatcherTypes.contains(DispatcherType.INCLUDE);
	}

	/**
	 * True if the dispatcher is for FORWARD.
	 */
	public boolean isForward()
	{
		return _dispatcherTypes != null && _dispatcherTypes.contains(DispatcherType.FORWARD);
	}

	/**
	 * True if the dispatcher is for ERROR.
	 */
	public boolean isError()
	{
		return _dispatcherTypes != null && _dispatcherTypes.contains(DispatcherType.ERROR);
	}
	
	/**
	 * True if the dispatcher match specify dispatcherType .
	 */
	public boolean matchDispatcherType(DispatcherType dispatcherType)
	{
		Assert.notNull(dispatcherType);
		
		if(_dispatcherTypes == null)
		{
			return dispatcherType.equals(DispatcherType.REQUEST);
		}
		
		//_dispatcherTypes != null
		return _dispatcherTypes.contains(dispatcherType);
	}

	/**
	 * Returns true if the filter map matches the invocation URL.
	 * 
	 * @param servletName
	 *            the servlet name to match
	 */
	boolean isMatch(String servletName)
	{
		for (int i = 0; i < _servletNames.size(); i++)
		{
			String matchName = _servletNames.get(i);

			if (matchName.equals(servletName) || "*".equals(matchName))
				return true;
		}

		return false;
	}

	/**
	 * Returns true if the filter map matches the invocation URL.
	 * 
	 * @param invocation
	 *            the request's invocation
	 */
	boolean isMatch(ServletInvocation invocation)
	{
		return isMatch(invocation.getServletPath(), invocation.getPathInfo());
	}

	/**
	 * Returns true if the filter map matches the servlet path and path info.
	 * */
	public boolean isMatch(String servletPath, String pathInfo)
	{
		String uri;

		if (pathInfo == null)
			uri = servletPath;
		else if (servletPath == null)
			uri = pathInfo;
		else
			uri = servletPath + pathInfo;

		for(String urlPattern : _urlPatterns)
		{
			if(matchFiltersURL(urlPattern, uri))
				return true;
		}

		return false;
	}

	
	/**
	 * Returns a printable representation of the filter config object.
	 */
	public String toString()
	{
		return "FilterMapping[pattern=" + _urlPatterns + ",name=" + getFilterConfig().getFilterName() + "]";
	}


	//util Method---------------------------------------------------------------------------
	
    /**
     * Return <code>true</code> if the context-relative request path
     * matches the requirements of the specified filter mapping;
     * otherwise, return <code>false</code>.
     *
     * @param urlPattern URL mapping being checked
     * @param requestPath Context-relative request path of this request(without query String)
     */
    private static boolean matchFiltersURL(String urlPattern, String requestPath) {
        
        if (urlPattern == null)
            return (false);

        // Case 1 - Exact Match
        if (urlPattern.equals(requestPath))
            return (true);

        // Case 2 - Path Match ("/.../*")
        if (urlPattern.equals("/*"))
            return (true);
        if (urlPattern.endsWith("/*")) {
            if (urlPattern.regionMatches(0, requestPath, 0, 
                                       urlPattern.length() - 2)) {
                if (requestPath.length() == (urlPattern.length() - 2)) {
                    return (true);
                } else if ('/' == requestPath.charAt(urlPattern.length() - 2)) {
                    return (true);
                }
            }
            return (false);
        }

        // Case 3 - Extension Match
        if (urlPattern.startsWith("*.")) {
            int slash = requestPath.lastIndexOf('/');
            int period = requestPath.lastIndexOf('.');
            if ((slash >= 0) && (period > slash) 
                && (period != requestPath.length() - 1)
                && ((requestPath.length() - period) 
                    == (urlPattern.length() - 1))) {
                return (urlPattern.regionMatches(2, requestPath, period + 1,
                                               urlPattern.length() - 2));
            }
        }

        // Case 4 - "Default" Match
        return (false); // NOTE - Not relevant for selecting filters

    }
}
