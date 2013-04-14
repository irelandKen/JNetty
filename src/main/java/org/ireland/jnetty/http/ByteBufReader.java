package org.ireland.jnetty.http;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.io.Reader;

public class ByteBufReader extends Reader
{
	private ByteBuf in;
	
	public ByteBufReader(ByteBuf in)
	{
		super();
		this.in = in;
	}

	@Override
	public int read(char[] cbuf, int off, int len) throws IOException
	{
		//in.read
		return 0;
	}

	@Override
	public void close() throws IOException
	{
		// TODO Auto-generated method stub

	}

}
