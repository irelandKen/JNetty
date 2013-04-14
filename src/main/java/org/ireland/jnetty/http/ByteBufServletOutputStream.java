package org.ireland.jnetty.http;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

import javax.servlet.ServletOutputStream;


public class ByteBufServletOutputStream extends ServletOutputStream
{
	private DefaultHttpServletResponse defaultHttpServletResponse;
	
	private ByteBuf out;

	public ByteBufServletOutputStream(DefaultHttpServletResponse defaultHttpServletResponse, ByteBuf out)
	{
		super();
		this.defaultHttpServletResponse = defaultHttpServletResponse;
		this.out = out;
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


    // -------------------------------------------------------- Package Methods


    /**
     * Clear facade.
     */
    void clear() {
        out = null;
    }


    // --------------------------------------------------- OutputStream Methods


    @Override
    public void write(int i)throws IOException 
    {
        out.writeByte(i);
    }


    @Override
    public void write(byte[] b, int off, int len)throws IOException 
    {
        out.writeBytes(b, off, len);
    }


    /**
     * Will send the buffer to the client.
     * 
     * Flush the buffer and commit this defaultHttpServletResponse.
     */
    @Override
    public void flush()throws IOException 
    {
    	defaultHttpServletResponse.flushBuffer();
    }


    
    @Override
    public void close()throws IOException 
    {
    	defaultHttpServletResponse.channel().close();
    }

}
