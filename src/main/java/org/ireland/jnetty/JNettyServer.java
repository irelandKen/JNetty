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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.aio.AioEventLoopGroup;
import io.netty.channel.socket.aio.AioServerSocketChannel;

/**
 * An HTTP server that sends back the content of the received HTTP request in a pretty plaintext form.
 */
public class JNettyServer
{

	public static final String HOST = "127.0.0.1";

	private static int PORT = 80;

	public void run() throws Exception
	{
		// Configure the server.
		EventLoopGroup bossGroup = new AioEventLoopGroup(1);
		EventLoopGroup workerGroup = new AioEventLoopGroup();
		try
		{
			ServerBootstrap bootstrap = new ServerBootstrap();
			
			bootstrap.group(bossGroup, workerGroup)
			         .channel(AioServerSocketChannel.class)
			         .childHandler(new JNettySocketChannelInitializer());

			Channel ch = bootstrap.bind(HOST, PORT).sync().channel();
			ch.closeFuture().sync();
		}
		finally
		{
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}

	public static void main(String[] args) throws Exception
	{
        if (args.length > 0) {
        	PORT = Integer.parseInt(args[0]);
        } 

		new JNettyServer().run();
	}
	
}
