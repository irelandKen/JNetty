package org.ireland.jnetty.dispatch.filter;

import javax.servlet.ServletException;

/**
 * Filter的配置器,包括 FilterCofigImpl的创建,FilterMapping的增加等操作
 * @author KEN
 *
 */
public interface FilterConfigurator
{

	/**
	 * 创建一个新的ServletConfigImpl
	 * @return
	 */
	FilterConfigImpl createNewFilterConfig();

	/**
	 * 创建一个新的ServletMapping
	 * @return
	 */
	FilterMapping createNewFilterMapping(FilterConfigImpl config);

	/**
	 * Adds a filter-mapping configuration.
	 * 
	 * 添加一个FilterMapping,相当于添加一个web.xml的<filter-mapping>标签
	 */
	void addFilterMapping(FilterMapping filterMapping) throws ServletException;

	/**
	 * Adds a filter configuration.
	 */
	void addFilter(FilterConfigImpl config);

}
