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

import io.netty.handler.codec.http.QueryStringDecoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.ireland.jnetty.dispatch.Invocation;

import com.caucho.util.L10N;

public class ForwardRequest extends HttpServletRequestWrapper
{
	private static final L10N L = new L10N(ForwardRequest.class);


	private Invocation _invocation;

	// the wrapped request
	private HttpServletRequest _request;

	private HttpServletResponse _response;

	//paremeters from the QueryString of forward + Original Parameters
	private Map<String, String[]> _parameters;

	public ForwardRequest(HttpServletRequest request)
	{
		super(request);
	}

	public ForwardRequest(HttpServletRequest request, HttpServletResponse response, Invocation invocation)
	{
		super(request);

		_request = request;
		_response = response;

		_invocation = invocation;
	}

	protected Invocation getInvocation()
	{
		return _invocation;
	}


	public HttpServletResponse getResponse()
	{
		return _response;
	}

	@Override
	public ServletContext getServletContext()
	{
		return _invocation.getWebApp();
	}

	@Override
	public DispatcherType getDispatcherType()
	{
		return DispatcherType.FORWARD;
	}

	//
	// HttpServletRequest
	//

	@Override
	public String getRequestURI()
	{
		return _invocation.getURI();
	}

	@Override
	public String getContextPath()
	{
		return _invocation.getContextPath();
	}

	@Override
	public String getServletPath()
	{
		return _invocation.getServletPath();
	}

	@Override
	public String getPathInfo()
	{
		return _invocation.getPathInfo();
	}

	@Override
	public String getQueryString()
	{
		return calculateQueryString();
	}

	protected String calculateQueryString()
	{
		// server/10j2
		// server/1ks7 vs server/1233

		String queryString = _invocation.getQueryString();

		if (queryString != null)
			return queryString;

		return _request.getQueryString();
	}


	public WebApp getWebApp()
	{
		return _invocation.getWebApp();
	}

	@Override
	public boolean isAsyncSupported()
	{
		return _invocation.isAsyncSupported() && getRequest().isAsyncSupported();
	}

	@Override
	public AsyncContext startAsync() throws IllegalStateException
	{
		if (!isAsyncSupported())
			throw new IllegalStateException(L.l(
					"The servlet '{0}' at '{1}' does not support async because the servlet or one of the filters does not support asynchronous mode.",
					getServletName(), getServletPath()));
		return super.startAsync();
	}

	public String getServletName()
	{
		if (_invocation != null)
		{
			return _invocation.getServletName();
		}
		else
			return null;
	}



	public HttpServletRequest unwrapRequest()
	{
		HttpServletRequest request = (HttpServletRequest) this.getRequest();

		while (request instanceof ForwardRequest)
		{
			request = (HttpServletRequest) ((ForwardRequest) request).getRequest();
		}

		return request;
	}

	//
	// parameter/form
	//

	/**
	 * Returns an enumeration of the form names.
	 */
	@Override
	public Enumeration<String> getParameterNames()
	{
		if (_parameters == null)
		{
			_parameters = calculateQuery();
		}

		return Collections.enumeration(_parameters.keySet());
	}

	/**
	 * Returns a map of the form.
	 */
	@Override
	public Map<String, String[]> getParameterMap()
	{
		if (_parameters == null)
		{
			_parameters = calculateQuery();
		}

		return Collections.unmodifiableMap(_parameters);
	}

	/**
	 * Returns the form's values for the given name.
	 * 
	 * @param name
	 *            key in the form
	 * @return value matching the key
	 */
	@Override
	public String[] getParameterValues(String name)
	{
		if (_parameters == null)
		{
			_parameters = calculateQuery();
		}
		

		return _parameters.get(name);
	}

	/**
	 * Returns the form primary value for the given name.
	 */
	@Override
	public String getParameter(String name)
	{
		String[] values = getParameterValues(name);

		if (values != null && values.length > 0)
			return values[0];
		else
			return null;
	}
	
	
	////////---------参数合并处理-------------------------------------------------------------
	private Map<String, String[]> calculateQuery()
	{
		Map<String, List<String>> newParameters = parseQuery();
		
		if(newParameters == null || newParameters.isEmpty())
			return super.getParameterMap();
		
		Map<String, String[]> oldParameters =  super.getParameterMap();
		
		if(oldParameters == null || oldParameters.isEmpty())
			return convenMap(newParameters);
		
		return mergeParameters(newParameters,oldParameters);
	}
	

	private Map<String, List<String>> parseQuery()
	{
		String queryString = _invocation.getQueryString();
		
		if(queryString == null || queryString.isEmpty())
			return null;
		
		Map<String, List<String>> newParameters = new QueryStringDecoder(queryString,false).parameters();
		
		return newParameters;
	}
	
	/**
	 * 合并新旧参数,新的参数优先
	 * @param newParameters
	 * @param oldParameters
	 * @return
	 */
	private Map<String,String[]> mergeParameters(Map<String, List<String>> newParameters,Map<String, String[]> oldParameters)
	{
		for(Entry<String, String[]> e : oldParameters.entrySet())
		{
			if(e.getValue() != null && e.getValue().length > 0)
			{
				List<String> list = newParameters.get(e.getKey());
				
				if(list == null)
					list = new ArrayList<String>(e.getValue().length);
				
				for(String str : e.getValue())
				{
					list.add(str);
				}
				
				newParameters.put(e.getKey(), list);
			}
		}
		
		return convenMap(newParameters);
	}

	private Map<String, String[]> convenMap(Map<String, List<String>> map)
	{
		Map<String, String[]> newMap = new HashMap<String, String[]>(map.size());
		
		for(Entry<String, List<String>> e : map.entrySet())
		{
			if(e.getValue() != null)
			{
				List<String> list = e.getValue();
				
				String[] strs = new String[list.size()];
				for(int i=0; i<list.size(); i++)
				{
					strs[i] = list.get(i);
				}
				
				newMap.put(e.getKey(), strs);
			}
		}
		
		return newMap;
	}
	//------------------------------------------------------------------------------------

}
