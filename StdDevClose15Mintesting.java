package jforex;

import java.util.*;
import java.text.*;
import java.math.*;

import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.*;

public class StdDevClose15Mintesting implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    public Instrument[] instruArray = {Instrument.USDCAD, Instrument.EURUSD, Instrument.GBPUSD, Instrument.USDCHF, Instrument.NZDUSD, Instrument.EURCAD};   
    public Instrument[] testArray = {Instrument.XAUUSD};   
    public List<Instrument> instruList = Arrays.asList(instruArray);
    public List<Instrument> testList = Arrays.asList(testArray);
    public Period myPeriod = Period.FIFTEEN_MINS;
    @Configurable(value="Sample Size")
    public int sampleSize = 0;
    @Configurable(value="Candle Threshold")
    public double candleThreshold = 0;    
    @Configurable(value="Lot Size")
    public double orderAmount = 0;
    @Configurable(value="Standard Deviation Multiplier")
    public double sDMult = 1;
    @Configurable(value="Max Sl")
    public double maxSLMult = 50;
    @Configurable(value="Min SL Req")
    public double minSLReqPip = 10;
    @Configurable(value="Min SL")
    public double minSLMult = 50;
    //not sure if sl and tp multipliers work
    public double slMult = 1.5;
    @Configurable(value="Tp Mult")
    public double tpMult = 1.5;
    public int orderNumber;
    public int check1=0;
    public int check2=0;
    public double accountEQHigh = 0;
    @Configurable(value="Account Size")
    public double accountSize = 0;
    public boolean _50kDef = false;
    public boolean _75kDef = false;
    public boolean _100kDef = false;
    public boolean _125kDef = false;
    public boolean _150kDef = false;
    public boolean _175kDef = false;
    
    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
        //sub to instruments
        Set<Instrument> instrumentHash = new HashSet<Instrument>();
        for (Instrument instru : instruList){
            instrumentHash.add(instru);
        }
                        
        context.setSubscribedInstruments(instrumentHash, true);

        
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
    }

    public void onStop() throws JFException {
        console.getOut().println("50k: " + _50kDef + " 75k: " + _75kDef + " 100k: " + _100kDef + " 125k: " + _125kDef + " 150k: " + _150kDef + " 175k: " + _175kDef);
        console.getOut().println("Check N1: " + check1 + " Check N2: " + check2);
        console.getOut().println("Account Balance: " + history.getEquity());


    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }
    
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (!instruList.contains(instrument) || !period.equals(myPeriod)){
                return;
        }   
        IBar recentBar = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
        IBar previousBar = history.getBar(instrument, myPeriod, OfferSide.BID, 2);
        
        String orderDescription = instrument.getName();
        Random rand = new Random();
        int randomOrderNumber = rand.nextInt(101);
        String alteredOD = orderDescription.substring(0, 3) + orderDescription.substring(4, 7) + randomOrderNumber;

        double inputMaxSL = instrument.getPipValue() * maxSLMult;
        double currentPrice = history.getLastTick(instrument).getBid();
        //console.getOut().println(currentPrice);        
        //double inputMaxSLPrice = currentPrice-inputMaxSL;      
        
        DecimalFormat df = new DecimalFormat("#.#####");                
        df.setRoundingMode(RoundingMode.CEILING);
        double accountEQ = history.getEquity();
        if (accountEQ>accountEQHigh){
            accountEQHigh = accountEQ;
        }
        if (accountEQ < accountEQHigh - accountSize){
            check1++;
            console.getOut().println("Current acc balance: " + accountEQ + " Check amount: " + check1++);
        }
        if (accountEQ < accountEQHigh - 50000){
            _50kDef = true;
        }
        if (accountEQ < accountEQHigh - 75000){
            _75kDef = true;
        }
        if (accountEQ < accountEQHigh - 100000){
            _100kDef = true;
        }
        if (accountEQ < accountEQHigh - 125000){
            _125kDef = true;
        }
        if (accountEQ < accountEQHigh - 150000){
            _150kDef = true;
        }
        if (accountEQ < accountEQHigh - 175000){
            _175kDef = true;
        }

        double recentClose = recentBar.getClose();
        double previousClose = previousBar.getClose();
        
        double differenceOfRecent = recentClose - previousClose;
        //if (differenceOfRecent < 0){
        //    differenceOfRecent = differenceOfRecent *-1;
        //}
        //console.getOut().println("Here is the Recent Difference: " + differenceOfRecent);
        List<Double> stdevList = new ArrayList<>();            
        
        for (int i=2;i<sampleSize+1;i++){
                IBar tempBar = history.getBar(instrument, myPeriod, OfferSide.BID, i);
                IBar prevToTempBar = history.getBar(instrument, myPeriod, OfferSide.BID, i+1);
                double tempClose = tempBar.getClose();
                double prevToTempClose = prevToTempBar.getClose();
                
                double difference = tempClose - prevToTempClose;
                //if (difference < 0){
                //    difference = difference*-1;
                //}
                //console.getOut().println("Here is the difference: " + difference);
                stdevList.add(difference);
        }
        
        double sd = 0;
        double average = 0;
        for (int i=0; i<stdevList.size(); i++){           
            double number = stdevList.get(i);
            average = number + average;
        }
        average = average/stdevList.size();
        
        for (int i=0; i<stdevList.size(); i++){
            double presquare = (stdevList.get(i) - average);
            sd +=  (presquare * presquare) / stdevList.size();
        }
        double standardDeviation = Math.sqrt(sd);
        //console.getOut().println("this is the average: " + average);
        //console.getOut().println("this is the stDEV: " + standardDeviation);        
        
        if (differenceOfRecent > (candleThreshold/1000) || differenceOfRecent*-1>(candleThreshold/1000)){                       
            
            if (differenceOfRecent > average + (standardDeviation) * sDMult){
                double topWick = recentBar.getHigh() - recentBar.getClose();
                double bottomWick = recentBar.getOpen() - recentBar.getLow();
                
                if (bottomWick <= (topWick * 0.95)){
                   orderNumber++;
                    double slNumber = recentBar.getLow() - ((recentBar.getClose() - recentBar.getOpen()) * slMult);
                     
                    //console.getOut().println(recentBar.getLow() + " " + (recentBar.getLow() - slNumber)/instrument.getPipValue());
                    double shortSL = Double.valueOf(df.format(slNumber));                    
                    double tpNumber = recentBar.getClose() + ((recentBar.getClose() - recentBar.getOpen()) * tpMult);
                    double shortTP = Double.valueOf(df.format(tpNumber));
                    if ((recentBar.getLow() - shortSL) < (instrument.getPipValue()*minSLReqPip)){
                        shortSL = recentBar.getLow() - (instrument.getPipValue()*minSLMult);
                        //console.getOut().println("ping");                        
                    }                    

                    if ((recentBar.getClose() - shortSL)/instrument.getPipValue() > maxSLMult){
                        shortSL = recentBar.getClose() - (instrument.getPipValue()*maxSLMult);
                        //console.getOut().println("ping");
                    }
                    //console.getOut().println("SL current: " + shortSL + " Projected SL: " + inputMaxSLPrice);
                    
                    engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUY, orderAmount, 0, 0, shortSL, shortTP);
                    //console.getOut().println("OrderN: " + orderNumber + " Order Open: " + recentBar.getClose() + " SL: " + slNumber); 
                }
                

            }
            if (differenceOfRecent < average - (standardDeviation * sDMult)){
                double topWick = recentBar.getHigh() - recentBar.getOpen();
                double bottomWick = recentBar.getClose() - recentBar.getLow();
                
                if (topWick <= (bottomWick * 0.95)){
                    orderNumber++;
                    double slNumber = recentBar.getHigh() + ((recentBar.getOpen() - recentBar.getClose()) * slMult);
                    double shortSL = Double.valueOf(df.format(slNumber));
                    double tpNumber = recentBar.getClose() - ((recentBar.getOpen() - recentBar.getClose()) * tpMult); 
                    double shortTP = Double.valueOf(df.format(tpNumber));
                    if ((shortSL - recentBar.getHigh()) < (instrument.getPipValue()*minSLReqPip)){
                        shortSL = recentBar.getHigh() + (instrument.getPipValue()*minSLMult);
                    }
                    if ((shortSL - recentBar.getClose())/instrument.getPipValue() > maxSLMult){
                        shortSL = recentBar.getClose() + (instrument.getPipValue()*maxSLMult);
                        //console.getOut().println("ping");
                    }

                    engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELL, orderAmount, 0, 0, shortSL, shortTP);
                }
                

            }        
        }
        
    }
}