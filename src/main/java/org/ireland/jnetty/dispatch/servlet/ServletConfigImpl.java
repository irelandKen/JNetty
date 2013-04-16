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

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.FilterChain;
import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletSecurityElement;
import javax.servlet.UnavailableException;
import javax.servlet.annotation.MultipartConfig;

import org.ireland.jnetty.dispatch.filterchain.ServletFilterChain;
import org.ireland.jnetty.webapp.WebApp;
import org.springframework.util.Assert;

import com.caucho.util.L10N;

/**
 * Configuration for a servlet.
 * 
 * 表示一个Servlet的所有配置(代表一个web.xml的<servlet>标签)
 * 
 	<servlet>
	
		<description>This is FirstServlet</description>
		
		<display-name>FirstServlet</display-name>
		
		<servlet-name>FirstServlet</servlet-name>
		<servlet-class>myServlet.FirstServlet</servlet-class>
		
		<init-param>
			<param-name>abc</param-name>
			<param-value>12</param-value>
		</init-param>
		
		<init-param>
			<param-name>addsf</param-name>
			<param-value>155</param-value>
		</init-param>
		
		<load-on-startup>2</load-on-startup>
		
		<async-supported>true</async-supported>
		
	</servlet>
 * 
 */
public class ServletConfigImpl implements ServletConfig, ServletRegistration.Dynamic
{

	static L10N L = new L10N(ServletConfigImpl.class);
	protected static final Logger log = Logger.getLogger(ServletConfigImpl.class.getName());

	
	private final WebApp _webApp;

	private final ServletContext _servletContext;

	private final ServletManager _servletManager;
	
	private final ServletMapper _servletMapper;
	
	
	
	// Servlet的类的硬盘本地位置
	private String _location;

	private String _servletName;
	private String _servletNameDefault;

	private String _servletClassName;
	private Class<? extends Servlet> _servletClass;
	private String _displayName;
	
	private int _loadOnStartup = Integer.MIN_VALUE;
	
	private boolean _asyncSupported;

	private HashMap<String, String> _initParams = new HashMap<String, String>();

	// used for params defined prior to applying fragments.
	private Set<String> _paramNames = new HashSet<String>();

	private MultipartConfigElement _multipartConfigElement;



	//Servlet的实例(单例)
	private Servlet _servlet;
	
	//Servlet实例对应的ServletFilterChain
	private FilterChain _servletChain;

	/**
	 * Creates a new servlet configuration object.
	 */
	public ServletConfigImpl(WebApp _webApp, ServletContext _servletContext, ServletManager _servletManager, ServletMapper _servletMapper)
	{
		Assert.notNull(_webApp);
		Assert.notNull(_servletContext);
		Assert.notNull(_servletManager);
		Assert.notNull(_servletMapper);
		
		this._webApp = _webApp;
		this._servletContext = _servletContext;
		this._servletManager = _servletManager;
		this._servletMapper = _servletMapper;
	}

	protected void copyFrom(ServletConfigImpl source)
	{
		_initParams.putAll(source._initParams);
	}

	public WebApp getWebApp()
	{
		return _webApp;
	}
	
	/**
	 * Sets the config location.
	 */
	public void setConfigUriLocation(String location, int line)
	{
		_location = location + ":" + line + ": ";
	}

	/**
	 * Sets the servlet name.
	 */
	public void setServletName(String name)
	{
		_servletName = name;
	}

	/**
	 * Gets the servlet name.
	 */
	@Override
	public String getServletName()
	{
		return _servletName;
	}

	@Override
	public String getName()
	{
		return getServletName();
	}

	@Override
	public String getClassName()
	{
		return _servletClassName;
	}

	/**
	 * 实际上是add初始化参数
	 */
	@Override
	public boolean setInitParameter(String name, String value)
	{
		if (_initParams.containsKey(name))
			return false;

		_initParams.put(name, value);

		return true;
	}

	@Override
	public void setMultipartConfig(MultipartConfigElement multipartConfig)
	{
		if (multipartConfig == null)
			throw new IllegalArgumentException();

		_multipartConfigElement = multipartConfig;
	}

	public MultipartConfigElement getMultipartConfig()
	{
		if (_multipartConfigElement == null)
		{
			Class<?> servletClass = null;

			try
			{
				servletClass = getServletClass();
			}
			catch (Exception e)
			{
				log.log(Level.FINER, e.toString(), e);
			}

			if (servletClass != null)
			{
				MultipartConfig config = (MultipartConfig) servletClass.getAnnotation(MultipartConfig.class);

				if (config != null)
					_multipartConfigElement = new MultipartConfigElement(config);
			}
		}

		return _multipartConfigElement;
	}

	/**
	 * Maps or exists if any of the patterns in urlPatterns already map to a different servlet
	 * 
	 * @param urlPatterns
	 * @return a Set of patterns previously mapped to a different servlet
	 */
	@Override
	public Set<String> addMapping(String... urlPatterns)
	{

		try
		{
			Set<String> result = new HashSet<String>();

			// server/12t8 vs server/12uc

			for (String urlPattern : urlPatterns)
			{
				ServletMapping mapping = _servletMapper.getServletMapping(urlPattern);

				if (mapping == null || mapping.isDefault())
				{
					continue;
				}

				String servletName = mapping.getServletConfig().getServletName();

				if (!_servletName.equals(servletName) && servletName != null)
				{
					if (log.isLoggable(Level.FINE))
					{
						log.fine(L.l("programmatic addMapping for '{0}' ignored because of existing servlet-mapping to '{1}'", urlPattern, servletName));
					}

					result.add(urlPattern);
				}
			}

			if (result.size() > 0)
			{
				return result;
			}

			ServletMapping mapping = _webApp.createNewServletMapping(this);
			
			mapping.setIfAbsent(true);

			for (String urlPattern : urlPatterns)
			{
				mapping.addURLPattern(urlPattern);
			}

			_webApp.addServletMapping(mapping);

			return Collections.unmodifiableSet(result);
		}
		catch (ServletException e)
		{
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	@Override
	public Collection<String> getMappings()
	{
		Set<String> patterns = _servletMapper.getUrlPatterns(_servletName);

		if (patterns != null)
			return Collections.unmodifiableSet(new LinkedHashSet<String>(patterns));
		else
			return new LinkedHashSet<String>();
	}

	/**
	 * 设置初始化参数
	 */
	@Override
	public Set<String> setInitParameters(Map<String, String> initParameters)
	{

		Set<String> conflicting = new HashSet<String>();

		for (Map.Entry<String, String> param : initParameters.entrySet())
		{
			if (_initParams.containsKey(param.getKey()))
				conflicting.add(param.getKey());
			else
				_initParams.put(param.getKey(), param.getValue());
		}

		return Collections.unmodifiableSet(conflicting);
	}

	public Map<String, String> getInitParameters()
	{
		return _initParams;
	}

	public void setAsyncSupported(boolean asyncSupported)
	{
		_asyncSupported = asyncSupported;
	}

	public boolean isAsyncSupported()
	{
		return _asyncSupported;
	}

	/**
	 * Sets the servlet name default when not specified
	 */
	public void setServletNameDefault(String name)
	{
		_servletNameDefault = name;
	}

	/**
	 * Gets the servlet name default.
	 */
	public String getServletNameDefault()
	{
		return _servletNameDefault;
	}

	/**
	 * Gets the servlet name.
	 */
	public String getServletClassName()
	{
		return _servletClassName;
	}

	/**
	 * 
	 * @return true:表示Servlet已配置完成
	 */
	public boolean isServletConfig()
	{
		return _servletClassName != null;
	}

	/**
	 * Sets the servlet class. 设置Servlet Class 并加载
	 */
	public void setServletClass(String servletClassName)
	{
		_servletClassName = servletClassName;

		ClassLoader loader = Thread.currentThread().getContextClassLoader();

		try
		{
			_servletClass = (Class<? extends Servlet>) Class.forName(servletClassName, false, loader);

		}
		catch (ClassNotFoundException e)
		{
			log.log(Level.ALL, e.toString(), e);
		}
	}

	public void setServletClass(Class<? extends Servlet> servletClass)
	{
		if (_servletClass == null)
		{
			throw new NullPointerException();
		}

		_servletClass = servletClass;
	}

	/**
	 * Gets the servlet class. 加载Servlet的Class
	 */
	public Class<? extends Servlet> getServletClass()
	{

		if (_servletClassName == null)
			return null;

		if (_servletClass == null)
		{
			try
			{
				Thread thread = Thread.currentThread();
				ClassLoader loader = thread.getContextClassLoader();

				_servletClass = (Class<? extends Servlet>) Class.forName(calculateServletClassName(), false, loader);
			}
			catch (Exception e)
			{
				error(L.l("'{0}' is not a known servlet class.  Servlets belong in the classpath, for example WEB-INF/classes.", _servletClassName), e);
			}
		}

		return _servletClass;
	}

	protected String calculateServletClassName()
	{
		return getServletClassName();
	}

	public void setServlet(Servlet servlet)
	{
		_servlet = servlet;
	}

	/**
	 * Sets an init-param
	 */
	public void setInitParam(String param, String value)
	{
		_initParams.put(param, value);
	}

	/**
	 * Gets the init params
	 */
	public Map getInitParamMap()
	{
		return _initParams;
	}

	/**
	 * Gets the init params
	 */
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
	 * Returns the servlet context.
	 */
	public ServletContext getServletContext()
	{
		return _servletContext;
	}


	/**
	 * Returns the servlet manager.
	 */
	public ServletManager getServletManager()
	{
		return _servletManager;
	}

	
	/**
	 * Sets the load-on-startup
	 */
	public void setLoadOnStartup(int loadOnStartup)
	{
		_loadOnStartup = loadOnStartup;
	}

	/**
	 * Gets the load-on-startup value.
	 */
	public int getLoadOnStartup()
	{
		if (_loadOnStartup > Integer.MIN_VALUE)
			return _loadOnStartup;
		else
			return Integer.MIN_VALUE;
	}

	/**
	 * Sets the display name
	 */
	public void setDisplayName(String displayName)
	{
		_displayName = displayName;
	}

	/**
	 * Gets the display name
	 */
	public String getDisplayName()
	{
		return _displayName;
	}

	/**
	 * Sets the description
	 */
	public void setDescription(String description)
	{
	}

	/**
	 * Returns the servlet.
	 */
	public Servlet getServlet()
	{
		return _servlet;
	}

	public void merge(ServletConfigImpl config)
	{
		if (_loadOnStartup == Integer.MIN_VALUE)
			_loadOnStartup = config._loadOnStartup;

		if (!getClassName().equals(config.getClassName()))
			throw new RuntimeException(
					L.l("Illegal attempt to specify different servlet-class '{0}' for servlet '{1}'. Servlet '{1}' has already been defined with servlet-class '{2}'. Consider using <absolute-ordering> to exclude conflicting web-fragment.",
							config.getClassName(), _servletName, _servletClassName));

		for (Map.Entry<String, String> param : config._initParams.entrySet())
		{
			if (_paramNames.contains(param.getKey()))
			{
			}
			else if (!_initParams.containsKey(param.getKey()))
				_initParams.put(param.getKey(), param.getValue());
			else if (!_initParams.get(param.getKey()).equals(param.getValue()))
			{
				throw new RuntimeException(
						L.l("Illegal attempt to specify different param-value of '{0}' for parameter '{1}'. This error indicates that two web-fragments use different values. Consider defining the parameter in web.xml to override definitions in web-fragment.",
								param.getValue(), param.getKey()));
			}
		}
	}

	/**
	 * Initialize the servlet config. 初始化Servlet配置
	 */
	public void init() throws ServletException
	{

		if (_servletName == null)
		{
			if (getServletNameDefault() != null)
				_servletName = getServletNameDefault();
			else
				setServletName(_servletClassName);
		}
	}

	/**
	 * 验证Servlet的Class是否存在,是否实现javax.servlet.Servlet接口
	 * 
	 * @param requireClass
	 * @throws ServletException
	 */
	protected void validateClass(boolean requireClass) throws ServletException
	{

		if (_loadOnStartup >= 0)
			requireClass = true;

		if (_servletClassName == null)
		{
		}
		else
		{
			if(_servletClass == null)
			{
				try
				{
					_servletClass = (Class<? extends Servlet>) Class.forName(_servletClassName, false, _webApp.getClassLoader());
				}
				catch (ClassNotFoundException e)
				{
					log.log(Level.FINER, e.toString(), e);
				}
			}

			if (_servletClass != null)
			{
			}
			else if (requireClass)
			{
				throw error(L.l("'{0}' is not a known servlet class.  Servlets belong in the classpath, for example WEB-INF/classes.", _servletClassName));
			}
			else
			{
				String location = _location != null ? _location : "";

				log.warning(L.l(location + "'{0}' is not a known servlet.  Servlets belong in the classpath, often in WEB-INF/classes.", _servletClassName));
				return;
			}

			if (!Servlet.class.isAssignableFrom(_servletClass))
			{
				throw error(L.l("'{0}' must implement javax.servlet.Servlet  All servlets must implement the Servlet interface.", _servletClassName));
			}

		}
	}

	/**
	 * Checks the class constructor for the public-zero arg. 检查Class是否实现了无参数的public的构造方法
	 */
	public void checkConstructor() throws ServletException
	{
		Constructor[] constructors = _servletClass.getDeclaredConstructors();

		Constructor zeroArg = null;
		for (int i = 0; i < constructors.length; i++)
		{
			if (constructors[i].getParameterTypes().length == 0)
			{
				zeroArg = constructors[i];
				break;
			}
		}

		if (zeroArg == null)
			throw error(L.l("'{0}' must have a zero arg constructor.  Servlets must have public zero-arg constructors.\n{1} is not a valid constructor.",
					_servletClassName, constructors != null ? constructors[0] : null));

		if (!Modifier.isPublic(zeroArg.getModifiers()))
			throw error(L.l("'{0}' must be public.  '{1}' must have a public, zero-arg constructor.", zeroArg, _servletClassName));
	}

	public FilterChain createServletChain() throws ServletException
	{
		synchronized (this)
		{
			if (_servletChain != null)
				return _servletChain;
			else
				return createServletChainImpl();
		}
	}

	/**
	 * 
	 * 
	 * @return
	 * @throws ServletException
	 */
	private FilterChain createServletChainImpl() throws ServletException
	{
		FilterChain servletChain = null;

		if (_servlet != null)
		{
			servletChain = new ServletFilterChain(this);

			return servletChain;
		}

		validateClass(true);

		Class<?> servletClass = getServletClass();

		if (servletClass == null)
		{
			throw new IllegalStateException(L.l("servlet class for {0} can't be null", getServletName()));
		}
		else
		{
			servletChain = new ServletFilterChain(this);
		}

		return servletChain;
	}

	/**
	 * Instantiates a servlet given its configuration.
	 * 
	 * 返回Servlet的实例(单例的,多次调用返回同一个实例)
	 * 
	 * 1:如果Servlet实例不存在,则创建一个Servlet实例,
	 *   并初始化它(调用javax.servlet.Servlet#init(ServletConfig config)
	 * 
	 * 2:返回已初始化的Servlet实例.
	 * 
	 * 
	 * @return the initialized servlet.
	 */
	public Servlet getInstance() throws ServletException
	{
		// server/102e
		if (_servlet != null)
			return _servlet;

		_servlet = createServletAndInit();
		
		return _servlet;
	}

	/*
	 * 
	 * 
	 * 实例化一个Servlet,并初始化它
	 * 
	 * servlet.init(this);
	 */
	private Servlet createServletAndInit() throws ServletException
	{

		Class<? extends Servlet> servletClass = getServletClass();

		Servlet servlet;

		if (servletClass == null)
			throw new ServletException(L.l("Null servlet class for '{0}'.", _servletName));

		
		try
		{
			servlet = servletClass.newInstance();
		}
		catch (Exception e)
		{
			throw new ServletException(e);
		}
			

		// 配置Servlet
		configureServlet(servlet);

		//初始化
		servlet.init(this);

		if (log.isLoggable(Level.FINE))
			log.finer("Servlet[" + _servletName + "] instantiated and inited");
		
		return servlet;
	}

	/**
	 * Configure the servlet (everything that is done after instantiation but before servlet.init()
	 */
	void configureServlet(Object servlet)
	{

	}

	/**
	 * @see javax.servlet.Servlet#destroy()
	 */
	public void destroyServlet()
	{
		Object servlet = _servlet;
		_servlet = null;

		if (servlet instanceof Servlet)
		{
			((Servlet) servlet).destroy();
		}
	}

	/**
	 * 销毁Servlet
	 */
	public void close()
	{
		destroyServlet();
	}

	protected ServletException error(String msg)
	{
		ServletException e;

		if (_location != null)
			e = new ServletException(_location + msg);
		else
			e = new ServletException(msg);

		log.warning(e.getMessage());

		return e;
	}

	protected ServletException error(String msg, Throwable e)
	{
		ServletException e1;

		if (_location != null)
			e1 = new ServletException(_location + msg, e);
		else
			e1 = new ServletException(msg, e);

		log.warning(e1.getMessage());

		return e1;
	}

	protected RuntimeException error(Throwable e)
	{
		RuntimeException e1;

		if (_location != null)
			e1 = new RuntimeException(_location + e.getMessage(), e);
		else
			e1 = new RuntimeException(e);

		log.warning(e1.toString());

		return e1;
	}

	/**
	 * Returns a printable representation of the servlet config object.
	 */
	public String toString()
	{
		return getClass().getSimpleName() + "[name=" + _servletName + ",class=" + _servletClass + "]";
	}

	
	//unused-------------------------------------------------
	@Override
	public String getRunAsRole()
	{
		return null;
	}

	@Override
	public void setRunAsRole(String roleName)
	{
	}

	@Override
	public Set<String> setServletSecurity(ServletSecurityElement constraint)
	{
		return null;
	}

	public ServletMapper getServletMapper()
	{
		return _servletMapper;
	}



}
