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

package org.ireland.jnetty.http.wrapper;


import com.caucho.util.HashMapImpl;
import com.caucho.util.IntMap;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.ireland.jnetty.dispatch.HttpInvocation;
import org.ireland.jnetty.webapp.WebApp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.NoSuchElementException;

public class IncludeRequest extends HttpServletRequestWrapper {
  private static final IntMap _includeAttributeMap = new IntMap();

  private static Enumeration<String> _emptyEnum;

  private static final int REQUEST_URI_CODE = 1;
  private static final int CONTEXT_PATH_CODE = 2;
  private static final int SERVLET_PATH_CODE = 3;
  private static final int PATH_INFO_CODE = 4;
  private static final int QUERY_STRING_CODE = 5;

  // the wrapped request
  private HttpInvocation _invocation;

  private HttpServletResponse _response;

  private HashMapImpl<String,String[]> _filledForm;
  private ArrayList<String> _headerNames;
  
  public IncludeRequest(HttpServletRequest request)
  {
	  super(request);
	  
  }
  
  public IncludeRequest(HttpServletRequest request,
                        HttpServletResponse response,
                        HttpInvocation invocation)
  {
    super(request);

    _response = response;

    _invocation = invocation;
  }

  protected HttpInvocation getInvocation()
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
    return DispatcherType.INCLUDE;
  }

  //
  // CauchoRequest
  //
  
  public String getPageURI()
  {
    return _invocation.getContextURI();
  }

  @Override
  public String getContextPath()
  {
    return _invocation.getFilterChainInvocation().getContextPath();
  }

  public String getPageContextPath()
  {
    return _invocation.getFilterChainInvocation().getContextPath();
  }
  
  public String getPageServletPath()
  {
    return _invocation.getFilterChainInvocation().getServletPath();
  }
  
  public String getPagePathInfo()
  {
    return _invocation.getFilterChainInvocation().getPathInfo();
  }
  
  public String getPageQueryString()
  {
    return _invocation.getQueryString();
  }

  public String getMethod()
  {
    String method = ((HttpServletRequest) getRequest()).getMethod();

    // server/10jk
    if ("POST".equalsIgnoreCase(method))
      return method;
    else
      return "GET";
  }
  
  public WebApp getWebApp()
  {
    return _invocation.getWebApp();
  }


  


















  //
  // attributes
  //

  @Override
  public Object getAttribute(String name)
  {
    switch (_includeAttributeMap.get(name)) {
    case REQUEST_URI_CODE:
      return _invocation.getContextURI();
      
    case CONTEXT_PATH_CODE:
      return _invocation.getFilterChainInvocation().getContextPath();
      
    case SERVLET_PATH_CODE:
      return _invocation.getFilterChainInvocation().getServletPath();
      
    case PATH_INFO_CODE:
      return _invocation.getFilterChainInvocation().getPathInfo();
      
    case QUERY_STRING_CODE:
      return _invocation.getQueryString();
      
    default:
      return super.getAttribute(name);
    }
  }

  @Override
  public Enumeration<String> getAttributeNames()
  {
    ArrayList<String> list = new ArrayList<String>();

    Enumeration<String> e = super.getAttributeNames();
    
    while (e.hasMoreElements()) {
      list.add(e.nextElement());
    }

    if (! list.contains(RequestDispatcher.INCLUDE_REQUEST_URI)) {
      list.add(RequestDispatcher.INCLUDE_REQUEST_URI);
      list.add(RequestDispatcher.INCLUDE_CONTEXT_PATH);
      list.add(RequestDispatcher.INCLUDE_SERVLET_PATH);
      list.add(RequestDispatcher.INCLUDE_PATH_INFO);
      list.add(RequestDispatcher.INCLUDE_QUERY_STRING);
    }

    return Collections.enumeration(list);
  }

  //
  // lifecycle
  //

  /**
   * Starts the request
   */
  public void startRequest()
  {
   /* _response.startRequest();*/
  }

  public void finishRequest()
    throws IOException
  {
/*    super.finishRequest();
    
    _response.finishRequest();*/
  }

  static {
    _includeAttributeMap.put(RequestDispatcher.INCLUDE_REQUEST_URI,
                             REQUEST_URI_CODE);
    _includeAttributeMap.put(RequestDispatcher.INCLUDE_CONTEXT_PATH,
                             CONTEXT_PATH_CODE);
    _includeAttributeMap.put(RequestDispatcher.INCLUDE_SERVLET_PATH,
                             SERVLET_PATH_CODE);
    _includeAttributeMap.put(RequestDispatcher.INCLUDE_PATH_INFO,
                             PATH_INFO_CODE);
    _includeAttributeMap.put(RequestDispatcher.INCLUDE_QUERY_STRING,
                             QUERY_STRING_CODE);

    _emptyEnum = new Enumeration<String>() {
      public boolean hasMoreElements() {
        return false;
      }

      public String nextElement() {
        throw new NoSuchElementException();
      }
    };
  }
}
