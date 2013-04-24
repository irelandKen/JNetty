import java.util.regex.Pattern;

import org.junit.Test;

import com.caucho.util.CharBuffer;



public class RegExpBench
{
	
	private static Pattern urlPatternToRegexp(String pattern, int flags) 
	{
		if (pattern.length() == 0 || pattern.length() == 1 && pattern.charAt(0) == '/')
		{
			try
			{
				return Pattern.compile("^/$", flags);
			}
			catch (Exception e)
			{
			}
		}

		int length = pattern.length();
		boolean isExact = true;

		if (pattern.charAt(0) != '/' && pattern.charAt(0) != '*')
		{
			pattern = "/" + pattern;
			length++;
		}

		int prefixLength = -1;
		boolean isShort = false;
		CharBuffer cb = new CharBuffer();
		cb.append("^");
		for (int i = 0; i < length; i++)
		{
			char ch = pattern.charAt(i);

			if (ch == '*' && i + 1 == length && i > 0)
			{
				isExact = false;

				if (pattern.charAt(i - 1) == '/')
				{
					cb.setLength(cb.length() - 1);

					if (prefixLength < 0)
						prefixLength = i - 1;

				}
				else if (prefixLength < 0)
					prefixLength = i;

				if (prefixLength == 0)
					prefixLength = 1;
			}
			else if (ch == '*')
			{
				isExact = false;
				cb.append(".*");
				if (prefixLength < 0)
					prefixLength = i;

				if (i == 0)
					isShort = true;
			}
			else if (ch == '.' || ch == '[' || ch == '^' || ch == '$' || ch == '{' || ch == '}' || ch == '|' || ch == '(' || ch == ')' || ch == '?')
			{
				cb.append('\\');
				cb.append(ch);
			}
			else
				cb.append(ch);
		}

		if (isExact)
			cb.append('$');
		else
			cb.append("(?=/)|" + cb.toString() + "$");

		try
		{
			return Pattern.compile(cb.close(), flags);
		}
		catch (Exception e)
		{
			return null;
		}
	}
	
	private static Pattern pattern = urlPatternToRegexp("/page/*",Pattern.CASE_INSENSITIVE);
	private static Pattern pattern2 = urlPatternToRegexp("/*",Pattern.CASE_INSENSITIVE);
	private static Pattern pattern3 = urlPatternToRegexp("/myspace",Pattern.CASE_INSENSITIVE);
	private static Pattern pattern4 = urlPatternToRegexp("*.jsp",Pattern.CASE_INSENSITIVE);
	private static Pattern pattern5 = urlPatternToRegexp("/",Pattern.CASE_INSENSITIVE);

	@Test//16.547S
	public void RegBench()
	{
		boolean b ;
		String uri = "/page/123";
		String uri2 = "/page/*";
		String uri3 = "/pdfskl";
		String uri4 = "/myapp/page/65";
		String uri5 = "/myspace";
		String uri6 = "/myspace?username=jack";
		String uri7 = "/home.jsp";
		
		for(int i=0; i<5000000; i++)
		{
			b = pattern.matcher(uri).find();
			b = pattern.matcher(uri2).find();
			b = pattern.matcher(uri3).find();
			b = pattern.matcher(uri4).find();
			b = pattern.matcher(uri5).find();
			b = pattern.matcher(uri6).find();
			b = pattern.matcher(uri7).find();
			
			b = pattern2.matcher(uri).find();
			b = pattern2.matcher(uri2).find();
			b = pattern2.matcher(uri3).find();
			b = pattern2.matcher(uri4).find();
			b = pattern2.matcher(uri5).find();
			b = pattern2.matcher(uri6).find();
			b = pattern2.matcher(uri7).find();
			
			b = pattern3.matcher(uri).find();
			b = pattern3.matcher(uri2).find();
			b = pattern3.matcher(uri3).find();
			b = pattern3.matcher(uri4).find();
			b = pattern3.matcher(uri5).find();
			b = pattern3.matcher(uri6).find();
			b = pattern3.matcher(uri7).find();
			
			b = pattern4.matcher(uri).find();
			b = pattern4.matcher(uri2).find();
			b = pattern4.matcher(uri3).find();
			b = pattern4.matcher(uri4).find();
			b = pattern4.matcher(uri5).find();
			b = pattern4.matcher(uri6).find();
			b = pattern4.matcher(uri7).find();
		}
		
		
	}
	
	
	@Test
	// 1.083S
	public void RegBench2()
	{


		boolean b;
		String uri = "/page/123";


		b = pattern5.matcher(uri).find();
		

	}
	
}
