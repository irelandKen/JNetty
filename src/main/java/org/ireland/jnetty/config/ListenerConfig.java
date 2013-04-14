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

package org.ireland.jnetty.config;

import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionListener;

import com.caucho.config.ConfigException;
import com.caucho.config.Configurable;
import com.caucho.util.L10N;

/**
 * Configuration for the listener
 * 
 */
@Configurable
public class ListenerConfig<T>
{
	static L10N L = new L10N(ListenerConfig.class);

	// The listener class
	private Class<T> _listenerClass;

	// The listener object
	private T _object;


	/**
	 * Sets the listener class.
	 */
	public void setListenerClass(Class<T> cl) throws ConfigException
	{

		if (ServletContextListener.class.isAssignableFrom(cl))
		{
		} else if (ServletContextAttributeListener.class.isAssignableFrom(cl))
		{
		} else if (ServletRequestListener.class.isAssignableFrom(cl))
		{
		} else if (ServletRequestAttributeListener.class.isAssignableFrom(cl))
		{
		} else if (HttpSessionListener.class.isAssignableFrom(cl))
		{
		} else if (HttpSessionAttributeListener.class.isAssignableFrom(cl))
		{
		} else if (HttpSessionActivationListener.class.isAssignableFrom(cl))
		{
		} else
			throw new ConfigException(
					L.l("listener-class '{0}' does not implement any web-app listener interface.",
							cl.getName()));

		_listenerClass = cl;
	}

	/**
	 * Gets the listener class.
	 */
	public Class<?> getListenerClass()
	{
		return _listenerClass;
	}

	/**
	 * Initialize.
	 */
	public Object createListenerObject() throws Exception
	{
		if (_object != null)
			return _object;

/*		InjectManager cdiManager = InjectManager.create();

		_object = cdiManager.createTransientObject(_listenerClass);*/

		return _object;
	}

	public void destroy()
	{

	}

	public String toString()
	{
		return getClass().getSimpleName() + "[" + _listenerClass + "]";
	}
}
