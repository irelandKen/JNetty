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

import org.ireland.jnetty.webapp.WebApp;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

public class HttpSnoopSocketChannelInitializer extends ChannelInitializer<SocketChannel> 
{
	private static WebApp webApp;
	
	static 
	{
    	
    	String rootDirectory = System.getProperty("user.dir");
    	
    	String host = "127.0.0.1";
    	
    	String contextPath = "ROOT";
    	
    	webApp = new WebApp(rootDirectory, host, contextPath);
    	
    	try
		{
			webApp.init();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
    	
    	webApp.parseWebXml();
    	
    	webApp.start();
	}
	
    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline p = ch.pipeline();

        p.addLast("httpServerCodec", new HttpServerCodec());
        
        //HttpChunks  Aggregator
        p.addLast("aggregator", new HttpObjectAggregator(1048576));
        
        p.addLast("handler", new HttpHandler(webApp));
    }
}
