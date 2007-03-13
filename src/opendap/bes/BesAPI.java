/////////////////////////////////////////////////////////////////////////////
// This file is part of the "OPeNDAP 4 Data Server (aka Hyrex)" project.
//
//
// Copyright (c) 2006 OPeNDAP, Inc.
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


package opendap.bes;

import opendap.coreServlet.Debug;

import opendap.ppt.OPeNDAPClient;
import opendap.ppt.PPTException;

import java.io.*;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;

import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.Element;
import org.jdom.filter.ElementFilter;
import org.jdom.input.SAXBuilder;
import org.slf4j.Logger;


/**
 * BES transaction code and client pool management are contained  in this class.
 * All of the methods are static, but the class is not stateless, as it
 * maintains an internal pool of client connections to the BES. Since all of the
 * BES transaction code for the OLFS is wrapped in this class, future
 * optimizations should be easier... (right?)
 */
public class BesAPI {


    public static String XML_ERRORS  = "xml";
    public static String DAP2_ERRORS = "dap2";



    private static Logger log;
    private static int _besPort = -1;
    private static String _besHost = "Not Configured!";
    private static boolean _configured = false;
    private static final Object syncLock = new Object();

    private static ArrayBlockingQueue<OPeNDAPClient> _clientQueue;
    private static Semaphore _checkOutFlag;
    private static int _maxClients;

    private static DevNull devNull = new DevNull();

    /**
     * The name of the BES Exception Element.
     */
    private static String BES_EXCEPTION = "BESException";

//------------------------------------------------------------------------------
//-------------------------- CLIENT POOL CODE ----------------------------------
//------------------------------------------------------------------------------


    /**
     * The pool of available OPeNDAPClient connections starts empty. When this
     * method is called the pool is checked. If no client is available, and the
     * number of clients has not reached the cap, then a new one is made,
     * started, and returned. If no client is available and the cap has been
     * reached then this method will BLOCK until a client becomes available.
     * If a client is available, it is returned.
     *
     * @return The next available OPeNDAPClient.
     * @throws PPTException .
     * @throws BadConfigurationException .
     */
    private static OPeNDAPClient getClient()
            throws PPTException, BadConfigurationException {

        OPeNDAPClient odc=null;

        try {
            _checkOutFlag.acquire();

            if (_clientQueue.size() == 0) {
                odc = new OPeNDAPClient();
                log.debug("getClient() - " +
                            "Made new OPeNDAPClient. Starting...");

                odc.startClient(getHost(), getPort());
                log.debug("OPeNDAPClient started.");


            } else {

                odc = _clientQueue.take();
                log.debug("getClient() - Retrieved " +
                            "OPeNDAPClient from queue.");
            }

            //if (Debug.isSet("BES"))
            //    odc.setOutput(System.out, true);
            //else
                odc.setOutput(devNull, true);



            //odc.isProperlyConnected();
            //odc.setOutput(devNull, false);
            //odc.executeCommand("show status;");




            return odc;
        }
        catch (Exception e) {
            log.error("ERROR encountered.");
            discardClient(odc);
            throw new PPTException(e);
        }

    }

    /**
     * When a piece of code is done using an OPeNDAPClient, it should return it
     * to the pool using this method.
     *
     * @param odc The OPeNDAPClient to return to the client pool.
     * @throws PPTException .
     */
    private static void returnClient(OPeNDAPClient odc) throws PPTException {

        try {


            String cmd = "delete definitions;\n";
            odc.executeCommand(cmd);

            cmd = "delete containers;\n";
            odc.executeCommand(cmd);


            odc.setOutput(null, false);

            _clientQueue.put(odc);
            _checkOutFlag.release();
            log.debug("Returned OPeNDAPClient to queue.");
        }
        catch (InterruptedException e) {
            e.printStackTrace(); // Don't do a thing
        } catch (PPTException e) {


            String msg = "\n*** BesAPI - WARNING! Problem with " +
                         "OPeNDAPClient, discarding.";

            discardClient(odc);

            throw new PPTException(msg, e);
        }

    }



    private static void discardClient(OPeNDAPClient odc){
        if(odc != null && odc.isRunning()){
            try {
                shutdownClient(odc);
            } catch (PPTException e) {
                log.debug("BesAPI: Discarding client " +
                            "encountered problems shutting down an " +
                            "OPeNDAPClient connection to the BES\n");

            }
        }
        // By releasing the flag and not checking the OPeNDAPClient back in
        // we essentially throw the client away. A new one will be made
        // the next time it's needed.
        _checkOutFlag.release();

    }



    /**
     * This method is meant to be called at program exit. It waits until all
     * clients are checked into the pool and then gracefully shuts down each
     * client's connection to the BES.
     */
    public static void shutdownBES() {


        try {

            log.debug("shutdownBES() - " +
                    "Waiting for BES client check in to complete.");
            _checkOutFlag.acquireUninterruptibly(_maxClients);
            log.debug(" All clients checked in.");

            log.debug("BesAPI.shutdownBES() - " + _clientQueue.size() +
                    " client(s) to shutdown.");


            int i = 0;
            while (_clientQueue.size() > 0) {
                OPeNDAPClient odc = _clientQueue.take();
                log.debug("Retrieved OPeNDAPClient["
                            + i++ + "] from queue.");

                shutdownClient(odc);


            }

        } catch (InterruptedException e) {
            e.printStackTrace(); // Do nothing
        } catch (PPTException e) {
            e.printStackTrace();  // Do nothing..
        }


    }

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------


    /**
     * Configures the BES. The BES may configured ONCE. Subsequent calls to
     * configure() will be ignored.
     *
     * @param host       The host name/ip of the BES
     * @param port       The port on which the BES is listening
     * @param maxClients The maximum number of concurrent client connections
     *                   that will be allowed to the BES.
     * @return False if configure() mas been called previously, True otherwise.
     */
    public static boolean configure(String host, int port, int maxClients) {

        synchronized (syncLock) {

            if (isConfigured())
                return false;

            _besHost = host;
            _besPort = port;
            _maxClients = maxClients;

            _clientQueue = new ArrayBlockingQueue<OPeNDAPClient>(_maxClients);
            _checkOutFlag = new Semaphore(_maxClients);

            _configured = true;

            log = org.slf4j.LoggerFactory.getLogger(BesAPI.class);

            log.debug("BES is configured - Host: " + _besHost
                    + "   Port: " + _besPort);


        }

        return true;

    }



    /**
     * Configures the BES using the passed BESConfig object. The BES may
     * configured ONCE. Subsequent calls to configure() will be ignored.
     *
     * @param bc .
     * @return False if configure() mas been called previously, True otherwise.
     */
    public static boolean configure(BESConfig bc) {

        return configure(bc.getBESHost(),bc.getBESPort(),bc.getBESMaxClients());

    }

    /**
     * @return True is the BES had been configured. False otherwise.
     */
    public static boolean isConfigured() {
        return _configured;
    }


    /**
     * @return The host/ip of the BES.
     * @throws BadConfigurationException If the BES has not been configured
     *                                   prior to calling this method.
     */
    public static String getHost() throws BadConfigurationException {
        if (!isConfigured())
            throw new BadConfigurationException("BES must be configured " +
                    "before use!\n");

        return _besHost;
    }

    /**
     * @return The port number of the BES.
     * @throws BadConfigurationException If the BES has not been configured
     *                                   prior to calling this method.
     */
    public static int getPort() throws BadConfigurationException {
        if (!isConfigured())
            throw new BadConfigurationException("BES must be configured " +
                    "before use!\n");
        return _besPort;
    }


    /**
     * Writes an OPeNDAP DDX for the dataSource to the passed stream.
     *
     * @param dataSource           The requested DataSource
     * @param constraintExpression The constraint expression to be applied to
     * the request..
     * @param os                   The Stream to which to write the response.
     * @param errorMsgFormat The message format scheme for BES generated errors.
     * @throws BadConfigurationException .
     * @throws PPTException .
     */
    public static void writeDDX(String dataSource,
                                String constraintExpression,
                                OutputStream os,
                                String errorMsgFormat)
            throws BadConfigurationException, PPTException {

        besGetTransaction(
                getAPINameForDDX(),
                dataSource,
                constraintExpression,
                os,
                errorMsgFormat);
    }


    public static Document getDDXDocument(String dataSource,
                                          String constraintExpression)
            throws PPTException,
            BadConfigurationException,
            IOException,
            JDOMException,
            BESException {

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        besGetTransaction(
                getAPINameForDDX(),
                dataSource,
                constraintExpression,
                os,
                "xml");

        SAXBuilder sb = new SAXBuilder();


        log.debug("getDDXDocument got this array:\n" +
                    os.toString());

        Document ddx = sb.build(new ByteArrayInputStream(os.toByteArray()));

        // Check for an exception:
        besExceptionHandler(ddx);


        return ddx;
    }


    /**
     * Writes an OPeNDAP DDS for the dataSource to the passed stream.
     *
     * @param dataSource           The requested DataSource
     * @param constraintExpression The constraint expression to be applied to
     * the request..
     * @param os                   The Stream to which to write the response.
     * @param errorMsgFormat The message format scheme for BES generated errors.
     * @throws BadConfigurationException .
     * @throws PPTException .
     */
    public static void writeDDS(String dataSource,
                                String constraintExpression,
                                OutputStream os,
                                String errorMsgFormat)
            throws BadConfigurationException, PPTException {

        besGetTransaction(
                getAPINameForDDS(),
                dataSource,
                constraintExpression,
                os,
                errorMsgFormat);
    }


    /**
     * Writes the source data (it is often a file, thus the method name) to
     * the passed stream.
     *
     * @param dataSource The requested DataSource
     * @param os         The Stream to which to write the response.
     * @param errorMsgFormat The message format scheme for BES generated errors.
     * @throws BadConfigurationException .
     * @throws PPTException .
     */
    public static void writeFile(String dataSource,
                                 OutputStream os,
                                 String errorMsgFormat)
            throws BadConfigurationException, PPTException {

        boolean trouble = false;

        OPeNDAPClient oc = getClient();

        try {
            configureTransaction(oc, dataSource, null, "stream",errorMsgFormat);

            getDataProduct(oc, getAPINameForStream(), os);
        }
        catch (PPTException e){
            trouble = true;
            throw new PPTException("BesAPI.writeFile(): " +
                    "Problem with OPeNDAPClient.\n");
        }
        finally{
            if (trouble) {
                discardClient(oc);
            }
            else {
                returnClient(oc);
            }
        }
    }


    /**
     * Writes an OPeNDAP DAS for the dataSource to the passed stream.
     *
     * @param dataSource           The requested DataSource
     * @param constraintExpression The constraint expression to be applied to
     * the request..
     * @param os                   The Stream to which to write the response.
     * @param errorMsgFormat The message format scheme for BES generated errors.
     * @throws BadConfigurationException .
     * @throws PPTException .
     */
    public static void writeDAS(String dataSource,
                                String constraintExpression,
                                OutputStream os,
                                String errorMsgFormat)
            throws BadConfigurationException, PPTException {

        besGetTransaction(
                getAPINameForDAS(),
                dataSource,
                constraintExpression,
                os,
                errorMsgFormat);
    }

    /**
     * Writes an OPeNDAP DAP2 data response for the dataSource to the
     * passed stream.
     *
     * @param dataSource           The requested DataSource
     * @param constraintExpression The constraint expression to be applied to
     * the request..
     * @param os                   The Stream to which to write the response.
     * @param errorMsgFormat The message format scheme for BES generated errors.
     * @throws BadConfigurationException .
     * @throws PPTException .
     */
    public static void writeDap2Data(String dataSource,
                                     String constraintExpression,
                                     OutputStream os,
                                     String errorMsgFormat)
            throws BadConfigurationException, PPTException {

        besGetTransaction(
                getAPINameForDODS(),
                dataSource,
                constraintExpression,
                os,
                errorMsgFormat);
    }


    /**
     * Writes the ASCII representation os the  OPeNDAP data response for the
     * dataSource to the passed stream.
     *
     * @param dataSource           The requested DataSource
     * @param constraintExpression The constraint expression to be applied to
     * the request..
     * @param os                   The Stream to which to write the response.
     * @param errorMsgFormat The message format scheme for BES generated errors.
     * @throws BadConfigurationException .
     * @throws PPTException .
     */
    public static void writeASCII(String dataSource,
                                  String constraintExpression,
                                  OutputStream os,
                                  String errorMsgFormat)
            throws BadConfigurationException, PPTException {

        besGetTransaction(
                getAPINameForASCII(),
                dataSource,
                constraintExpression,
                os,
                errorMsgFormat);
    }


    /**
     * Writes the HTML data request form (aka the I.F.H.) for the OPeNDAP the
     * dataSource to the passed stream.
     *
     * @param dataSource The requested DataSource
     * @param url        The URL to refernence in the HTML form.
     * @param os         The Stream to which to write the response.
     * @throws BadConfigurationException .
     * @throws PPTException .
     */
    public static void writeHTMLForm(String dataSource,
                                     String url,
                                     OutputStream os)
            throws BadConfigurationException, PPTException {


        boolean trouble = false;

        OPeNDAPClient oc = getClient();

        try {
            configureTransaction(oc, dataSource, null, DAP2_ERRORS);

            String cmd = "get " + getAPINameForHTMLForm() +
                    " for d1 using " + url + ";\n";

            log.debug("Sending command: " + cmd);

            oc.setOutput(os, false);
            oc.executeCommand(cmd);

        }
        catch (PPTException e){
            trouble = true;
            throw new PPTException("BesAPI.writeHTMLForm(): " +
                    "Problem with OPeNDAPClient.\n");
        }
        finally{
            if (trouble) {
                discardClient(oc);
            }
            else {
                returnClient(oc);
            }
        }
    }


    /**
     * Writes the OPeNDAP INFO response for the dataSource to the passed
     * stream.
     *
     * @param dataSource The requested DataSource
     * @param os         The Stream to which to write the response.
     * @param errorMsgFormat The message format scheme for BES generated errors.
     * @throws BadConfigurationException .
     * @throws PPTException .
     */
    public static void writeINFOPage(String dataSource,
                                     OutputStream os,
                                     String errorMsgFormat)
            throws BadConfigurationException, PPTException {

        besGetTransaction(
                getAPINameForINFOPage(),
                dataSource,
                null,
                os,
                errorMsgFormat);
    }


    /**
     * Returns an InputStream that holds an OPeNDAP DAP2 data for the requested
     * dataSource. The DDS header is stripped, so the InputStream holds ONLY
     * the XDR encoded binary data.
     *
     * @param dataSource           The requested DataSource
     * @param constraintExpression .
     * @param errorMsgFormat .
     * @return A DAP2 data stream, no DDS just the XDR encoded binary data.
     * @throws BadConfigurationException .
     * @throws PPTException .
     * @throws IOException .
     */
    public static InputStream getDap2DataStream(String dataSource,
                                                String constraintExpression,
                                                String errorMsgFormat)
            throws BadConfigurationException, PPTException, IOException {

        //@todo Make this more efficient by adding support to the BES that reurns this stream. Caching the resposnse in memory is a BAD BAD thing.

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        writeDap2Data(dataSource, constraintExpression, baos, errorMsgFormat);

        InputStream is = new ByteArrayInputStream(baos.toByteArray());

        HeaderInputStream his = new HeaderInputStream(is);

        boolean done = false;
        int val;
        while (!done) {
            val = his.read();
            if (val == -1)
                done = true;

        }

        return is;

    }


    /**
     * Returns an the version document for the BES.
     *
     * @return The version Document
     * @throws BadConfigurationException .
     * @throws PPTException .
     * @throws IOException .
     * @throws JDOMException .
     * @throws BESException .
     */
    public static Document showVersion() throws
            BadConfigurationException,
            PPTException,
            IOException,
            JDOMException,
            BESException {

        // Get the version response from the BES (an XML doc)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        besShowTransaction("version", baos);

        log.debug("BES returned this document:\n"+
                "-----------\n" + baos +"-----------");


        // Parse the XML doc into a Document object.
        SAXBuilder sb = new SAXBuilder();
        Document doc = sb.build(new ByteArrayInputStream(baos.toByteArray()));

        // Check for an exception:
        besExceptionHandler(doc);

        // Tweak it!

        // First find the response Element
        Element ver = doc.getRootElement().getChild("response");

        // Disconnect it from it's parent and then rename it.
        ver.detach();
        ver.setName("OPeNDAP-Version");

        doc.detachRootElement();
        doc.setRootElement(ver);

        return doc;
    }


    /**
     * Returns the BES INFO document for the spcified dataSource.
     *
     * @param dataSource .
     * @return The BES info document, stripped of it's <response> parent.
     * @throws PPTException .
     * @throws BadConfigurationException .
     * @throws IOException .
     * @throws JDOMException .
     * @throws BESException .
     */
    public static Document getInfoDocument(String dataSource) throws
            PPTException,
            BadConfigurationException,
            IOException,
            JDOMException,
            BESException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        String product = "info for " + "\"" + dataSource + "\"";

        log.debug("Sending BES cmd: show " +
                    product);


        BesAPI.besShowTransaction(product, baos);


        log.debug("BES returned this document:\n" +
                    "-----------\n" + baos +"-----------");


        // Parse the XML doc into a Document object.
        SAXBuilder sb = new SAXBuilder();
        Document doc = sb.build(new ByteArrayInputStream(baos.toByteArray()));

        // Check for an exception:
        besExceptionHandler(doc);

        // Prepare the response:

        // First find the response Element

        Element topDataset =
                doc.getRootElement().getChild("response").getChild("dataset");

        // Disconnect it from it's parent and then rename it.
        topDataset.detach();
        doc.detachRootElement();
        doc.setRootElement(topDataset);

        return doc;

    }


    /**
     * Look for and process an Exception in the response from the BES.
     *
     * @param doc .
     * @throws BESException .
     */
    private static void besExceptionHandler(Document doc) throws BESException {


        Element exception;
        String msg = "";

        ElementFilter exceptionFilter = new ElementFilter(BES_EXCEPTION);
        Iterator i = doc.getDescendants(exceptionFilter);
        if (i.hasNext()) {
            int j = 0;
            while (i.hasNext()) {
                if (j > 0)
                    msg += "\n";
                exception = (Element) i.next();
                msg += makeBesExceptionMsg(exception, j++);
            }

            throw new BESException(msg);
        }

    }

    private static String makeBesExceptionMsg(Element exception, int number) {
        String msg = "";

        msg += "[";
        msg += "[BESException: " + number + "]";
        msg += "[Type: " + exception.getChild("Type").getTextTrim() + "]";
        msg += "[Message: " + exception.getChild("Message").getTextTrim() + "]";
        msg += "[Location: ";
        msg += exception.getChild("Location").getChild("File").getTextTrim()
                + " line ";
        msg += exception.getChild("Location").getChild("Line").getTextTrim()
                + "]";
        msg += "]";


        log.warn("Exception Message: " + msg);

        return msg;
    }


    /**
     * Returns the BES catalog Document for the specified dataSource, striped
     * of the <response> parent element.
     *
     * @param dataSource .
     * @return The BES catalog Document.
     * @throws PPTException .
     * @throws BadConfigurationException .
     * @throws IOException .
     * @throws JDOMException .
     * @throws BESException .
     */
    public static Document showCatalog(String dataSource) throws
            PPTException,
            BadConfigurationException,
            IOException,
            JDOMException,
            BESException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        String product = "catalog for " + "\"" + dataSource + "\"";

        BesAPI.besShowTransaction(product, baos);

        log.debug("BES returned this document:\n" +
                "-----------\n" + baos +"-----------");

        // Parse the XML doc into a Document object.
        SAXBuilder sb = new SAXBuilder();
        Document doc = sb.build(new ByteArrayInputStream(baos.toByteArray()));

        // Check for an exception:
        besExceptionHandler(doc);
        // Tweak it!

        // First find the response Element

        Element topDataset =
                doc.getRootElement().getChild("response").getChild("dataset");

        // Disconnect it from it's parent and then rename it.
        topDataset.detach();
        doc.detachRootElement();
        doc.setRootElement(topDataset);

        return doc;

    }

    /**
     * Creates a new OPeNDAPClient connection to the BES and starts up the
     * connection.
     *
     * @return A new OPeNDAP Client.
     * @throws BadConfigurationException .
     * @throws PPTException .
     */
    public static OPeNDAPClient startClient()
            throws BadConfigurationException, PPTException {


        OPeNDAPClient oc = new OPeNDAPClient();
        oc.startClient(getHost(), getPort());


        log.debug("Got OPeNDAPClient. BES - Host: "
                    + _besHost + "  Port:" + _besPort);


        //if (Debug.isSet("BES"))
        //    oc.setOutput(System.out, true);
        //else
            oc.setOutput(devNull, true);



        return oc;
    }


    private static void configureTransaction(OPeNDAPClient oc,
                                             String dataset,
                                             String constraintExpression,
                                             String errorMsgFormat)

            throws PPTException {
        configureTransaction(oc, dataset, constraintExpression, null, errorMsgFormat);
    }


    private static void configureTransaction(OPeNDAPClient oc,
                                             String dataset,
                                             String constraintExpression,
                                             String type,
                                             String errorMsgFormat)
            throws PPTException {


        log.debug("Setting error context to: \""+errorMsgFormat+"\"");
        oc.executeCommand("set context errors to "+errorMsgFormat+";\n");



        String cmd = "set container in catalog values "
                + dataset
                + ", "
                + dataset
                + (type == null ? "" : ", " + type) + ";\n";


        log.debug("Sending BES command: " + cmd);


        oc.executeCommand(cmd);


        log.debug("ConstraintExpression: " + constraintExpression);


        if (constraintExpression == null ||
                constraintExpression.equalsIgnoreCase("")) {

            cmd = "define d1 as " + dataset + ";\n";

        } else {
            cmd = "define d1 as "
                    + dataset
                    + " with "
                    + dataset
                    + ".constraint=\""
                    + constraintExpression + "\"  ;\n";

        }

        log.debug("Sending BES command: " + cmd);
        oc.executeCommand(cmd);

    }

    private static String getGetCmd(String product) {

        return "get " + product + " for d1;\n";

    }

    private static String getAPINameForDDS() {
        return "dds";
    }

    private static String getAPINameForDAS() {
        return "das";
    }

    private static String getAPINameForDODS() {
        return "dods";
    }

    private static String getAPINameForDDX() {
        return "ddx";
    }


    private static String getAPINameForStream() {
        return "stream";
    }


    private static String getAPINameForASCII() {
        return "ascii";
    }

    private static String getAPINameForHTMLForm() {
        return "html_form";
    }

    private static String getAPINameForINFOPage() {
        return "info_page";
    }


    private static void getDataProduct(OPeNDAPClient oc,
                                       String product,
                                       OutputStream os) throws PPTException {

        String cmd = getGetCmd(product);
        log.debug("Sending command: " + cmd);

        oc.setOutput(os, false);
        oc.executeCommand(cmd);

    }


    private static void shutdownClient(OPeNDAPClient oc) throws PPTException {
        log.debug("Shutting down client...");

        oc.setOutput(null, false);

        oc.shutdownClient();
        log.debug("Client shutdown.");


    }






    private static void besGetTransaction(String product,
                                          String dataset,
                                          String constraintExpression,
                                          OutputStream os,
                                          String errorMsgFormat)
            throws BadConfigurationException, PPTException {

        boolean trouble = false;



        log.debug("besGetTransaction started.");


        OPeNDAPClient oc;

        oc = getClient();

        try {

            configureTransaction(oc,
                                 dataset,
                                 constraintExpression,
                                 errorMsgFormat);

            getDataProduct(oc, product, os);

        }
        catch (PPTException e){
            trouble = true;
            throw new PPTException("BesAPI.besGetTransaction(): " +
                    "Problem with OPeNDAPClient.\n");
        }
        finally{
            if (trouble) {
                log.error("besGetTransaction(): Problem encountered," +
                        " discarding OPeNDAPCLient. ",trouble);
                discardClient(oc);
            }
            else {
                returnClient(oc);
            }
        }
        log.debug("besGetTransaction complete.");

    }


    private static void besShowTransaction(String product, OutputStream os)
            throws PPTException, BadConfigurationException {

        log.debug("besShowTransaction started.");

        boolean trouble = false;

        OPeNDAPClient oc = getClient();

        try {

            String cmd = "show " + product + ";\n";
            log.debug("Sending command: " + cmd);
            oc.setOutput(os, false);
            log.debug("Setting error context to: \""+XML_ERRORS+"\"");
            oc.executeCommand("set context errors to "+XML_ERRORS+";\n");
            oc.executeCommand(cmd);

        }
        catch (PPTException e){
            trouble = true;
            throw new PPTException("BesAPI.besShowTransaction(): " +
                    "Problem with OPeNDAPClient.\n");
        }
        finally{
            if (trouble) {
                log.error("besShowTransaction(): Problem encountered," +
                        " discarding OPeNDAPCLient. ",trouble);
                discardClient(oc);
            }
            else {
                returnClient(oc);
            }
        }
        log.debug("besShowTransaction complete.");

    }





}