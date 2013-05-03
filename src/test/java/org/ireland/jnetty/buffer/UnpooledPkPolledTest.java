package org.ireland.jnetty.buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 模拟HttpServletResponseImpl的输出测试
 * @author KEN
 *
 */
public class UnpooledPkPolledTest
{
	//假设数据: 128Byte 一行
	private static byte[] src = "<a id='nav-shop-all-button' href='/gp/site-directory' class='nav_a nav-button-outer nav-menu-inactive' alt='全部商品分类'>".getBytes();
	
	//10K 个请求每秒
	private static int RPS = 10 * 1000;
	
	//模拟10次
	private static int LOOP = 10;
	
	/**
	 * UnpooledHeapByteBuf: 内部用byte[]来存放数据,当当前byte数组长度不足以存放 待写入的数据时,
	 * 则新建一个长度为原来byte数据长度2部的数组,然后利用 System.arraycopy(...)复制数据,
	 * 会造成性能下降 和 增加GC压力 
	 * 
	 * 128B >> 256B >> 512B >> 1KB >> 2KB >> 4KB >> 8KB >> 16KB >> 32KB >> 64KB >> 128KB >> 256KB
	 * 
	 * 逐行输入的方式来输出一个256KB的页面,其中在新建 和 数组复制的操作 达 12次 ....
	 * 
	 * 
	 */

	@BeforeClass
	public static void init()
	{
		Unpooled.buffer(0);
		PooledByteBufAllocator.DEFAULT.directBuffer(0);
	}
	
	@Test//24.288S
	public void test_UnpooledHeapByteBuf()
	{
		long start = System.currentTimeMillis();
		
		for(int i=0; i<RPS * LOOP; i++)
		{
			ByteBuf byteBuf = Unpooled.buffer(0);

			writeHtmlPage(byteBuf);
		}
		
		long ms = System.currentTimeMillis() - start;
		
		System.out.println("UnpooledHeapByteBuf:每10K请求耗时: "+ms/(RPS) + "(ms)");
		//每10K请求耗时: 2(ms)
	}
	
	/**
	 * PooledUnsafeDirectByteBuf: 内部预分配的DirectByteBuffer 数据块 来存放数据
	 * 
	 * 128B >> 256B >> 512B >> 1KB >> 2KB >> 4KB >> 8KB >> 16KB >> 32KB >> 64KB >> 128KB >> 256KB
	 * 
	 * 逐行输入的方式来输出一个256KB的页面,其中PlatformDependent.copyMemory 进行 复制的操作 达 12次 ....
	 * 
	 * PlatformDependent.copyMemory
	 * 
	 * 尽管和UnpooledHeapByteBuf一样要进行多次的数据复制,但PooledUnsafeDirectByteBuf的数据空间是预先开辟的,是池化的,
	 * 而UnpooledHeapByteBuf是未池化的,要用到的时候才开辟,性能较差
	 * 
	 * TODO: 能否开发出 (池化+组合)的ByteBuf,免去大量的中间数据的复制开销??
	 * 
	 */
	@Test//18.335s
	public void test_PooledUnsafeDirectByteBuf()
	{
		long start = System.currentTimeMillis();
		
		for(int i=0; i<RPS * LOOP; i++)
		{
			ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.directBuffer(0);

			writeHtmlPage(byteBuf);
		}
		
		long ms = System.currentTimeMillis() - start;
		
		System.out.println("PooledUnsafeDirectByteBuf:每10K请求耗时: "+ms/(RPS) + "(ms)");
		//每10K请求耗时: 2(ms)
	}
	
	@Test//18.968s
	public void test_PooledHeapByteBuf()
	{
		long start = System.currentTimeMillis();
		
		for(int i=0; i<RPS * LOOP; i++)
		{
			ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.heapBuffer(0);

			writeHtmlPage(byteBuf);
		}
		
		long ms = System.currentTimeMillis() - start;
		
		System.out.println("PooledHeapByteBuf:每10K请求耗时: "+ms/(RPS) + "(ms)");
		//每10K请求耗时: 2(ms)
	}
	
	/**
	 * 页面大小参考京东: 256KB = 128Byte/行 * 2048行
	 * @param byteBuf
	 */
	public void writeHtmlPage(ByteBuf byteBuf)
	{
		for(int j = 0; j<2048; j++)
			byteBuf.writeBytes(src);
		
		byteBuf.release();
	}

}
