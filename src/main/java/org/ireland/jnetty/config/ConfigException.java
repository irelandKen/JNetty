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

import java.io.*;
import java.lang.reflect.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Thrown by the various Builders
 */
public class ConfigException extends RuntimeException
{
	private static final Log log = LogFactory.getLog(ConfigException.class.getName());

	/**
	 * Create a null exception
	 */
	public ConfigException()
	{
	}

	/**
	 * Creates an exception with a message
	 */
	public ConfigException(String msg)
	{
		super(msg);
	}

	/**
	 * Creates an exception with a message and throwable
	 */
	public ConfigException(String msg, Throwable e)
	{
		super(msg, e);
	}

	/**
	 * Creates an exception with a throwable
	 */
	protected ConfigException(Throwable e)
	{
		super(getMessage(e), e);
	}

	protected static String getMessage(Throwable e)
	{
		if (e.getMessage() != null)
			return e.getMessage();
		else
			return e.toString();
	}

	public static RuntimeException create(String location, Throwable e)
	{
		if (e instanceof InstantiationException && e.getCause() != null)
			e = e.getCause();

		if (e instanceof InvocationTargetException && e.getCause() != null)
			e = e.getCause();

		return new ConfigException(location + e, e);
	}

	public static RuntimeException createLine(String line, Throwable e)
	{
		return new ConfigException(line + e, e);
	}

	public static RuntimeException create(Field field, Throwable e)
	{
		return create(loc(field), e);
	}

	public static RuntimeException create(Method method, Throwable e)
	{
		return create(loc(method), e);
	}

	public static RuntimeException create(Method method, String msg, Throwable e)
	{
		return new ConfigException(loc(method) + msg, e);
	}

	public static RuntimeException create(Method method, String msg)
	{
		return new ConfigException(loc(method) + msg);
	}

	public static ConfigException createConfig(Throwable e)
	{
		if (e instanceof ConfigException)
			return (ConfigException) e;
		else
			return new ConfigException(e);
	}

	public static RuntimeException create(Throwable e)
	{
		while (e.getCause() != null
				&& (e instanceof InstantiationException || e instanceof InvocationTargetException || e.getClass().equals(RuntimeException.class)))
		{
			e = e.getCause();
		}

		return (RuntimeException) e;
	}

	public void print(PrintWriter out)
	{
		out.println(getMessage());
	}

	public static String loc(Field field)
	{
		return field.getDeclaringClass().getName() + "." + field.getName() + ": ";
	}

	public static String loc(Method method)
	{
		return method.getDeclaringClass().getName() + "." + method.getName() + "(): ";
	}
}
