package org.ireland.jnetty.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.DispatcherType;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.ireland.jnetty.dispatch.filter.FilterConfigImpl;
import org.ireland.jnetty.dispatch.servlet.ServletConfigImpl;
import org.ireland.jnetty.webapp.WebApp;

/**
 * A Loader to parse web.xml FilterConfig ,ServletConfig
 * @author KEN
 *
 */
public class WebXmlLoader
{
    private static final String DATA_FILE_NAME = System.getProperty("user.dir")+ "\\src\\main\\webapp\\WEB-INF\\web.xml";  
    
    private WebApp webApp;
    
    
	public WebXmlLoader(WebApp webApp)
	{
		super();
		this.webApp = webApp;
	}

	public void praseFilter() throws ClassNotFoundException
	{
		XMLConfiguration xmlConfig = null;
		try
		{
			xmlConfig = new XMLConfiguration(DATA_FILE_NAME);
		} catch (ConfigurationException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		List<FilterConfigImpl> filterConfigs = new ArrayList<FilterConfigImpl>();
		
		int cnt = 0;
		
		String filterName = null;
		
		//Find <filter>
		while( ((filterName = xmlConfig.getString("filter("+cnt+").filter-name")) != null)
				&& (filterName.trim().length()>0))
		{
			FilterConfigImpl config = new FilterConfigImpl();
			
			config.setWebApp(webApp);
			config.setServletContext(webApp);
			config.setFilterManager(webApp.getFilterManager());
			
			config.setFilterName(filterName);
			config.setFilterClass(xmlConfig.getString("filter("+cnt+").filter-class"));
			config.setAsyncSupported(xmlConfig.getBoolean("filter("+cnt+").async-supported"));
			config.setDescription(xmlConfig.getString("filter("+cnt+").description"));
			config.setDisplayName(xmlConfig.getString("filter("+cnt+").display-name"));
			

			
			
			
			int i = 0;
			
			while(xmlConfig.getString("filter("+cnt+").init-param("+i+").param-name") != null)
			{
				String name = xmlConfig.getString("filter("+cnt+").init-param("+i+").param-name");
				String value = xmlConfig.getString("filter("+cnt+").init-param("+i+").param-value");
						
				config.addInitParam(name, value);
			
				i++;
			}
			
			
			//find <filter-mapping> for <filter>
			int j = 0;
			
			while(xmlConfig.getString("filter-mapping("+j+").filter-name") != null)
			{
				String name = xmlConfig.getString("filter-mapping("+j+").filter-name").trim();
				
				//只取有关联的
				if(!name.equals(filterName))
					continue;
				
				String value;
				
				//<filter-mapping>可以包含多个 <url-pattern>
				Set<String> url_patterns = new HashSet<String>();
				int q = 0;
				while((value = xmlConfig.getString("filter-mapping("+j+").url-pattern("+q+")")) != null)
				{
					url_patterns.add(value);
					q++;
				}
				
				
				//<filter-mapping>可以包含多个 <servlet-name>
				Set<String> servlet_names = new HashSet<String>();
				q = 0;
				while((value = xmlConfig.getString("filter-mapping("+j+").servlet-name("+q+")")) != null)
				{
					servlet_names.add(value);
					q++;
				}
				
				
				//查找这个Filter的所有"<dispatcher>"标签,一个<filter-mapping>可以包含多个<dispatcher>标签
				List<DispatcherType> list = new ArrayList<DispatcherType>();
				int k = 0;
				
				while((value = xmlConfig.getString("filter-mapping("+j+").dispatcher("+k+")")) != null)
				{
					list.add(DispatcherType.valueOf(value));
					k++;
				}
			
				//查找<filter-mapping>的所有<servlet-name>标签,一个<servlet-name>可以包含多个<servlet-name>标签
				if(url_patterns.size() > 0)
				{
					config.addMappingForUrlPatterns(EnumSet.copyOf(list), true, toArray(url_patterns));
				}
				else
				if(servlet_names.size() > 0)
				{
					config.addMappingForServletNames(EnumSet.copyOf(list), true, toArray(servlet_names));
				}
			
				j++;
			}
			
			
			
			filterConfigs.add(config);

			cnt++;
		}
		
		
		System.out.println();
		
	}

	/**
	 * 解释web.xml中的"<servlet>"和<servlet-mapping>标签
	 * @throws ClassNotFoundException
	 */
	public void praseServletConfig() throws ClassNotFoundException
	{
		XMLConfiguration xmlConfig = null;
		try
		{
			xmlConfig = new XMLConfiguration(DATA_FILE_NAME);
		} catch (ConfigurationException e)
		{
			e.printStackTrace();
		}

		List<ServletConfigImpl> servletConfigs = new ArrayList<ServletConfigImpl>();
		
		int cnt = 0;
		
		String servletName = null;
		
		//查找每一个 <servlet> 元素,将其转换为ServletConfigImpl
		while( ((servletName = xmlConfig.getString("servlet("+cnt+").servlet-name")) != null)
				&& (!servletName.trim().isEmpty()))
		{
			servletName = servletName.trim();
			
			ServletConfigImpl config = new ServletConfigImpl();
			
			config.setServletName(servletName);
			config.setServletClass(xmlConfig.getString("servlet("+cnt+").servlet-class"));
			
			config.setAsyncSupported(xmlConfig.getBoolean("servlet("+cnt+").async-supported"));
			
			config.setDescription(xmlConfig.getString("servlet("+cnt+").description"));
			config.setDisplayName(xmlConfig.getString("servlet("+cnt+").display-name"));
			config.setLoadOnStartup(xmlConfig.getInt("servlet("+cnt+").load-on-startup"));
			
			
			int i = 0;
			
			while(xmlConfig.getString("servlet("+cnt+").init-param("+i+").param-name") != null)
			{
				String name = xmlConfig.getString("servlet("+cnt+").init-param("+i+").param-name");
				String value = xmlConfig.getString("servlet("+cnt+").init-param("+i+").param-value");
						
				config.setInitParam(name, value);
				i++;
			}
			
			
			
			
			/**
			 * find <servlet-mapping> for <servlet>
			 * 查找 当前<servlet>标签 所关联 的所有 "<servlet-mapping>"标签
			 */
			int j = 0;
			
			String name;
			
			while( (name = xmlConfig.getString("servlet-mapping("+j+").servlet-name")) != null
				&& !name.trim().isEmpty()	
					)
			{
				name = name.trim();
				
				//只取有关联的
				if(!name.equals(servletName))
					continue;
				
				String value;
				
				//一个<servlet-mapping>可以包含多个 <url-pattern>
				Set<String> url_patterns = new HashSet<String>();
				int q = 0;
				while((value = xmlConfig.getString("servlet-mapping("+j+").url-pattern("+q+")")) != null)
				{
					url_patterns.add(value);
					q++;
				}
	
			
				if(url_patterns.size() > 0)
				{
					config.addMapping(toArray(url_patterns));
				}
				
				j++;
			}
			
			
			
			servletConfigs.add(config);

			cnt++;
		}
		
		
		System.out.println();
		  

	}
	
	private static <E> String[] toArray(Collection<String> c)
	{
		String[] array = new String[c.size()];
		
		int i = 0;
		for(String e : c)
		{
			array[i] = e;
			i++;
		}
		
		return array;
	}
}

