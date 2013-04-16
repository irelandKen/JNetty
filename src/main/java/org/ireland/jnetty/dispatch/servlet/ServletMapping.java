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

import javax.servlet.ServletException;

import org.springframework.util.Assert;



import com.caucho.config.ConfigException;
import com.caucho.util.L10N;

/**
 * Configuration for a servlet.
 * 
 * 代表web.xml中一个"<servlet-mapping>"元素
 * 
 *   <servlet-mapping>
 *       <servlet-name>MyServlet</servlet-name>
 *       <url-pattern>/myservlet.do</url-pattern>
 *       <url-pattern>/my/*</url-pattern>
 *       <url-pattern>/mys/*</url-pattern>
 *   </servlet-mapping>
 * 
 */
public class ServletMapping
{
	private static final L10N L = new L10N(ServletMapping.class);

	//<servlet>
	private final ServletConfigImpl servletConfig;
	
	//<url-pattern> 列表
	private ArrayList<String> _urlPatternList = new ArrayList<String>();

	//是否严格匹配
	private boolean _isStrictMapping;

	private boolean _ifAbsent;
	private boolean _isDefault;

	/**
	 * Creates a new servlet mapping object.
	 */
	public ServletMapping(ServletConfigImpl servletConfig)
	{
		Assert.notNull(servletConfig);
		
		this.servletConfig = servletConfig;
	}

	public ServletConfigImpl getServletConfig()
	{
		return servletConfig;
	}


	public void setIfAbsent(boolean ifAbsent)
	{
		_ifAbsent = ifAbsent;
	}

	/**
	 * Sets the url pattern
	 */
	public void addURLPattern(String pattern)
	{
		if (pattern.indexOf('\n') > -1)
		{
			throw new ConfigException(
					L.l("'url-pattern' cannot contain newline"));
		}

		_urlPatternList.add(pattern);

		// server/13f4
		if (servletConfig.getServletNameDefault() == null)
			servletConfig.setServletNameDefault(pattern);
	}

	/**
	 * True if strict mapping should be enabled.
	 */
	public boolean isStrictMapping()
	{
		return _isStrictMapping;
	}

	/**
	 * Set if strict mapping should be enabled.
	 */
	public void setStrictMapping(boolean isStrictMapping)
	{
		_isStrictMapping = isStrictMapping;
	}

	/**
	 * Set for default mapping that can be overridden by programmatic mapping.
	 */
	public void setDefault(boolean isDefault)
	{
		_isDefault = isDefault;
	}

	/**
	 * True for default mapping that can be overridden by programmatic mapping.
	 */
	public boolean isDefault()
	{
		return _isDefault;
	}

	/**
	 * initialize.
	 */
	public void init(ServletMapper mapper) throws ServletException
	{
		boolean hasInit = false;

		if (servletConfig.getServletName() == null)
			servletConfig.setServletName(servletConfig.getServletNameDefault());

		for (int i = 0; i < _urlPatternList.size(); i++)
		{

			String urlPattern = _urlPatternList.get(i);

			if (servletConfig.getServletName() == null && servletConfig.getServletClassName() != null && urlPattern != null)
			{
				servletConfig.setServletName(urlPattern);
			}

			if (urlPattern != null && !hasInit)
			{
				hasInit = true;
				servletConfig.init();

				if (servletConfig.getServletClass() != null)
					mapper.getServletManager().addServlet(servletConfig);
			}

			if (urlPattern != null)
				mapper.addUrlMapping(urlPattern, this,_ifAbsent);
		}

	}


	// ------------------------------------------------------------

	/**
	 * Returns a printable representation of the servlet config object.
	 */
	public String toString()
	{
		StringBuilder builder = new StringBuilder();

		builder.append("ServletMapping[");

		for (int i = 0; i < _urlPatternList.size(); i++)
		{
			String urlPattern = _urlPatternList.get(i);

			if (urlPattern != null)
			{
				builder.append("url-pattern=");
				builder.append(urlPattern);
				builder.append(", ");
			}
		}

		builder.append("name=");
		builder.append(servletConfig.getServletName());

		if (servletConfig.getServletClassName() != null)
		{
			builder.append(", class=");
			builder.append(servletConfig.getServletClassName());
		}

		builder.append("]");

		return builder.toString();
	}

}
