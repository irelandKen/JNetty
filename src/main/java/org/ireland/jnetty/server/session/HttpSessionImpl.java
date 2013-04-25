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



import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ireland.jnetty.webapp.WebApp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;

/**
 * Implements a HTTP session.
 */
public class HttpSessionImpl implements HttpSession
{
	private static final Log log = LogFactory.getLog(HttpSessionImpl.class.getName());
	
	private static final boolean debug = log.isDebugEnabled();

	// the session's identifier
	private String _id;

	// the owning session manager
	protected SessionManager _manager;
	// the session objectStore

	// Map containing the actual values.
	protected Map<String, Object> _values;

	// time the session was created
	private long _creationTime;
	
	// time the session was last accessed
	private long _accessTime;

	// maximum time the session may stay alive.
	private long _idleTimeout;
	

	private long _lastUseTime;



	// true if the session is new
	private boolean _isNew = true;

	// true if the session is still valid, i.e. not invalidated
	private boolean _isValid = true;

	/**
	 * Create a new session object.
	 * 
	 * @param manager
	 *            the owning session manager.
	 * @param id
	 *            the session identifier.
	 * @param creationTime
	 *            the time in milliseconds when the session was created.
	 */
	public HttpSessionImpl(SessionManager manager, String id, long creationTime)
	{
		_manager = manager;


		_creationTime = creationTime;
		
		_accessTime = _creationTime;

		_lastUseTime = _accessTime;
		_idleTimeout = manager.getSessionTimeout();

		_id = id;

		_values = createValueMap();

		if (debug)
			log.debug(this + " new");
	}

	/**
	 * Create the map used to objectStore values.
	 */
	protected Map<String, Object> createValueMap()
	{
		return new TreeMap<String, Object>();
	}

	/**
	 * Returns the time the session was created.
	 */
	@Override
	public long getCreationTime()
	{
		// this test forced by TCK
		if (!_isValid)
			throw new IllegalStateException(this+": can't call getCreationTime() when session is no longer valid.");

		return _creationTime;
	}

	/**
	 * Returns the session identifier.
	 */
	@Override
	public String getId()
	{
		return _id;
	}

	/**
	 * Returns the last objectAccess time.
	 */
	@Override
	public long getLastAccessedTime()
	{
		// this test forced by TCK
		if (!_isValid)
			throw new IllegalStateException(this+": can't call getLastAccessedTime() when session is no longer valid.");

		return _accessTime;
	}

	/**
	 * Returns the time the session is allowed to be alive.
	 * 
	 * @return time allowed to live in seconds
	 */
	@Override
	public int getMaxInactiveInterval()
	{
		if (Long.MAX_VALUE / 2 <= _idleTimeout)
			return -1;
		else
			return (int) (_idleTimeout / 1000);
	}

	/**
	 * Sets the maximum time a session is allowed to be alive.
	 * 
	 * @param value
	 *            time allowed to live in seconds
	 */
	@Override
	public void setMaxInactiveInterval(int value)
	{
		if (value < 0)
			_idleTimeout = Long.MAX_VALUE / 2;
		else
			_idleTimeout = ((long) value) * 1000;
	}

	/**
	 * Returns the session context.
	 * 
	 * @deprecated
	 */
	@Override
	public HttpSessionContext getSessionContext()
	{
		return null;
	}

	/**
	 * Returns the servlet context.
	 */
	@Override
	public ServletContext getServletContext()
	{
		return _manager.getWebApp();
	}

	/**
	 * Returns the session manager.
	 */
	public SessionManager getManager()
	{
		return _manager;
	}

	/**
	 * Returns true if the session is new.
	 */
	@Override
	public boolean isNew()
	{
		if (!_isValid)
			throw new IllegalStateException(this+" can't call isNew() when session is no longer valid.");

		return _isNew;
	}

	/**
	 * Returns true if the session is valid.
	 */
	public boolean isValid()
	{
		return _isValid;
	}

	public boolean isTimeout()
	{
		return isTimeout(System.currentTimeMillis());
	}

	boolean isTimeout(long now)
	{
		long maxIdleTime = _idleTimeout;

		long lastUseTime = getLastUseTime();

		return lastUseTime + maxIdleTime < now;
	}

	private long getLastUseTime()
	{
		return _lastUseTime;
	}

	/**
	 * Returns true if the session is empty.
	 */
	public boolean isEmpty()
	{
		return _values == null || _values.size() == 0;
	}

	//
	// Attribute API
	//

	/**
	 * Returns the named attribute from the session.
	 */
	@Override
	public Object getAttribute(String name)
	{
		if (!_isValid)
			throw new IllegalStateException(this+": can't call getAttribute() when session is no longer valid.");

		synchronized (_values)
		{
			Object value = _values.get(name);

			return value;
		}
	}

	/**
	 * Sets a session attribute. If the value is a listener, notify it of the objectModified. If the value has changed
	 * mark the session as changed for persistent sessions.
	 * 
	 * @param name
	 *            the name of the attribute
	 * @param value
	 *            the value of the attribute
	 */
	@Override
	public void setAttribute(String name, Object value)
	{
		if (!_isValid)
			throw new IllegalStateException(this+": can't call setAttribute(String, Object) when session is no longer valid.");

		Object oldValue;

		if (value != null && !(value instanceof Serializable) && debug)
		{
			log.debug(this+" attribute '"+name+"' value is non-serializable type '"+value.getClass().getName()+"'");
		}

		synchronized (_values)
		{
			if (value != null)
				oldValue = _values.put(name, value);
			else
				oldValue = _values.remove(name);
		}


		if (oldValue instanceof HttpSessionBindingListener)
		{
			HttpSessionBindingListener listener;
			listener = (HttpSessionBindingListener) oldValue;

			listener.valueUnbound(new HttpSessionBindingEvent(HttpSessionImpl.this, name, oldValue));
		}

		if (value instanceof HttpSessionBindingListener)
		{
			HttpSessionBindingListener listener;
			listener = (HttpSessionBindingListener) value;

			listener.valueBound(new HttpSessionBindingEvent(HttpSessionImpl.this, name, value));
		}

		// Notify the attribute listeners
		ArrayList listeners = _manager.getAttributeListeners();

		if (listeners != null && listeners.size() > 0)
		{
			HttpSessionBindingEvent event;

			if (oldValue != null)
				event = new HttpSessionBindingEvent(this, name, oldValue);
			else
				event = new HttpSessionBindingEvent(this, name, value);

			for (int i = 0; i < listeners.size(); i++)
			{
				HttpSessionAttributeListener listener;
				listener = (HttpSessionAttributeListener) listeners.get(i);

				if (oldValue != null)
					listener.attributeReplaced(event);
				else
					listener.attributeAdded(event);
			}
		}
	}

	/**
	 * Remove a session attribute. If the value is a listener, notify it of the objectModified.
	 * 
	 * @param name
	 *            the name of the attribute to objectRemove
	 */
	@Override
	public void removeAttribute(String name)
	{
		if (!_isValid)
			throw new IllegalStateException(this+": can't call removeAttribute(String) when session is no longer valid.");

		Object oldValue;

		synchronized (_values)
		{
			oldValue = _values.remove(name);
		}

		notifyAttributeRemoved(name, oldValue);
	}

	/**
	 * Notify any Attribute unbound listeners.
	 */
	private void notifyAttributeRemoved(String name, Object oldValue)
	{
		if (oldValue == null)
			return;

		if (oldValue instanceof HttpSessionBindingListener)
		{
			HttpSessionBindingListener listener;
			listener = (HttpSessionBindingListener) oldValue;

			listener.valueUnbound(new HttpSessionBindingEvent(this, name, oldValue));
		}

		// Notify the attributes listeners
		ArrayList listeners = _manager.getAttributeListeners();
		if (listeners != null)
		{
			HttpSessionBindingEvent event;

			event = new HttpSessionBindingEvent(this, name, oldValue);

			for (int i = 0; i < listeners.size(); i++)
			{
				HttpSessionAttributeListener listener;
				listener = (HttpSessionAttributeListener) listeners.get(i);

				listener.attributeRemoved(event);
			}
		}
	}

	/**
	 * Return an enumeration of all the sessions' attribute names.
	 * 
	 * @return enumeration of the attribute names.
	 */
	@Override
	public Enumeration<String> getAttributeNames()
	{
		synchronized (_values)
		{
			if (!_isValid)
				throw new IllegalStateException(this+" can't call getAttributeNames() when session is no longer valid.");

			return Collections.enumeration(_values.keySet());
		}
	}

	/**
	 * @deprecated
	 */
	public Object getValue(String name)
	{
		return getAttribute(name);
	}

	/**
	 * @deprecated
	 */
	public void putValue(String name, Object value)
	{
		setAttribute(name, value);
	}

	/**
	 * @deprecated
	 */
	public void removeValue(String name)
	{
		removeAttribute(name);
	}

	/**
	 * @deprecated
	 */
	public String[] getValueNames()
	{
		synchronized (_values)
		{
			if (!_isValid)
				throw new IllegalStateException(this+" can't call getValueNames() when session is no longer valid.");

			if (_values == null)
				return new String[0];

			String[] s = new String[_values.size()];

			Enumeration<String> e = getAttributeNames();
			int count = 0;
			while (e.hasMoreElements())
				s[count++] = (String) e.nextElement();

			return s;
		}
	}

	//
	// lifecycle: creation
	//

	/**
	 * Creates a new session.
	 */
	void create(long now, boolean isCreate)
	{
		if (debug)
		{
			log.debug(this + " create session");
		}

		if (_isValid)
		{
			clearAllAttributes();
		}

		// TCK now cares about exact time
		now = System.currentTimeMillis();

		_isValid = true;
		_isNew = true;
		_creationTime = now;
	}

	//
	// invalidation, lru, timeout
	//

	/**
	 * Invalidates the session, called by user code.
	 * 
	 */
	@Override
	public void invalidate()
	{
		if (debug)
			log.debug(this + " invalidate");
		
		clearAllAttributes();
		
		_isValid = false;

		_manager.removeSession(this);
	}

	/**
	 * Cleans up the session.
	 */
	void clearAllAttributes()
	{
		if (_values.size() == 0)
			return;

		// ClusterObject clusterObject = _clusterObject;

		ArrayList<String> names = new ArrayList<String>();
		ArrayList<Object> values = new ArrayList<Object>();

		synchronized (_values)
		{

			for (Map.Entry<String, Object> entry : _values.entrySet())
			{
				names.add(entry.getKey());
				values.add(entry.getValue());
			}

			_values.clear();
		}

		// server/015a
		for (int i = 0; i < names.size(); i++)
		{
			String name = names.get(i);
			Object value = values.get(i);

			notifyValueUnbound(name, value);
		}
	}

	/**
	 * Notify any value unbound listeners.
	 */
	private void notifyValueUnbound(String name, Object oldValue)
	{
		if (oldValue == null)
			return;

		if (oldValue instanceof HttpSessionBindingListener)
		{
			HttpSessionBindingListener listener;
			listener = (HttpSessionBindingListener) oldValue;

			listener.valueUnbound(new HttpSessionBindingEvent(this, name, oldValue));
		}

		// Notify the attributes listeners
		ArrayList listeners = _manager.getAttributeListeners();
		if (listeners != null)
		{
			HttpSessionBindingEvent event;

			event = new HttpSessionBindingEvent(this, name, oldValue);

			for (int i = 0; i < listeners.size(); i++)
			{
				HttpSessionAttributeListener listener;
				listener = (HttpSessionAttributeListener) listeners.get(i);

				listener.attributeRemoved(event);
			}
		}
	}


	
	  /**
	   * Invalidates a session based on a timeout
	   */
	  void timeout()
	  {
		  clearAllAttributes();
	  }

	/**
	 * Callback when the session is removed from the session cache, generally because the session cache is full.
	 */
	public void removeEvent()
	{

		if (debug)
			log.debug(this + " remove");

		long now = System.currentTimeMillis();

		// server/015k, server/10g2
		if (_accessTime + getMaxInactiveInterval() < now)
		{
			publishSessionDestroyed();
		}

	}

	/**
	 * 触发Session Destroyed事件
	 */
	private void publishSessionDestroyed()
	{

		ArrayList<HttpSessionListener> listeners = _manager.getListeners();

		if (listeners != null)
		{
			HttpSessionEvent event = new HttpSessionEvent(this);

			for (int i = listeners.size() - 1; i >= 0; i--)
			{
				HttpSessionListener listener;
				listener = (HttpSessionListener) listeners.get(i);

				listener.sessionDestroyed(event);
			}
		}
	}

	@Override
	public String toString()
	{
		String contextPath = "";

		SessionManager manager = _manager;
		if (manager != null)
		{
			WebApp webApp = manager.getWebApp();

			if (webApp != null)
				contextPath = "," + webApp.getContextPath();
		}

		return getClass().getSimpleName() + "[" + getId() + contextPath + "]";
	}

}
