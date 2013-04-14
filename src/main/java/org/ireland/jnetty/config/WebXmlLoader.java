package org.ireland.jnetty.config;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.DispatcherType;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.ireland.jnetty.dispatch.filter.FilterConfigImpl;
import org.ireland.jnetty.dispatch.servlet.ServletConfigImpl;
import org.ireland.jnetty.webapp.WebApp;

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
	private static final String DATA_FILE_NAME = System.getProperty("user.dir") + "\\src\\main\\webapp\\WEB-INF\\web.xml";

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
			e.printStackTrace();
		}
	}

	public void praseFilter() throws ClassNotFoundException
	{

		int cnt = 0;

		String filterName = null;

		// Find <filter>
		while (((filterName = getString("filter(" + cnt + ").filter-name")) != null) && (filterName.trim().length() > 0))
		{
			FilterConfigImpl config = webApp.createNewFilterConfig();

			config.setFilterName(filterName);
			config.setFilterClass(getString("filter(" + cnt + ").filter-class"));
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

			// find <filter-mapping> for <filter>
			int j = 0;

			while (getString("filter-mapping(" + j + ").filter-name") != null)
			{
				String name = getString("filter-mapping(" + j + ").filter-name").trim();

				// 只取有关联的
				if (!name.equals(filterName))
				{
					j++;
					continue;
				}

				String value;

				// <filter-mapping>可以包含多个 <url-pattern>
				Set<String> url_patterns = new HashSet<String>();
				int q = 0;
				while ((value = getString("filter-mapping(" + j + ").url-pattern(" + q + ")")) != null)
				{
					url_patterns.add(value);
					q++;
				}

				// <filter-mapping>可以包含多个 <servlet-name>
				Set<String> servlet_names = new HashSet<String>();
				q = 0;
				while ((value = getString("filter-mapping(" + j + ").servlet-name(" + q + ")")) != null)
				{
					servlet_names.add(value);
					q++;
				}

				// 查找这个Filter的所有"<dispatcher>"标签,一个<filter-mapping>可以包含多个<dispatcher>标签
				Set<DispatcherType> dispatcherTypes = new HashSet<DispatcherType>();
				int k = 0;

				while ((value = getString("filter-mapping(" + j + ").dispatcher(" + k + ")")) != null)
				{
					dispatcherTypes.add(DispatcherType.valueOf(value));
					k++;
				}

				// 查找<filter-mapping>的所有<servlet-name>标签,一个<servlet-name>可以包含多个<servlet-name>标签
				if (url_patterns.size() > 0)
				{
					config.addMappingForUrlPatterns(EnumSet.copyOf(dispatcherTypes), true, toArray(url_patterns));
				}
				
				if (servlet_names.size() > 0)
				{
					config.addMappingForServletNames(EnumSet.copyOf(dispatcherTypes), true, toArray(servlet_names));
				}

				j++;
			}

			cnt++;
		}

	}

	/**
	 * 解释web.xml中的"<servlet>"和<servlet-mapping>标签
	 * 
	 * @throws ClassNotFoundException
	 */
	public void praseServletConfig() throws ClassNotFoundException
	{

		int cnt = 0;

		String servletName = null;

		// 查找每一个 <servlet> 元素,将其转换为ServletConfigImpl
		while (((servletName = getString("servlet(" + cnt + ").servlet-name")) != null) && (!servletName.trim().isEmpty()))
		{
			servletName = servletName.trim();

			ServletConfigImpl config = webApp.createNewServletConfig();

			config.setServletName(servletName);
			config.setServletClass(getString("servlet(" + cnt + ").servlet-class"));

			config.setAsyncSupported(getBoolean("servlet(" + cnt + ").async-supported"));

			config.setDescription(getString("servlet(" + cnt + ").description"));
			config.setDisplayName(getString("servlet(" + cnt + ").display-name"));
			config.setLoadOnStartup(getInt("servlet(" + cnt + ").load-on-startup",Integer.MIN_VALUE));

			int i = 0;

			while (getString("servlet(" + cnt + ").init-param(" + i + ").param-name") != null)
			{
				String name = getString("servlet(" + cnt + ").init-param(" + i + ").param-name");
				String value = getString("servlet(" + cnt + ").init-param(" + i + ").param-value");

				config.setInitParam(name, value);
				i++;
			}

			/**
			 * find <servlet-mapping> for <servlet> 查找 当前<servlet>标签 所关联 的所有 "<servlet-mapping>"标签
			 */
			int j = 0;

			String name;

			while ((name = getString("servlet-mapping(" + j + ").servlet-name")) != null && !name.trim().isEmpty())
			{
				name = name.trim();

				// 只取有关联的
				if (!name.equals(servletName))
				{
					j++;
					continue;
				}

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

			cnt++;
		}

	}

	
	private String getString(String node)
	{
		return getString(node,null);
	}
	
	private String getString(String node,String defaultValue)
	{
		String value = defaultValue;
		
		try
		{
			value = xmlConfig.getString(node);
		}
		catch(Exception ex)
		{
		}
		
		return value;
	}
	
	private boolean getBoolean(String node)
	{
		return getBoolean(node, false);
	}
	
	private boolean getBoolean(String node,boolean defaultValue)
	{
		boolean value = defaultValue;
		
		try
		{
			value = xmlConfig.getBoolean(node);
		}
		catch(Exception ex)
		{
		}
		
		return value;
	}
	
	private int getInt(String node,int defaultValue)
	{
		int value = defaultValue;
		
		try
		{
			value = xmlConfig.getInt(node);
		}
		catch(Exception ex)
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
