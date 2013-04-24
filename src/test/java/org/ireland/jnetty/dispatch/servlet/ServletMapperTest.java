package org.ireland.jnetty.dispatch.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.ireland.jnetty.util.http.UrlMap;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.util.Assert;



public class ServletMapperTest
{
	private static ServletMapperForTest mapper = new ServletMapperForTest(null, null,null);
	
	private static UrlMap<ServletMapping> _servletMappings = new UrlMap<ServletMapping>();
	
	
	@BeforeClass
	public static void init() throws ServletException
	{
		ServletConfigImpl firstServlet = new ServletConfigImpl(null, null, null, null);
		firstServlet.setServletName("FirstServlet");
		
		ServletMapping mapping = new ServletMapping(firstServlet);
		
		
		ServletConfigImpl secondServlet = new ServletConfigImpl(null, null, null, null);
		secondServlet.setServletName("SecondServlet");
		
		ServletMapping mapping2 = new ServletMapping(secondServlet);
		
		
		
		_servletMappings.addMap("/FirstServlet", mapping);
		_servletMappings.addMap("/*", mapping);
		_servletMappings.addMap("/user/*", mapping);
		_servletMappings.addMap("*.do", mapping);
		_servletMappings.addMap("*.html", mapping);
		
		_servletMappings.addMap("/SecondServlet", mapping2);
		_servletMappings.addMap("/user/home/*", mapping2);
		_servletMappings.addMap("*.htm", mapping2);
		_servletMappings.addMap("*.doj", mapping2);
		_servletMappings.addMap("/home/*.do", mapping2);

		
		mapper.addUrlMapping("/FirstServlet", mapping);
		mapper.addUrlMapping("/*", mapping);
		mapper.addUrlMapping("/user/*", mapping);
		mapper.addUrlMapping("*.do", mapping);
		mapper.addUrlMapping("*.html", mapping);
		
		mapper.addUrlMapping("/SecondServlet", mapping2);
		mapper.addUrlMapping("/user/home/*", mapping2);
		mapper.addUrlMapping("*.htm", mapping2);
		mapper.addUrlMapping("*.doj", mapping2);
		mapper.addUrlMapping("/home/*.do", mapping2);
	}
	

	//@Test
	public void correctTest()
	{
		ServletConfigImpl config1;
		ServletConfigImpl config2;

		config1 = _servletMappings.map("/FirstServlet").getServletConfig();
		config2 = mapper.mapServlet("/FirstServlet");
				
		System.out.println(config1.getName());
		System.out.println(config2.getName());
		System.out.println();
		
		
		config1 = _servletMappings.map("/FirstServlet").getServletConfig();
		config2 = mapper.mapServlet("/FirstServlet");
		System.out.println();
		
		System.out.println(config1.getName());
		System.out.println(config2.getName());
		System.out.println();
		
		config1 = _servletMappings.map("/SecondServlet").getServletConfig() ; 
		config2 =  mapper.mapServlet("/SecondServlet");
		
		System.out.println(config1.getName());
		System.out.println(config2.getName());
		System.out.println();
		
		
		config1 = _servletMappings.map("/123").getServletConfig() ; 
		config2 =  mapper.mapServlet("/123");
		
		System.out.println(config1.getName());
		System.out.println(config2.getName());
		System.out.println();
		
		config1 = _servletMappings.map("/user/123").getServletConfig() ;
		config2 =  mapper.mapServlet("/user/123");
		
		System.out.println(config1.getName());
		System.out.println(config2.getName());
		System.out.println();
		
		config1 = _servletMappings.map("/user/home/123").getServletConfig() ;
		config2 =  mapper.mapServlet("/user/home/123");
		
		System.out.println(config1.getName());
		System.out.println(config2.getName());
		System.out.println();
		
		config1 = _servletMappings.map("/123.do").getServletConfig() ;
		config2 =  mapper.mapServlet("/123.do");
		
		System.out.println(config1.getName());
		System.out.println(config2.getName());
		System.out.println();
		
		
		config1 = _servletMappings.map("/123.doj").getServletConfig() ;
		config2 =  mapper.mapServlet("/123.doj");
		
		System.out.println(config1.getName());
		System.out.println(config2.getName());
		System.out.println();
		
		config1 = _servletMappings.map("/123.htm").getServletConfig() ;
		config2 =  mapper.mapServlet("/123.htm");
		
		System.out.println(config1.getName());
		System.out.println(config2.getName());
		System.out.println();
		
		config1 = _servletMappings.map("/123.html").getServletConfig() ;
		config2 =  mapper.mapServlet("/123.html");
		
		System.out.println(config1.getName());
		System.out.println(config2.getName());
		System.out.println();
		
		config1 = _servletMappings.map("/home/123.do").getServletConfig() ;
		config2 =  mapper.mapServlet("/home/123.do");
		
		System.out.println(config1.getName());
		System.out.println(config2.getName());
		System.out.println();
		
	}
	
	@Test//10S
	public void speedTest1()
	{
		
		ServletConfigImpl config1;

		for(int i=0; i<1000000; i++)
		{
			config1 = _servletMappings.map("/FirstServlet").getServletConfig();
	
			
			
			config1 = _servletMappings.map("/FirstServlet").getServletConfig();
	
			
			
			config1 = _servletMappings.map("/SecondServlet").getServletConfig() ; 
			
			
			
			config1 = _servletMappings.map("/123").getServletConfig() ; 
			
			
			config1 = _servletMappings.map("/user/123").getServletConfig() ;
			
			
			config1 = _servletMappings.map("/user/home/123").getServletConfig() ;
			
			
			config1 = _servletMappings.map("/123.do").getServletConfig() ;
			
			
			
			config1 = _servletMappings.map("/123.doj").getServletConfig() ;
			
			
			config1 = _servletMappings.map("/123.htm").getServletConfig() ;
			
			
			config1 = _servletMappings.map("/123.html").getServletConfig() ;
			
			
			config1 = _servletMappings.map("/home/123.do").getServletConfig() ;
		
		}
	}
	
	@Test//0.639s
	public void speedTest2()
	{
		ServletConfigImpl config2;

		for(int i=0; i<1000000; i++)
		{
		config2 = mapper.mapServlet("/FirstServlet");
				
		
		
		config2 = mapper.mapServlet("/FirstServlet");
		
		config2 =  mapper.mapServlet("/SecondServlet");
		
		config2 =  mapper.mapServlet("/123");
		config2 =  mapper.mapServlet("/user/123");
		
		
		config2 =  mapper.mapServlet("/user/home/123");
		
		
		config2 =  mapper.mapServlet("/123.do");
		
		config2 =  mapper.mapServlet("/123.doj");
		
		
		config2 =  mapper.mapServlet("/123.htm");
		
		config2 =  mapper.mapServlet("/123.html");
		
		config2 =  mapper.mapServlet("/home/123.do");
		}
		
	}
}
