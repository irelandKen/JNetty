package org.ireland.jnetty.http;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

import javax.servlet.ServletInputStream;

/**
 * 
 * Wrape ByteBuf as a ServletInputStream
 * 
 * @author KEN
 *
 */

public class ByteBufServletInputStream extends ServletInputStream
{
	private ByteBuf in;

	public ByteBufServletInputStream(ByteBuf in)
	{
		super();
		this.in = in;
	}


    // --------------------------------------------------------- Public Methods


    /**
     * Prevent cloning the facade.
     */
    @Override
    protected Object clone()
        throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }


    // --------------------------------------------- ServletInputStream Methods
    
	@Override
	public int read() throws IOException
	{
		return in.readByte();
	}


	@Override
	public int available() throws IOException
	{
		return in.readableBytes();
	}

}
