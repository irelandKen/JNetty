package Filters;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 
 * @author Future
 *这是一个登录过滤器
 *如果取得的user 为null,则sendRedirect到登录的页面
 *否则
 *
 */
public class LoginFilter implements Filter
{

	@Override
	public void init(FilterConfig filterConfig) throws ServletException
	{
		System.out.println("LoginFilter init()");
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException
	{
		HttpServletRequest req = (HttpServletRequest)request;
		
		String user = req.getParameter("user");
		
		/*if(null == user) 
		{
			System.out.println("you have not Logined");
			((HttpServletResponse)response).sendRedirect("index.jsp?user=ken");
		}
		else
		{
			System.out.println("you have Logined");
			chain.doFilter(request, response);
		}*/
			
		
		System.out.println("LoginFilter doFilter()");
		
		chain.doFilter(request, response);
	}

	@Override
	public void destroy()
	{
		System.out.println("LoginFilter destroy()");
	}

}
