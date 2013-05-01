/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.ireland.jnetty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.ServerCookieEncoder;
import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.ServletException;

import org.ireland.jnetty.http.HttpServletRequestImpl;
import org.ireland.jnetty.http.HttpServletResponseImpl;
import org.ireland.jnetty.webapp.RequestDispatcherImpl;
import org.ireland.jnetty.webapp.WebApp;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

/**
 * HttpHandler是无状态的,故标记为@Sharable,可让所有ChannelPipeline共享
 * 
 * @author KEN
 * 
 */
@Sharable
public class HttpHandler extends ChannelInboundMessageHandlerAdapter<FullHttpMessage>
{
	private final WebApp webApp;

	public HttpHandler(WebApp webApp)
	{
		this.webApp = webApp;
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, FullHttpMessage message) throws Exception
	{
		if (message instanceof FullHttpRequest)
		{
			FullHttpRequest request = (FullHttpRequest) message;

			if (is100ContinueExpected(request))
			{
				send100Continue(ctx);
			}

			FullHttpResponse response = new DefaultFullHttpResponse(request.getProtocolVersion(), OK, Unpooled.buffer(0));

			handle(ctx, request, response);

			// flush(ctx, request,response); need flush??
		}
	}

	private void handle(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, FullHttpResponse fullHttpResponse) throws ServletException, IOException
	{
		HttpServletResponseImpl response = new HttpServletResponseImpl(webApp,(SocketChannel) ctx.channel(), ctx, fullHttpResponse, fullHttpRequest);

		HttpServletRequestImpl request = new HttpServletRequestImpl(webApp, webApp, (SocketChannel) ctx.channel(), ctx, fullHttpResponse, fullHttpRequest,response);
		
		response.setHttpServletRequest(request);

		//
		String rawUri = fullHttpRequest.getUri();

		dispatch(rawUri, request, response);
	}

	/**
	 * 
	 * @param rawContextUri
	 *            带参数的uri(带参数(?))
	 * @param request
	 * @param response
	 * @throws IOException
	 * @throws ServletException
	 */
	private void dispatch(String rawContextUri, HttpServletRequestImpl request, HttpServletResponseImpl response) throws ServletException, IOException
	{

		RequestDispatcherImpl dispatcher = webApp.getRequestDispatcher(rawContextUri);

		dispatcher.dispatch(request, response);
	}


	private static void send100Continue(ChannelHandlerContext ctx)
	{
		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, CONTINUE);
		ctx.nextOutboundMessageBuffer().add(response);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
	{
		cause.printStackTrace();
		ctx.close();
	}
}
