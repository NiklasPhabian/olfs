/*
 * /////////////////////////////////////////////////////////////////////////////
 * // This file is part of the "Hyrax Data Server" project.
 * //
 * //
 * // Copyright (c) 2013 OPeNDAP, Inc.
 * // Author: Nathan David Potter  <ndp@opendap.org>
 * //
 * // This library is free software; you can redistribute it and/or
 * // modify it under the terms of the GNU Lesser General Public
 * // License as published by the Free Software Foundation; either
 * // version 2.1 of the License, or (at your option) any later version.
 * //
 * // This library is distributed in the hope that it will be useful,
 * // but WITHOUT ANY WARRANTY; without even the implied warranty of
 * // MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * // Lesser General Public License for more details.
 * //
 * // You should have received a copy of the GNU Lesser General Public
 * // License along with this library; if not, write to the Free Software
 * // Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 * //
 * // You can contact OPeNDAP, Inc. at PO Box 112, Saunderstown, RI. 02874-0112.
 * /////////////////////////////////////////////////////////////////////////////
 */

package opendap.gateway;

import opendap.bes.BESError;
import opendap.bes.BadConfigurationException;
import opendap.bes.PathInfo;
import opendap.bes.dap2Responders.BesApi;
import opendap.coreServlet.OPeNDAPException;
import opendap.coreServlet.ReqInfo;
import opendap.coreServlet.Util;
import opendap.namespaces.BES;
import opendap.ppt.PPTException;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.slf4j.Logger;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: ndp
 * Date: 11/28/11
 * Time: 5:17 PM
 * To change this template use File | Settings | File Templates.
 */
public class BesGatewayApi extends BesApi {


    private Logger _log;
    private String _servicePrefix;

    public static final String
            stripDotSuffixRegexString = "\\..*";
    public static final Pattern stripDotSuffixPattern = Pattern.compile(stripDotSuffixRegexString);

    public BesGatewayApi(){
        super();
        _servicePrefix = "";
        _log = org.slf4j.LoggerFactory.getLogger(this.getClass());
    }

    public BesGatewayApi(String servicePrefix){
        this();
        _servicePrefix = servicePrefix;
    }

    /**
     * This method defines which "space" (aka catalog) the BES will use to service the request. Here
     * we override the parent class which uses the "space" called "catalog" to use the "space" called "gateway".
     * This is what causes the BES to invoke the gateway handler
     * @return
     */
    @Override
    public Document getDap2RequestDocument(String type,
                                           String remoteDataSourceUrl,
                                           String ce,
                                           String xdap_accept,
                                           int maxResponseSize,
                                           String xmlBase,
                                           String formURL,
                                           String returnAs,
                                           String errorContext)
                throws BadConfigurationException {


        _log.debug("Building request for BES gateway_module request. remoteDataSourceUrl: "+ remoteDataSourceUrl);
        Element e, request = new Element("request", BES.BES_NS);

        String reqID = "["+Thread.currentThread().getName()+":"+
                Thread.currentThread().getId()+":gateway_request]";
        request.setAttribute("reqID",reqID);


        if(xdap_accept!=null)
            request.addContent(setContextElement(XDAP_ACCEPT_CONTEXT,xdap_accept));
        else
            request.addContent(setContextElement(XDAP_ACCEPT_CONTEXT, DEFAULT_XDAP_ACCEPT));

        request.addContent(setContextElement(EXPLICIT_CONTAINERS_CONTEXT,"no"));

        request.addContent(setContextElement(ERRORS_CONTEXT,errorContext));

        if(xmlBase!=null)
            request.addContent(setContextElement(XMLBASE_CONTEXT,xmlBase));

        if(maxResponseSize>=0)
            request.addContent(setContextElement(MAX_RESPONSE_SIZE_CONTEXT,maxResponseSize+""));


        request.addContent(setContainerElement("gatewayContainer","gateway",remoteDataSourceUrl,type));

        Element def = defineElement("d1","default");
        e = (containerElement("gatewayContainer"));

        if(ce!=null && !ce.equals(""))
            e.addContent(constraintElement(ce));

        def.addContent(e);

        request.addContent(def);

        e = getElement(type,"d1",formURL,returnAs);

        request.addContent(e);

        _log.debug("Built request for BES gateway_module.");


        return new Document(request);

    }

    private String getDataSourceUrl(HttpServletRequest req, String pathPrefix)  {
        String relativeURL = ReqInfo.getLocalUrl(req);
        return getRemoteDataSourceUrl(relativeURL, pathPrefix, Pattern.compile(_regexToMatchLastDotSuffixString));
    }

    public String getRemoteDataSourceUrl(String relativeURL, String pathPrefix, Pattern suffixMatchPattern )  {

        // Strip leading slash(es)
        while(relativeURL.startsWith("/") && !relativeURL.equals("/"))
            relativeURL = relativeURL.substring(1,relativeURL.length());

        String dataSourceUrl = relativeURL;
        // Strip the path off.
        if(pathPrefix!=null && dataSourceUrl.startsWith(pathPrefix))
            dataSourceUrl = dataSourceUrl.substring(pathPrefix.length());

        if(!dataSourceUrl.equals("")){
            dataSourceUrl = Util.dropSuffixFrom(dataSourceUrl, suffixMatchPattern);
        }
        dataSourceUrl = HexAsciiEncoder.hexToString(dataSourceUrl);

        // URL url = new URL(dataSourceUrl);
        // log.debug(urlInfo(url));

        return dataSourceUrl;
    }


    /**
     *  Returns the DDX request document for the passed dataSource
     *  using the passed constraint expression.
     * @param dataSource The data set whose DDX is being requested
     * @param ce The constraint expression to apply.
     * @param xdap_accept The version of the dap that should be used to build the
     * response.
     * @param xmlBase The request URL.
     * @param contentID contentID of the first MIME part.
     * @param mimeBoundary The MIME boundary to use in the response..
     * @return The DDX request document.
     * @throws BadConfigurationException When no BES can be found to
     * service the request.
     */
    public Document getDap4DataRequest(String dataSource,
                                       String ce,
                                       String xdap_accept,
                                       int maxResponseSize,
                                       String xmlBase,
                                       String contentID,
                                       String mimeBoundary)
            throws BadConfigurationException {

        Document reqDoc = getDap2RequestDocument(DataDDX, dataSource, ce, xdap_accept, maxResponseSize, xmlBase, null, null, XML_ERRORS);

        Element req = reqDoc.getRootElement();
        if(req==null)
            throw new BadConfigurationException("Request document is corrupt! Missing root element!");

        Element getReq = req.getChild("get",BES.BES_NS);
        if(getReq==null)
            throw new BadConfigurationException("Request document is corrupt! Missing 'get' element!");

        Element e = new Element("contentStartId",BES.BES_NS);
        e.setText(contentID);
        getReq.addContent(e);

        e = new Element("mimeBoundary",BES.BES_NS);
        e.setText(mimeBoundary);
        getReq.addContent(e);

        return reqDoc;
    }


    /**
     * Because the gateway doesn't support a catalog we ignore the checkWithBes parameter
     * @param relativeUrl The relative URL of the client request. No Constraint expression (i.e. No query section of
     * the URL - the question mark and everything after it.)
     * @param suffixMatchPattern This parameter provides the method with a suffix regex to use in evaluating what part,
     * if any, of the relative URL must be removed to construct the besDataSourceId/
     * @return
     */
    @Override
    public String getBesDataSourceID(String relativeUrl, Pattern suffixMatchPattern){

        _log.debug("getBesDataSourceID() - relativeUrl: " + relativeUrl);


        if(Util.matchesSuffixPattern(relativeUrl,suffixMatchPattern)){
            try {
                String remoteDatasourceUrl = getRemoteDataSourceUrl(relativeUrl, _servicePrefix, suffixMatchPattern);

                _log.debug("getBesDataSourceID() - besDataSourceId: {}", remoteDatasourceUrl);
                return remoteDatasourceUrl;
            }
            catch (NumberFormatException e){
                _log.debug("getBesDataSourceID() - Failed to extract target dataset URL from relative URL '{}'", relativeUrl);
            }
        }
        return null;
    }


    @Override
    public void getBesCatalog(String dataSourceUrl, Document response) throws IOException {
        // Go get the HEAD for the catalog
        // FIXME: This DOES NOT utilize the whitelist in the BES and this should to be MOVED to the BES
        HttpClient httpClient = new HttpClient();
        HeadMethod headReq = new HeadMethod(dataSourceUrl);
        try {
            int statusCode = httpClient.executeMethod(headReq);
            if (statusCode != HttpStatus.SC_OK) {
                _log.error("Unable to HEAD remote resource: " + dataSourceUrl);
                String msg = "OLFS: Unable to access requested resource: " + dataSourceUrl;
                throw new OPeNDAPException(statusCode,msg);
            }
            Header lastModifiedHeader = headReq.getResponseHeader("Last-Modified");
            Date lastModified = new Date();
            if (lastModifiedHeader != null) {
                String lmtString = lastModifiedHeader.getValue();
                SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
                try {
                    lastModified = format.parse(lmtString);
                } catch (ParseException e) {
                    _log.warn("Failed to parse last modified time. LMT String: {}, resource URL: {}", lmtString, dataSourceUrl);
                }
            }
            int size = -1;
            Header contentLengthHeader = headReq.getResponseHeader("Content-Length");
            if (contentLengthHeader != null) {
                String sizeStr = contentLengthHeader.getValue();
                try {
                    size = Integer.parseInt(sizeStr);
                } catch (NumberFormatException nfe) {
                    _log.warn("Received invalid content length from datasource: {}: ", dataSourceUrl);
                }
            }
            Element catalogElement = getShowCatalogResponseDocForDatasetUrl(dataSourceUrl, size, lastModified);
            response.detachRootElement();
            response.setRootElement(catalogElement);
            return;

        } catch (Exception e) {
            _log.warn("Unable to HEAD the remote resource: {} Error Msg: {}", dataSourceUrl, e.getMessage());
        }
        // I don't know how this bullshit (below) got in here, but it pretty much borks all the stuff downstream
        // If the resource can't be accessed it's an error. I commented this out and added the appropriate
        // exception immediately above - ndp 12/28/17
        //
        //Element catalogElement = getShowCatalogResponseDocForDatasetUrl("", 0, new Date());
        //response.detachRootElement();
        //response.setRootElement(catalogElement);
    }



    public Element getShowCatalogResponseDocForDatasetUrl(String dataSourceURL, int size, Date lastModified) throws IOException {

        Element root = new Element("response",BES.BES_NS);
        root.addNamespaceDeclaration(BES.BES_NS);
        root.setAttribute("reqID","BesGatewayApi_Construct");

        Element showCatalog = new Element("showCatalog",BES.BES_NS);
        root.addContent(showCatalog);
        if(dataSourceURL!=null && dataSourceURL.length()>0){
            Element dataset = new Element("dataset",BES.BES_NS);
            showCatalog.addContent(dataset);
            dataset.setAttribute("name",dataSourceURL);
            dataset.setAttribute("size",""+size);
            SimpleDateFormat sdf = new SimpleDateFormat(opendap.bes.BES.DATE_FORMAT);
            dataset.setAttribute("lastModified",sdf.format(lastModified));
            dataset.setAttribute("node","false");

            Element serviceRef = new Element("serviceRef",BES.BES_NS);
            serviceRef.setText("dap");
            dataset.addContent(serviceRef);
        }
        else {
            throw new IOException("Gateway target URL is unusable (either null or zero length)");
        }
        return root;
    }
}
