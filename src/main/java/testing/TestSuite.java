package testing;
import csv.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import survey.*;

public class TestSuite{
    public static void main(String[] args) throws SurveyException{
        //String filename = args[1];
        String separator = System.getProperty("file.separator");
        System.out.println(separator);
        String filename = "data"+separator+"blah.csv";
        Survey survey1 = null;
        try {
            survey1 = csv.CSVParser.parse(filename, ",");
            System.out.println(survey1);
        } catch (IOException ex) {
            System.out.println("File not found");
        }
        
        for (Question q : survey1.questions){
            System.out.println(q.data);
            System.out.println(q.options);
        }
        
        System.out.println();
        System.out.println("Generating responses:");
        System.out.println();
        
        ArrayList<SurveyResponse> responses = new ArrayList<SurveyResponse>();
        Random rand = new Random();
        //generate group of respondents who always pick option 1
        int numResponses = 25;
        int numRandomResponses = 5;
        for(int x=0; x<numResponses; x++){
            SurveyResponse sr = new SurveyResponse(""+rand.nextInt(1000));
            responses.add(sr.consistentResponse(survey1));
        }
        //generate group of random respondents
        for(int x=0; x<numRandomResponses; x++){
            SurveyResponse sr = new SurveyResponse(""+rand.nextInt(1000));
            responses.add(sr.randomResponse(survey1));
        }
        
        //shuffle real and random responses
        Collections.shuffle(responses);
        
        for(SurveyResponse r: responses){
            System.out.println(r);
        }
    }
}