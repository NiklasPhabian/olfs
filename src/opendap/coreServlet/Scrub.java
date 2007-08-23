/////////////////////////////////////////////////////////////////////////////
// This file is part of the "Server4" project, a Java implementation of the
// OPeNDAP Data Access Protocol.
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

package opendap.coreServlet;

import org.slf4j.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: ndp
 * Date: Aug 14, 2007
 * Time: 3:28:03 PM
 */
public class Scrub {

    private static Logger log = org.slf4j.LoggerFactory.getLogger(Scrub.class);



    private static String urlContentInclusionRegex = "[-a-zA-Z0-9:/.%]*";
    private static String urlContentExclusionRegex = "[^-a-zA-Z0-9:/.%]";

    private static Pattern urlContentInclusionPattern = Pattern.compile(urlContentInclusionRegex);



    public static String urlContent(String urlContent){


        Matcher m = urlContentInclusionPattern.matcher(urlContent);

        log.debug("URL() - Scrubbing URL Content: "+urlContent+"   white list pattern: "+ urlContentInclusionRegex +"    matches: "+m.matches());



        if(m.matches()){
            return urlContent;
        }
        else {
            return urlContent.replaceAll(urlContentExclusionRegex,"#");
        }


    }




    private static String completeURLInclusionRegex = "http://"+urlContentInclusionRegex;
    private static String completeURLExclusionRegex = "http://"+urlContentExclusionRegex;

    private static Pattern completeURLInclusionPattern = Pattern.compile(completeURLInclusionRegex);


    public static String completeURL(String url){


        Matcher m = completeURLInclusionPattern.matcher(url);

        log.debug("URL() - Scrubbing URL: "+url+"   white list pattern: "+ completeURLInclusionRegex +"    matches: "+m.matches());



        if(m.matches()){
            return url;
        }
        else {
            return url.replaceAll(completeURLExclusionRegex,"#");
        }


    }






    private static String simpleStringInclusionRegex = "[a-zA-Z0-9]*";
    private static String simpleStringExclusionRegex = "[^a-zA-Z0-9]";

    private static Pattern simpleStringInclusionPattern = Pattern.compile(simpleStringInclusionRegex);


    public static String simpleString(String s){


        Matcher m = simpleStringInclusionPattern.matcher(s);

        log.debug("URL() - Scrubbing String: "+s+"   white list pattern: "+ simpleStringInclusionRegex +"    matches: "+m.matches());



        if(m.matches()){
            return s;
        }
        else {
            return s.replaceAll(simpleStringExclusionRegex,"#");
        }


    }









    public static void main(String[] args){


        String s = "this <> should suck.";

        System.out.println("source: "+s+"   result: "+urlContent(s));

        s = "this IsGood";

        System.out.println("source: "+s+"   result: "+urlContent(s));



    }

















}
