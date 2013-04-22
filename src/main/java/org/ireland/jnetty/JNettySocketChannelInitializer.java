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


import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ireland.jnetty.webapp.WebApp;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

public class JNettySocketChannelInitializer extends ChannelInitializer<SocketChannel> 
{
	private static final Log log = LogFactory.getLog(JNettySocketChannelInitializer.class.getName());
	
	private static final char SLASH = File.separatorChar;
	
	private static final WebApp webApp;
	
	private static final HttpHandler httpHandler;
	static 
	{	
    	//String rootDirectory = log.isDebugEnabled() ? System.getProperty("user.dir") + SLASH + "src" + SLASH + "main" + SLASH + "webapp" : System.getProperty("user.dir");
    	String rootDirectory = System.getProperty("user.dir");
    	
    	String host = "127.0.0.1";
    	
    	String contextPath = "";
    	
    	webApp = new WebApp(rootDirectory, host, contextPath);
    	
		webApp.init();
    	
    	webApp.start();
    	
    	httpHandler = new HttpHandler(webApp);
	}
	
    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline p = ch.pipeline();

        p.addLast("httpServerCodec", new HttpServerCodec());
        
        //HttpChunks  Aggregator
        p.addLast("aggregator", new HttpObjectAggregator(1048576));
        
        //Share The HttpHandler
        p.addLast("handler", httpHandler);
    }
}
