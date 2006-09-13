/////////////////////////////////////////////////////////////////////////////
// This file is part of the "Server4" project, a Java implementation of the
// OPeNDAP Data Access Protocol.
//
// Copyright (c) 2005 OPeNDAP, Inc.
// Author: Nathan David Potter  <ndp@opendap.org>
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// You can contact OPeNDAP, Inc. at PO Box 112, Saunderstown, RI. 02874-0112.
/////////////////////////////////////////////////////////////////////////////

package opendap.niotest;


import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Date;

/**
 * User: ndp
 * Date: Sep 5, 2006
 * Time: 3:47:22 PM
 */
public class NioServlet extends HttpServlet {


    private int maxChunkSize = 8192;


    public void init() throws ServletException {
        System.out.println("NioServlet loaded.");



    }


    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {


        String path = request.getPathInfo();



        String msg = "NOOP";


        Date startTime = new Date();
        Date endTime   = startTime;


        if(path == null){

        }else if(path.equals("/nio") || path.equals("/nio/")){

            msg = ("NIOREAD");
            startTime = new Date();
            doNIO(request,response);
            endTime = new Date();
        }
        else if(path.equals("/block") || path.equals("/block/")){

            msg = ("BLOCKREAD");
            startTime = new Date();
            doBLOCK(request,response);
            endTime = new Date();
        }

        else if(path.equals("/byte") || path.equals("/byte/")){

            msg = ("BYTEREAD");
            startTime = new Date();
            doBYTE(request,response);
            endTime = new Date();
        }



        long elapsed = endTime.getTime() - startTime.getTime();

        System.out.println(msg + "_Elapsed_Time: "+elapsed+" ms");






    }












    public void doNIO(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {




        byte[] chunkArray   = new byte[4];
        ByteBuffer chunk    = ByteBuffer.wrap(chunkArray);

        byte[] crlfArray     = new byte[2];
        ByteBuffer crlf      = ByteBuffer.wrap(crlfArray);


        byte[] dataArray    = new byte[maxChunkSize];
        ByteBuffer data     = ByteBuffer.wrap(dataArray);

        ByteBuffer send = ByteBuffer.wrap(("send\r\n").getBytes());




        ServletOutputStream os = response.getOutputStream();

        response.setContentType("image/jpeg");
        //response.setContentType("text/ascii");
        response.setHeader("Content-Description", "My Big Picture");

        SocketChannel sc = SocketChannel.open(new InetSocketAddress("localhost",10007));


        sc.configureBlocking(true);

        sc.write(send);

        boolean moreData = true;
        while(moreData){
            chunk.clear();
            crlf.clear();

            sc.read(chunk);
            sc.read(crlf);

            int chunkSize = Integer.valueOf(new String(chunkArray),16);

            //System.out.println("chunkSize: "+chunkSize);

            if(chunkSize == 0) {
                moreData = false;
            }
            else {

                data.clear();
                data.limit(chunkSize);

                int count=0;

                boolean done = false;
                while(!done){
                    count += sc.read(data);
                    if(count == chunkSize)
                       done = true;
                    //System.out.println("count: "+count);
                }

                os.write(data.array(),0,chunkSize);
                crlf.clear();
                sc.read(crlf);
            }
        }
        //System.out.println("Closing connections, flushing buffers, etc...");
        os.flush();
        sc.close();
        response.setStatus(200);
    }




    public void doBLOCK(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {




        byte[] chunk  = new byte[   4];
        byte[] crlf   = new byte[   2];
        byte[] data   = new byte[maxChunkSize];

        byte[] send = ("send\r\n").getBytes();


        ServletOutputStream os = response.getOutputStream();

        response.setContentType("image/jpeg");
        //response.setContentType("text/ascii");
        response.setHeader("Content-Description", "My Big Picture");

        Socket sc = new Socket();
        sc.connect(new InetSocketAddress("localhost",10007));

        OutputStream sourceOS = sc.getOutputStream();
        InputStream  sourceIS = sc.getInputStream();




        //System.out.println("sc.write(send) wrote: "+sc.write(send)+" bytes.");
        sourceOS.write(send);


        boolean moreData = true;
        while(moreData){
            completeRead(sourceIS,chunk);
            completeRead(sourceIS,crlf);

            int chunkSize = Integer.valueOf(new String(chunk),16);

            //System.out.println("chunkSize: "+chunkSize);

            if(chunkSize == 0) {
                moreData = false;
            }
            else {


                completeRead(sourceIS,data,0,chunkSize);

                os.write(data,0,chunkSize);

                completeRead(sourceIS,crlf);


            }

        }

        //System.out.println("Closing connections, flushing buffers, etc...");
        os.flush();
        sc.close();
        response.setStatus(200);




    }
    public void doBYTE(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {




        byte[] chunk  = new byte[   4];
        byte[] crlf   = new byte[   2];

        byte[] send = ("send\r\n").getBytes();


        ServletOutputStream os = response.getOutputStream();

        response.setContentType("image/jpeg");
        //response.setContentType("text/ascii");
        response.setHeader("Content-Description", "My Big Picture");

        Socket sc = new Socket();
        sc.connect(new InetSocketAddress("localhost",10007));

        OutputStream sourceOS = sc.getOutputStream();
        InputStream  sourceIS = sc.getInputStream();




        //System.out.println("sc.write(send) wrote: "+sc.write(send)+" bytes.");
        sourceOS.write(send);


        int i;
        boolean moreData = true;
        while(moreData){
            byteRead(sourceIS,chunk);
            byteRead(sourceIS,crlf);

            int chunkSize = Integer.valueOf(new String(chunk),16);

            //System.out.println("chunkSize: "+chunkSize);

            if(chunkSize == 0) {
                moreData = false;
            }
            else {


                for(i=0; i<chunkSize; i++){

                    os.write(sourceIS.read());

                }

                byteRead(sourceIS,crlf);


            }

        }

        //System.out.println("Closing connections, flushing buffers, etc...");
        os.flush();
        sc.close();
        response.setStatus(200);




    }



    public void byteRead(InputStream is, byte[] data) throws IOException {

        for(int i=0; i<data.length; i++){
            data[i] = (byte) is.read();
        }



    }

    public void completeRead(InputStream is, byte[] data) throws IOException {

        completeRead(is,data,0,data.length);



    }


    public void completeRead(InputStream is, byte[] data, int offset, int length) throws IOException {

        int readCount=0;


        while(readCount < length){
            readCount += is.read(data,offset+readCount,length-readCount);
        }



    }



}