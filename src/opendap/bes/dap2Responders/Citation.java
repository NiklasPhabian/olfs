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
package opendap.bes.dap2Responders;

import opendap.bes.Version;
import opendap.bes.dap4Responders.Dap4Responder;
import opendap.bes.dap4Responders.MediaType;
import opendap.coreServlet.OPeNDAPException;
import opendap.coreServlet.ReqInfo;
import opendap.coreServlet.RequestCache;
import opendap.http.mediaTypes.TextPlain;
import opendap.bes.hashing.HashLog;

import org.slf4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


/**
 * Responder that transmits Citation to client.
 */

class DASParser {
    public String dasString;
    public String author;
    public String date;

    public DASParser(String dasString) {
        this.dasString = dasString;
        extractAuthor();
        extractDate();
    }

    public void extractAuthor() {
        Pattern pattern = Pattern.compile("Author[\\s]\\\"(.*)\\\";");
        Matcher matcher = pattern.matcher(dasString);
        if (matcher.find()) {author = matcher.group(1);}
    }

    public void extractDate() {
        Pattern pattern = Pattern.compile("Date[\\s]\\\"(.*)\\\";");
        Matcher matcher = pattern.matcher(dasString);
        if (matcher.find()) {date = matcher.group(1);}

    }

}

class CitationJSON {
    private String hash;
    private String queryString;
    private String URI;
    private String URL;
    private String relativeURL;
    private String constraintExpression;
    private String date;
    private String author;
    private String returnAs;
    private String subset;
    private String reretrievalURL;

    public String getString(){
        Gson gson = new Gson();
        String  jsonString = gson.toJson(this);
        return jsonString;
    }

    public String getPrettyString(){
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        String jsonString = gson.toJson(this);
        return jsonString;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }
    
    public void setURI(String URI) {
        this.URI = URI;
    }

    public void setURL(String URL) {
        this.URL = URL;
    }

    public void setRelativeURL(String relativeURL) {
        this.relativeURL = relativeURL.replace(".citation", "");
    }

    public void setConstraintExpression(String constraintExpression) {
        this.constraintExpression = constraintExpression;
    }

    public void setSubset(String subset) {
        this.subset = subset;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setReturnAs(String returnAs) {
        this.returnAs= returnAs;
    }

    public void setReretrievalURL(String reretrievalURL) {this.reretrievalURL = reretrievalURL;}
}



public class Citation extends Dap4Responder {
    private HashLog hashLog;

    private Logger log;
    private static String _defaultRequestSuffix = ".citation";

    public Citation(String sysPath, BesApi besApi) {
        this(sysPath,null, _defaultRequestSuffix, besApi);
    }

    public Citation(String sysPath, String pathPrefix, BesApi besApi) {
        this(sysPath, pathPrefix, _defaultRequestSuffix, besApi);
    }

    public Citation(String sysPath, String pathPrefix,  String requestSuffixRegex, BesApi besApi) {

        super(sysPath, pathPrefix, requestSuffixRegex, besApi);
        log = org.slf4j.LoggerFactory.getLogger(this.getClass());

        hashLog = new HashLog();

        setServiceRoleId("http://services.opendap.org/dap2/citation");
        setServiceTitle("Plain text Citation");
        setServiceDescription("The DAP2 Citation response in plain text.");
        setServiceDescriptionLink("http://docs.opendap.org/index.php/DAP4:_Specification_Volume_2#DAP2:_Citation_Service");

        setNormativeMediaType(new TextPlain(getRequestSuffix()));

        log.debug("Using RequestSuffix:              '{}'", getRequestSuffix());
        log.debug("Using CombinedRequestSuffixRegex: '{}'", getCombinedRequestSuffixRegex());
    }


    public boolean isDataResponder(){ return false; }
    public boolean isMetadataResponder(){ return true; }


    @Override
    public void sendNormativeRepresentation(HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.debug("Sending aCitation");


        String relativeURL = ReqInfo.getLocalUrl(request);
        String resourceID = getResourceId(relativeURL, false);
        String constraintExpression = ReqInfo.getConstraintExpression(request);

        BesApi besApi = getBesApi();


        log.debug("sendCitation() for dataset: " + resourceID);

        MediaType responseMediaType =  getNormativeMediaType();

        // Stash the Media type in case there's an error. That way the error handler will know how to encode the error.
        RequestCache.put(OPeNDAPException.ERROR_RESPONSE_MEDIA_TYPE_KEY, responseMediaType);

        response.setContentType(responseMediaType.getMimeType());
        Version.setOpendapMimeHeaders(request,response,besApi);
        response.setHeader("Content-Description", "citation");
        // Commented because of a bug in the OPeNDAP C++ stuff...
        //response.setHeader("Content-Encoding", "plain");

        response.setStatus(HttpServletResponse.SC_OK);
        String xdap_accept = request.getHeader("XDAP-Accept");

        OutputStream os = response.getOutputStream();

        OutputStream das_os = new ByteArrayOutputStream(1024);
        //besApi.writeDAS(resourceID, constraintExpression, xdap_accept,das_os);
        String dasString = das_os.toString();

        DASParser dasParser = new DASParser(dasString);

        String dataSource = relativeURL.replace(".citation", "");
        String returnAs = constraintExpression.split("&returnAs=")[1];
        String subset = constraintExpression.split("&returnAs=")[0];
        String uri = request.getRequestURI().replace(".citation", "");
        String url = request.getRequestURL().toString().replace(".citation", "");
        String hash = hashLog.getHash(subset, dataSource, returnAs);
        String reretrievalURL = url + "." + returnAs + "?" + subset + "&" +  "hash" + "=;" + hash;

        CitationJSON citation = new CitationJSON();
        citation.setHash(hash);
        citation.setURI(uri);
        citation.setURL(url);
        citation.setSubset(subset);
        citation.setDate(dasParser.date);
        citation.setAuthor(dasParser.author);
        citation.setReturnAs(returnAs);
        citation.setReretrievalURL(reretrievalURL);

        os.write(citation.getPrettyString().getBytes("UTF-8"));

        os.flush();
        log.debug("Sent DAP Citation data response.");


    }

}
