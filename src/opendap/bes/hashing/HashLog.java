package opendap.bes.hashing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;

import java.util.Date;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

import javax.naming.Context;
import javax.naming.InitialContext;


class CurrentUtcDate {
    public static String get() {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String utcTimeStamp = dateFormat.format(date);
        return utcTimeStamp;
    }
}


class Utils {
    private static String digits = "0123456789abcdef";

    public static String toHex(byte[] data, int length) {
        StringBuffer	buf = new StringBuffer();
        for (int i = 0; i != length; i++) {
            int	v = data[i] & 0xff;
            buf.append(digits.charAt(v >> 4));
            buf.append(digits.charAt(v & 0xf));
        }
        return buf.toString();
    }

    public static String toHex(byte[] data) {
        return toHex(data, data.length);
    }
}

class RequestParser {
    private static final Namespace BES_NS = opendap.namespaces.BES.BES_NS;
    private Document request;
    public String dataSource;
    public static boolean isDODS;
    public String returnAs;
    public String constraint;

    public RequestParser (Document request) {
        this.request = request;
        parse();
    }

    private void parse() {
        getDataSource();
        getIsDODS();
        getReturnAs();
        getConstraint();
    }

    private void getDataSource() {
        Element root = request.getRootElement();
        Element tag = root.getChild("setContainer", BES_NS);
        this.dataSource = tag.getTextTrim();
    }

    private void getIsDODS() {
        Element root = request.getRootElement();
        Element tag = root.getChild("get", BES_NS);
        String type = tag.getAttributeValue("type");
        this.isDODS = new String("dods").equals(type);
    }

    private void getReturnAs() {
        Element root = request.getRootElement();
        Element tag = root.getChild("get", BES_NS);
        this.returnAs = tag.getAttributeValue("returnAs");
    }

    private void getConstraint() {
        Element root = request.getRootElement();
        Element tag = root.getChild("define", BES_NS);
        tag = tag.getChild("container", BES_NS);
        tag = tag.getChild("constraint", BES_NS);
        if (tag ==null){
            this.constraint = null;
        } else {
            this.constraint = tag.getTextTrim();
        }
    }
}

public class HashLog {
    private Logger log;
    private Connection connection;


    public HashLog() {
        log = org.slf4j.LoggerFactory.getLogger(getClass());
    }

    public void connect() {
        this.connection = null;
        try {
            Context initCtx = new InitialContext();
            Context envCtx = (Context) initCtx.lookup("java:comp/env");
            DataSource ds = (DataSource) envCtx.lookup("jdbc/sqlite");
            this.connection = ds.getConnection();
            log.debug("sqlite log db connected");
        } catch (Exception e) {
            log.error("sqlite could not connect to db", e);
        }
    }

    public void disconnect() {
        try {
            this.connection.close();
        } catch (Exception e) {
            log.error("could not close sqlite connection");
        }
    }

    public void insertHash(byte[] hash, String query, Document request) {
        RequestParser requestParser = new RequestParser(request);
        if (!RequestParser.isDODS) {
            return;
        }

        String hashString = Utils.toHex(hash);

        String dataSource = requestParser.dataSource;
        String constraint = requestParser.constraint;
        if (constraint == null) {constraint = "";}
        String returnAs = requestParser.returnAs;

        String timestamp = CurrentUtcDate.get();
        log.debug("Inserting hash", hashString);
        String sql = "INSERT INTO hashes (hash, dataSource, constr, returnAs, timestamp, query) VALUES (?,?,?,?,?,?);";
        PreparedStatement preparedStatement = null;
        try {
            this.connect();
            preparedStatement = this.connection.prepareStatement(sql);
            preparedStatement.setString(1, hashString);
            preparedStatement.setString(2, dataSource);
            preparedStatement.setString(3, constraint);
            preparedStatement.setString(4, returnAs);
            preparedStatement.setString(5, timestamp);
            preparedStatement.setString(6, query);
            preparedStatement.executeUpdate();
            this.disconnect();
        } catch (Exception e) {
            log.error("could not insert hash", e);
        }
    }


    public String getHash(String constr, String dataSource, String returnAs){
        String sql = "SELECT hash FROM hashes WHERE constr=? AND dataSource=? AND returnAs=?;";
        PreparedStatement preparedStatement = null;
        String hash = "";
        try {
            this.connect();
            preparedStatement = this.connection.prepareStatement(sql);
            preparedStatement.setString(1, constr);
            preparedStatement.setString(2, dataSource);
            preparedStatement.setString(3, returnAs);
            ResultSet resultSet = preparedStatement.executeQuery();
            hash = resultSet.getString(1);
            log.debug("Has found: ", hash);
            this.disconnect();
        } catch (java.sql.SQLException e){
            log.debug("Hash not in DB", e);
        } catch (Exception e) {
            log.error("could not fetch hash", e);
        }
        return hash;
    }

    public String getHashTimeStamp(String constraintExpression, String dataSource, String returnAs){
        String sql = "SELECT timestamp FROM hashes WHERE constr=? AND dataSource=? AND returnAs=?;";
        PreparedStatement preparedStatement = null;
        String timestamp = "";
        try {
            this.connect();
            preparedStatement = this.connection.prepareStatement(sql);
            preparedStatement.setString(1, constraintExpression);
            preparedStatement.setString(2, dataSource);
            preparedStatement.setString(3, returnAs);
            ResultSet resultSet = preparedStatement.executeQuery();
            timestamp = resultSet.getString(1);
            log.debug("timestamp found: ", timestamp);
            this.disconnect();
        } catch (java.sql.SQLException e){
            log.debug("Hash not in DB", e);
        } catch (Exception e) {
            log.error("could not fetch hash", e);
        }
        return timestamp;
    }

}
