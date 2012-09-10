package opendap.bes.dap4Responders.DataResponse;

import opendap.bes.Version;
import opendap.bes.dap4Responders.Dap4Responder;
import opendap.bes.dap4Responders.ServiceMediaType;
import opendap.bes.dapResponders.BesApi;
import opendap.coreServlet.MimeBoundary;
import opendap.coreServlet.ReqInfo;
import opendap.dap.User;
import org.jdom.Document;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.slf4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

/**
 * Created by IntelliJ IDEA.
 * User: ndp
 * Date: 9/5/12
 * Time: 10:11 AM
 * To change this template use File | Settings | File Templates.
 */
public class NormativeDR extends Dap4Responder {
    private Logger log;
    private static String defaultRequestSuffix = ".dap";


    public NormativeDR(String sysPath, BesApi besApi) {
        this(sysPath, null, defaultRequestSuffix, besApi);
    }

    public NormativeDR(String sysPath, String pathPrefix, BesApi besApi) {
        this(sysPath, pathPrefix, defaultRequestSuffix, besApi);
    }

    public NormativeDR(String sysPath, String pathPrefix, String requestSuffix, BesApi besApi) {
        super(sysPath, pathPrefix, requestSuffix, besApi);
        log = org.slf4j.LoggerFactory.getLogger(this.getClass());


        setServiceRoleId("http://services.opendap.org/dap4/data");
        setServiceTitle("DAP4 Data Response");
        setServiceDescription("DAP4 Data Response object.");
        setServiceDescriptionLink("http://docs.opendap.org/index.php/DAP4_Web_Services#DAP4:_Data_Service");


        setNormativeMediaType(new ServiceMediaType("application","vnd.org.opendap.dap4.data", defaultRequestSuffix));

        addAltRepResponder(new CsvDR(sysPath, pathPrefix, besApi));
        addAltRepResponder(new XmlDR(sysPath, pathPrefix, besApi));
        addAltRepResponder(new Netcdf3DR(sysPath, pathPrefix, besApi));


        log.debug("defaultRequestSuffix: '{}'", defaultRequestSuffix);

    }







    public void sendNormativeRepresentation(HttpServletRequest request, HttpServletResponse response) throws Exception {

        String requestedResourceId = ReqInfo.getLocalUrl(request);
        String constraintExpression = ReqInfo.getConstraintExpression(request);
        String xmlBase = getXmlBase(request);

        String resourceID = getResourceId(requestedResourceId, false);


        BesApi besApi = getBesApi();

        log.debug("Sending {} for dataset: {}",getServiceTitle(),resourceID);

        response.setContentType(getNormativeMediaType().getMimeType());
        Version.setOpendapMimeHeaders(request, response, besApi);
        response.setHeader("Content-Description", "dap4:Dataset");
        // Commented because of a bug in the OPeNDAP C++ stuff...
        //response.setHeader("Content-Encoding", "plain");

        String xdap_accept = request.getHeader("XDAP-Accept");


        MimeBoundary mb = new MimeBoundary();
        String startID = mb.newContentID();

        User user = new User(request);



        OutputStream os = response.getOutputStream();
        ByteArrayOutputStream erros = new ByteArrayOutputStream();

        Document reqDoc = besApi.getDataDDXRequest(resourceID,
                                                        constraintExpression,
                                                        xdap_accept,
                                                        user.getMaxResponseSize(),
                                                        xmlBase,
                                                        startID,
                                                        mb.getBoundary());

        XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());
        log.debug("BesApi.getDataDDXRequest() returned:\n "+xmlo.outputString(reqDoc));

        if(!besApi.besTransaction(resourceID,reqDoc,os,erros)){
            String msg = new String(erros.toByteArray());
            log.error("respondToHttpGetRequest() encountered a BESError: "+msg);
            os.write(msg.getBytes());

        }


        os.flush();
        log.info("Sent {}.",getServiceTitle());




    }


}
