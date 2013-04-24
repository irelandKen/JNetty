//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.ireland.jnetty.loader;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/* ------------------------------------------------------------ */
/**
 * ClassLoader for HttpContext. Specializes URLClassLoader with some utility and file mapping methods.
 * 
 * This loader defaults to the 2.3 servlet spec behavior where non system classes are loaded from the classpath in
 * preference to the parent loader. Java2 compliant loading, where the parent loader always has priority, can be
 * selected with the {@link org.eclipse.jetty.webapp.WebAppContext#setParentLoaderPriority(boolean)} method and
 * influenced with {@link WebAppContext#isServerClass(String)} and {@link WebAppContext#isSystemClass(String)}.
 * 
 * If no parent class loader is provided, then the current thread context classloader will be used. If that is null then
 * the classloader that loaded this class is used as the parent.
 * 
 */
public class WebAppClassLoader extends URLClassLoader
{
	private static final Log LOG = LogFactory.getLog(WebAppClassLoader.class);

	//private final ServletContext _servletContext;

	//Default: SystemClassLoader
	private final ClassLoader _parent;

	
	private String _name = String.valueOf(hashCode());

	
	/**
	 * Should this class loader delegate to the parent class loader <strong>before</strong> searching its own
	 * repositories (i.e. the usual Java2 delegation model)? If set to <code>false</code>, this class loader will search
	 * its own repositories first, and delegate to the parent only if the class or resource is not found locally. Note
	 * that the default, <code>false</code>, is the behavior called for by the servlet specification.
	 */
	protected boolean delegate = false;


	/* ------------------------------------------------------------ */
	/**
	 * Constructor.
	 */
	public WebAppClassLoader() throws IOException
	{
		this(null);
	}

	/* ------------------------------------------------------------ */
	/**
	 * Constructor.
	 */
	public WebAppClassLoader(ClassLoader parent) throws IOException
	{
		super(new URL[] {}, 
				parent != null ? parent : (Thread.currentThread().getContextClassLoader() != null ? Thread.currentThread().getContextClassLoader()
				: (WebAppClassLoader.class.getClassLoader() != null ? WebAppClassLoader.class.getClassLoader() : ClassLoader.getSystemClassLoader())));
		
		_parent = getParent();

		if (_parent == null)
			throw new IllegalArgumentException("no parent classloader!");
	}

	/* ------------------------------------------------------------ */
	/**
	 * @return the name of the classloader
	 */
	public String getName()
	{
		return _name;
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param name
	 *            the name of the classloader
	 */
	public void setName(String name)
	{
		_name = name;
	}



	/* ------------------------------------------------------------ */
	/**
	 * @param classPath
	 *            Comma or semicolon separated path of filenames or URLs pointing to directories or jar files.
	 *            Directories should end with '/'.
	 */
	public void addClassPath(URL classPath)
	{
		addURL(classPath);
	}



	/* ------------------------------------------------------------ */
	/**
	 * Add elements to the class path for the context from the jar and zip files found in the specified resource.
	 * 
	 * @param lib
	 *            the resource that contains the jar and/or zip files.
	 */
	public void addJar(URL jarFile)
	{
		addURL(jarFile);
	}


	/* ------------------------------------------------------------ */
	@Override
	public Enumeration<URL> getResources(String name) throws IOException
	{
		List<URL> from_parent = toList( _parent.getResources(name));
		List<URL> from_webapp = toList( this.findResources(name));

		if (delegate)
		{
			from_parent.addAll(from_webapp);
			return Collections.enumeration(from_parent);
		}
		
		from_webapp.addAll(from_parent);
		return Collections.enumeration(from_webapp);
	}

	/* ------------------------------------------------------------ */
	private List<URL> toList(Enumeration<URL> e)
	{
		if (e == null)
			return new ArrayList<URL>();
		return Collections.list(e);
	}

	/* ------------------------------------------------------------ */
	/**
	 * Get a resource from the classloader
	 * 
	 */
	@Override
	public URL getResource(String name)
	{
		URL url = null;

		// (1) Delegate to parent if requested
		if (delegate)
		{
			url = _parent.getResource(name);
			
            if (url != null) {
                
            	if (LOG.isDebugEnabled())
                	LOG.debug("  --> Returning '" + url.toString() + "' from parent classloader: "+_parent);
                
                return (url);
            }
		}

		
		// (2) Search local resources
		url = this.findResource(name);
		
        if (url != null) {
            
        	if (LOG.isDebugEnabled())
            	LOG.debug("  --> Returning '" + url.toString() + "' from "+toString());
            
            return (url);
        }
		
		
        // (3) Delegate to parent unconditionally if not already attempted
		if (!delegate)
		{
			url = _parent.getResource(name);
			
            if (url != null) {
                
            	if (LOG.isDebugEnabled())
                	LOG.debug("  --> Returning '" + url.toString() + "' from parent classloader: "+_parent);
                
                return (url);
            }
		}

		
        // (4) Resource was not found
        if (LOG.isDebugEnabled())
            LOG.debug("  --> Resource not found, returning null");
        return (null);
	}

	/* ------------------------------------------------------------ */
	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException
	{
		return loadClass(name, false);
	}

	/* ------------------------------------------------------------ */
	@Override
	protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
	{
		Class<?> clazz = loadClass0(name);

		if (clazz != null)
		{
			if (resolve)
				resolveClass(clazz);
			
			return (clazz);
		}

		throw new ClassNotFoundException(name);
	}

	
	/**
	 * Load class but not resolve
	 * @param name
	 * @return
	 * @throws ClassNotFoundException
	 */
	protected synchronized Class<?> loadClass0(String name) throws ClassNotFoundException
	{
		// (0) Check our previously loaded class cache

		Class<?> clazz = findLoadedClass(name);

		if (clazz != null)
		{
			//if (LOG.isDebugEnabled()) LOG.debug("  Returning class from cache,class: "+clazz);

			return (clazz);
		}

		
		// (1) Delegate to our parent if requested
		if (delegate)
		{
			if (LOG.isDebugEnabled())
				LOG.debug("  Delegating to parent classloader1: " + _parent);

			ClassLoader loader = _parent;
			try
			{
				clazz = Class.forName(name, false, loader);
				if (clazz != null)
				{
					if (LOG.isDebugEnabled())
						LOG.debug("  Loaded class from parent,class: "+clazz);
					return (clazz);
				}
			}
			catch (ClassNotFoundException e)
			{
				// Ignore
			}
		}
		
		// (2) Search local resources
		if (LOG.isDebugEnabled())
			LOG.debug("  Searching local ClassPaths @ " + name);
		
		try
		{
			clazz = findClass(name);
			if (clazz != null)
			{
				if (LOG.isDebugEnabled())
					LOG.debug("  Loaded class from local ClassPaths,class: "+clazz);
				return (clazz);
			}
		}
		catch (ClassNotFoundException e)
		{
			// Ignore
		}

		// (3) Delegate to parent unconditionally
		if (!delegate)
		{
			if (LOG.isDebugEnabled())
				LOG.debug("  Delegating to parent classloader at end: " + _parent);

			ClassLoader loader = _parent;

			try
			{
				clazz = Class.forName(name, false, loader);
				if (clazz != null)
				{
					if (LOG.isDebugEnabled())
						LOG.debug("  Loaded class from parent,class: "+clazz);
					return (clazz);
				}
			}
			catch (ClassNotFoundException e)
			{
				// Ignore
			}
		}

		throw new ClassNotFoundException(name);
	}
	
	/* ------------------------------------------------------------ */
	@Override
	public String toString()
	{
		return "WebAppClassLoader=" + _name + "@" + Long.toHexString(hashCode());
	}
}
