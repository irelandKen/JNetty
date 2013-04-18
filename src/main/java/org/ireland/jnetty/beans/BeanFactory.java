package org.ireland.jnetty.beans;

import java.lang.reflect.InvocationTargetException;

import javax.naming.NamingException;

import org.apache.tomcat.InstanceManager;

public class BeanFactory implements InstanceManager
{
	/**
	 * Creates an object, but does not register the component with webbeans.
	 */
	public <T> T createBean(Class<T> type)
	{
		T instance = null;
		try
		{
			instance = type.newInstance();
		}
		catch (InstantiationException e)
		{
			e.printStackTrace();
		}
		catch (IllegalAccessException e)
		{
			e.printStackTrace();
		}

		return instance;
	}

	
	@Override
	public Object newInstance(String className) throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException,ClassNotFoundException
	{
		return Class.forName(className).newInstance();
	}

	@Override
	public Object newInstance(String className, ClassLoader classLoader) throws IllegalAccessException, InvocationTargetException, NamingException,
			InstantiationException, ClassNotFoundException
	{
		return Class.forName(className, false, classLoader).newInstance();
	}

	@Override
	public void newInstance(Object o) throws IllegalAccessException, InvocationTargetException, NamingException
	{
		
	}

	@Override
	public void destroyInstance(Object o) throws IllegalAccessException, InvocationTargetException
	{
		
	}

}
