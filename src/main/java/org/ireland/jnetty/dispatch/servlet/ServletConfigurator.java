package org.ireland.jnetty.dispatch.servlet;

import javax.servlet.ServletException;

public interface ServletConfigurator
{
	/**
	 * 创建一个新的ServletConfigImpl
	 * @return
	 */
	ServletConfigImpl createNewServletConfig();

	/**
	 * 创建一个新的ServletMapping
	 * @return
	 */
	ServletMapping createNewServletMapping(ServletConfigImpl config);

	/**
	 * Adds a servlet-mapping configuration.
	 */
	void addServletMapping(ServletMapping servletMapping) throws ServletException;

	/**
	 * Adds a servlet configuration.
	 */
	void addServlet(ServletConfigImpl config) throws ServletException;

}
