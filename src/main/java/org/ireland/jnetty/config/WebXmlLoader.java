package org.ireland.jnetty.config;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;

import javax.servlet.DispatcherType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import org.ireland.jnetty.dispatch.filter.FilterConfigImpl;
import org.ireland.jnetty.dispatch.servlet.ServletConfigImpl;
import org.ireland.jnetty.webapp.WebApp;

import org.springframework.util.Assert;

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

	private static String FILE_PATH;

	private Document doc;
	
	private final WebApp webApp;

	
	public WebXmlLoader(WebApp webApp)
	{
		Assert.notNull(webApp);

		this.webApp = webApp;

		FILE_PATH = webApp.getRealPath("/WEB-INF/web.xml");

		
		StringBuilder builder = new StringBuilder();

		try
		{
			String line;
			
			BufferedReader reader = new BufferedReader( new FileReader(FILE_PATH));
			
			while((line = reader.readLine()) != null)
			{
				builder.append(line);
			}
			
			String xmlString = builder.toString();
			
			doc = DocumentHelper.parseText(xmlString);
		}
		catch (Exception e1)
		{
			e1.printStackTrace();
		}
		
		
		if (log.isDebugEnabled())
		{
			log.debug(FILE_PATH + " is loaded.");
		}
	}

	/**
	 * 加载 <context-param> 标签
	 */
	public void loadInitParam()
	{
		log.debug("loading <context-param>.");
		Element rootElt = doc.getRootElement(); // 获取根节点

		Iterator iter = rootElt.elementIterator("context-param"); // 获取根节点下的子节点context-param

		// 遍历context-param节点
		while (iter.hasNext())
		{
			Element recordEle = (Element) iter.next();
			
			String name = recordEle.elementTextTrim("param-name"); // 拿到context-param节点下的子节点param-name值
			
			String value = recordEle.elementTextTrim("param-value"); //
			
			webApp.setInitParameter(name, value);
		}
	}

	/**
	 * 加载<listener>标签
	 */
	public void loadListener()
	{
		log.debug("loading <listener>");
		
		Element rootElt = doc.getRootElement(); // 获取根节点

		Iterator iter = rootElt.elementIterator("listener"); // 获取根节点下的子节点listener

		// 遍历listener节点
		while (iter.hasNext())
		{
			Element recordEle = (Element) iter.next();
			
			String name = recordEle.elementTextTrim("listener-class"); 
			
			webApp.addListener(name);
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
		log.debug("loading <filter>.");
		
		Element rootElt = doc.getRootElement(); // 获取根节点

		Iterator iter = rootElt.elementIterator("filter"); // 获取根节点下的子节点<filter>

		LinkedHashMap<String, FilterConfigImpl> filterMap = new LinkedHashMap<String, FilterConfigImpl>();
		
		// 遍历context-param节点
		while (iter.hasNext())
		{
			Element recordEle = (Element) iter.next();
			
			String filterName = recordEle.elementTextTrim("filter-name"); // 拿到context-param节点下的子节点param-name值
			
			FilterConfigImpl config = webApp.createNewFilterConfig();

			config.setFilterName(filterName);
			
			String filterClassName = recordEle.elementTextTrim("filter-class"); //
			
			if (filterClassName == null || filterClassName.isEmpty())
				throw new ConfigException("<filter-class> can not be empty");

			config.setFilterClass(filterClassName);
			
			config.setAsyncSupported(Boolean.parseBoolean(recordEle.elementTextTrim("async-supported")));
			
			config.setDescription(recordEle.elementTextTrim("description"));
			config.setDisplayName(recordEle.elementTextTrim("display-name"));

			
			Iterator innerIter = recordEle.elementIterator("init-param");
			
			
			//<init-param>
			while (innerIter.hasNext())
			{
				Element innerEle = (Element) innerIter.next();
				
				String name = innerEle.elementTextTrim("param-name");
				String value = innerEle.elementTextTrim("param-value");
				
				config.addInitParam(name, value);
			}

			//
			filterMap.put(filterName, config);
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
		log.debug("loading <filter-mapping>.");
		
		Element rootElt = doc.getRootElement(); // 获取根节点

		Iterator iter = rootElt.elementIterator("filter-mapping"); // 获取根节点下的子节点<filter-mapping>

		while (iter.hasNext())
		{
			Element recordEle = (Element) iter.next();
			
			String filterName = recordEle.elementTextTrim("filter-name");
			
			FilterConfigImpl config = filterMap.get(filterName);

			if (config == null)
				throw new ConfigException("the <filter> element with name[" + filterName + "] is not exist.");

			
			// <filter-mapping>可以包含多个 <url-pattern>
			Set<String> urlPatterns = new HashSet<String>();

			Iterator innerIter = recordEle.elementIterator("url-pattern");
			
			//<filter-mapping>
			while (innerIter.hasNext())
			{
				Element innerEle = (Element) innerIter.next();
				
				urlPatterns.add(innerEle.getTextTrim());
			}

			
			// <filter-mapping>可以包含多个 <servlet-name>
			Set<String> servletNames = new HashSet<String>();
			
			innerIter = recordEle.elementIterator("servlet-name");
			
			//<filter-mapping>
			while (innerIter.hasNext())
			{
				Element innerEle = (Element) innerIter.next();
				
				servletNames.add(innerEle.getTextTrim());
			}
			
		

			// 查找这个Filter的所有"<dispatcher>"标签,一个<filter-mapping>可以包含多个<dispatcher>标签
			Set<DispatcherType> dispatcherTypes = new HashSet<DispatcherType>();

			innerIter = recordEle.elementIterator("dispatcher");
			
			//<filter-mapping>
			while (innerIter.hasNext())
			{
				Element innerEle = (Element) innerIter.next();
				
				dispatcherTypes.add(DispatcherType.valueOf(innerEle.getTextTrim()));
			}


			// 查找<filter-mapping>的所有<servlet-name>标签,一个<servlet-name>可以包含多个<servlet-name>标签
			if (urlPatterns.size() > 0)
			{
				config.addMappingForUrlPatterns(dispatcherTypes.size() == 0 ? null : EnumSet.copyOf(dispatcherTypes), true, toArray(urlPatterns));
			}

			if (servletNames.size() > 0)
			{
				config.addMappingForServletNames(dispatcherTypes.size() == 0 ? null : EnumSet.copyOf(dispatcherTypes), true, toArray(servletNames));
			}

		}
		
		
	}

	/**
	 * 解释web.xml中的<servlet>标签
	 * 
	 * @throws ClassNotFoundException
	 */
	public LinkedHashMap<String, ServletConfigImpl> praseServletConfig() throws ClassNotFoundException
	{
		log.debug("loading <servlet>.");
		
		Element rootElt = doc.getRootElement(); // 获取根节点

		Iterator iter = rootElt.elementIterator("servlet"); // 获取根节点下的子节点<servlet>
		
		LinkedHashMap<String, ServletConfigImpl> servletMap = new LinkedHashMap<String, ServletConfigImpl>();
		
		// 遍历<servlet>节点
		while (iter.hasNext())
		{
			Element recordEle = (Element) iter.next();
			
			String servletName = recordEle.elementTextTrim("servlet-name"); // 拿到context-param节点下的子节点param-name值
			
			ServletConfigImpl config = webApp.createNewServletConfig();

			config.setServletName(servletName);
			
			String className = recordEle.elementTextTrim("servlet-class"); //
			
			if (className == null || className.isEmpty())
				throw new ConfigException("<servlet-class> can not be empty");

			config.setServletClass(className);
			
			config.setAsyncSupported(Boolean.parseBoolean(recordEle.elementTextTrim("async-supported")));
			
			config.setDescription(recordEle.elementTextTrim("description"));
			config.setDisplayName(recordEle.elementTextTrim("display-name"));
			
			
			String loadOnStartup = recordEle.elementTextTrim("load-on-startup");
			
			if(loadOnStartup != null)
				config.setLoadOnStartup(Integer.parseInt(loadOnStartup));

			Iterator innerIter = recordEle.elementIterator("init-param");
			
			
			//<init-param>
			while (innerIter.hasNext())
			{
				Element innerEle = (Element) innerIter.next();
				
				String name = innerEle.elementTextTrim("param-name");
				String value = innerEle.elementTextTrim("param-value");
				
				config.setInitParam(name, value);
			}

			//
			servletMap.put(servletName, config);
		}

		return servletMap;
		
	}

	public void parseServletMapping(LinkedHashMap<String, ServletConfigImpl> servletMap) throws ClassNotFoundException
	{
		log.debug("loading <servlet-mapping>.");

		/**
		 * find <servlet-mapping> for <servlet> 查找 当前<servlet>标签 所关联 的所有 "<servlet-mapping>"标签
		 */
		
		Element rootElt = doc.getRootElement(); // 获取根节点

		Iterator iter = rootElt.elementIterator("servlet-mapping"); // 获取根节点下的子节点<servlet-mapping>


		// 遍历<servlet>节点
		while (iter.hasNext())
		{
			Element recordEle = (Element) iter.next();
			
			String servletName = recordEle.elementTextTrim("servlet-name");

			ServletConfigImpl config = servletMap.get(servletName);

			if (config == null)
				throw new ConfigException("the <servlet> element with name[" + servletName + "] is not exist.");

			String value;

			// 一个<servlet-mapping>可以包含多个 <url-pattern>
			Set<String> urlPatterns = new HashSet<String>();

			Iterator innerIter = recordEle.elementIterator("url-pattern");
			
			//<filter-mapping>
			while (innerIter.hasNext())
			{
				Element innerEle = (Element) innerIter.next();
				
				urlPatterns.add(innerEle.getTextTrim());
			}

			if (urlPatterns.size() > 0)
			{
				config.addMapping(toArray(urlPatterns)); // 添加<servlet-mapping>
			}
		}

	}

	// util


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
