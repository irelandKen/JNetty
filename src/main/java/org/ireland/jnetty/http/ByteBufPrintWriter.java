/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ireland.jnetty.http;


import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;


/**
 * Coyote implementation of the servlet writer.
 *
 * @author Remy Maucherat
 */
public class ByteBufPrintWriter extends PrintWriter {

	private DefaultHttpServletResponse defaultHttpServletResponse;
	



    // ----------------------------------------------------- Instance Variables


    protected boolean error = false;


    // ----------------------------------------------------------- Constructors


    public ByteBufPrintWriter(DefaultHttpServletResponse defaultHttpServletResponse,String charsetName) throws UnsupportedEncodingException, IOException {
        super(new OutputStreamWriter(defaultHttpServletResponse.getOutputStream(), charsetName));
    }

    public ByteBufPrintWriter(DefaultHttpServletResponse defaultHttpServletResponse,Charset cs) throws IOException{
        super(new OutputStreamWriter(defaultHttpServletResponse.getOutputStream(), cs));
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


    /**
     * Recycle.
     */
    void recycle() {
        error = false;
    }


    // --------------------------------------------------------- Writer Methods


    @Override
    public void flush() {

        if (error) {
            return;
        }

        try {
            out.flush();
        } catch (IOException e) {
            error = true;
        }

    }


    @Override
    public void close() {

        // We don't close the PrintWriter - super() is not called,
        // so the stream can be reused. We close out.
        try {
            out.close();
        } catch (IOException ex ) {
            // Ignore
        }
        error = false;

    }


    @Override
    public boolean checkError() {
        flush();
        return error;
    }


    @Override
    public void write(int c) {

        if (error) {
            return;
        }

        try {
            out.write(c);
        } catch (IOException e) {
            error = true;
        }

    }


    @Override
    public void write(char buf[], int off, int len) {

        if (error) {
            return;
        }

        try {
            out.write(buf, off, len);
        } catch (IOException e) {
            error = true;
        }

    }




    @Override
    public void write(String s, int off, int len) {

        if (error) {
            return;
        }

        try {
            out.write(s, off, len);
        } catch (IOException e) {
            error = true;
        }

    }


    // ---------------------------------------------------- PrintWriter Methods








    @Override
    public void print(Object obj) {
        write(String.valueOf(obj));
    }


    @Override
    public void println(boolean b) {
        print(b);
        println();
    }


    @Override
    public void println(char c) {
        print(c);
        println();
    }


    @Override
    public void println(int i) {
        print(i);
        println();
    }


    @Override
    public void println(long l) {
        print(l);
        println();
    }


    @Override
    public void println(float f) {
        print(f);
        println();
    }


    @Override
    public void println(double d) {
        print(d);
        println();
    }


    @Override
    public void println(char c[]) {
        print(c);
        println();
    }


    @Override
    public void println(String s) {
        print(s);
        println();
    }


    @Override
    public void println(Object o) {
        print(o);
        println();
    }


}
