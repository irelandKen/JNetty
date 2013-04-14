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
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterRegistration;
import javax.servlet.MultipartConfigElement;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.ServletSecurityElement;
import javax.servlet.SessionCookieConfig;
import javax.servlet.UnavailableException;
import javax.servlet.annotation.HandlesTypes;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.ireland.jnetty.beans.BeanFactory;
import org.ireland.jnetty.config.ConfigException;
import org.ireland.jnetty.config.ListenerConfig;
import org.ireland.jnetty.config.WebXmlLoader;
import org.ireland.jnetty.dispatch.Invocation;
import org.ireland.jnetty.dispatch.InvocationBuilder;
import org.ireland.jnetty.dispatch.SubInvocation;
import org.ireland.jnetty.dispatch.filter.FilterConfigImpl;
import org.ireland.jnetty.dispatch.filter.FilterManager;
import org.ireland.jnetty.dispatch.filter.FilterMapper;
import org.ireland.jnetty.dispatch.filter.FilterMapping;
import org.ireland.jnetty.dispatch.filterchain.ErrorFilterChain;
import org.ireland.jnetty.dispatch.filterchain.ExceptionFilterChain;
import org.ireland.jnetty.dispatch.filterchain.FilterChainBuilder;
import org.ireland.jnetty.dispatch.filterchain.RedirectFilterChain;
import org.ireland.jnetty.dispatch.servlet.ServletConfigImpl;
import org.ireland.jnetty.dispatch.servlet.ServletManager;
import org.ireland.jnetty.dispatch.servlet.ServletMapper;
import org.ireland.jnetty.dispatch.servlet.ServletMapping;
import org.ireland.jnetty.util.http.Encoding;
import org.ireland.jnetty.util.http.URIDecoder;
import org.ireland.jnetty.util.http.UrlMap;

import com.caucho.i18n.CharacterEncoding;



import com.caucho.server.session.SessionManager;
import com.caucho.server.webapp.CacheMapping;
import com.caucho.server.webapp.MultipartForm;

import com.caucho.util.CurrentTime;
import com.caucho.util.L10N;
import com.caucho.util.LruCache;

/**
 * Resin's webApp implementation.
 */
public class WebApp extends ServletContextImpl implements InvocationBuilder
{
	private static final L10N L = new L10N(WebApp.class);
	private static final Logger log = Logger.getLogger(WebApp.class.getName());

	// The context path is the URL prefix for the web-app
	private String _contextPath;

	// The environment class loader
	private ClassLoader _classLoader;

	private String _host;

	private String _hostName = "";

	// The canonical URL
	private String _url;

	private String _serverName = "";
	private int _serverPort = 0;

	// The webbeans container
	private BeanFactory beanFactory;

	private URIDecoder _uriDecoder;

	private String _moduleName = "default";

	// The context path
	private String _baseContextPath = "";

	private String _servletVersion;

	// -----servlet--------------------------

	// The servlet manager
	private ServletManager _servletManager;

	// The servlet mapper
	private ServletMapper _servletMapper;
	// -----servlet--------------------------

	// True the mapper should be strict
	private boolean _isStrictMapping;

	// -----filter--------------------------
	// The filter manager
	private FilterManager _filterManager;

	// The filter mapper
	private FilterMapper _requestFilterMapper;

	// The dispatch filter mapper
	private FilterMapper _dispatchFilterMapper;

	// The include filter mapper
	private FilterMapper _includeFilterMapper;

	// The forward filter mapper
	private FilterMapper _forwardFilterMapper;

	// The error filter mapper
	private FilterMapper _errorFilterMapper;
	// -----filter--------------------------

	private FilterChainBuilder _securityBuilder;

	
	// The session manager
	private SessionManager _sessionManager;

	private String _characterEncoding;

	private int _formParameterMax = 10000;

	
	// The cache

	// 用LRU算法Cache最近最常使用的url与FilterChain之间的映射关系()
	private LruCache<String, FilterChainEntry> _filterChainCache = new LruCache<String, FilterChainEntry>(256);

	private UrlMap<CacheMapping> _cacheMappingMap = new UrlMap<CacheMapping>();

	private LruCache<String, RequestDispatcherImpl> _dispatcherCache;

	// True for SSL secure.
	private boolean _isSecure;

	// Error pages.
	private ErrorPageManager _errorPageManager;

	private String errorPage;

	private LruCache<String, String> _realPathCache = new LruCache<String, String>(
			1024);

	// real-path mapping
	// private RewriteRealPath _rewriteRealPath;

	// mime mapping
	private HashMap<String, String> _mimeMapping = new HashMap<String, String>();

	// locale mapping
	private HashMap<String, String> _localeMapping = new HashMap<String, String>();

	// listeners------------
	// List of all the listeners.
	private ArrayList<ListenerConfig> _listeners = new ArrayList<ListenerConfig>();

	// List of the ServletContextListeners from the configuration file
	private ArrayList<ServletContextListener> _webAppListeners = new ArrayList<ServletContextListener>();

	// List of the ServletContextAttributeListeners from the configuration file
	private ArrayList<ServletContextAttributeListener> _attributeListeners = new ArrayList<ServletContextAttributeListener>();

	// List of the ServletRequestListeners from the configuration file
	private ArrayList<ServletRequestListener> _requestListeners = new ArrayList<ServletRequestListener>();

	private ServletRequestListener[] _requestListenerArray = new ServletRequestListener[0];

	// List of the ServletRequestAttributeListeners from the configuration file
	private ArrayList<ServletRequestAttributeListener> _requestAttributeListeners = new ArrayList<ServletRequestAttributeListener>();

	private ServletRequestAttributeListener[] _requestAttributeListenerArray = new ServletRequestAttributeListener[0];

	// listeners-----------------------------------------------

	private ArrayList<String> _welcomeFileList = new ArrayList<String>();

	private String rootDirectory;

	private String _tempDir;

	private boolean _cookieHttpOnly;

	private HashMap<String, Object> _extensions = new HashMap<String, Object>();

	private MultipartForm _multipartForm;

	private boolean _isEnabled = true;

	private Pattern _cookieDomainPattern = null;

	/**
	 * Creates the webApp with its environment loader.
	 */
	public WebApp(String rootDirectory, String host, String contextPath)
	{
		_classLoader = this.getClass().getClassLoader();

		this.rootDirectory = rootDirectory;

		_host = host;

		if (_host == null)
			throw new IllegalStateException(L.l("{0} requires an active {1}",getClass().getSimpleName()));

		_uriDecoder = new URIDecoder();

		_moduleName = _baseContextPath;

		if ("".equals(_moduleName))
			_moduleName = "ROOT";
		else if (_moduleName.startsWith("/"))
			_moduleName = _moduleName.substring(1);

		initConstructor();
	}

	private void initConstructor()
	{
	
		// Path rootDirectory = getRootDirectory();

		_servletManager = new ServletManager();
		_servletMapper = new ServletMapper(this);
		_servletMapper.setServletManager(_servletManager);

		_filterManager = new FilterManager();
		_requestFilterMapper = new FilterMapper();
		_requestFilterMapper.setServletContext(this);
		_requestFilterMapper.setFilterManager(_filterManager);

		_includeFilterMapper = new FilterMapper();
		_includeFilterMapper.setServletContext(this);
		_includeFilterMapper.setFilterManager(_filterManager);

		_forwardFilterMapper = new FilterMapper();
		_forwardFilterMapper.setServletContext(this);
		_forwardFilterMapper.setFilterManager(_filterManager);

		_dispatchFilterMapper = new FilterMapper();
		_dispatchFilterMapper.setServletContext(this);
		_dispatchFilterMapper.setFilterManager(_filterManager);

		_errorFilterMapper = new FilterMapper();
		_errorFilterMapper.setServletContext(this);
		_errorFilterMapper.setFilterManager(_filterManager);

		// _errorPageManager = new ErrorPageManager(_server, this);

		beanFactory = new BeanFactory();

	}

	/**
	 * Returns the webApp's canonical context path, e.g. /foo-1.0
	 */
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

	public String getModuleName()
	{
		return _moduleName;
	}

	public void setModuleName(String moduleName)
	{
		_moduleName = moduleName;
	}

	public URIDecoder getURIDecoder()
	{

		return _uriDecoder;
	}

	/**
	 * Gets the environment class loader.
	 */
	public ClassLoader getClassLoader()
	{
		if(_classLoader == null)
			_classLoader = this.getClass().getClassLoader();
		
		return _classLoader;
	}

	/**
	 * Sets the root directory (app-dir).
	 */

	public void setRootDirectory(Path appDir)
	{
	}

	/**
	 * Sets the webApp directory.
	 */
	public void setAppDir(Path appDir)
	{
		setRootDirectory(appDir);
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

	/**
	 * Sets the schema location.
	 */
	public void setSchemaLocation(String location)
	{
	}

	public void setEnabled(boolean isEnabled)
	{
		_isEnabled = isEnabled;
	}

	public boolean isEnabled()
	{
		return _isEnabled;
	}

	public void setDistributable(boolean isDistributable)
	{
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
		config.setServletContext(this);

		_servletManager.addServlet(config);
	}

	@Override
	public <T extends Servlet> T createServlet(Class<T> servletClass)
			throws ServletException
	{
		return beanFactory.createBean(servletClass);
	}



	@Override
	public ServletRegistration.Dynamic addServlet(String servletName,
			String className)
	{
		Class<? extends Servlet> servletClass;

		try
		{
			servletClass = (Class) Class.forName(className, false,
					getClassLoader());

		}
		catch (ClassNotFoundException e)
		{
			throw new IllegalArgumentException(L.l(
					"'{0}' is an unknown class in {1}", className, this), e);
		}

		return addServlet(servletName, className, servletClass, null);
	}

	@Override
	public ServletRegistration.Dynamic addServlet(String servletName,
			Class<? extends Servlet> servletClass)
	{
		return addServlet(servletName, servletClass.getName(), servletClass,
				null);
	}

	@Override
	public ServletRegistration.Dynamic addServlet(String servletName,
			Servlet servlet)
	{
		Class cl = servlet.getClass();

		return addServlet(servletName, cl.getName(), cl, servlet);
	}

	/**
	 * Adds a new or augments existing registration
	 * 
	 * @since 3.0
	 */
	private ServletRegistration.Dynamic addServlet(String servletName,
			String servletClassName, Class<? extends Servlet> servletClass,
			Servlet servlet)
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

			if (log.isLoggable(Level.FINE))
			{
				log.fine(L
						.l("dynamic servlet added [name: '{0}', class: '{1}'] (in {2})",
								servletName, servletClassName, this));
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
		Map<String, ServletConfigImpl> configMap = _servletManager
				.getServlets();

		Map<String, ServletRegistration> result = new HashMap<String, ServletRegistration>(
				configMap);

		return Collections.unmodifiableMap(result);
	}

	@Override
	public <T extends Filter> T createFilter(Class<T> filterClass)
			throws ServletException
	{
		return beanFactory.createBean(filterClass);
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName,String className)
	{
		return addFilter(filterName, className, null, null);
	}

	@Override
	public FilterRegistration.Dynamic addFilter(String filterName,Class<? extends Filter> filterClass)
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
	private FilterRegistration.Dynamic addFilter(String filterName,String className, Class<? extends Filter> filterClass, Filter filter)
	{

		try
		{
			FilterConfigImpl config = new FilterConfigImpl();

			config.setWebApp(this);
			config.setServletContext(this);

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
	 * Set true if strict mapping.
	 */

	public void setStrictMapping(boolean isStrict) throws ServletException
	{
		_isStrictMapping = isStrict;
	}

	/**
	 * Get the strict mapping setting.
	 */
	public boolean getStrictMapping()
	{
		return _isStrictMapping;
	}

	/**
	 * 创建一个新的ServletConfigImpl
	 * @return
	 */
	public ServletConfigImpl createNewServletConfig()
	{
		ServletConfigImpl config = new ServletConfigImpl();
		
		config.setWebApp(this);
		config.setServletContext(this);
		config.setServletManager(_servletManager);
		config.setServletMapper(_servletMapper);

		return config;
	}
	
	/**
	 * 创建一个新的ServletMapping
	 * @return
	 */
	public ServletMapping createNewServletMapping(ServletConfigImpl config)
	{
		
		ServletMapping servletMapping = new ServletMapping(config);
		
		servletMapping.setStrictMapping(getStrictMapping());

		return servletMapping;
	}

	/**
	 * 创建一个新的ServletConfigImpl
	 * @return
	 */
	public FilterConfigImpl createNewFilterConfig()
	{
		FilterConfigImpl config = new FilterConfigImpl();
		
		config.setWebApp(this);
		config.setServletContext(this);
		config.setFilterManager(_filterManager);

		return config;
	}
	
	/**
	 * 创建一个新的ServletMapping
	 * @return
	 */
	public FilterMapping createNewFilterMapping(FilterConfigImpl config)
	{
		
		FilterMapping servletMapping = new FilterMapping(config);
		
		return servletMapping;
	}
	
	/**
	 * Adds a servlet-mapping configuration.
	 */

	public void addServletMapping(ServletMapping servletMapping)
			throws ServletException
	{
		// log.fine("adding servlet mapping: " + servletMapping);
		servletMapping.getServletConfig().setServletContext(this);

		servletMapping.init(_servletMapper);
	}

	/**
	 * Adds a filter configuration.
	 */

	public void addFilter(FilterConfigImpl config)
	{
		config.setServletContext(this);

		config.setFilterManager(_filterManager);
		
		config.setWebApp(this);

		_filterManager.addFilter(config);
	}

	/**
	 * Adds a filter-mapping configuration.
	 * 
	 * 添加一个FilterMapping,相当于添加一个web.xml的<filter-mapping>标签
	 */

	public void addFilterMapping(FilterMapping filterMapping)throws ServletException
	{
		filterMapping.getFilterConfig().setServletContext(this);

		_filterManager.addFilterMapping(filterMapping);

		
		//按DispatcherType分类存放
		if (filterMapping.isRequest())
		{
			_requestFilterMapper.addFilterMapping(filterMapping);
		}

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

		Map<String, FilterRegistration> result = new HashMap<String, FilterRegistration>(
				configMap);

		return Collections.unmodifiableMap(result);
	}

	/**
	 * Adds a welcome file list to the webApp.
	 */

	public void addWelcomeFileList(ArrayList<String> fileList)
	{

		_welcomeFileList = new ArrayList<String>(fileList);

		// _servletMapper.setWelcomeFileList(fileList);
	}

	public ArrayList<String> getWelcomeFileList()
	{
		return _welcomeFileList;
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
	 * Sets the maximum number of form parameters
	 */

	public void setFormParameterMax(int max)
	{
		_formParameterMax = max;
	}

	public int getFormParameterMax()
	{
		return _formParameterMax;
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
	public <T extends EventListener> T createListener(Class<T> listenerClass)
			throws ServletException
	{
	  return beanFactory.createBean(listenerClass);

	}

	@Override
	public void addListener(String className)
	{
		try
		{
			Class listenerClass = Class.forName(className, false,
					getClassLoader());

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
		addListener(beanFactory.createBean(listenerClass));
	}

	@Override
	public <T extends EventListener> void addListener(T listener)
	{
		addListenerObject(listener, true);
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
		if (listenerObj instanceof ServletContextListener)
		{
			ServletContextListener scListener = (ServletContextListener) listenerObj;
			_webAppListeners.add(scListener);

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
					log.log(Level.FINE, e.toString(), e);
				}
			}
		}

		if (listenerObj instanceof ServletContextAttributeListener)
			addAttributeListener((ServletContextAttributeListener) listenerObj);

		if (listenerObj instanceof ServletRequestListener)
		{
			_requestListeners.add((ServletRequestListener) listenerObj);

			_requestListenerArray = new ServletRequestListener[_requestListeners
					.size()];
			_requestListeners.toArray(_requestListenerArray);
		}

		if (listenerObj instanceof ServletRequestAttributeListener)
		{
			_requestAttributeListeners
					.add((ServletRequestAttributeListener) listenerObj);

			_requestAttributeListenerArray = new ServletRequestAttributeListener[_requestAttributeListeners
					.size()];
			_requestAttributeListeners.toArray(_requestAttributeListenerArray);
		}

		if (listenerObj instanceof HttpSessionListener)
			getSessionManager().addListener((HttpSessionListener) listenerObj);

		if (listenerObj instanceof HttpSessionAttributeListener)
			getSessionManager().addAttributeListener(
					(HttpSessionAttributeListener) listenerObj);

		if (listenerObj instanceof HttpSessionActivationListener)
			getSessionManager().addActivationListener(
					(HttpSessionActivationListener) listenerObj);
	}

	/**
	 * Returns the request listeners.
	 */
	public ServletRequestListener[] getRequestListeners()
	{
		return _requestListenerArray;
	}

	/**
	 * Returns the request attribute listeners.
	 */
	public ServletRequestAttributeListener[] getRequestAttributeListeners()
	{
		return _requestAttributeListenerArray;
	}

	// special config

	/**
	 * Multipart form config.
	 */

	public MultipartForm createMultipartForm()
	{
		if (_multipartForm == null)
			_multipartForm = new MultipartForm();

		return _multipartForm;
	}

	/**
	 * Returns true if multipart forms are enabled.
	 */
	public boolean isMultipartFormEnabled()
	{
		return _multipartForm != null && _multipartForm.isEnable();
	}

	/**
	 * Returns the form upload max.
	 */
	public long getFormUploadMax()
	{
		if (_multipartForm != null)
			return _multipartForm.getUploadMax();
		else
			return -1;
	}

	/**
	 * Returns the form upload max.
	 */
	public long getFormParameterLengthMax()
	{
		if (_multipartForm != null)
			return _multipartForm.getParameterLengthMax();
		else
			return -1;
	}

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
	public void init() throws Exception
	{

		//setAttribute("javax.servlet.context.tempdir", new File(_tempDir));

		_characterEncoding = CharacterEncoding.getLocalEncoding();

	}

	private boolean isAttributeListener(Class<?> cl)
	{
		if (ServletContextAttributeListener.class.isAssignableFrom(cl))
			return true;
		else
			return false;
	}

	
	public void parseWebXml()
	{
		WebXmlLoader loader = new WebXmlLoader(this);
		
		try
		{
			loader.praseFilter();
			
			loader.praseServletConfig();
		}
		catch (ClassNotFoundException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	
	/**
	 * Start App
	 */
	public void start()
	{

		try
		{

			//初始化SeccionManager
/*			try
			{
				if (getSessionManager() != null)
					getSessionManager().start();
			}
			catch (Throwable e)
			{
				log.log(Level.WARNING, e.toString(), e);
			}
*/
			ServletContextEvent event = new ServletContextEvent(this);

			//初始化Listener
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

			for (int i = 0; i < _webAppListeners.size(); i++)
			{
				ServletContextListener listener = _webAppListeners.get(i);

				try
				{
					listener.contextInitialized(event);
				}
				catch (Exception e)
				{
					log.log(Level.WARNING, e.toString(), e);
				}
			}

			// Servlet 3.0

			try
			{
				_filterManager.init();
				_servletManager.init();

			}
			catch (Exception e)
			{
				// XXX: CDI TCK
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
	 * Returns the servlet context for the URI.
	 */
	@Override
	public ServletContext getContext(String uri)
	{
		if (uri == null)
			throw new IllegalArgumentException(
					L.l("getContext URI must not be null."));

		else if (uri.startsWith("/"))
		{
		}
		else if (uri.equals(""))
			uri = "/";
		else
			throw new IllegalArgumentException(L.l(
					"getContext URI '{0}' must be absolute.", uri));

		return this;
	}

	/**
	 * Returns the best matching servlet pattern.
	 */
	public String getServletPattern(String uri)
	{
		return _servletMapper.getServletPattern(uri);
	}

	/**
	 * Returns the best matching servlet pattern.
	 */
	public ArrayList<String> getServletMappingPatterns()
	{
		return _servletMapper.getURLPatterns();
	}

	/**
	 * Fills the servlet instance. (Generalize?)
	 */
	@Override
	public Invocation buildInvocation(Invocation invocation)
	{
		try
		{
			FilterChain chain = null;

			if (!isEnabled())
			{
				if (log.isLoggable(Level.FINE))
					log.fine(this + " is disabled '" + invocation.getRawURI()
							+ "'");
				int code = HttpServletResponse.SC_SERVICE_UNAVAILABLE;
				chain = new ErrorFilterChain(code);
				invocation.setFilterChain(chain);

				return invocation;
			}
			else
			{
				FilterChainEntry entry = null;

				// jsp/1910 - can't cache jsp_precompile
				String query = invocation.getQueryString();

				boolean isCache = true;
				if (query != null && query.indexOf("jsp_precompile") >= 0)
					isCache = false;

				if (isCache)
					entry = _filterChainCache.get(invocation.getContextURI());

				if (entry != null)
				{
					chain = entry.getFilterChain();
					invocation.setServletName(entry.getServletName());

					if (!entry.isAsyncSupported())
						invocation.clearAsyncSupported();

					invocation.setMultipartConfig(entry.getMultipartConfig());
				}
				else
				{
					chain = _servletMapper.mapServlet(invocation);

					// server/13s[o-r]
					_requestFilterMapper.buildDispatchChain(invocation, chain);
					chain = invocation.getFilterChain();

					chain = applyWelcomeFile(DispatcherType.REQUEST,
							invocation, chain);

					entry = new FilterChainEntry(chain, invocation);
					chain = entry.getFilterChain();

					if (isCache)
						_filterChainCache
								.put(invocation.getContextURI(), entry);
				}

				chain = buildSecurity(chain, invocation);

				chain = createWebAppFilterChain(chain, invocation, true);

				invocation.setFilterChain(chain);
				invocation.setPathInfo(entry.getPathInfo());
				invocation.setServletPath(entry.getServletPath());
			}

			return invocation;
		}
		catch (Throwable e)
		{
			log.log(Level.WARNING, e.toString(), e);

			FilterChain chain = new ExceptionFilterChain(e);
			invocation.setFilterChain(chain);

			return invocation;
		}
	}

	private FilterChain applyWelcomeFile(DispatcherType type,
			Invocation invocation, FilterChain chain) throws ServletException
	{
		if ("".equals(invocation.getContextURI()))
		{
			// server/1u3l
			return new RedirectFilterChain(getContextPath() + "/");
		}

		return chain;
	}

	FilterChain createWebAppFilterChain(FilterChain chain,
			Invocation invocation, boolean isTop)
	{
		// the cache must be outside of the WebAppFilterChain because
		// the CacheListener in ServletInvocation needs the top to
		// be a CacheListener. Otherwise, the cache won't get lru.

		if (getRequestListeners() != null && getRequestListeners().length > 0)
		{
			chain = new WebAppListenerFilterChain(chain, this,
					getRequestListeners());
		}

		// TCK: cache needs to be outside because the cache flush conflicts
		// with the request listener destroy callback
		// top-level filter elements
		// server/021h - cache not logging

		WebAppFilterChain webAppChain = new WebAppFilterChain(chain, this);

		// webAppChain.setSecurityRoleMap(invocation.getSecurityRoleMap());
		chain = webAppChain;

		return chain;
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
		synchronized (_filterChainCache)
		{
			_filterChainCache.clear();
			_dispatcherCache = null;
		}

	}

	/**
	 * Fills the invocation for an include request.
	 */
	public void buildIncludeInvocation(Invocation invocation)
			throws ServletException
	{
		buildDispatchInvocation(invocation, _includeFilterMapper);
	}

	/**
	 * Fills the invocation for a forward request.
	 */
	public void buildForwardInvocation(Invocation invocation)
			throws ServletException
	{
		buildDispatchInvocation(invocation, _forwardFilterMapper);
	}

	/**
	 * Fills the invocation for an error request.
	 */
	public void buildErrorInvocation(Invocation invocation)
			throws ServletException
	{
		buildDispatchInvocation(invocation, _errorFilterMapper);
	}

	/**
	 * Fills the invocation for a rewrite-dispatch/dispatch request.
	 */
	public void buildDispatchInvocation(Invocation invocation)
			throws ServletException
	{
		// buildDispatchInvocation(invocation, _dispatchFilterMapper);
		buildDispatchInvocation(invocation, _requestFilterMapper);

		buildSecurity(invocation);
	}

	/**
	 * Fills the invocation for subrequests.
	 */
	public void buildDispatchInvocation(Invocation invocation,
			FilterMapper filterMapper) throws ServletException
	{
		invocation.setWebApp(this);

		Thread thread = Thread.currentThread();
		ClassLoader oldLoader = thread.getContextClassLoader();

		thread.setContextClassLoader(getClassLoader());
		try
		{
			FilterChain chain;

			if (!isEnabled())
			{
				Exception exn = new UnavailableException(L.l(
						"'{0}' is not currently available.", getContextPath()));
				chain = new ExceptionFilterChain(exn);
			}
			else
			{
				chain = _servletMapper.mapServlet(invocation);
				chain = filterMapper.buildDispatchChain(invocation, chain);

				if (filterMapper == _includeFilterMapper)
				{
					chain = applyWelcomeFile(DispatcherType.INCLUDE,
							invocation, chain);

				}
				else if (filterMapper == _forwardFilterMapper)
				{
					chain = applyWelcomeFile(DispatcherType.FORWARD,
							invocation, chain);

				}

			}

			invocation.setFilterChain(chain);
		}
		catch (Exception e)
		{
			log.log(Level.FINE, e.toString(), e);

			FilterChain chain = new ExceptionFilterChain(e);
			invocation.setFilterChain(chain);
		}
		finally
		{
			thread.setContextClassLoader(oldLoader);
		}
	}

	private void buildSecurity(Invocation invocation)
	{
		invocation.setFilterChain(buildSecurity(invocation.getFilterChain(),
				invocation));
	}

	private FilterChain buildSecurity(FilterChain chain, Invocation invocation)
	{
		if (_securityBuilder != null)
		{
			return _securityBuilder.build(chain, invocation);
		}

		return chain;
	}

	/**
	 * Returns a dispatcher for the named servlet. TODO:其实可以将具体build invocation的时刻延迟到RequestDispatcherImpl里实现?
	 * 等RequestDispatcherImpl选择了forward还是什么的时候再实现
	 */
	@Override
	public RequestDispatcherImpl getRequestDispatcher(String url)
	{
		if (url == null)
			throw new IllegalArgumentException(L.l("request dispatcher url can't be null."));
		else if (!url.startsWith("/"))
			throw new IllegalArgumentException(L.l("request dispatcher url '{0}' must be absolute", url));

		RequestDispatcherImpl disp = getDispatcherCache().get(url);

		if (disp != null)
			return disp;

		Invocation dispatchInvocation = new SubInvocation();
		Invocation includeInvocation = new SubInvocation();
		Invocation forwardInvocation = new SubInvocation();
		Invocation errorInvocation = new SubInvocation();
		

		URIDecoder decoder = getURIDecoder();

		String rawURI = escapeURL(getContextPath() + url);

		try
		{
			decoder.splitQuery(dispatchInvocation, rawURI);
			decoder.splitQuery(includeInvocation, rawURI);
			decoder.splitQuery(forwardInvocation, rawURI);
			decoder.splitQuery(errorInvocation, rawURI);
			

			buildIncludeInvocation(includeInvocation);
			buildForwardInvocation(forwardInvocation);
			buildErrorInvocation(errorInvocation);
			buildDispatchInvocation(dispatchInvocation);

			disp = new RequestDispatcherImpl(includeInvocation,forwardInvocation, errorInvocation, dispatchInvocation,this);

			getDispatcherCache().put(url, disp);

			return disp;
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			log.log(Level.FINE, e.toString(), e);

			return null;
		}
	}

	private LruCache<String, RequestDispatcherImpl> getDispatcherCache()
	{
		LruCache<String, RequestDispatcherImpl> cache = _dispatcherCache;

		if (cache != null)
			return cache;

		synchronized (this)
		{
			cache = new LruCache<String, RequestDispatcherImpl>(1024);
			_dispatcherCache = cache;
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

			FilterChain chain = _servletManager.createServletChain(servletName,
					invocation);

			FilterChain includeChain = _includeFilterMapper.buildFilterChain(
					chain, servletName);
			FilterChain forwardChain = _forwardFilterMapper.buildFilterChain(
					chain, servletName);

			return new NamedDispatcherImpl(includeChain, forwardChain, null,this);

		}
		catch (Exception e)
		{
			log.log(Level.FINEST, e.toString(), e);

			return null;
		}
	}

	/**
	 * Maps from a URI to a real path.
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

		WebApp webApp = this;
		String tail = uri;

		String fullURI = getContextPath() + "/" + uri;

		try
		{
			fullURI = getURIDecoder().normalizeUri(fullURI);
		}
		catch (Exception e)
		{
			log.log(Level.WARNING, e.toString(), e);
		}

		webApp = (WebApp) getContext(fullURI);

		if (webApp == null)
			webApp = this;

		String cp = webApp.getContextPath();
		tail = fullURI.substring(cp.length());

		realPath = tail;

		if (log.isLoggable(Level.FINEST))
			log.finest("real-path " + uri + " -> " + realPath);

		_realPathCache.put(uri, realPath);

		return realPath;
	}

	/**
	 * Returns the mime type for a uri
	 */
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
			log.log(Level.WARNING, e.toString(), e);
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
		if (_sessionManager == null)
		{

			if (_sessionManager == null)
			{
				Thread thread = Thread.currentThread();
				ClassLoader oldLoader = thread.getContextClassLoader();

				try
				{
					thread.setContextClassLoader(getClassLoader());

					//_sessionManager = new SessionManager(this);  ken
				}
				catch (Throwable e)
				{
					throw ConfigException.create(e);
				}
				finally
				{
					thread.setContextClassLoader(oldLoader);
				}
			}
		}

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
	 * Returns the maximum length for a cache.
	 */
	public void addCacheMapping(CacheMapping mapping) throws Exception
	{
		if (mapping.getUrlRegexp() != null)
			_cacheMappingMap.addRegexp(mapping.getUrlRegexp(), mapping);
		else
			_cacheMappingMap.addMap(mapping.getUrlPattern(), mapping);
	}

	/**
	 * Returns the time for a cache mapping.
	 */
	public long getMaxAge(String uri)
	{
		CacheMapping map = _cacheMappingMap.map(uri);

		if (map != null)
			return map.getMaxAge();
		else
			return Long.MIN_VALUE;
	}

	/**
	 * Returns the time for a cache mapping.
	 */
	public long getSMaxAge(String uri)
	{
		CacheMapping map = _cacheMappingMap.map(uri);

		if (map != null)
			return map.getSMaxAge();
		else
			return Long.MIN_VALUE;
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

	public String generateCookieDomain(HttpServletRequest request)
	{
		String serverName = request.getServerName();

		if (_cookieDomainPattern == null)
			return _sessionManager.getCookieDomain();

		String domain;
		Matcher matcher = _cookieDomainPattern.matcher(serverName);

		// XXX: performance?
		if (matcher.find())
		{
			domain = matcher.group();
		}
		else
		{
			domain = null;
		}

		return domain;
	}

	/**
	 * Stops the webApp.
	 */
	public void stop()
	{
		Thread thread = Thread.currentThread();
		ClassLoader oldLoader = thread.getContextClassLoader();

		try
		{
			thread.setContextClassLoader(getClassLoader());

			long beginStop = CurrentTime.getCurrentTime();

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
			if (_webAppListeners != null)
			{
				for (int i = _webAppListeners.size() - 1; i >= 0; i--)
				{
					ServletContextListener listener = _webAppListeners.get(i);

					try
					{
						listener.contextDestroyed(event);
					}
					catch (Exception e)
					{
						log.log(Level.WARNING, e.toString(), e);
					}
				}
			}

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
					log.log(Level.WARNING, e.toString(), e);
				}
			}

		}
		finally
		{
			thread.setContextClassLoader(oldLoader);

			clearCache();
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
			log.log(Level.WARNING, e.toString(), e);
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
		MultipartConfigElement _multipartConfig;

		FilterChainEntry(FilterChain filterChain, Invocation invocation)
		{
			_filterChain = filterChain;
			_pathInfo = invocation.getPathInfo();
			_servletPath = invocation.getServletPath();
			_servletName = invocation.getServletName();
			_isAsyncSupported = invocation.isAsyncSupported();
			_multipartConfig = invocation.getMultipartConfig();
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

		public MultipartConfigElement getMultipartConfig()
		{
			return _multipartConfig;
		}
	}

	static class ClassComparator implements Comparator<Class<?>>
	{
		@Override
		public int compare(Class<?> a, Class<?> b)
		{
			return a.getName().compareTo(b.getName());
		}

	}

	// --util-----------------------------------------------------------------

	private static String escapeURL(String url)
	{
		return url;

		/*
		 * jsp/15dx CharBuffer cb = CharBuffer.allocate();
		 * 
		 * int length = url.length(); for (int i = 0; i < length; i++) { char ch = url.charAt(i);
		 * 
		 * if (ch < 0x80) cb.append(ch); else if (ch < 0x800) { cb.append((char) (0xc0 | (ch >> 6))); cb.append((char)
		 * (0x80 | (ch & 0x3f))); } else { cb.append((char) (0xe0 | (ch >> 12))); cb.append((char) (0x80 | ((ch >> 6) &
		 * 0x3f))); cb.append((char) (0x80 | (ch & 0x3f))); } }
		 * 
		 * return cb.close();
		 */
	}

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
			log.log(Level.WARNING, this + " " + message, e);
		else
			log.info(this + " " + message);
	}

}
