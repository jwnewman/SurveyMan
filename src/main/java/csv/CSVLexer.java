package csv;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.apache.log4j.Logger;
import survey.SurveyException;
import utils.Gensym;
import utils.Out;
import scala.collection.Seq;
import scalautils.QuotMarks;

public class CSVLexer {

    private static final Logger LOGGER = Logger.getLogger("csv");
    public static final String encoding = "UTF-8";
    public static int separator = ",".codePointAt(0);
    public static final String[] knownHeaders =
            {"QUESTION", "BLOCK", "OPTIONS", "RESOURCE", "EXCLUSIVE", "ORDERED", "PERTURB", "BRANCH"};
    public static String[] headers = null;
    private static final PrintStream out = new Out(encoding).out;
    public static HashMap<String, String> xmlChars = new HashMap<String, String>();
    static {
        xmlChars.put("<", "&lt;");
        xmlChars.put(">", "&gt;");
        xmlChars.put("&", "&amp;");
        QuotMarks.addQuots(xmlChars);
    }
    private static int quots2strip = 0;

    private static void logThrowFatal (SurveyException se) throws SurveyException {
        LOGGER.fatal(se);
        throw se;
    }

    private static String sep2string() {
        return Character.toString((char) separator);
    }

    private static boolean inQuot(String line) {
        // searches for quotation marks
        // since there are multiple possibilities for the right qmark,
        // consider the first match the matching one
        // only care about the outer quotation.
        char[] c = line.toCharArray();
        boolean inQ = false;
        String lqmark = "";
        ArrayList<String> rqmarks = null;
        int i = 0;
        while (i < c.length) {
            String s = String.valueOf(c[i]);
            if (QuotMarks.isA(s)) {
                if (inQ) {
                    assert (rqmarks!=null);
                    if (rqmarks.contains(s)) {
                        if (i+1 < c.length && c[i]==c[i+1]) // valid escape seq
                            i++;
                        else inQ = false; // else close quot
                    }
                } else {
                    // if I'm not already in a quote, check whether this is a 2-char quot.
                    if (i + 1 < c.length && QuotMarks.isA(s + String.valueOf(c[i+1]))) {
                        lqmark = s + String.valueOf(c[i+1]); i++;
                    } else lqmark = s ;
                    inQ=true ; rqmarks = QuotMarks.getMatch(lqmark);
                }
            }
            i++;
            // out.print(i+" ");
        }
        return inQ;
    }

    private static String stripQuots(String text, boolean header) throws SurveyException {
        String txt = text;
        if (header) {
            int qs = 0;
            while (QuotMarks.isA(txt.substring(0,1))) {
                boolean matchFound = false;
                for (String quot : QuotMarks.getMatch(txt.substring(0,1))) {
                    if (txt.endsWith(quot)) {
                        txt = txt.substring(1, txt.length() - 1);
                        qs++; matchFound = true; break;
                    }
                }
                if (!matchFound) logThrowFatal(new HeaderException("Matching wrapped quotation marks not found : " + text));
            }
            if (quots2strip==0)
                quots2strip=qs;
            else if (quots2strip > qs) logThrowFatal(new HeaderException("Inconsistent header quotation wrapping : " + text));
            else if (quots2strip < qs) logThrowFatal(new HeaderException("Headers cannot contain quotation marks : " + text));
        } else {
            for (int i = 0 ; i < quots2strip ; i ++) {
                boolean matchFound = false;
                for (String quot : QuotMarks.getMatch(txt.substring(0,1))) {
                    if (txt.endsWith(quot)) {
                        txt = txt.substring(1, txt.length() - 1);
                        matchFound = true; break;
                    }
                }
                if (!matchFound) logThrowFatal(new HeaderException("Matching wrapped quotation marks not found : "+ text));
            }
        }
        return txt.trim();
    }

    private static String[] getHeaders (String line) throws SurveyException{
        Gensym gensym = new Gensym("GENCOLHEAD");
        String[] headers = line.split(sep2string());
        for (int i = 0; i < headers.length ; i++) {
            headers[i] = headers[i].trim().toUpperCase();
            if (headers[i].equals(""))
                headers[i] = gensym.next();
            else {
                // strip quotes
                headers[i] = stripQuots(headers[i], true);
                // make sure it doesn't contain quotes
                for (int j = 0; j < headers[i].length() ; j++) {
                    if (QuotMarks.isA(headers[i].substring(j, j+1))
                            || ((j+1 < headers[i].length()) && QuotMarks.isA(headers[i].substring(j, j+2))))
                        logThrowFatal(new HeaderException("Headers cannot contain quotation marks : "+headers[i]));
                }
            }
        }
        for (int i = 0 ; i < headers.length ; i++ ) {
            boolean in = false;
            for (int j = 0 ; j < knownHeaders.length ; j++)
                if (headers[i].equals(knownHeaders[j])) {
                    in = true; break;
                }
            if (!in) 
                LOGGER.warn(String.format("WARNING: Column header %s has no known semantics."
                        , headers[i]));
        }
        return headers;
    }

    public static String xmlChars2HTML(String s) {
        s = s.replaceAll("&", xmlChars.get("&"));
        for (Map.Entry<String, String> e : xmlChars.entrySet())
            if (! e.getKey().equals("&"))
                s = s.replaceAll(e.getKey(), e.getValue());
        return s;
    }

    public static String htmlChars2XML(String s) {
        for (Map.Entry<String, String> e : xmlChars.entrySet())
            s = s.replaceAll(e.getValue(), e.getKey());
        return s;
    }

    private static void clean (HashMap<String, ArrayList<CSVEntry>> entries) throws SurveyException {
        for (String key : entries.keySet()){
            // all entries need to have the beginning/trailing separator and whitespace removed
            for (CSVEntry entry : entries.get(key)) {
                if (entry.contents.endsWith(sep2string()))
                    entry.contents = entry.contents.substring(0, entry.contents.length()-sep2string().length());
                if (entry.contents.startsWith(sep2string()))
                    entry.contents = entry.contents.substring(sep2string().length());
                entry.contents = entry.contents.trim();
                // remove beginning/trailing quotation marks
                if (entry.contents.length() > 0 ) {
                    for (int i = 0 ; i < quots2strip ; i ++) {
                        int len = entry.contents.length();
                        String lquot = entry.contents.substring(0,1);
                        String rquot = entry.contents.substring(len-1, len);
                        boolean foundMatch = false;
                        if (! QuotMarks.isA(lquot)) {
                            LOGGER.warn(new MalformedQuotationException(entry.lineNo, entry.colNo, String.format("entry (%s) does not begin with a known quotation mark", entry.contents)));
                            break;
                        }
                        for (String quot : QuotMarks.getMatch(lquot)){
                            if (entry.contents.endsWith(quot)) {
                                foundMatch = true; break;
                            }
                        }
                        if (! foundMatch)
                            throw new MalformedQuotationException(entry.lineNo, entry.colNo, String.format("entry (%s) does not have matching quotation marks.", entry.contents));
                        entry.contents = entry.contents.substring(1, len-1);
                    }
                }
            }
        }
    }
    
    private static void resetHeaders() {
        headers = null;
    }

    public static HashMap<String, ArrayList<CSVEntry>> lex(String filename)
            throws FileNotFoundException, IOException, RuntimeException, SurveyException {
        // FileReader uses the system's default encoding.
        // BufferedReader makes 16-bit chars
        BufferedReader br = new BufferedReader(new FileReader(filename));
        HashMap<String, ArrayList<CSVEntry>> entries = null;
        String line = "";
        int lineno = 0;
        // in case this is called multiple times:
        resetHeaders();
        while((line = br.readLine()) != null) {
            lineno+=1;
            if (headers==null) {
                headers = getHeaders(line);
                entries = new HashMap<String, ArrayList<CSVEntry>>(headers.length);
                for (int i = 0 ; i < headers.length ; i++)
                    entries.put(headers[i], new ArrayList<CSVEntry>());
            } else {
                // check to make sure this isn't a false alarm where we're in a quot
                // this isnt super inefficient, but whatever, we'll make it better later or maybe we won't notice.
                while (inQuot(line)) {
                    String newLine = br.readLine();
                    lineno += 1;
                    if (newLine != null)
                        line  = line + newLine;
                    else throw new MalformedQuotationException(lineno, -1, line);
                }
                // for each header, read an entry.
                String entry = null;
                String restOfLine = line;
                for (int i = 0 ; i < headers.length ; i ++) {
                    if (i == headers.length - 1) {
                        if (inQuot(restOfLine)) throw new MalformedQuotationException(lineno, i, restOfLine);
                        entries.get(headers[i]).add(new CSVEntry(restOfLine, lineno, i));
                    } else {
                        int a = restOfLine.indexOf(Character.toString((char) separator));
                        int b = 1;
                        if (a == -1) {
                            LOGGER.warn(String.format("separator '%s'(unicode:%s) not found in line %d:\n\t (%s)."
                                    , Character.toString((char) separator)
                                    , String.format("\\u%04x", separator)
                                    , lineno
                                    , line));
                        }
                        entry = restOfLine.substring(0, a + b);
                        restOfLine = restOfLine.substring(entry.length());
                        while (inQuot(entry)) {
                            if (restOfLine.equals("")) throw new MalformedQuotationException(lineno, i, entry);
                            a = restOfLine.indexOf(Character.toString((char) separator));
                            entry = entry + restOfLine.substring(0, a + b);
                            restOfLine = restOfLine.substring(a + b);
                        }
                        try{
                            entries.get(headers[i]).add(new CSVEntry(entry, lineno, i));
                        } catch (NullPointerException e) {
                            LOGGER.warn(String.format("NPE for header [%s] and entry [%s], csv source lineno %d"
                                    ,headers[i]
                                    , entry
                                    , lineno));
                        }
                    }
                }
            }
        }
        LOGGER.info(filename+"("+(lineno-1)+"):"+Character.toString((char) separator));
        clean(entries);
        if (! entries.keySet().contains("QUESTION")) throw new CSVColumnException("QUESTION");
        if (! entries.keySet().contains("OPTIONS")) throw new CSVColumnException("OPTIONS");
        return entries;
    }

    protected static int specialChar(String stemp) throws SurveyException{
        if (stemp.codePointAt(0)!=0x5C) logThrowFatal(new FieldSeparatorException(stemp));
        switch (stemp.charAt(1)) {
            case 't': return 0x9;
            case 'b' : return 0x8;
            case 'n' : return 0xA;
            case 'r' : return 0xD;
            case 'f' : return 0xC;
            case 'u' : return Integer.decode(stemp.substring(2, 5));
            default: logThrowFatal(new FieldSeparatorException(stemp));
        }
        return 0; // unreachable
    }

    public static void main(String[] args) 
            throws FileNotFoundException, IOException, UnsupportedEncodingException, RuntimeException, SurveyException {
        HashMap<String, ArrayList<CSVEntry>> entries;
        if (args.length == 2 && args[1].startsWith("--sep=")) {
            String stemp = args[1].substring("--sep=".length());
            if (stemp.length() > 1)
                separator = specialChar(stemp);
            else separator = stemp.codePointAt(0);
        }
        entries = lex(args[0]);
    }
}

class MalformedQuotationException extends SurveyException {
    public MalformedQuotationException(int row, int column, String msg) {
        super(String.format("Malformed quotation in cell (%d,%d) : %s."
                , row
                , column
                , msg));
    }
}
class FieldSeparatorException extends SurveyException {
    public FieldSeparatorException(String separator) {
        super(separator.startsWith("\\")?
                "Illegal sep: " + separator
                        + " is " + separator.length()
                        + " chars and " + separator.getBytes().length
                        + " bytes."
                : "Illegal escape char (" + separator.charAt(0)
                + ") in sep " + separator );
    }
}

class CSVColumnException extends SurveyException{
    public CSVColumnException(String colName) {
        super(String.format("CSVs column headers must contain a %s column"
                , colName.toUpperCase()));
    }
}

class HeaderException extends SurveyException {
    public HeaderException(String msg) {
        super(msg);
    }
}