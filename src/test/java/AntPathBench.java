import org.junit.Test;

public class AntPathBench
{
	private static boolean matchFiltersURL(String testPath, String requestPath)
	{

		if (testPath == null)
			return (false);

		// Case 1 - Exact Match
		if (testPath.equals(requestPath))
			return (true);

		// Case 2 - Path Match ("/.../*")
		if (testPath.equals("/*"))
			return (true);
		if (testPath.endsWith("/*"))
		{
			if (testPath.regionMatches(0, requestPath, 0, testPath.length() - 2))
			{
				if (requestPath.length() == (testPath.length() - 2))
				{
					return (true);
				}
				else if ('/' == requestPath.charAt(testPath.length() - 2))
				{
					return (true);
				}
			}
			return (false);
		}

		// Case 3 - Extension Match
		if (testPath.startsWith("*."))
		{
			int slash = requestPath.lastIndexOf('/');
			int period = requestPath.lastIndexOf('.');
			if ((slash >= 0) && (period > slash) && (period != requestPath.length() - 1) && ((requestPath.length() - period) == (testPath.length() - 1)))
			{
				return (testPath.regionMatches(2, requestPath, period + 1, testPath.length() - 2));
			}
		}

		// Case 4 - "Default" Match
		return (false); // NOTE - Not relevant for selecting filters

	}

	@Test
	// 1.083S
	public void RegBench()
	{
		String antPattern = "/page/*";
		String antPattern2 = "/*";
		String antPattern3 = "/myspace";
		String antPattern4 = "*.jsp";

		boolean b;
		String uri = "/page/123";
		String uri2 = "/page/*";
		String uri3 = "/pdfskl";
		String uri4 = "/myapp/page/65";
		String uri5 = "/myspace";
		String uri6 = "/myspace?username=jack";
		String uri7 = "/home.jsp";
		for (int i = 0; i < 5000000; i++)
		{
			b = matchFiltersURL(antPattern, uri);
			b = matchFiltersURL(antPattern, uri2);
			b = matchFiltersURL(antPattern, uri3);
			b = matchFiltersURL(antPattern, uri4);
			b = matchFiltersURL(antPattern, uri5);
			b = matchFiltersURL(antPattern, uri6);
			b = matchFiltersURL(antPattern, uri7);

			b = matchFiltersURL(antPattern2, uri);
			b = matchFiltersURL(antPattern2, uri2);
			b = matchFiltersURL(antPattern2, uri3);
			b = matchFiltersURL(antPattern2, uri4);
			b = matchFiltersURL(antPattern2, uri5);
			b = matchFiltersURL(antPattern2, uri6);
			b = matchFiltersURL(antPattern2, uri7);

			b = matchFiltersURL(antPattern3, uri);
			b = matchFiltersURL(antPattern3, uri2);
			b = matchFiltersURL(antPattern3, uri3);
			b = matchFiltersURL(antPattern3, uri4);
			b = matchFiltersURL(antPattern3, uri5);
			b = matchFiltersURL(antPattern3, uri6);
			b = matchFiltersURL(antPattern3, uri7);
			
			b = matchFiltersURL(antPattern4, uri);
			b = matchFiltersURL(antPattern4, uri2);
			b = matchFiltersURL(antPattern4, uri3);
			b = matchFiltersURL(antPattern4, uri4);
			b = matchFiltersURL(antPattern4, uri5);
			b = matchFiltersURL(antPattern4, uri6);
			b = matchFiltersURL(antPattern4, uri7);
		}

	}
	
	@Test
	// 1.083S
	public void AntBench2()
	{
		String antPattern = "/";


		boolean b;
		String uri = "/page/123";


		b = matchFiltersURL(antPattern, uri);
		

	}

}
