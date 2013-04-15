package Filters;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class EncodeFilter implements Filter
{

	@Override
	public void init(FilterConfig filterConfig) throws ServletException
	{
		System.out.println("EncodeFilter init()");
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException
	{
		response.setCharacterEncoding("utf-8");  //将所以经过这个过滤器的页面编码都变成中文GBK
		System.out.println("EncodeFilter doFilter() ");
		chain.doFilter(request, response);  //处理过滤器链上的下一个过滤器
	}

	@Override
	public void destroy()
	{
		System.out.println("EncodeFilter destroy()");
	}

}
