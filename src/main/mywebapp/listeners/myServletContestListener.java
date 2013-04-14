package listeners;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;


public class myServletContestListener implements ServletContextListener
{

	@Override  //初始化时,与数据库连接,并将连接后的对象放进ServletContext
	public void contextInitialized(ServletContextEvent sce)
	{
		System.out.println("-ServletContestListener.contextInitialized()");
		
		
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce)
	{
		System.out.println("-ServletContestListener.contextDestroyed()");
	}

}
