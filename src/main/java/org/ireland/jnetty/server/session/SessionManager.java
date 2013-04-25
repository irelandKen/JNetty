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

package org.ireland.jnetty.server.session;

import java.util.ArrayList;
import java.util.Iterator;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

import javax.servlet.SessionCookieConfig;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.ireland.jnetty.config.ConfigException;
import org.ireland.jnetty.webapp.WebApp;

import com.caucho.util.Crc64;

import com.caucho.util.LruCache;
import com.caucho.util.RandomUtil;

/**
 * Manages sessions in a web-webApp.
 */
public final class SessionManager implements SessionCookieConfig
{
	private static final Log log = LogFactory.getLog(SessionManager.class.getName());
	private static final boolean debug = log.isDebugEnabled();

	private static final int FALSE = 0;
	private static final int COOKIE = 1;
	private static final int TRUE = 2;

	private static final int UNSET = 0;
	private static final int SET_TRUE = 1;
	private static final int SET_FALSE = 2;

	private static final int[] DECODE;

	private final WebApp _webApp;

	// active sessions
	private LruCache<String, HttpSessionImpl> _sessions;


	// allow session rewriting
	private boolean _enableSessionUrls = true;

	// maximum number of sessions
	private int _sessionMax = 8192;

	// how long a session will be inactive before it times out
	private long _sessionTimeout = 30 * 60 * 1000;

	private String _cookieName = "JSESSIONID";
	private String _sslCookieName;

	// Rewriting strings.
	private String _sessionSuffix = ";jsessionid=";
	private String _sessionPrefix;

	// default cookie version
	private int _cookieVersion;

	private String _cookieDomain;

	private String _cookieDomainRegexp;

	private String _cookiePath;

	private long _cookieMaxAge = 24 * 60 * 60; //1 day

	private int _isCookieHttpOnly;

	private String _cookieComment;

	private String _cookiePort;

	private int _cookieLength = 21;

	// Servlet 3.0 plain | ssl session tracking cookies become secure when set to true
	private boolean _isSecure;

	// List of the HttpSessionListeners from the configuration file
	private ArrayList<HttpSessionListener> _listeners;

	// List of the HttpSessionListeners from the configuration file
	private ArrayList<HttpSessionActivationListener> _activationListeners;

	// List of the HttpSessionAttributeListeners from the configuration file
	private ArrayList<HttpSessionAttributeListener> _attributeListeners;

	private boolean _isClosed;

	/**
	 * Creates and initializes a new session manager
	 * 
	 * @param webApp
	 *            the web-webApp webApp
	 */
	public SessionManager(WebApp webApp)
	{
		_webApp = webApp;

		_sessions = new LruCache<String, HttpSessionImpl>(_sessionMax);

		_cookiePath = _webApp.getContextPath();

		if (_cookiePath == null || "".equals(_cookiePath))
			_cookiePath = "/";
	}

	/**
	 * Returns the session prefix, ie.. ";jsessionid=".
	 */
	public String getSessionPrefix()
	{
		return _sessionSuffix;
	}

	/**
	 * Returns the alternate session prefix, before the URL for wap.
	 */
	public String getAlternateSessionPrefix()
	{
		return _sessionPrefix;
	}

	/**
	 * Returns the cookie version.
	 */
	public int getCookieVersion()
	{
		return _cookieVersion;
	}

	/**
	 * Sets the cookie version.
	 */
	public void setCookieVersion(int cookieVersion)
	{
		_cookieVersion = cookieVersion;
	}

	/**
	 * Sets the cookie ports.
	 */
	public void setCookiePort(String port)
	{
		_cookiePort = port;
	}

	/**
	 * Gets the cookie ports.
	 */
	public String getCookiePort()
	{
		return _cookiePort;
	}

	/**
	 * Returns the SessionManager's webApp
	 */
	public WebApp getWebApp()
	{
		return _webApp;
	}

	/**
	 * Returns the current number of active sessions.
	 */
	public int getActiveSessionCount()
	{
		if (_sessions == null)
			return -1;
		else
			return _sessions.size();
	}

	/**
	 * Returns the active sessions.
	 */
	public int getSessionActiveCount()
	{
		return getActiveSessionCount();
	}

	/**
	 * Adds a new HttpSessionListener.
	 */
	public void addListener(HttpSessionListener listener)
	{
		if (_listeners == null)
			_listeners = new ArrayList<HttpSessionListener>();

		_listeners.add(listener);
	}

	/**
	 * Adds a new HttpSessionListener.
	 */
	ArrayList<HttpSessionListener> getListeners()
	{
		return _listeners;
	}

	/**
	 * Adds a new HttpSessionActivationListener.
	 */
	public void addActivationListener(HttpSessionActivationListener listener)
	{
		if (_activationListeners == null)
			_activationListeners = new ArrayList<HttpSessionActivationListener>();

		_activationListeners.add(listener);
	}

	/**
	 * Returns the activation listeners.
	 */
	ArrayList<HttpSessionActivationListener> getActivationListeners()
	{
		return _activationListeners;
	}

	/**
	 * Adds a new HttpSessionAttributeListener.
	 */
	public void addAttributeListener(HttpSessionAttributeListener listener)
	{
		if (_attributeListeners == null)
			_attributeListeners = new ArrayList<HttpSessionAttributeListener>();

		_attributeListeners.add(listener);
	}

	/**
	 * Gets the HttpSessionAttributeListener.
	 */
	ArrayList<HttpSessionAttributeListener> getAttributeListeners()
	{
		return _attributeListeners;
	}

	/**
	 * Returns true if the sessions are closed.
	 */
	public boolean isClosed()
	{
		return _isClosed;
	}

	/**
	 * Returns the default session timeout in milliseconds.
	 */
	public long getSessionTimeout()
	{
		return _sessionTimeout;
	}

	/**
	 * Set the default session timeout in minutes
	 */
	public void setSessionTimeout(long timeout)
	{
		if (timeout <= 0 || Integer.MAX_VALUE / 2 < timeout)
			_sessionTimeout = Long.MAX_VALUE / 2;
		else
			_sessionTimeout = 60000L * timeout;
	}

	/**
	 * Returns the idle time.
	 */
	public long getMaxIdleTime()
	{
		return _sessionTimeout;
	}

	/**
	 * Returns the maximum number of sessions.
	 */
	public int getSessionMax()
	{
		return _sessionMax;
	}

	/**
	 * Returns the maximum number of sessions.
	 */
	public void setSessionMax(int max)
	{
		if (max < 1)
			throw new ConfigException("session-max '["+max+"]' is too small.  session-max must be a positive number");

		_sessionMax = max;
	}


	/**
	 * Returns true if sessions can use the session rewriting.
	 */
	public boolean enableSessionUrls()
	{
		return _enableSessionUrls;
	}

	/**
	 * Returns true if sessions can use the session rewriting.
	 */
	public void setEnableUrlRewriting(boolean enableUrls)
	{
		_enableSessionUrls = enableUrls;
	}

	// SessionCookieConfig implementation (Servlet 3.0)
	@Override
	public void setName(String name)
	{
		setCookieName(name);
	}

	@Override
	public String getName()
	{
		return getCookieName();
	}

	@Override
	public void setDomain(String domain)
	{
		setCookieDomain(domain);
	}

	@Override
	public String getDomain()
	{
		return getCookieDomain();
	}

	@Override
	public void setPath(String path)
	{
		_cookiePath = path;
	}

	@Override
	public String getPath()
	{
		return _cookiePath;
	}

	@Override
	public void setComment(String comment)
	{
		_cookieComment = comment;
	}

	@Override
	public String getComment()
	{
		return _cookieComment;
	}

	@Override
	public void setHttpOnly(boolean httpOnly)
	{
		setCookieHttpOnly(httpOnly);
	}

	@Override
	public boolean isHttpOnly()
	{
		return isCookieHttpOnly();
	}

	@Override
	public void setSecure(boolean secure)
	{
		_isSecure = secure;
	}

	@Override
	public boolean isSecure()
	{
		return _isSecure;
	}

	@Override
	public void setMaxAge(int maxAge)
	{
		_cookieMaxAge = maxAge * 1000;
	}

	@Override
	public int getMaxAge()
	{
		return (int) (_cookieMaxAge / 1000);
	}

	public void setCookieName(String cookieName)
	{
		_cookieName = cookieName;
	}

	/**
	 * Returns the default cookie name.
	 */
	public String getCookieName()
	{
		return _cookieName;
	}

	/**
	 * Returns the SSL cookie name.
	 */
	public String getSSLCookieName()
	{
		if (_sslCookieName != null)
			return _sslCookieName;
		else
			return _cookieName;
	}

	/**
	 * Returns the default session cookie domain.
	 */
	public String getCookieDomain()
	{
		return _cookieDomain;
	}

	/**
	 * Sets the default session cookie domain.
	 */
	public void setCookieDomain(String domain)
	{
		_cookieDomain = domain;
	}

	public String getCookieDomainRegexp()
	{
		return _cookieDomainRegexp;
	}

	public void setCookieDomainRegexp(String regexp)
	{
		_cookieDomainRegexp = regexp;
	}

	/**
	 * Sets the default session cookie domain.
	 */
	public void setCookiePath(String path)
	{
		_cookiePath = path;
	}

	/**
	 * Returns the max-age of the session cookie.
	 */
	public long getCookieMaxAge()
	{
		return _cookieMaxAge;
	}

	/**
	 * Sets the max-age of the session cookie.
	 */
	public void setCookieMaxAge(long maxAge)
	{
		_cookieMaxAge = maxAge;
	}

	/**
	 * Returns the secure of the session cookie.
	 */
	public boolean isCookieSecure()
	{
		if (_isSecure)
			return true;
		else
			return !_cookieName.equals(_sslCookieName);
	}

	/**
	 * Sets the secure of the session cookie.
	 */
	public void setCookieSecure(boolean isSecure)
	{
		_isSecure = isSecure;
	}

	/**
	 * Returns the http-only of the session cookie.
	 */
	public boolean isCookieHttpOnly()
	{
		if (_isCookieHttpOnly == SET_TRUE)
			return true;
		else if (_isCookieHttpOnly == SET_FALSE)
			return false;
		else
			return getWebApp().getCookieHttpOnly();
	}

	/**
	 * Sets the http-only of the session cookie.
	 */
	public void setCookieHttpOnly(boolean httpOnly)
	{
		_isCookieHttpOnly = httpOnly ? SET_TRUE : SET_FALSE;
	}

	/**
	 * Sets the cookie length
	 */
	public void setCookieLength(int cookieLength)
	{
		if (cookieLength < 7)
			cookieLength = 7;

		_cookieLength = cookieLength;
	}

	/**
	 * Returns the cookie length.
	 */
	public long getCookieLength()
	{
		return _cookieLength;
	}

	/**
	 * Returns true if the session exists in this manager.
	 */
	public boolean containsSession(String id)
	{
		return _sessions.get(id) != null;
	}

	/**
	 * Creates a pseudo-random session id. If there's an old id and the group matches, then use it because different
	 * webApps on the same matchine should use the same cookie.
	 * 
	 * @param request
	 *            current request
	 */
	public String createSessionId(HttpServletRequest request)
	{
		return createSessionId(request, false);
	}

	/**
	 * Creates a pseudo-random session id. If there's an old id and the group matches, then use it because different
	 * webApps on the same machine should use the same cookie.
	 * 
	 * @param request
	 *            current request
	 */
	public String createSessionId(HttpServletRequest request, boolean create)
	{
		String id;

		do
		{
			id = createSessionIdImpl(request);
		} while (create && getSession(id, 0, create, true) != null);

		if (id == null || id.equals(""))
			throw new RuntimeException();

		return id;
	}

	public String createSessionIdImpl(HttpServletRequest request)
	{
		return createCookieValue();
	}

	/**
	 * 生成随机的SessionId(用作Cookie)
	 * 
	 * @param owner
	 * @return
	 */
	protected String createCookieValue()
	{
		StringBuilder sb = new StringBuilder();
		// this section is the host specific session index
		// the most random bit is the high bit

		int length = _cookieLength;

		length -= sb.length();

		long random = RandomUtil.getRandomLong();

		for (int i = 0; i < 11 && length-- > 0; i++)
		{
			sb.append(convert(random));
			random = random >> 6;
		}

		if (length > 0)
		{
			long time = System.currentTimeMillis();

			for (int i = 0; i < 7 && length-- > 0; i++)
			{
				sb.append(convert(time));
				time = time >> 6;
			}
		}

		while (length > 0)
		{
			random = RandomUtil.getRandomLong();
			for (int i = 0; i < 11 && length-- > 0; i++)
			{
				sb.append(convert(random));
				random = random >> 6;
			}
		}

		return sb.toString();
	}

	/**
	 * Finds a session in the session store, creating one if 'create' is true
	 * 
	 * @param isCreate
	 *            if the session doesn't exist, create it
	 * @param request
	 *            current request
	 * @sessionId a desired sessionId or null
	 * @param now
	 *            the time in milliseconds
	 * @param fromCookie
	 *            true if the session id comes from a cookie
	 * 
	 * @return the cached session.
	 */
	public HttpSessionImpl createSession(boolean isCreate, HttpServletRequest request, String sessionId, long now, boolean fromCookie)
	{
		if (_sessions == null)
			return null;

		HttpSessionImpl session = _sessions.get(sessionId);

		if (session != null && !session.isValid())
		{
			session = null;
		}

		boolean isNew = false;

		if (session == null && sessionId != null)
		{

			session = create(sessionId, now, isCreate);

			isNew = true;
		}

		if (session != null)
		{
			if (session.isTimeout(now))
			{
				session.timeout();
				session = null;
			}

		}

		if (!isCreate)
			return null;

		if (sessionId == null || sessionId.length() <= 6)
		{
			sessionId = createSessionId(request, true);
		}

		session = new HttpSessionImpl(this, sessionId, now);

		// If another thread has created and stored a new session,
		// putIfNew will return the old session
		session = _sessions.putIfNew(sessionId, session);

		if (!sessionId.equals(session.getId()))
			throw new IllegalStateException(sessionId + " != " + session.getId());

		session.create(now, true);

		handleCreateListeners(session);

		return session;
	}

	/**
	 * Returns a session from the session store, returning null if there's no cached session.
	 * 
	 * @param key
	 *            the session id
	 * @param now
	 *            the time in milliseconds
	 * 
	 * @return the cached session.
	 */
	public HttpSessionImpl getSession(String key, long now, boolean create, boolean fromCookie)
	{
		HttpSessionImpl session;
		boolean isNew = false;
		boolean killSession = false;

		if (_sessions == null)
			return null;

		session = _sessions.get(key);

		if (session != null && !session.getId().equals(key))
			throw new IllegalStateException(key + " != " + session.getId());

		if (now <= 0) // just generating id
			return session;

		if (session == null)
		{

			session = create(key, now, create);

			isNew = true;
		}

		if (session == null)
			return null;

		if (killSession && (!create))
		{
			_sessions.remove(key);
			// XXX:
			// session._isValid = false;

			return null;
		}
		else if (isNew)
			handleCreateListeners(session);
		// else
		// session.setAccess(now);

		return session;
	}

	public HttpSessionImpl getSession(String key)
	{
		if (_sessions == null || key == null)
			return null;

		return _sessions.get(key);
	}

	/**
	 * Create a new session.
	 * 
	 * @param oldId
	 *            the id passed to the request. Reuse if possible.
	 * @param request
	 *            - current HttpServletRequest
	 * @param fromCookie
	 */
	public HttpSessionImpl createSession(String oldId, long now, HttpServletRequest request, boolean fromCookie)
	{
		if (_sessions == null)
		{
			log.debug(this + " createSession called when sessionManager closed");

			return null;
		}

		String id = oldId;

		if (id == null || id.length() < 4)
		{

			id = createSessionId(request, true);
		}

		HttpSessionImpl session = create(id, now, true);

		if (session == null)
			return null;

		synchronized (session)
		{
			session.create(now, true);
		}

		// after load so a reset doesn't clear any setting
		handleCreateListeners(session);

		return session;
	}

	public HttpSessionImpl createNewSession(HttpServletRequest request)
	{
		if (_sessions == null)
		{
			log.debug(this + " createSession called when sessionManager closed");

			return null;
		}

		long creationTime = System.currentTimeMillis();

		String id = createSessionId(request, true);

		HttpSessionImpl session = create(id, creationTime, true);

		if (session == null)
			return null;

		// after load so a reset doesn't clear any setting
		handleCreateListeners(session);

		return session;
	}

	/**
	 * Creates a session. It's already been established that the key does not currently have a session.
	 */
	private HttpSessionImpl create(String key, long creationTime, boolean isCreate)
	{
		HttpSessionImpl session = new HttpSessionImpl(this, key, creationTime);

		// If another thread has created and stored a new session,
		// putIfNew will return the old session
		session = _sessions.putIfNew(key, session);

		if (!key.equals(session.getId()))
			throw new IllegalStateException(key + " != " + session.getId());

		return session;
	}

	/**
	 * 发布SessionCreated事件
	 * 
	 * @param session
	 */
	private void handleCreateListeners(HttpSessionImpl session)
	{
		if (_listeners != null)
		{
			HttpSessionEvent event = new HttpSessionEvent(session);

			for (int i = 0; i < _listeners.size(); i++)
			{
				HttpSessionListener listener = _listeners.get(i);

				listener.sessionCreated(event);
			}
		}
	}

	/**
	 * Adds a session from the cache.
	 */
	void addSession(HttpSessionImpl session)
	{
		_sessions.put(session.getId(), session);
	}

	/**
	 * Removes a session from the cache.
	 */
	void removeSession(HttpSessionImpl session)
	{
		_sessions.remove(session.getId());
	}

	public String[] sessionIdList()
	{
		ArrayList<String> sessionIds = new ArrayList<String>();

		synchronized (_sessions)
		{
			Iterator<LruCache.Entry<String, HttpSessionImpl>> sessionsIterator = _sessions.iterator();

			while (sessionsIterator.hasNext())
			{
				sessionIds.add(sessionsIterator.next().getKey());
			}
		}

		String[] ids = new String[sessionIds.size()];

		sessionIds.toArray(ids);

		return ids;
	}

	/**
	 * 清理过期Session
	 * 
	 * @return number of live sessions for stats
	 */
	public void clearInvalidSession()
	{

	}

	/**
	 * Cleans up the sessions when the WebApp shuts down gracefully.
	 */
	public void close()
	{
		synchronized (this)
		{
			if (_isClosed)
				return;
			_isClosed = true;
		}

		if (_sessions == null)
			return;

		ArrayList<HttpSessionImpl> list = new ArrayList<HttpSessionImpl>();

		for (int i = list.size() - 1; i >= 0; i--)
		{
			HttpSessionImpl session = list.get(i);

			if (!session.isValid())
				continue;

			if (debug)
				log.debug("close session " + session.getId());

			try
			{
				if (session.isValid())
					_sessions.remove(session.getId());
			}
			catch (Exception e)
			{
				log.debug( e.toString(), e);
			}
		}

	}

	/**
	 * Converts an integer to a printable character
	 */
	private static char convert(long code)
	{
		code = code & 0x3f;

		if (code < 26)
			return (char) ('a' + code);
		else if (code < 52)
			return (char) ('A' + code - 26);
		else if (code < 62)
			return (char) ('0' + code - 52);
		else if (code == 62)
			return '_';
		else
			return '-';
	}

	static int getServerCode(String id, int count)
	{
		if (id == null)
			return -1;

		if (count == 0)
		{
			return decode(id.charAt(0));
		}

		long hash = Crc64.generate(id);

		for (int i = 0; i < count; i++)
		{
			hash >>= 6;
		}

		return (int) (hash & 0x3f);
	}

	private static int decode(int code)
	{
		return DECODE[code & 0x7f];
	}

	@Override
	public String toString()
	{
		return getClass().getSimpleName() + "[" + _webApp.getContextPath() + "]";
	}

	static
	{
		DECODE = new int[128];
		for (int i = 0; i < 64; i++)
			DECODE[(int) convert(i)] = i;
	}

	/***
	 * 验证指定 session是否有效
	 * 
	 * @param session
	 * @return
	 */
	public boolean isValid(HttpSessionImpl session)
	{
		if(session == null)
			return false;
		
		HttpSessionImpl trueSession = _sessions.get(session.getId());

		if (trueSession == null)
			return false;

		if (trueSession == session && !trueSession.isTimeout())
			return true;

		return true;
	}

	/**
	 * 生成JSESSIONID 的 Cookie
	 * @param session
	 * @param contextPath
	 * @param secure
	 * @return
	 */
	public Cookie getSessionCookie(HttpSessionImpl session, String contextPath, boolean secure)
	{

		String sessionPath = contextPath;

		sessionPath = (sessionPath == null || sessionPath.length() == 0) ? "/" : sessionPath;

		String id = session.getId();

		Cookie cookie = null;

		cookie = new Cookie(_cookieName, id);

		cookie.setComment(_cookieComment);
		
		if(_cookieDomain != null)
			cookie.setDomain(_cookieDomain);

		cookie.setHttpOnly(isHttpOnly());
		cookie.setMaxAge((int) _cookieMaxAge);

		cookie.setPath(sessionPath);

		cookie.setSecure(secure);
		cookie.setVersion(_cookieVersion);

		return cookie;

	}
}
