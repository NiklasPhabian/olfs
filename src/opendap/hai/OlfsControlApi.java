package opendap.hai;


import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.Appender;
import opendap.coreServlet.*;
import org.apache.commons.lang.StringEscapeUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;


import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.read.CyclicBufferAppender;


/**
 * Created by IntelliJ IDEA.
 * User: ndp
 * Date: Nov 12, 2010
 * Time: 2:35:04 PM
 * To change this template use File | Settings | File Templates.
 */
public class OlfsControlApi extends HttpResponder {


    private Logger log;

    private static String defaultRegex = ".*\\/olfsctl";


    private String AdminLogger = "HAI_DEBUG_LOGGER";

    // LoggerContext.ROOT_NAME = "root"
    private String ROOT_NAME = "ROOT";
    private SimpleDateFormat sdf;


    private CyclicBufferAppender cyclicBufferAppender;

    public void init() {
        log = (Logger) LoggerFactory.getLogger(getClass());
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();

        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS Z");

        String msg = "";

        for (ch.qos.logback.classic.Logger logger : lc.getLoggerList()) {
            msg += "   Logger: " + logger.getName() + "\n";

            Iterator<Appender<ILoggingEvent>> i = logger.iteratorForAppenders();
            while (i.hasNext()) {
                Appender<ILoggingEvent> a = i.next();
                msg += "        Appender: " + a.getName() + "\n";

            }


        }
        log.debug("Initializing ViewLastLog Servlet. \n" + msg);

        ch.qos.logback.classic.Logger rootLogger = lc.getLogger(ROOT_NAME);

        cyclicBufferAppender = (CyclicBufferAppender) rootLogger.getAppender(AdminLogger);

    }


    public OlfsControlApi(String sysPath) {
        super(sysPath, null, defaultRegex);
        init();
    }


    public void respondToHttpGetRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {



        HashMap<String,String> kvp = Util.processQuery(request);



        response.getWriter().print(processOlfsCommand(kvp));

    }



    private String getOlfsLog(String lines) {
        StringBuilder logContent = new StringBuilder();
        int count = -1;

        if(lines!=null){

            try {
                int maxLines = Integer.parseInt(lines);

                if(maxLines>0 && maxLines<20000)
                    cyclicBufferAppender.setMaxSize(maxLines);

            }
            catch(NumberFormatException e){
                log.error("Failed to parse the value of the parameter 'lines': {}",lines);
            }

        }




        if (cyclicBufferAppender != null) {
            count = cyclicBufferAppender.getLength();
        }

        if (count == -1) {
            logContent.append("Failed to locate CyclicBuffer content!\n");
        } else if (count == 0) {
            logContent.append("No logging events to display.\n");
        } else {
            LoggingEvent le;
            for (int i = 0; i < count; i++) {
                le = (LoggingEvent) cyclicBufferAppender.get(i);
                logContent.append(StringEscapeUtils.escapeHtml(formatLoggingEvent(le)));
            }
        }

        return logContent.toString();
    }


    private String formatLoggingEvent(LoggingEvent event){


        //private String PATTERN = "%d{yyyy-MM-dd'T'HH:mm:ss.SSS Z} [thread:%t] [%r][%X{ID}] [%X{SOURCE}]   %-5p - %c - %m%n";

        StringBuffer sbuf = new StringBuffer(128);
        Date date = new Date(event.getTimeStamp());


        sbuf.append(sdf.format(date));
        sbuf.append(" ");
        sbuf.append(" [").append(event.getThreadName()).append("] ");
        sbuf.append(event.getLevel());
        sbuf.append(" - ");
        sbuf.append(event.getLoggerName());
        sbuf.append(" - ");
        sbuf.append(event.getFormattedMessage());
        sbuf.append("\n");

        IThrowableProxy itp = event.getThrowableProxy();
        if(itp!=null){
            for(StackTraceElementProxy ste  : itp.getStackTraceElementProxyArray()){
                sbuf.append("    ").append(ste);
                sbuf.append("\n");
            }
        }
        return sbuf.toString();

    }



    public String setLogLevel(HashMap<String, String> kvp){

        StringBuilder sb = new StringBuilder();
        String level = kvp.get("level");
        String loggerName = kvp.get("logger");


        if(loggerName != null){
            Logger namedLog = (Logger) LoggerFactory.getLogger(loggerName);
            if( level.equals("all") ){
                namedLog.setLevel(Level.ALL);
                sb.append(loggerName).append(" logging level set to: ").append(level);
            }
            else if( level.equals("error") ){
                namedLog.setLevel(Level.ERROR);
                sb.append(loggerName).append(" logging level set to: ").append(level);
            }
            else if( level.equals("warn") ){
                namedLog.setLevel(Level.WARN);
                sb.append(loggerName).append(" logging level set to: ").append(level);
            }
            else if( level.equals("info") ){
                namedLog.setLevel(Level.INFO);
                sb.append(loggerName).append(" logging level set to: ").append(level);
            }
            else if( level.equals("debug") ){
                namedLog.setLevel(Level.DEBUG);
                sb.append(loggerName).append(" logging level set to: ").append(level);
            }
        }

        return sb.toString();

    }

    public String getLogLevel(HashMap<String, String> kvp){

        StringBuilder sb = new StringBuilder();
        String loggerName = kvp.get("logger");


        if(loggerName != null){
            Logger namedLog = (Logger) LoggerFactory.getLogger(loggerName);

            Level level = namedLog.getLevel();

            String levelStr = "off";
            if(level!=null)
                levelStr = level.toString().toLowerCase();

            sb.append(levelStr);
        }

        return sb.toString();

    }

    /**
     *
     * @param kvp
     * @return
     */
    public String processOlfsCommand(HashMap<String, String> kvp) {

        StringBuilder sb = new StringBuilder();

        String olfsCmd = kvp.get("cmd");


        if ( olfsCmd != null) {


            if (olfsCmd.equals("getLog")) {
                String lines = kvp.get("lines");


                String log =  getOlfsLog(lines);

                log = StringEscapeUtils.escapeXml(log);

                sb.append(log);
            }
            else if (olfsCmd.equals("getLogLevel")){

                sb.append(getLogLevel(kvp));
            }
            else if (olfsCmd.equals("setLogLevel")){

                sb.append(setLogLevel(kvp));
            }
            else  {
                sb.append(" Unrecognized OLFS command: ").append(Scrub.simpleString(olfsCmd));
            }
        }
        else {

            sb.append(" Waiting for you to do something...");
        }


        return sb.toString();


    }




}