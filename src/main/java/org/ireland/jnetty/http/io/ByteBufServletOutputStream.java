package org.ireland.jnetty.http.io;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

import javax.servlet.ServletOutputStream;

import org.ireland.jnetty.http.HttpServletResponseImpl;


public class ByteBufServletOutputStream extends ServletOutputStream
{
	private HttpServletResponseImpl httpServletResponseImpl;
	
	private ByteBuf out;

	public ByteBufServletOutputStream(HttpServletResponseImpl httpServletResponseImpl, ByteBuf out)
	{
		super();
		this.httpServletResponseImpl = httpServletResponseImpl;
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
     * Flush the buffer and commit this httpServletResponseImpl.
     */
    @Override
    public void flush()throws IOException 
    {
    	httpServletResponseImpl.flushBuffer();
    }


    
    @Override
    public void close()throws IOException 
    {
    	httpServletResponseImpl.channel().close();
    }

}
