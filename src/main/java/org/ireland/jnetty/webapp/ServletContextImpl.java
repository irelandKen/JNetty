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

import com.caucho.util.L10N;

import javax.servlet.*;
import javax.servlet.descriptor.JspConfigDescriptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ireland.jnetty.config.ListenerConfig;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ObjectUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Bare-bones servlet context implementation.
 */
public abstract class ServletContextImpl implements ServletContext
{
	static final Log log = LogFactory.getLog(ServletContextImpl.class);
	
	static final L10N L = new L10N(ServletContextImpl.class);
	
	private final ResourceLoader resourceLoader = new FileSystemResourceLoader();

	private String _name = "";

	private HashMap<String, Object> _attributes = new HashMap<String, Object>();

	private ArrayList<ServletContextAttributeListener> _applicationAttributeListeners;

	private HashMap<String, String> _initParams = new HashMap<String, String>();

	public String getRootDirectory()
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * Sets the servlet context name
	 */
	public void setDisplayName(String name)
	{
		_name = name;
	}

	/**
	 * Gets the servlet context name
	 */
	@Override
	public String getServletContextName()
	{
		return _name;
	}

	/**
	 * Gets the servlet context name
	 */
	@Override
	public String getContextPath()
	{
		return _name;
	}

	/**
	 * Adds the listener.
	 */
	protected void addAttributeListener(ServletContextAttributeListener listener)
	{
		if (_applicationAttributeListeners == null)
			_applicationAttributeListeners = new ArrayList<ServletContextAttributeListener>();

		_applicationAttributeListeners.add(listener);
	}

	/**
	 * Returns the server information
	 */
	@Override
	public String getServerInfo()
	{
		return "JNetty/" + getMajorVersion() + "." + getMinorVersion();
	}

	/**
	 * Returns the servlet major version
	 */
	@Override
	public int getMajorVersion()
	{
		return 3;
	}

	@Override
	public int getEffectiveMajorVersion()
	{
		return 3;
	}

	/**
	 * Returns the servlet minor version
	 */
	@Override
	public int getMinorVersion()
	{
		return 0;
	}

	@Override
	public int getEffectiveMinorVersion()
	{
		return 0;
	}

	/**
	 * Sets an init param
	 */
	@Override
	public boolean setInitParameter(String name, String value)
	{

/*		if (isActive())
			throw new IllegalStateException(
					L.l("setInitParameter must be called before the web-app has been initialized, because it's required by the servlet spec."));*/

		// server/1h12
		if (_initParams.containsKey(name))
			return false;

		_initParams.put(name, value);

		return true;
	}

	/**
	 * Sets an init param
	 */
	protected void setInitParam(String name, String value)
	{
		_initParams.put(name, value);
	}

	/**
	 * Gets the init params
	 */
	@Override
	public String getInitParameter(String name)
	{
		return _initParams.get(name);
	}

	/**
	 * Gets the init params
	 */
	@Override
	public Enumeration<String> getInitParameterNames()
	{
		return Collections.enumeration(_initParams.keySet());
	}

	/**
	 * Returns the named attribute.
	 */
	@Override
	public Object getAttribute(String name)
	{
		synchronized (_attributes)
		{
			Object value = _attributes.get(name);

			return value;
		}
	}

	/**
	 * Returns an enumeration of the attribute names.
	 */
	@Override
	public Enumeration<String> getAttributeNames()
	{
		synchronized (_attributes)
		{
			return Collections.enumeration(_attributes.keySet());
		}
	}

	/**
	 * Sets an application attribute.
	 * 
	 * @param name
	 *            the name of the attribute
	 * @param value
	 *            the value of the attribute
	 */
	@Override
	public void setAttribute(String name, Object value)
	{
		Object oldValue;

		synchronized (_attributes)
		{
			if (value != null)
				oldValue = _attributes.put(name, value);
			else
				oldValue = _attributes.remove(name);
		}

		// Call any listeners
		if (_applicationAttributeListeners != null)
		{
			ServletContextAttributeEvent event;

			if (oldValue != null)
				event = new ServletContextAttributeEvent(this, name, oldValue);
			else
				event = new ServletContextAttributeEvent(this, name, value);

			for (int i = 0; i < _applicationAttributeListeners.size(); i++)
			{
				ServletContextAttributeListener listener;

				Object objListener = _applicationAttributeListeners.get(i);
				listener = (ServletContextAttributeListener) objListener;

				try
				{
					if (oldValue != null)
						listener.attributeReplaced(event);
					else
						listener.attributeAdded(event);
				}
				catch (Exception e)
				{
					log(e.toString(), e);
				}
			}
		}
	}

	/**
	 * Removes an attribute from the servlet context.
	 * 
	 * @param name
	 *            the name of the attribute to remove.
	 */
	@Override
	public void removeAttribute(String name)
	{
		Object oldValue;

		synchronized (_attributes)
		{
			oldValue = _attributes.remove(name);
		}

		// Call any listeners
		if (_applicationAttributeListeners != null)
		{
			ServletContextAttributeEvent event;

			event = new ServletContextAttributeEvent(this, name, oldValue);

			for (int i = 0; i < _applicationAttributeListeners.size(); i++)
			{
				ServletContextAttributeListener listener;

				Object objListener = _applicationAttributeListeners.get(i);
				listener = (ServletContextAttributeListener) objListener;

				try
				{
					listener.attributeRemoved(event);
				}
				catch (Throwable e)
				{
					log.debug(e.toString(), e);
				}
			}
		}
	}
	
	/**
	 * Build a full resource location for the given path,
	 * prepending the resource base path of this MockServletContext.
	 * @param path the path as specified
	 * @return the full resource path
	 * @see org.springframework.mock.web.MockServletContext
	 */
	protected String getResourceLocation(String path) {
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		return  getRootDirectory() + SEPARATOR + "src" + SEPARATOR + "main" + SEPARATOR + "webapp" + SEPARATOR + path;
	}

	/**
	 * Maps from a URI to a real path.
	 * @see org.springframework.mock.web.MockServletContext
	 */
	@Override
	public String getRealPath(String path)
	{
		Resource resource = this.resourceLoader.getResource(getResourceLocation(path));
		try {
			return resource.getFile().getAbsolutePath();
		}
		catch (IOException ex) {
			log("Couldn't determine real path of resource " + resource, ex);
			return null;
		}
	}

	
	/**
	 * Returns a resource for the given uri.
	 * 
	 *
	 * @see org.springframework.mock.web.MockServletContext
	 */
	@Override
	public URL getResource(String path) throws java.net.MalformedURLException
	{
		Resource resource = this.resourceLoader.getResource(getResourceLocation(path));
		
		if (!resource.exists()) {
			return null;
		}
		try {
			return resource.getURL();
		}
		catch (MalformedURLException ex) {
			throw ex;
		}
		catch (IOException ex) {
			log.info("Couldn't get URL for " + resource, ex);
			return null;
		}
	}
	

	
	private static final char SEPARATOR = File.separatorChar;



	/**
	 * Returns the resource for a uripath as an input stream.
	 * @see org.springframework.mock.web.MockServletContext
	 */
	@Override
	public InputStream getResourceAsStream(String path)
	{
		Resource resource = this.resourceLoader.getResource(getResourceLocation(path));
		if (!resource.exists()) {
			return null;
		}
		try {
			return resource.getInputStream();
		}
		catch (IOException ex) {
			log.debug("Couldn't open InputStream for " + resource, ex);
			return null;
		}
	}

	/**
	 * Returns an enumeration of all the resources.
	 * @see  org.springframework.mock.web.MockServletContext.getResourcePaths(String path)
	 */
	@Override
	public Set<String> getResourcePaths(String path)
	{
		String actualPath = (path.endsWith("/") ? path : path + "/");
		Resource resource = this.resourceLoader.getResource(getResourceLocation(actualPath));
		try {
			File file = resource.getFile();
			String[] fileList = file.list();
			if (ObjectUtils.isEmpty(fileList)) {
				return null;
			}
			Set<String> resourcePaths = new LinkedHashSet<String>(fileList.length);
			for (String fileEntry : fileList) {
				String resultPath = actualPath + fileEntry;
				if (resource.createRelative(fileEntry).getFile().isDirectory()) {
					resultPath += "/";
				}
				resourcePaths.add(resultPath);
			}
			return resourcePaths;
		}
		catch (IOException ex) {
			log.warn("Couldn't get resource paths for " + resource, ex);
			return null;
		}
	}

	/**
	 * Returns the servlet context for the name.
	 */
	@Override
	public ServletContext getContext(String uri)
	{
		return this;
	}




	/**
	 * Returns a dispatcher for the named servlet.
	 */
	public RequestDispatcher getNamedDispatcher(String servletName)
	{
		return null;
	}

	/**
	 * Logging.
	 */

	/**
	 * Logs a message to the error file.
	 * 
	 * @param msg
	 *            the message to log
	 */
	@Override
	public final void log(String message)
	{
		log(message, null);
	}

	/**
	 * @deprecated
	 */
	@Override
	public final void log(Exception e, String msg)
	{
		log(msg, e);
	}

	/**
	 * Error logging
	 * 
	 * @param message
	 *            message to log
	 * @param e
	 *            stack trace of the error
	 */
	@Override
	public void log(String message, Throwable e)
	{
		if (e != null)
			log.debug(message, e);
		else
			log.info(message);
	}

	//
	// Deprecated methods
	//
	@Deprecated
	@Override
	public Servlet getServlet(String name)
	{
		throw new UnsupportedOperationException("getServlet is deprecated");
	}

	@Deprecated
	@Override
	public Enumeration<String> getServletNames()
	{
		throw new UnsupportedOperationException("getServletNames is deprecated");
	}

	public Enumeration<Servlet> getServlets()
	{
		throw new UnsupportedOperationException("getServlets is deprecated");
	}







	public void addListener(ListenerConfig config) throws Exception
	{

	}


	public <T extends EventListener> void addListener(T t)
	{
		throw new UnsupportedOperationException(getClass().getName());
	}

	public void addListener(Class<? extends EventListener> listenerClass)
	{
		throw new UnsupportedOperationException(getClass().getName());
	}

	public ClassLoader getClassLoader()
	{
		throw new UnsupportedOperationException(getClass().getName());
	}

	public void declareRoles(String... roleNames)
	{
		throw new UnsupportedOperationException(getClass().getName());
	}

	protected boolean isActive()
	{
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.servlet.ServletContext#createListener(java.lang.Class)
	 */
	@Override
	public <T extends EventListener> T createListener(Class<T> listenerClass) throws ServletException
	{
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public SessionCookieConfig getSessionCookieConfig()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<SessionTrackingMode> getDefaultSessionTrackingModes()
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public Set<SessionTrackingMode> getEffectiveSessionTrackingModes()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public JspConfigDescriptor getJspConfigDescriptor()
	{
		// TODO Auto-generated method stub
		return null;
	}
}
