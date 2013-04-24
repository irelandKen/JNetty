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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.SessionCookieConfig;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.InstanceManager;

import org.ireland.jnetty.beans.BeanFactory;
import org.ireland.jnetty.config.ConfigException;
import org.ireland.jnetty.config.ListenerConfig;
import org.ireland.jnetty.config.WebXmlLoader;
import org.ireland.jnetty.dispatch.Invocation;
import org.ireland.jnetty.dispatch.filter.FilterConfigImpl;
import org.ireland.jnetty.dispatch.filter.FilterManager;
import org.ireland.jnetty.dispatch.filter.FilterMapper;
import org.ireland.jnetty.dispatch.filter.FilterMapping;
import org.ireland.jnetty.dispatch.filterchain.ExceptionFilterChain;
import org.ireland.jnetty.dispatch.filterchain.ServletRequestListenerFilterChain;
import org.ireland.jnetty.dispatch.servlet.ServletConfigImpl;
import org.ireland.jnetty.dispatch.servlet.ServletManager;
import org.ireland.jnetty.dispatch.servlet.ServletMapper;
import org.ireland.jnetty.dispatch.servlet.ServletMapping;
import org.ireland.jnetty.jsp.JspServletComposite;
import org.ireland.jnetty.loader.WebAppClassLoader;
import org.ireland.jnetty.server.session.SessionManager;
import org.ireland.jnetty.util.http.Encoding;
import org.ireland.jnetty.util.http.URIDecoder;

import org.springframework.util.Assert;

import com.caucho.i18n.CharacterEncoding;

import com.caucho.util.LruCache;

/**
 * Resin's webApp implementation.
 */
public class WebApp extends ServletContextImpl
{
	private static final Log log = LogFactory.getLog(WebApp.class.getName());

	// The context path is the URL prefix for the web-app
	private String _contextPath;

	// The environment class loader
	private ClassLoader _classLoader;

	private String _host;

	private String _hostName = "";

	// The canonical URL
	private String _url;

	private String _serverName = "";
	private int _serverPort;

	// The webbeans container
	private BeanFactory _beanFactory;

	private URIDecoder _uriDecoder;

	private String _servletVersion;

	// -----servlet--------------------------

	// The servlet manager
	private ServletManager _servletManager;

	// The servlet mapper
	private ServletMapper _servletMapper;
	// -----servlet--------------------------

	// -----filter--------------------------
	// The filter manager
	private FilterManager _filterManager;

	// The dispatch filter mapper (DispatcherType#REQUEST)
	private FilterMapper _dispatchFilterMapper;

	// The forward filter mapper (DispatcherType#FORWARD)
	private FilterMapper _forwardFilterMapper;

	// The include filter mapper (DispatcherType#INCLUDE)
	private FilterMapper _includeFilterMapper;

	// The error filter mapper (DispatcherType#ERROR)
	private FilterMapper _errorFilterMapper;
	// -----filter--------------------------

	// The FilterChain Cache

	// 用LRU算法Cache最近最常使用的url与FilterChain之间的映射关系()
	private LruCache<String, FilterChainEntry> _dispatchFilterChainCache = new LruCache<String, FilterChainEntry>(128);
	private LruCache<String, FilterChainEntry> _forwardFilterChainCache = new LruCache<String, FilterChainEntry>(128);
	private LruCache<String, FilterChainEntry> _includeFilterChainCache = new LruCache<String, FilterChainEntry>(32);
	private LruCache<String, FilterChainEntry> _errorFilterChainCache = new LruCache<String, FilterChainEntry>(32);

	// <rowContextURI,_requestDispatcherCache>
	private LruCache<String, RequestDispatcherImpl> _requestDispatcherCache;

	// True for SSL secure.
	private boolean _isSecure;

	// Error pages.
	private ErrorPageManager _errorPageManager;

	private String errorPage;

	private LruCache<String, String> _realPathCache = new LruCache<String, String>(1024);

	// real-path mapping
	// private RewriteRealPath _rewriteRealPath;

	// mime mapping
	private HashMap<String, String> _mimeMapping = new HashMap<String, String>();

	// locale mapping
	private HashMap<String, String> _localeMapping = new HashMap<String, String>();

	// listeners------------
	// List of all the listeners.
	private List<ListenerConfig> _listeners = new ArrayList<ListenerConfig>();

	// List of the ServletContextListeners from the configuration file
	private ArrayList<ServletContextListener> _contextListeners = new ArrayList<ServletContextListener>();

	// List of the ServletContextAttributeListeners from the configuration file
	private ArrayList<ServletContextAttributeListener> _contextAttributeListeners = new ArrayList<ServletContextAttributeListener>();

	// List of the ServletRequestListeners from the configuration file
	private ArrayList<ServletRequestListener> _requestListeners = new ArrayList<ServletRequestListener>();

	// List of the ServletRequestAttributeListeners from the configuration file
	private ArrayList<ServletRequestAttributeListener> _requestAttributeListeners = new ArrayList<ServletRequestAttributeListener>();

	// listeners-----------------------------------------------

	// WebApp的根目录
	private final String _rootDirectory;

	private String _tempDir;

	private boolean _cookieHttpOnly;

	private HashMap<String, Object> _extensions = new HashMap<String, Object>();

	private boolean _isEnabled = true;

	// The session manager
	private SessionManager _sessionManager;

	private String _characterEncoding;

	/**
	 * Creates the webApp with its environment loader.
	 */
	public WebApp(String rootDirectory, String host, String contextPath)
	{
		_rootDirectory = rootDirectory;
		_host = host;

		if (contextPath == null || contextPath.equals("ROOT") || contextPath.equals("/")) // ROOT Context
			_contextPath = "";
		else
			_contextPath = contextPath;

		if (_host == null)
			throw new IllegalStateException(L.l("{0} requires an active {1}", getClass().getSimpleName()));

		initClassLoader();

		//if (log.isDebugEnabled())
		//	displayClassLoader();
		//
		Thread.currentThread().setContextClassLoader(getClassLoader());

		_uriDecoder = new URIDecoder();

		initConstructor();
	}

	/**
	 * 使用自定义的类加载器,将/WEB-INF/classes和/WEB-INF/lib/*.jar加入类加载器的classPath
	 */
	protected void initClassLoader()
	{
		WebAppClassLoader webAppClassLoader = null;
		try
		{
			ClassLoader parent = Thread.currentThread().getContextClassLoader();

			if (parent == null)
				parent = WebApp.class.getClassLoader();

			webAppClassLoader = new WebAppClassLoader(parent);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		List<URL> urls = new ArrayList<URL>();

		// JarFile: "/WEB-INF/lib"
		File libPath = new File(getRealPath("/WEB-INF/lib"));

		if (libPath.isDirectory())
		{
			for (File file : libPath.listFiles())
			{
				if (file.getPath().endsWith(".jar"))
				{
					try
					{
						URL url = file.toURI().toURL();
						urls.add(url);
					}
					catch (MalformedURLException e)
					{
						e.printStackTrace();
					}
				}
			}
		}

		for (URL jarFile : urls)
		{
			webAppClassLoader.addJar(jarFile);
		}

		// ClassPath: "/WEB-INF/classes/"
		URL classPath = null;
		try
		{
			classPath = super.getResource("/WEB-INF/classes/");
		}
		catch (MalformedURLException e)
		{
			e.printStackTrace();
		}

		if (classPath != null)
		{
			webAppClassLoader.addClassPath(classPath);
		}

		_classLoader = webAppClassLoader;
	}

	void displayClassLoader()
	{
		log.debug("BootstrapClassLoader 的加载路径: ");

		URL[] urls = sun.misc.Launcher.getBootstrapClassPath().getURLs();
		for (URL url : urls)
			log.debug(url);
		log.debug("----------------------------");

		// 取得扩展类加载器
		URLClassLoader extClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader().getParent();

		log.debug(extClassLoader);
		log.debug("扩展类加载器 的加载路径: ");

		urls = extClassLoader.getURLs();
		for (URL url : urls)
			log.debug(url);

		log.debug("----------------------------");

		// 取得应用(系统)类加载器
		URLClassLoader appClassLoader = (URLClassLoader) _classLoader.getParent();

		log.debug(appClassLoader);
		log.debug("应用(系统)类加载器 的加载路径: ");

		urls = appClassLoader.getURLs();
		for (URL url : urls)
			log.debug(url);

		log.debug("----------------------------");

		// 取得应用(系统)类加载器
		appClassLoader = (URLClassLoader) _classLoader;

		log.debug(appClassLoader);
		log.debug("应用(系统)类加载器 的加载路径: ");

		urls = appClassLoader.getURLs();
		for (URL url : urls)
			log.debug(url);

		log.debug("----------------------------");
	}

	private void initConstructor()
	{

		_beanFactory = new BeanFactory(getClassLoader());

		_servletManager = new ServletManager(this);
		_servletMapper = new ServletMapper(this, this, _servletManager);

		_filterManager = new FilterManager(this, this);

		_dispatchFilterMapper = new FilterMapper(this, _filterManager, DispatcherType.REQUEST);

		_includeFilterMapper = new FilterMapper(this, _filterManager, DispatcherType.INCLUDE);

		_forwardFilterMapper = new FilterMapper(this, _filterManager, DispatcherType.FORWARD);

		_errorFilterMapper = new FilterMapper(this, _filterManager, DispatcherType.ERROR);

		// _errorPageManager = new ErrorPageManager(_server, this);

		// Use JVM temp dir as ServletContext temp dir.
		_tempDir = System.getProperty(TEMPDIR);

		_sessionManager = new SessionManager(this);

	}

	/**
	 * Gets the webApp directory.
	 */
	@Override
	public String getRootDirectory()
	{
		return _rootDirectory;
	}

	/**
	 * Returns the webApp's canonical context path, e.g. /foo-1.0
	 */
	@Override
	public String getContextPath()
	{
		return _contextPath;
	}

	void setContextPath(String contextPath)
	{
		// server/1h10

		_contextPath = contextPath;
	}

	/**
	 * Returns the owning host.
	 */
	public String getHost()
	{
		return _host;
	}

	public URIDecoder getURIDecoder()
	{

		return _uriDecoder;
	}

	/**
	 * Gets the environment class loader.
	 */
	@Override
	public ClassLoader getClassLoader()
	{
		if (_classLoader == null)
		{
			_classLoader = Thread.currentThread().getContextClassLoader();

			if (_classLoader == null)
				_classLoader = this.getClass().getClassLoader();
		}

		return _classLoader;
	}

	/**
	 * Sets the servlet version.
	 */

	public void setVersion(String version)
	{
		_servletVersion = version;
	}

	/**
	 * Returns the servlet version.
	 */
	public String getVersion()
	{
		return _servletVersion;
	}

	public void setEnabled(boolean isEnabled)
	{
		_isEnabled = isEnabled;
	}

	public boolean isEnabled()
	{
		return _isEnabled;
	}

	/**
	 * Gets the URL
	 */
	public String getURL()
	{
		return getContextPath();
	}

	/**
	 * Gets the URL
	 */
	public String getHostName()
	{
		return _hostName;
	}

	/**
	 * Adds a servlet configuration.
	 */
	public void addServlet(ServletConfigImpl config) throws ServletException
	{
		checkServlerConfig(config);

		_servletManager.addServlet(config);
	}

	/**
	 * 检查ServletConfigImpl里的webApp,servletContext,ServletManager是否符合本WebApp里的
	 * 
	 * @param config
	 */
	private void checkServlerConfig(ServletConfigImpl config)
	{
		Assert.notNull(config);

		Assert.isTrue(config.getWebApp() == this);
		Assert.isTrue(config.getServletContext() == this);
		Assert.isTrue(config.getServletManager() == this._servletManager);
		Assert.isTrue(config.getServletMapper() == this.getServletMapper());
	}

	/**
	 * 检查ServletConfigImpl里的webApp,servletContext,ServletManager是否符合本WebApp里的
	 * 
	 * @param config
	 */
	private void checkServletMapping(ServletMapping servletMapping)
	{
		Assert.notNull(servletMapping);

		checkServlerConfig(servletMapping.getServletConfig());
	}

	@Override
	public <T extends Servlet> T createServlet(Class<T> servletClass) throws ServletException
	{
		return _beanFactory.createBean(servletClass);
	}

	@Override
	public ServletRegistration.Dynamic addServlet(String servletName, String className)
	{
		Class<? extends Servlet> servletClass;

		try
		{
			servletClass = (Class) Class.forName(className, false, getClassLoader());
		}
		catch (ClassNotFoundException e)
		{
			throw new IllegalArgumentException(L.l("'{0}' is an unknown class in {1}", className, this), e);
		}

		return addServlet(servletName, className, servletClass, null);
	}

	@Override
	public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass)
	{
		return addServlet(servletName, servletClass.getName(), servletClass, null);
	}

	@Override
	public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet)
	{
		Class cl = servlet.getClass();

		return addServlet(servletName, cl.getName(), cl, servlet);
	}

	/**
	 * Adds a new or augments existing registration
	 * 
	 * @since 3.0
	 */
	private ServletRegistration.Dynamic addServlet(String servletName, String servletClassName, Class<? extends Servlet> servletClass, Servlet servlet)
	{

		try
		{
			ServletConfigImpl config = (ServletConfigImpl) getServletRegistration(servletName);

			if (config == null)
			{
				config = createNewServletConfig();

				config.setServletName(servletName);
				config.setServletClass(servletClassName);
				config.setServletClass(servletClass);
				config.setServlet(servlet);

				addServlet(config);
			}
			else
			{
				if (config.getClassName() == null)
					config.setServletClass(servletClassName);

				if (config.getServletClass() == null)
					config.setServletClass(servletClass);

				if (config.getServlet() == null)
					config.setServlet(servlet);
			}

			if (log.isDebugEnabled())
			{
				log.debug(L.l("dynamic servlet added [name: '{0}', class: '{1}'] (in {2})", servletName, servletClassName, this));
			}

			return config;
		}
		catch (ServletException e)
		{
			// spec declares no throws so far
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	@Override
	public ServletRegistration getServletRegistration(String servletName)
	{
		return _servletManager.getServlet(servletName);
	}

	@Override
	public Map<String, ServletRegistration> getServletRegistrations()
	{
		Map<String, ServletConfigImpl> configMap = _servletManager.getServlets();

		Map<String, ServletRegistration> result = new HashMap<String, ServletRegistration>(configMap);

		return Collections.unmodifiableMap(result);
	}

	@Override
	public <T extends Filter> T createFilter(Class<T> filterClass) throws ServletException
	{
		return _beanFactory.createBean(filterClass);
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, String className)
	{
		return addFilter(filterName, className, null, null);
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass)
	{
		return addFilter(filterName, filterClass.getName(), filterClass, null);
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName, Filter filter)
	{
		Class cl = filter.getClass();

		return addFilter(filterName, cl.getName(), cl, filter);
	}

	/**
	 * 
	 * 增加Filter
	 * 
	 * @param filterName
	 * @param className
	 * @param filterClass
	 * @param filter
	 * @return
	 */
	private FilterRegistration.Dynamic addFilter(String filterName, String className, Class<? extends Filter> filterClass, Filter filter)
	{

		try
		{
			FilterConfigImpl config = createNewFilterConfig();

			config.setFilterName(filterName);
			config.setFilterClass(className);

			if (filterClass != null)
				config.setFilterClass(filterClass);

			if (filter != null)
				config.setFilter(filter);

			addFilter(config);

			return config;
		}
		catch (ClassNotFoundException e)
		{
			e.printStackTrace();
			// spec declares no throws so far.
			throw new RuntimeException(e.getMessage(), e);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Returns the character encoding.
	 */
	public String getCharacterEncoding()
	{
		return _characterEncoding;
	}

	/**
	 * 创建一个新的ServletConfigImpl
	 * 
	 * @return
	 */
	public ServletConfigImpl createNewServletConfig()
	{
		ServletConfigImpl config = new ServletConfigImpl(this, this, _servletManager, _servletMapper);

		return config;
	}

	/**
	 * 创建一个新的ServletMapping
	 * 
	 * @return
	 */
	public ServletMapping createNewServletMapping(ServletConfigImpl config)
	{
		checkServlerConfig(config);

		ServletMapping servletMapping = new ServletMapping(config);

		return servletMapping;
	}

	/**
	 * 创建一个新的ServletConfigImpl
	 * 
	 * @return
	 */
	public FilterConfigImpl createNewFilterConfig()
	{
		FilterConfigImpl config = new FilterConfigImpl(this, this, _filterManager);

		return config;
	}

	/**
	 * 创建一个新的ServletMapping
	 * 
	 * @return
	 */
	public FilterMapping createNewFilterMapping(FilterConfigImpl config)
	{
		checkFilterConfig(config);

		FilterMapping servletMapping = new FilterMapping(config);

		return servletMapping;
	}

	/**
	 * Adds a servlet-mapping configuration.
	 */
	public void addServletMapping(ServletMapping servletMapping) throws ServletException
	{
		checkServletMapping(servletMapping);

		_servletMapper.addServletMapping(servletMapping);
	}

	/**
	 * Adds a filter configuration.
	 * 
	 * @throws ServletException
	 */
	public void addFilter(FilterConfigImpl config) throws ServletException
	{
		checkFilterConfig(config);

		_filterManager.addFilter(config);
	}

	/**
	 * 检查FilterConfigImpl里的webApp,servletContext,FilterManager是否符合本WebApp里的
	 * 
	 * @param config
	 */
	private void checkFilterConfig(FilterConfigImpl config)
	{
		Assert.notNull(config);

		Assert.isTrue(config.getWebApp() == this);
		Assert.isTrue(config.getServletContext() == this);
		Assert.isTrue(config.getFilterManager() == this.getFilterManager());
	}

	/**
	 * 检查FilterMapping里的各属性是否符合本WebApp的属性
	 * 
	 * @param filterMapping
	 */
	private void checkFilterMapping(FilterMapping filterMapping)
	{
		Assert.notNull(filterMapping);

		checkFilterConfig(filterMapping.getFilterConfig());
	}

	/**
	 * Adds a filter-mapping configuration.
	 * 
	 * 添加一个FilterMapping,相当于添加一个web.xml的<filter-mapping>标签
	 */
	public void addFilterMapping(FilterMapping filterMapping) throws ServletException
	{
		checkFilterMapping(filterMapping);

		_filterManager.addFilterMapping(filterMapping);

		// 按DispatcherType分类存放
		if (filterMapping.isRequest())
			_dispatchFilterMapper.addFilterMapping(filterMapping);

		if (filterMapping.isInclude())
			_includeFilterMapper.addFilterMapping(filterMapping);

		if (filterMapping.isForward())
			_forwardFilterMapper.addFilterMapping(filterMapping);

		if (filterMapping.isError())
			_errorFilterMapper.addFilterMapping(filterMapping);
	}

	@Override
	public FilterRegistration getFilterRegistration(String filterName)
	{
		return _filterManager.getFilter(filterName);
	}

	/**
	 * Returns filter registrations
	 * 
	 * @return
	 */
	@Override
	public Map<String, ? extends FilterRegistration> getFilterRegistrations()
	{
		Map<String, FilterConfigImpl> configMap = _filterManager.getFilters();

		Map<String, FilterRegistration> result = new HashMap<String, FilterRegistration>(configMap);

		return Collections.unmodifiableMap(result);
	}

	/**
	 * Configures the session manager.
	 */
	public SessionManager createSessionConfig() throws Exception
	{

		SessionManager manager = getSessionManager();

		return manager;
	}

	/**
	 * Adds the session manager.
	 */

	public void addSessionConfig(SessionManager manager) throws ConfigException
	{

	}

	/**
	 * Sets the cookie-http-only
	 */

	public void setCookieHttpOnly(boolean isHttpOnly)
	{
		_cookieHttpOnly = isHttpOnly;
	}

	/**
	 * Sets the cookie-http-only
	 */
	public boolean getCookieHttpOnly()
	{
		return _cookieHttpOnly;
	}

	/**
	 * Adds a mime-mapping
	 */

	public void addMimeMapping(String _extension, String _mimeType)
	{
		_mimeMapping.put(_extension, _mimeType);
	}

	/**
	 * Adds a locale-mapping
	 */
	public void putLocaleEncoding(String locale, String encoding)
	{
		_localeMapping.put(locale.toLowerCase(Locale.ENGLISH), encoding);
	}

	/**
	 * Returns the locale encoding.
	 */
	public String getLocaleEncoding(Locale locale)
	{
		String encoding;

		String key = locale.toString();
		encoding = _localeMapping.get(key.toLowerCase(Locale.ENGLISH));

		if (encoding != null)
			return encoding;

		if (locale.getVariant() != null)
		{
			key = locale.getLanguage() + '_' + locale.getCountry();
			encoding = _localeMapping.get(key.toLowerCase(Locale.ENGLISH));
			if (encoding != null)
				return encoding;
		}

		if (locale.getCountry() != null)
		{
			key = locale.getLanguage();
			encoding = _localeMapping.get(key.toLowerCase(Locale.ENGLISH));
			if (encoding != null)
				return encoding;
		}

		return Encoding.getMimeName(locale);
	}

	/**
	 * Sets the secure requirement.
	 */

	public void setSecure(boolean isSecure)
	{
		_isSecure = isSecure;

	}

	public boolean isSecure()
	{
		return _isSecure;
	}

	public Boolean isRequestSecure()
	{
		return isSecure();
	}

	@Override
	public <T extends EventListener> T createListener(Class<T> listenerClass) throws ServletException
	{
		return _beanFactory.createBean(listenerClass);

	}

	@Override
	public void addListener(String className)
	{
		try
		{
			Class listenerClass = Class.forName(className, false, getClassLoader());

			addListener(listenerClass);
		}
		catch (ClassNotFoundException e)
		{
			throw ConfigException.create(e);
		}
	}

	@Override
	public void addListener(Class<? extends EventListener> listenerClass)
	{
		addListener(_beanFactory.createBean(listenerClass));
	}

	@Override
	public <T extends EventListener> void addListener(T listener)
	{
		addListenerObject(listener, false);
	}

	/**
	 * Returns true if a listener with the given type exists.
	 */
	public boolean hasListener(Class<?> listenerClass)
	{
		for (int i = 0; i < _listeners.size(); i++)
		{
			ListenerConfig listener = _listeners.get(i);

			if (listenerClass.equals(listener.getListenerClass()))
				return true;
		}

		return false;
	}

	/**
	 * Adds the listener object.
	 */
	private void addListenerObject(Object listenerObj, boolean start)
	{

		// ServletContextListener
		if (listenerObj instanceof ServletContextListener)
		{
			ServletContextListener scListener = (ServletContextListener) listenerObj;
			_contextListeners.add(scListener);

			// 发布 ServletContextEvent#contextInitialized 事件
			if (start)
			{
				ServletContextEvent event = new ServletContextEvent(this);

				try
				{
					scListener.contextInitialized(event);
				}
				catch (Exception e)
				{
					e.printStackTrace();
					log.debug(e.toString(), e);
				}
			}
		}

		// ServletContextAttributeListener
		if (listenerObj instanceof ServletContextAttributeListener)
			addAttributeListener((ServletContextAttributeListener) listenerObj);

		// ServletRequestListener
		if (listenerObj instanceof ServletRequestListener)
		{
			_requestListeners.add((ServletRequestListener) listenerObj);
		}

		// ServletRequestAttributeListener
		if (listenerObj instanceof ServletRequestAttributeListener)
		{
			_requestAttributeListeners.add((ServletRequestAttributeListener) listenerObj);
		}

		// HttpSessionListener
		if (listenerObj instanceof HttpSessionListener)
			getSessionManager().addListener((HttpSessionListener) listenerObj);

		// HttpSessionListener
		if (listenerObj instanceof HttpSessionAttributeListener)
			getSessionManager().addAttributeListener((HttpSessionAttributeListener) listenerObj);

		// HttpSessionActivationListener
		if (listenerObj instanceof HttpSessionActivationListener)
			getSessionManager().addActivationListener((HttpSessionActivationListener) listenerObj);
	}

	/**
	 * Returns the request listeners.
	 */
	public List<ServletRequestListener> getRequestListeners()
	{
		return _requestListeners;
	}

	/**
	 * Returns the request attribute listeners.
	 */
	public List<ServletRequestAttributeListener> getRequestAttributeListeners()
	{
		return _requestAttributeListeners;
	}

	// special config

	/**
	 * Sets the temporary directory
	 */
	public void setTempDir(String path)
	{
		_tempDir = path;
	}

	/**
	 * Returns an extension.
	 */
	public Object getExtension(String key)
	{
		return _extensions.get(key);
	}

	/**
	 * Returns true if should ignore client disconnect.
	 */
	public boolean isIgnoreClientDisconnect()
	{
		return true;
	}

	/**
	 * Initializes.
	 */
	@PostConstruct
	public void init()
	{
		log.debug("Initializing.");
		// setAttribute("javax.servlet.context.tempdir", new File(_tempDir));
		setAttribute(InstanceManager.class.getName(), _beanFactory);

		_characterEncoding = CharacterEncoding.getLocalEncoding();

	}

	public void parseWebXml() throws ServletException
	{
		log.debug("parseing webXml.");
		WebXmlLoader loader = new WebXmlLoader(this);

		try
		{
			// 加载 <context-param>参数
			loader.loadInitParam();

			// 加载<listener>标签
			loader.loadListener();

			// 解释web.xml所有的<filter>元素
			LinkedHashMap<String, FilterConfigImpl> filterConfigMap = loader.praseFilter();

			for (Entry<String, FilterConfigImpl> e : filterConfigMap.entrySet())
			{
				addFilter(e.getValue());
			}

			// 解释web.xml所有的<filter-mapping>元素
			loader.parseFilterMapping(filterConfigMap);

			// 解释web.xml中的<servlet>标签
			LinkedHashMap<String, ServletConfigImpl> servletConfigMap = loader.praseServletConfig();

			// 解释web.xml中的<servlet-mapping>标签
			loader.parseServletMapping(servletConfigMap);
		}
		catch (ClassNotFoundException e)
		{
			e.printStackTrace();
		}

	}

	/**
	 * Start App
	 */
	public void start()
	{
		log.debug(" Starting.");

		try
		{
			// 加载web.xml
			parseWebXml();

			configJsp();

			// 初始化Listener
			for (ListenerConfig listener : _listeners)
			{
				try
				{
					addListenerObject(listener.createListenerObject(), false);
				}
				catch (Exception e)
				{
					throw ConfigException.create(e);
				}
			}

			//
			publishContextInitializedEvent();

			// Servlet 3.0

			try
			{
				_filterManager.init();
				_servletManager.init();

			}
			catch (Exception e)
			{
				throw e;
			}

			clearCache();

		}
		catch (Exception e)
		{
			throw ConfigException.create(e);
		}
		finally
		{

		}
	}

	/**
	 * 配置JSP相关的 JspServletComposite的ServletConfig配置信息
	 * 
	 * @throws ServletException
	 */
	private void configJsp() throws ServletException
	{
		ServletConfigImpl config = createNewServletConfig();

		config.setServletName(JspServletComposite.class.getCanonicalName());
		config.setServletClass(JspServletComposite.class);

		// 缺省情况下,关闭development模式,提高性能
		config.setInitParameter("development", "false");

		_servletManager.addServlet(config);
	}

	/* ------------------------------------------------------------ */
	/**
	 * 发布publish ContextInitialized Event 事件
	 */
	protected void publishContextInitializedEvent()
	{
		log.debug("publish ContextInitialized Event");

		// 发布ServletContextListener#contextInitialized事件
		ServletContextEvent event = new ServletContextEvent(this);

		for (int i = 0; i < _contextListeners.size(); i++)
		{
			ServletContextListener listener = _contextListeners.get(i);

			try
			{
				listener.contextInitialized(event);
			}
			catch (Exception e)
			{
				log.warn(e.toString(), e);
			}
		}
	}

	/**
	 * Returns the servlet context for the URI.
	 */
	@Override
	public ServletContext getContext(String uri)
	{
		if (uri == null)
			throw new IllegalArgumentException(L.l("getContext URI must not be null."));

		else if (uri.startsWith("/"))
		{
		}
		else if (uri.equals(""))
			uri = "/";
		else
			throw new IllegalArgumentException(L.l("getContext URI '{0}' must be absolute.", uri));

		return this;
	}

	public ServletMapper getServletMapper()
	{
		return _servletMapper;
	}

	/**
	 * Clears all caches, including the invocation cache, the filter cache, and the proxy cache.
	 */
	public void clearCache()
	{
		// server/1kg1
		synchronized (_dispatchFilterChainCache)
		{
			_dispatchFilterChainCache.clear();
			_requestDispatcherCache = null;
		}

	}

	/**
	 * Fills the invocation for a rewrite-dispatch/dispatch request.
	 */
	public void buildDispatchInvocation(Invocation invocation) throws ServletException
	{
		// try to get from Cache
		FilterChainEntry entry = _dispatchFilterChainCache.get(invocation.getContextURI());

		if (entry != null)
		{

			invocation.setFilterChain(entry.getFilterChain());

			invocation.setServletName(entry.getServletName());
			invocation.setServletPath(entry.getServletPath());
			invocation.setPathInfo(entry.getPathInfo());

			if (!entry.isAsyncSupported())
				invocation.clearAsyncSupported();

			return;
		}

		// build it
		buildInvocation(invocation, _dispatchFilterMapper);

		// Build FilterChain for the notification of ServletRequestListener(s)
		if (_requestListeners != null && _requestListeners.size() > 0)
		{
			FilterChain filterChain = new ServletRequestListenerFilterChain(invocation.getFilterChain(), this, _requestListeners);
			invocation.setFilterChain(filterChain);
		}

		// put to cache
		if (invocation.getFilterChain() != null)
		{
			FilterChainEntry filterChainEntry = new FilterChainEntry(invocation.getFilterChain(), invocation);
			_dispatchFilterChainCache.put(invocation.getContextURI(), filterChainEntry);
		}
	}

	/**
	 * Fills the invocation for a forward request.
	 */
	public void buildForwardInvocation(Invocation invocation) throws ServletException
	{
		// try to get from Cache
		FilterChainEntry entry = _forwardFilterChainCache.get(invocation.getContextURI());

		if (entry != null)
		{

			invocation.setFilterChain(entry.getFilterChain());

			invocation.setServletName(entry.getServletName());
			invocation.setServletPath(entry.getServletPath());
			invocation.setPathInfo(entry.getPathInfo());

			if (!entry.isAsyncSupported())
				invocation.clearAsyncSupported();

			return;
		}

		// build it
		buildInvocation(invocation, _forwardFilterMapper);

		// put to cache
		if (invocation.getFilterChain() != null)
		{
			FilterChainEntry filterChainEntry = new FilterChainEntry(invocation.getFilterChain(), invocation);
			_forwardFilterChainCache.put(invocation.getContextURI(), filterChainEntry);
		}
	}

	/**
	 * Fills the invocation for an include request.
	 */
	public void buildIncludeInvocation(Invocation invocation) throws ServletException
	{
		// try to get from Cache
		FilterChainEntry entry = _includeFilterChainCache.get(invocation.getContextURI());

		if (entry != null)
		{

			invocation.setFilterChain(entry.getFilterChain());

			invocation.setServletName(entry.getServletName());
			invocation.setServletPath(entry.getServletPath());
			invocation.setPathInfo(entry.getPathInfo());

			if (!entry.isAsyncSupported())
				invocation.clearAsyncSupported();

			return;
		}

		// build it
		buildInvocation(invocation, _includeFilterMapper);

		// put to cache
		if (invocation.getFilterChain() != null)
		{
			FilterChainEntry filterChainEntry = new FilterChainEntry(invocation.getFilterChain(), invocation);
			_includeFilterChainCache.put(invocation.getContextURI(), filterChainEntry);
		}
	}

	/**
	 * Fills the invocation for an error request.
	 */
	public void buildErrorInvocation(Invocation invocation) throws ServletException
	{
		// try to get from Cache
		FilterChainEntry entry = _errorFilterChainCache.get(invocation.getContextURI());

		if (entry != null)
		{

			invocation.setFilterChain(entry.getFilterChain());

			invocation.setServletName(entry.getServletName());
			invocation.setServletPath(entry.getServletPath());
			invocation.setPathInfo(entry.getPathInfo());

			if (!entry.isAsyncSupported())
				invocation.clearAsyncSupported();

			return;
		}

		// build it
		buildInvocation(invocation, _errorFilterMapper);

		// put to cache
		if (invocation.getFilterChain() != null)
		{
			FilterChainEntry filterChainEntry = new FilterChainEntry(invocation.getFilterChain(), invocation);
			_errorFilterChainCache.put(invocation.getContextURI(), filterChainEntry);
		}
	}

	/**
	 * Fills FilterChain to invocation for subrequests.
	 */
	void buildInvocation(Invocation invocation, FilterMapper filterMapper) throws ServletException
	{
		if (log.isDebugEnabled())
			log.debug("buildInvocation:" + invocation.getRawURI());

		try
		{
			FilterChain chain;

			if (!isEnabled())
			{
				Exception exn = new UnavailableException(L.l("'{0}' is not currently available.", getContextPath()));
				chain = new ExceptionFilterChain(exn);
			}
			else
			{
				chain = _servletMapper.createServletChain(invocation); // 测试了Jetty和Tomcat,就是无法找到合适的Sevlet来匹配,也要调用匹配的Filter
				chain = filterMapper.buildDispatchChain(invocation, chain);

			}

			invocation.setFilterChain(chain);
		}
		catch (Exception e)
		{
			log.debug(e.toString(), e);

			FilterChain chain = new ExceptionFilterChain(e);
			invocation.setFilterChain(chain);
		}
	}

	/**
	 * 创建用于触发ServletRequestListener相关事件的FilterChain
	 * 
	 * @param chain
	 * @return
	 */
	FilterChain createServletRequestListenerFilterChain(FilterChain chain)
	{
		if (getRequestListeners() != null && getRequestListeners().size() > 0)
		{
			chain = new ServletRequestListenerFilterChain(chain, this, getRequestListeners());
		}

		return chain;
	}

	/**
	 * Returns a dispatcher for the named servlet.
	 * 
	 */
	@Override
	public RequestDispatcherImpl getRequestDispatcher(String rawContextURI)
	{
		if (rawContextURI == null)
			throw new IllegalArgumentException(L.l("request dispatcher url can't be null."));
		else if (!rawContextURI.startsWith("/"))
			throw new IllegalArgumentException(L.l("request dispatcher url '{0}' must be absolute", rawContextURI));

		// 尝试从缓存中取出RequestDispatcher
		RequestDispatcherImpl disp = getRequestDispatcherCache().get(rawContextURI);

		if (disp != null)
			return disp;

		try
		{
			// 将Invocation的创建延迟到RequestDispatcher的具体的dispatch或forward方法调用时再进行(很情况下不需要所有DispatcherType都创建)
			disp = new RequestDispatcherImpl(this, rawContextURI);

			// 缓存RequestDispatcher
			getRequestDispatcherCache().put(rawContextURI, disp);

			return disp;
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			log.debug(e.toString(), e);

			return null;
		}
	}

	private LruCache<String, RequestDispatcherImpl> getRequestDispatcherCache()
	{
		LruCache<String, RequestDispatcherImpl> cache = _requestDispatcherCache;

		if (cache != null)
			return cache;

		synchronized (this)
		{
			cache = new LruCache<String, RequestDispatcherImpl>(1024);
			_requestDispatcherCache = cache;
			return cache;
		}
	}

	/**
	 * Returns a dispatcher for the named servlet. 返回一个转发到指定名称的Servlet的dispatcher
	 */
	@Override
	public RequestDispatcher getNamedDispatcher(String servletName)
	{
		try
		{
			Invocation invocation = null;

			FilterChain chain = _servletManager.createServletChain(servletName, invocation);

			FilterChain includeChain = _includeFilterMapper.buildFilterChain(chain, servletName);

			FilterChain forwardChain = _forwardFilterMapper.buildFilterChain(chain, servletName);

			return new NamedDispatcherImpl(includeChain, forwardChain, null, this);

		}
		catch (Exception e)
		{
			log.debug(e.toString(), e);

			return null;
		}
	}

	/**
	 * Maps from a URI to a real path.
	 */
	/*
	 * @Override public String getRealPath(String uri) { // server/10m7 if (uri == null) return null;
	 * 
	 * String realPath = _realPathCache.get(uri);
	 * 
	 * if (realPath != null) return realPath;
	 * 
	 * WebApp webApp = this; String tail = uri;
	 * 
	 * String fullURI = getContextPath() + "/" + uri;
	 * 
	 * try { fullURI = getURIDecoder().normalizeUri(fullURI); } catch (Exception e) { log.warn(e.toString(), e); }
	 * 
	 * webApp = (WebApp) getContext(fullURI);
	 * 
	 * if (webApp == null) webApp = this;
	 * 
	 * String cp = webApp.getContextPath(); tail = fullURI.substring(cp.length());
	 * 
	 * realPath = tail;
	 * 
	 * if (log.isDebugEnabled()) log.debug("real-path " + uri + " -> " + realPath);
	 * 
	 * _realPathCache.put(uri, realPath);
	 * 
	 * return realPath; }
	 */

	@Override
	public String getRealPath(String uri)
	{
		// server/10m7
		if (uri == null)
			return null;

		String realPath = _realPathCache.get(uri);

		if (realPath != null)
			return realPath;

		realPath = super.getRealPath(uri);

		if (log.isDebugEnabled())
			log.debug("real-path " + uri + " -> " + realPath);

		if (realPath != null)
			_realPathCache.put(uri, realPath);

		return realPath;
	}

	/**
	 * Returns the mime type for a uri
	 */
	@Override
	public String getMimeType(String uri)
	{
		if (uri == null)
			return null;

		String fullURI = getContextPath() + "/" + uri;

		try
		{
			fullURI = getURIDecoder().normalizeUri(fullURI);
		}
		catch (Exception e)
		{
			log.warn(e.toString(), e);
		}

		WebApp webApp = (WebApp) getContext(fullURI);

		if (webApp == null)
			return null;

		int p = uri.lastIndexOf('.');

		if (p < 0)
			return null;
		else
			return webApp.getMimeTypeImpl(uri.substring(p));
	}

	/**
	 * Maps from a URI to a real path.
	 */
	public String getMimeTypeImpl(String ext)
	{
		return _mimeMapping.get(ext);
	}

	@Override
	public SessionCookieConfig getSessionCookieConfig()
	{
		return getSessionManager();
	}

	/**
	 * Gets the session manager.
	 */
	public SessionManager getSessionManager()
	{
		return _sessionManager;
	}

	/**
	 * Gets the error page manager.
	 */
	public ErrorPageManager getErrorPageManager()
	{
		if (_errorPageManager == null)
		{
			_errorPageManager = new ErrorPageManager(this);
		}

		return _errorPageManager;
	}

	/**
	 * Returns the active session count.
	 */
	public int getActiveSessionCount()
	{
		SessionManager manager = getSessionManager();

		if (manager != null)
			return manager.getActiveSessionCount();
		else
			return 0;
	}

	/**
	 * Stops the webApp.
	 */
	public void stop()
	{

		long beginStop = System.currentTimeMillis();

		clearCache();

		ServletContextEvent event = new ServletContextEvent(this);

		SessionManager sessionManager = _sessionManager;
		_sessionManager = null;

		if (sessionManager != null)
		{
			sessionManager.close();
		}

		if (_servletManager != null)
			_servletManager.destroy();
		if (_filterManager != null)
			_filterManager.destroy();

		// server/10g8 -- webApp listeners after session

		// 发布 ServletContextListener#contextDestroyed事件
		publishContextDestroyedEvent(event);

		// server/10g8 -- webApp listeners after session
		for (int i = _listeners.size() - 1; i >= 0; i--)
		{
			ListenerConfig listener = _listeners.get(i);

			try
			{
				listener.destroy();
			}
			catch (Exception e)
			{
				log.warn(e.toString(), e);
			}
		}
	}

	/**
	 * Closes the webApp.
	 */
	public void destroy()
	{
		try
		{
			stop();
		}
		catch (Throwable e)
		{
			log.warn(e.toString(), e);
		}

	}

	/**
	 * 发布publish ContextDestroyed Event 事件
	 */
	protected void publishContextDestroyedEvent(ServletContextEvent event)
	{
		if (_contextListeners != null)
		{
			for (int i = _contextListeners.size() - 1; i >= 0; i--)
			{
				ServletContextListener listener = _contextListeners.get(i);

				try
				{
					listener.contextDestroyed(event);
				}
				catch (Exception e)
				{
					log.warn(e.toString(), e);
				}
			}
		}
	}

	// /static-----------------------------------------------------------------------------

	public FilterManager getFilterManager()
	{
		return _filterManager;
	}

	static class FilterChainEntry
	{
		FilterChain _filterChain;
		String _pathInfo;
		String _servletPath;
		String _servletName;
		boolean _isAsyncSupported;

		FilterChainEntry(FilterChain filterChain, Invocation invocation)
		{
			_filterChain = filterChain;
			_pathInfo = invocation.getPathInfo();
			_servletPath = invocation.getServletPath();
			_servletName = invocation.getServletName();
			_isAsyncSupported = invocation.isAsyncSupported();
		}

		FilterChain getFilterChain()
		{
			return _filterChain;
		}

		String getPathInfo()
		{
			return _pathInfo;
		}

		String getServletPath()
		{
			return _servletPath;
		}

		String getServletName()
		{
			return _servletName;
		}

		boolean isAsyncSupported()
		{
			return _isAsyncSupported;
		}
	}

	// --util-----------------------------------------------------------------

	/**
	 * Error logging
	 * 
	 * @param message
	 *            message to log
	 * @param e
	 *            stack trace of the error
	 */
	public void log(String message, Throwable e)
	{
		if (e != null)
			log.warn(this + " " + message, e);
		else
			log.info(this + " " + message);
	}

}
