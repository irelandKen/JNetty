package org.ireland.jnetty.beans;

public class BeanFactory
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

}
