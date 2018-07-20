/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.divudi.data.lab;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.http.Consts;

/**
 *
 * @author Dr. M H B Ariyaratne <buddhika.ari@gmail.com>
 */
public class Dimension {

    private String inputStringBytesSpaceSeperated;
    private String inputStringBytesPlusSeperated;
    private String inputStringCharactors;
    private List<Byte> bytes;

    private int fieldCount;
    private Map<Integer, String> requestFields;
    private Map<Integer, String> responseFields;

    private String responseString;

    private Boolean limsHasSamplesToSend;

    private MessageType limsMessageType;
    private MessageSubtype limsMessageSubtype;
    private String limsPatientId;
    private String limsSampleId;
    private String limsSampleType;
    private Priority limsPriority;
    private List<String> limsTests;

    private Boolean toDeleteSampleRequest;

    private MessageType analyzerMessageType;
    private MessageSubtype analyzerMessageSubtype;
    private String analyzerPatientId;
    private String analyzerSampleId;
    private SampleTypeForDimension analyzerSampleType;
    private DimensionPriority analyzerPriority;

    private String instrumentId;
    private Byte firstPollValue;
    private Byte requestValue;
    private String requestAcceptanceStatus;

    public void analyzeReceivedMessage() {
        textToByteArraySeperatedBySpace();
        byteArrayToFields();
        classifyMessage();
        determineValues();
        determineMessageSubtype();

    }

    public void prepareResponse() {
        createResponseFields();
        createResponseString();
    }

    private void classifyMessage() {
        if (requestFields.size() < 2) {
            analyzerMessageType = MessageType.EmptyMessage;
            return;
        }
        String mt = requestFields.get(0).toUpperCase();
        if (mt == null) {
            analyzerMessageType = MessageType.EmptyMessage;
        } else if (mt.equalsIgnoreCase("P")) {
            analyzerMessageType = MessageType.Poll;
        } else if (mt.equalsIgnoreCase("D")) {
            analyzerMessageType = MessageType.SampleRequest;
        } else if (mt.equalsIgnoreCase("N")) {
            analyzerMessageType = MessageType.NoRequest;
        } else if (mt.equalsIgnoreCase("M")) {
            if (requestFields.size() == 3) {
                analyzerMessageType = MessageType.ResultAcceptance;
            } else if (requestFields.size() == 6) {
                analyzerMessageType = MessageType.RequestAcceptance;
            }
        } else if (mt.equalsIgnoreCase("I")) {
            if (requestFields.size() == 4) {
                analyzerMessageType = MessageType.EnhancedQueryMessage;
            } else if (requestFields.size() == 2) {
                analyzerMessageType = MessageType.QueryMessage;
            }
        } else if (mt.equalsIgnoreCase("R")) {
            analyzerMessageType = MessageType.ResultMessage;
        } else if (mt.equalsIgnoreCase("C")) {
            analyzerMessageType = MessageType.CaliberationResultMessage;
        } else {
            analyzerMessageType = MessageType.EmptyMessage;
        }
        System.out.println("analyzerMessageType = " + analyzerMessageType);
    }

    private void determineValues() {
        if (analyzerMessageType == MessageType.Poll) {
            instrumentId = requestFields.get(1);
            firstPollValue = getByte(requestFields.get(2));
            requestValue = getByte(requestFields.get(3));
        } else if (analyzerMessageType == MessageType.ResultAcceptance) {
            requestAcceptanceStatus = requestFields.get(1);
            //TODO: Reason for Rejection, Get Cup Positions
        } else if (analyzerMessageType == MessageType.QueryMessage) {
            analyzerSampleId = requestFields.get(1);
        }
    }

    private void determineMessageSubtype() {
        System.out.println("determineMessageSubtype");
        System.out.println("analyzerMessageType = " + analyzerMessageType);
        System.out.println("requestValue = " + requestValue);
        
        if (analyzerMessageType == MessageType.Poll) {
            if (firstPollValue == 1) {
                analyzerMessageSubtype = MessageSubtype.FirstPoll;
            } else {
                if (requestValue == 1) {
                    analyzerMessageSubtype = MessageSubtype.ConversationalPollReady;
                } else {
                    analyzerMessageSubtype = MessageSubtype.ConversationalPollBusy;
                }
            }
            System.out.println("analyzerMessageSubtype = " + analyzerMessageSubtype);
            return;
        } else if (analyzerMessageType == MessageType.RequestAcceptance) {
            if (requestAcceptanceStatus.equals("A")) {
                analyzerMessageSubtype = MessageSubtype.RequestAcceptanceSuccess;
            } else {
                analyzerMessageSubtype = MessageSubtype.RequestAcceptanceFailed;
            }
        }
        System.out.println("analyzerMessageSubtype = " + analyzerMessageSubtype);
    }

    private void createResponseFields() {
        System.out.println("createResponseFields");
        responseFields = new HashMap<>();
        System.out.println("analyzerMessageType = " + analyzerMessageType);
        if (analyzerMessageSubtype == MessageSubtype.FirstPoll) {
            createNoSampleRequestMessage();
        }else if(analyzerMessageSubtype == MessageSubtype.ConversationalPollBusy){
            createNoSampleRequestMessage();
            return;
        }else if(analyzerMessageSubtype == MessageSubtype.ConversationalPollReady){
            if (limsHasSamplesToSend) {
                createSampleRequestMessage();
            } else {
                createNoSampleRequestMessage();
            }
            return;
        }
    }

    private void createNoSampleRequestMessage() {
        System.out.println("createNoSampleRequestMessage");
        limsMessageType = MessageType.SampleRequest;
        limsMessageSubtype = MessageSubtype.SampleRequestsNo;
        responseFields.put(0, "N");
    }

    private void createSampleRequestMessage() {
        System.out.println("createSampleRequestMessage");
        if (limsTests == null || limsTests.isEmpty()) {
            createNoSampleRequestMessage();
            return;
        }
        limsMessageType = MessageType.SampleRequest;
        limsMessageSubtype = MessageSubtype.SampleRequestYes;
        responseFields.put(0, "D");
        responseFields.put(1, "0");
        responseFields.put(2, "0");
        if (toDeleteSampleRequest) {
            responseFields.put(3, "D");
        } else {
            responseFields.put(3, "A");
        }
        responseFields.put(4, limsPatientId);
        responseFields.put(5, limsSampleId);
        responseFields.put(6, analyzerSampleType.getFiledValue());
        responseFields.put(7, "");
        responseFields.put(8, analyzerPriority.getValue() + "");
        responseFields.put(9, "1");
        responseFields.put(10, "**");
        responseFields.put(11, "1");
        responseFields.put(12, limsTests.size() + "");
        int temTestCount = 13;
        for (String t : limsTests) {
            responseFields.put(temTestCount, t);
            temTestCount++;
        }

    }

    private void convertSampleStringToSampleType() {
        if (limsSampleType == null) {
            analyzerSampleType = null;
        } else if (limsSampleType.toLowerCase().contains("blood")) {
            analyzerSampleType = SampleTypeForDimension.W;
        } else if (limsSampleType.toLowerCase().contains("Serum")) {
            analyzerSampleType = SampleTypeForDimension.One;
        } else if (limsSampleType.toLowerCase().contains("Plasma")) {
            analyzerSampleType = SampleTypeForDimension.Two;
        } else if (limsSampleType.toLowerCase().contains("Urine")) {
            analyzerSampleType = SampleTypeForDimension.Three;
        } else if (limsSampleType.toLowerCase().contains("CSF")) {
            analyzerSampleType = SampleTypeForDimension.Four;
        }
    }

    private void createResponseString() {
        String temRs = "";
        System.out.println("responseFields.size() = " + responseFields.size());
        for (int i = 0; i < responseFields.size(); i++) {
            temRs +=  responseFields.get(i) + (char) 28 ;
            System.out.println("responseFields.get(i) = " + responseFields.get(i));
        }
        
        System.out.println("temRs = " + temRs);
        String checkSum = calculateChecksum(temRs);
        System.out.println("checkSum = " + checkSum);
        temRs = (char) 2 + temRs + checkSum + (char) 3;
        System.out.println("temRs = " + temRs);
        byte[] temRes = temRs.getBytes(StandardCharsets.US_ASCII);
        System.out.println("temRes = " + temRes);
        temRs = "";
        for(Byte b:temRes){
            temRs += b +"+" ;
            System.out.println("b = " + b);
        }
        System.out.println("temRs = " + temRs);
        responseString = temRs;
    }

    
   
    
    
    public String calculateChecksum(String input, boolean replaceFieldSeperator) {
        String ip = input;
        String fs = (char) 28 + "";
        ip = ip.replaceAll("<FS>", fs);
        return calculateChecksum(ip);
    }

    public String calculateChecksum(String input) {
        byte[] temBytes = stringToByteArray(input);
        return calculateChecksum(temBytes);
    }

    public String calculateChecksum(byte[] bytes) {
        long checksum = 0;
        for (int i = 0; i < bytes.length; i++) {
            checksum += (bytes[i] & 0xffffffffL);
        }
        int integerChecksum = (int) checksum;
        String hexChecksum = Integer.toHexString(integerChecksum).toUpperCase();
        return hexChecksum.substring(Math.max(hexChecksum.length() - 2, 0));
    }

    private byte[] stringToByteArray(String s) {
        return s.getBytes();
    }

    public boolean isCorrectReport() {
        System.out.println("Checking wether the report is Correct");
        boolean flag = true;

        return true;
    }

    private void textToByteArraySeperatedByPlus() {
        bytes = new ArrayList<>();
        String strInput = inputStringBytesPlusSeperated;
        String[] strByte = strInput.split(Pattern.quote("+"));
        for (String s : strByte) {
            try {
                Byte b = Byte.parseByte(s);
                bytes.add(b);
            } catch (Exception e) {
//                System.out.println("e = " + e);
                bytes.add(null);
            }
        }
    }

    private void textToByteArraySeperatedBySpace() {
        bytes = new ArrayList<>();
        String strInput = inputStringBytesSpaceSeperated;
        String[] strByte = strInput.split("\\s+");
        for (String s : strByte) {
            try {
                Byte b = Byte.parseByte(s);
                bytes.add(b);
            } catch (Exception e) {
                bytes.add(null);
            }
        }
    }

    private Byte getByte(String input) {
        try {
            Byte b = Byte.parseByte(input);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getInteger(String input) {
        try {
            Integer b = Integer.parseInt(input);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private Double getDouble(String input) {
        try {
            Double b = Double.parseDouble(input);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private void textToByteArrayByCharactors() {
        bytes = new ArrayList<>();
        String strInput = inputStringCharactors;
        char[] strByte = strInput.toCharArray();
        for (char s : strByte) {
            try {
                Byte b = (byte) s;
                bytes.add(b);
            } catch (Exception e) {
//                System.out.println("e = " + e);
                bytes.add(null);
            }
        }
    }

    private void byteArrayToFields() {
        System.out.println("byteArrayToFields");
        List<Byte> temBytes = new ArrayList<>();
        requestFields = new HashMap<>();
        for (Byte b : bytes) {
            if (b !=2 && b != 3 && b != 5) {
                temBytes.add(b);
                System.out.println("b = " + b);
            }
        }
        String temStr = "";
        Integer i = 0;
        for (byte b : temBytes) {
            if (b == 28) {
                requestFields.put(i, temStr);
                System.out.println("temStr = " + temStr);
                i++;
                temStr = new String();
            } else {
                char c = (char) b;
                temStr += c;
            }
        }
        fieldCount = i;
        System.out.println("fieldCount = " + fieldCount);
        System.out.println("requestFields.size() = " + requestFields.size());
    }

    public String addDecimalSeperator(String val) {
        String formatString = "#,###";
        Double dbl = Double.parseDouble(val);
        DecimalFormat formatter = new DecimalFormat(formatString);
        return formatter.format(dbl);
    }

    private String round(double value, int places) {
        String returnVal = "";
        if (places == 0) {
            returnVal = ((long) value) + "";
        } else if (places < 0) {
            long tn = (long) value;
            long pow = (long) Math.pow(10, Math.abs(places));
            tn = (tn / pow) * pow;
            returnVal = tn + "";
        } else {
            BigDecimal bd = new BigDecimal(value);
            bd = bd.setScale(places, RoundingMode.HALF_UP);
            returnVal = bd.doubleValue() + "";
        }
        return returnVal;
    }

    public String getInputStringBytesSpaceSeperated() {
        return inputStringBytesSpaceSeperated;
    }

    public void setInputStringBytesSpaceSeperated(String inputStringBytesSpaceSeperated) {
        this.inputStringBytesSpaceSeperated = inputStringBytesSpaceSeperated;
    }

    public List<Byte> getBytes() {
        return bytes;
    }

    public void setBytes(List<Byte> bytes) {
        this.bytes = bytes;
    }

    public String getInputStringBytesPlusSeperated() {
        return inputStringBytesPlusSeperated;
    }

    public void setInputStringBytesPlusSeperated(String inputStringBytesPlusSeperated) {
        this.inputStringBytesPlusSeperated = inputStringBytesPlusSeperated;
        textToByteArraySeperatedByPlus();
    }

    public String getInputStringCharactors() {
        return inputStringCharactors;
    }

    public void setInputStringCharactors(String inputStringCharactors) {
        this.inputStringCharactors = inputStringCharactors;
        textToByteArrayByCharactors();
    }

    public MessageType getAnalyzerMessageType() {
        return analyzerMessageType;
    }

    public void setAnalyzerMessageType(MessageType analyzerMessageType) {
        this.analyzerMessageType = analyzerMessageType;
    }

    public MessageSubtype getAnalyzerMessageSubtype() {
        return analyzerMessageSubtype;
    }

    public void setAnalyzerMessageSubtype(MessageSubtype analyzerMessageSubtype) {
        this.analyzerMessageSubtype = analyzerMessageSubtype;
    }

    public int getFieldCount() {
        return fieldCount;
    }

    public void setFieldCount(int fieldCount) {
        this.fieldCount = fieldCount;
    }

    public Map<Integer, String> getRequestFields() {
        return requestFields;
    }

    public void setRequestFields(Map<Integer, String> requestFields) {
        this.requestFields = requestFields;
    }

    public Map<Integer, String> getResponseFields() {
        return responseFields;
    }

    public void setResponseFields(Map<Integer, String> responseFields) {
        this.responseFields = responseFields;
    }

    public Boolean getLimsHasSamplesToSend() {
        return limsHasSamplesToSend;
    }

    public void setLimsHasSamplesToSend(Boolean limsHasSamplesToSend) {
        this.limsHasSamplesToSend = limsHasSamplesToSend;
    }

    public Boolean getToDeleteSampleRequest() {
        return toDeleteSampleRequest;
    }

    public void setToDeleteSampleRequest(Boolean toDeleteSampleRequest) {
        this.toDeleteSampleRequest = toDeleteSampleRequest;
    }

    public String getLimsPatientId() {
        return limsPatientId;
    }

    public void setLimsPatientId(String limsPatientId) {
        this.limsPatientId = limsPatientId;
    }

    public String getAnalyzerPatientId() {
        return analyzerPatientId;
    }

    public void setAnalyzerPatientId(String analyzerPatientId) {
        this.analyzerPatientId = analyzerPatientId;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(String instrumentId) {
        this.instrumentId = instrumentId;
    }

    public Byte getFirstPollValue() {
        return firstPollValue;
    }

    public void setFirstPollValue(Byte firstPollValue) {
        this.firstPollValue = firstPollValue;
    }

    public Byte getRequestValue() {
        return requestValue;
    }

    public void setRequestValue(Byte requestValue) {
        this.requestValue = requestValue;
    }

    public String getLimsSampleId() {
        return limsSampleId;
    }

    public void setLimsSampleId(String limsSampleId) {
        this.limsSampleId = limsSampleId;
    }

    public String getAnalyzerSampleId() {
        return analyzerSampleId;
    }

    public void setAnalyzerSampleId(String analyzerSampleId) {
        this.analyzerSampleId = analyzerSampleId;
    }

    public String getLimsSampleType() {
        return limsSampleType;
    }

    public void setLimsSampleType(String limsSampleType) {
        this.limsSampleType = limsSampleType;
        convertSampleStringToSampleType();
    }

    public SampleTypeForDimension getAnalyzerSampleType() {
        return analyzerSampleType;
    }

    public void setAnalyzerSampleType(SampleTypeForDimension analyzerSampleType) {
        this.analyzerSampleType = analyzerSampleType;
    }

    public DimensionPriority getAnalyzerPriority() {
        return analyzerPriority;
    }

    public void setAnalyzerPriority(DimensionPriority analyzerPriority) {
        this.analyzerPriority = analyzerPriority;
    }

    public Priority getLimsPriority() {
        return limsPriority;
    }

    public void setLimsPriority(Priority limsPriority) {
        this.limsPriority = limsPriority;
        switch (limsPriority) {
            case Asap:
                analyzerPriority = DimensionPriority.Two;
                break;
            case Stat:
                analyzerPriority = DimensionPriority.One;
                break;
            case Routeine:
                analyzerPriority = DimensionPriority.Zero;
                break;
        }
    }

    public List<String> getLimsTests() {
        return limsTests;
    }

    public void setLimsTests(List<String> limsTests) {
        this.limsTests = limsTests;
    }

    public String getResponseString() {
        return responseString;
    }

    public void setResponseString(String responseString) {
        this.responseString = responseString;
    }

    public String getRequestAcceptanceStatus() {
        return requestAcceptanceStatus;
    }

    public void setRequestAcceptanceStatus(String requestAcceptanceStatus) {
        this.requestAcceptanceStatus = requestAcceptanceStatus;
    }

    public MessageType getLimsMessageType() {
        return limsMessageType;
    }

    public void setLimsMessageType(MessageType limsMessageType) {
        this.limsMessageType = limsMessageType;
    }

    public MessageSubtype getLimsMessageSubtype() {
        return limsMessageSubtype;
    }

    public void setLimsMessageSubtype(MessageSubtype limsMessageSubtype) {
        this.limsMessageSubtype = limsMessageSubtype;
    }

}
