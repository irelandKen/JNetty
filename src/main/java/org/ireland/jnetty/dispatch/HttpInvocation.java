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

package org.ireland.jnetty.dispatch;

import org.ireland.jnetty.webapp.WebApp;

/**
 * A repository for request information gleaned from the uri.
 * 
 * and the FilterChain that match the URI
 * 
 * A HttpInvocation include the URI information and the FilterChain that match the URI
 * 
 * HttpInvocation 是面向特定_rawURI的,故QueryString部分难以重用
 * 
 * 对于rawContextURI相同(即包括参数也相同)时,并发的情况下是可重用的和共享的,像单例一般的重用, 但一般情况下,URL上的参数都不太相同,所以对HttpInvocation作缓存的意义不太
 * 
 */
public class HttpInvocation
{
	protected final WebApp _webApp;

	// FilterChainInvocation: maybe share with other
	private FilterChainInvocation _filterChainInvocation;

	private String _rawHost;

	// canonical host and port
	private String _hostName;

	private int _port;

	private boolean _isSecure;

	// -------------------------

	private String _rawContextURI;

	private String _contextURI;

	private String _queryString;

	private String _sessionIdFromUri;

	public HttpInvocation(WebApp webApp, String rawContextURI)
	{
		_webApp = webApp;

		_rawContextURI = rawContextURI;
	}

	/**
	 * Returns the mapped webApp.
	 */
	public final WebApp getWebApp()
	{
		return _webApp;
	}
	
	public FilterChainInvocation getFilterChainInvocation()
	{
		return _filterChainInvocation;
	}

	public void setFilterChainInvocation(FilterChainInvocation filterChainInvocation)
	{
		_filterChainInvocation = filterChainInvocation;
	}

	/**
	 * Returns the secure flag
	 */
	public final boolean isSecure()
	{
		return _isSecure;
	}

	/**
	 * Sets the secure flag
	 */
	public final void setSecure(boolean isSecure)
	{
		_isSecure = isSecure;
	}

	/**
	 * Returns the raw host from the protocol. This may be different from the canonical host name.
	 */
	public final String getHost()
	{
		return _rawHost;
	}

	/**
	 * Sets the protocol's host.
	 */
	public final void setHost(String host)
	{
		_rawHost = host;
	}

	/**
	 * Returns canonical host name.
	 */
	public final String getHostName()
	{
		return _hostName;
	}

	/**
	 * Sets the protocol's host.
	 */
	public final void setHostName(String hostName)
	{
		if (hostName != null && !hostName.equals(""))
			_hostName = hostName;
	}

	/**
	 * Returns canonical port
	 */
	public final int getPort()
	{
		return _port;
	}

	/**
	 * Sets the canonical port
	 */
	public final void setPort(int port)
	{
		_port = port;
	}

	/**
	 * Returns the raw URI from the protocol before any normalization. The raw URI includes the query string. (?)
	 */
	public final String getRawContextURI()
	{
		return _rawContextURI;
	}

	/**
	 * Sets the raw URI from the protocol before any normalization. The raw URI includes the query string. (?)
	 */
	public final void setRawContextURI(String rawContextURI)
	{
		_rawContextURI = rawContextURI;
	}

	/**
	 * Returns the raw URI length.
	 */
	public int getURLLength()
	{
		if (_rawContextURI != null)
			return _rawContextURI.length();
		else
			return 0;
	}

	/**
	 * Returns the URI after normalization, e.g. character escaping, URL session, and query string.
	 */
	public final String getContextURI()
	{
		return _contextURI;
	}

	/**
	 * Sets the URI after normalization.
	 */
	public final void setContextURI(String uri)
	{
		_contextURI = uri;
	}

	/**
	 * Returns a URL-based session id.
	 */
	public final String getSessionId()
	{
		return _sessionIdFromUri;
	}

	/**
	 * Sets the URL-based session id.
	 */
	public final void setSessionId(String sessionId)
	{
		_sessionIdFromUri = sessionId;
	}

	/**
	 * Returns the query string. Characters remain unescaped.
	 */
	public final String getQueryString()
	{
		return _queryString;
	}

	/**
	 * Returns the query string. Characters remain unescaped.
	 */
	public final void setQueryString(String queryString)
	{
		_queryString = queryString;
	}

	/**
	 * Returns the invocation's hash code.
	 */
	public int hashCode()
	{
		int hash = _rawContextURI.hashCode();

		if (_rawHost != null)
			hash = hash * 65521 + _rawHost.hashCode();

		hash = hash * 65521 + _port;

		return hash;
	}

	/**
	 * Checks for equality
	 */
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		else if (o == null)
			return false;

		if (getClass() != o.getClass())
			return false;

		HttpInvocation inv = (HttpInvocation) o;

		if (_isSecure != inv._isSecure)
			return false;

		if (_rawContextURI != inv._rawContextURI && (_rawContextURI == null || !_rawContextURI.equals(inv._rawContextURI)))
			return false;

		if (_rawHost != inv._rawHost && (_rawHost == null || !_rawHost.equals(inv._rawHost)))
			return false;

		if (_port != inv._port)
			return false;

		String aQuery = getQueryString();
		String bQuery = inv.getQueryString();

		if (aQuery != bQuery && (aQuery == null || !aQuery.equals(bQuery)))
			return false;

		return true;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();

		sb.append(getClass().getSimpleName());
		sb.append("[");
		sb.append(_rawContextURI);

		sb.append(",").append(_filterChainInvocation.getWebApp());

		sb.append("]");

		return sb.toString();
	}
}
