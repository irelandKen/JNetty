package myServlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class FirstServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		System.out.println("1st Servlet.");
		
		resp.setContentType("text/html");
		resp.setCharacterEncoding("utf-8");
		
		PrintWriter out = resp.getWriter();
		out.println("<!DOCTYPE html><html><body>");
		out.println("<h1 style=\"color: red; text-align: center;\">Fanstay!It go Well!</h1>");
		
		out.println("<h2 style=\"color: blue; text-align: center;\">Power by KEN</h2>");
		out.println("</body></html>");
		
		out.flush();
	}
}
