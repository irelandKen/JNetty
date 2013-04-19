package org.ireland.jnetty.config;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

import javax.servlet.DispatcherType;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ireland.jnetty.dispatch.filter.FilterConfigImpl;
import org.ireland.jnetty.dispatch.servlet.ServletConfigImpl;
import org.ireland.jnetty.webapp.WebApp;
import org.junit.Test;

/**
 * A Loader to parse web.xml FilterConfig ,ServletConfig
 * 
 * 加载 配置文件[web.xml]
 * 
 * @author KEN
 * 
 */
public class WebXmlLoader
{
	private static final Log log = LogFactory.getLog(WebXmlLoader.class);
	
	private static final char SEPARATOR = File.separatorChar;
	private static final String DATA_FILE_NAME = System.getProperty("user.dir") + SEPARATOR + "src" + SEPARATOR + "main" + SEPARATOR + "webapp" + SEPARATOR
			+ "WEB-INF" + SEPARATOR + "web.xml";

	private static final String DATA_FILE_NAME2 = System.getProperty("user.dir") + SEPARATOR+ "WEB-INF" + SEPARATOR + "web.xml";
	
	private WebApp webApp;

	private XMLConfiguration xmlConfig = null;

	public WebXmlLoader(WebApp webApp)
	{
		this.webApp = webApp;

		try
		{
			xmlConfig = new XMLConfiguration(DATA_FILE_NAME);
		}
		catch (ConfigurationException e)
		{
			//e.printStackTrace();
			try
			{
				xmlConfig = new XMLConfiguration(DATA_FILE_NAME2);
			}
			catch (ConfigurationException e1)
			{
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		
		if(log.isDebugEnabled())
		{
			log.debug(xmlConfig.getFileName()+" is loaded.");
		}
	}
	
	/**
	 * 加载 <context-param> 标签
	 */
	public void loadInitParam()
	{
		int cnt = 0;

		while (getString("context-param(" + cnt + ").param-name") != null)
		{
			String name = getString("context-param(" + cnt + ").param-name");
			String value = getStringPlain("context-param(" + cnt + ").param-value");
			
			webApp.setInitParameter(name, value);
			
			cnt++;
		}
		
	}
	
	/**
	 * 加载<listener>标签
	 */
	public void loadListener()
	{
		int cnt = 0;

		while (getString("listener(" + cnt + ").listener-class") != null)
		{
			String name = getString("listener(" + cnt + ").listener-class");
			
			webApp.addListener(name);
			
			cnt++;
		}
	}

	/**
	 * 解释web.xml所有的<filter>元素,并返回一个<filterName,FilterConfigImpl>的map
	 * 
	 * @return
	 * @throws ClassNotFoundException
	 */
	public LinkedHashMap<String, FilterConfigImpl> praseFilter() throws ClassNotFoundException
	{

		int cnt = 0;

		String filterName = null;

		LinkedHashMap<String, FilterConfigImpl> filterMap = new LinkedHashMap<String, FilterConfigImpl>();

		// Find <filter>
		while (((filterName = getString("filter(" + cnt + ").filter-name")) != null) && (filterName.trim().length() > 0))
		{
			FilterConfigImpl config = webApp.createNewFilterConfig();

			config.setFilterName(filterName);
			
			String filterClassName = getString("filter(" + cnt + ").filter-class");
			
			if(filterClassName == null || filterClassName.isEmpty())
				throw new ConfigException("<filter-class> can not be empty");
			
			config.setFilterClass(filterClassName);
			
			config.setAsyncSupported(getBoolean("filter(" + cnt + ").async-supported"));
			config.setDescription(getString("filter(" + cnt + ").description"));
			config.setDisplayName(getString("filter(" + cnt + ").display-name"));

			int i = 0;

			while (getString("filter(" + cnt + ").init-param(" + i + ").param-name") != null)
			{
				String name = getString("filter(" + cnt + ").init-param(" + i + ").param-name");
				String value = getString("filter(" + cnt + ").init-param(" + i + ").param-value");

				config.addInitParam(name, value);

				i++;
			}

			
			
			//
			filterMap.put(filterName, config);

			cnt++;
		}

		return filterMap;
	}

	/**
	 * 1:调用praseFilter()取得所有filter 2:按顺序解释每个<filter-mapping>元素,在filterMap中找到对应的FilterConfigImpl,并配置到webApp中
	 * 
	 * @throws ClassNotFoundException
	 */
	public void parseFilterMapping(LinkedHashMap<String, FilterConfigImpl> filterMap) throws ClassNotFoundException
	{

		// find <filter-mapping>
		int cnt = 0;

		while (getString("filter-mapping(" + cnt + ").filter-name") != null)
		{
			String filterName = getString("filter-mapping(" + cnt + ").filter-name").trim();

			FilterConfigImpl config = filterMap.get(filterName);

			if (config == null)
				throw new ConfigException("the <filter> element with name[" + filterName + "] is not exist.");

			String value;

			// <filter-mapping>可以包含多个 <url-pattern>
			Set<String> urlPatterns = new HashSet<String>();
			int q = 0;
			while ((value = getString("filter-mapping(" + cnt + ").url-pattern(" + q + ")")) != null)
			{
				urlPatterns.add(value);
				q++;
			}

			// <filter-mapping>可以包含多个 <servlet-name>
			Set<String> servletNames = new HashSet<String>();
			q = 0;
			while ((value = getString("filter-mapping(" + cnt + ").servlet-name(" + q + ")")) != null)
			{
				servletNames.add(value);
				q++;
			}

			// 查找这个Filter的所有"<dispatcher>"标签,一个<filter-mapping>可以包含多个<dispatcher>标签
			Set<DispatcherType> dispatcherTypes = new HashSet<DispatcherType>();
			int k = 0;

			while ((value = getString("filter-mapping(" + cnt + ").dispatcher(" + k + ")")) != null)
			{
				dispatcherTypes.add(DispatcherType.valueOf(value));
				k++;
			}

			// 查找<filter-mapping>的所有<servlet-name>标签,一个<servlet-name>可以包含多个<servlet-name>标签
			if (urlPatterns.size() > 0)
			{
				config.addMappingForUrlPatterns(EnumSet.copyOf(dispatcherTypes), true, toArray(urlPatterns));
			}

			if (servletNames.size() > 0)
			{
				config.addMappingForServletNames(EnumSet.copyOf(dispatcherTypes), true, toArray(servletNames));
			}

			cnt++;
		}
	}

	/**
	 * 解释web.xml中的"<servlet>"和<servlet-mapping>标签
	 * 
	 * @throws ClassNotFoundException
	 */
	public LinkedHashMap<String, ServletConfigImpl> praseServletConfig() throws ClassNotFoundException
	{
		LinkedHashMap<String, ServletConfigImpl> servletMap = new LinkedHashMap<String, ServletConfigImpl>();

		int cnt = 0;

		String servletName = null;

		// 查找每一个 <servlet> 元素,将其转换为ServletConfigImpl
		while (((servletName = getString("servlet(" + cnt + ").servlet-name")) != null) && (!servletName.trim().isEmpty()))
		{
			servletName = servletName.trim();

			ServletConfigImpl config = webApp.createNewServletConfig();

			config.setServletName(servletName);
			
			String servletClassName =  getString("servlet(" + cnt + ").servlet-class");
			
			if(servletClassName == null || servletClassName.isEmpty())
				throw new ConfigException("<servlet-class> can not be empty");
			
			config.setServletClass(servletClassName);

			config.setAsyncSupported(getBoolean("servlet(" + cnt + ").async-supported"));

			config.setDescription(getString("servlet(" + cnt + ").description"));
			config.setDisplayName(getString("servlet(" + cnt + ").display-name"));
			config.setLoadOnStartup(getInt("servlet(" + cnt + ").load-on-startup", Integer.MIN_VALUE));

			int i = 0;

			while (getString("servlet(" + cnt + ").init-param(" + i + ").param-name") != null)
			{
				String name = getString("servlet(" + cnt + ").init-param(" + i + ").param-name");
				String value = getString("servlet(" + cnt + ").init-param(" + i + ").param-value");

				config.setInitParam(name, value);
				i++;
			}

			servletMap.put(servletName, config);
			
			cnt++;
		}

		return servletMap;

	}

	public void parseServletMapping(LinkedHashMap<String, ServletConfigImpl> servletMap) throws ClassNotFoundException
	{

		/**
		 * find <servlet-mapping> for <servlet> 查找 当前<servlet>标签 所关联 的所有 "<servlet-mapping>"标签
		 */
		int j = 0;

		String servletName;

		while ((servletName = getString("servlet-mapping(" + j + ").servlet-name")) != null && !servletName.trim().isEmpty())
		{
			servletName = servletName.trim();

			ServletConfigImpl config = servletMap.get(servletName);

			if (config == null)
				throw new ConfigException("the <servlet> element with name[" + servletName + "] is not exist.");

			String value;

			// 一个<servlet-mapping>可以包含多个 <url-pattern>
			Set<String> url_patterns = new HashSet<String>();
			int q = 0;
			while ((value = getString("servlet-mapping(" + j + ").url-pattern(" + q + ")")) != null)
			{
				url_patterns.add(value);
				q++;
			}

			if (url_patterns.size() > 0)
			{
				config.addMapping(toArray(url_patterns));
			}

			j++;
		}

	}

	// util
	// method---------------------------------------------------------------------------------------------------------
	private String getString(String node)
	{
		return getString(node, null);
	}
	
	private String getStringPlain(String node)
	{
		String value = null;

		try
		{
			value = xmlConfig.getString(node);
		}
		catch (Exception ex)
		{
		}

		return value;
	}

	private String getString(String node, String defaultValue)
	{
		String value = defaultValue;

		try
		{
			value = xmlConfig.getString(node);
			value.trim();
		}
		catch (Exception ex)
		{
		}

		return value;
	}

	private boolean getBoolean(String node)
	{
		return getBoolean(node, false);
	}

	private boolean getBoolean(String node, boolean defaultValue)
	{
		boolean value = defaultValue;

		try
		{
			value = xmlConfig.getBoolean(node);
		}
		catch (Exception ex)
		{
		}

		return value;
	}

	private int getInt(String node, int defaultValue)
	{
		int value = defaultValue;

		try
		{
			value = xmlConfig.getInt(node);
		}
		catch (Exception ex)
		{
		}

		return value;
	}

	private static <E> String[] toArray(Collection<String> c)
	{
		String[] array = new String[c.size()];

		int i = 0;
		for (String e : c)
		{
			array[i] = e;
			i++;
		}

		return array;
	}
}
