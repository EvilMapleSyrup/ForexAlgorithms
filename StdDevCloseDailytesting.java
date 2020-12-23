package jforex;

import java.util.*;
import java.text.*;
import java.math.*;

import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.*;

public class StdDevCloseDailytesting implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    public Instrument[] instruArray = {Instrument.USDCAD, Instrument.EURUSD, Instrument.GBPUSD, Instrument.USDCHF, Instrument.NZDUSD, Instrument.EURCAD, Instrument.AUDUSD, Instrument.AUDCAD, Instrument.CADCHF};   
    public Instrument[] testArray = {Instrument.USDCAD};   
    public List<Instrument> instruList = Arrays.asList(instruArray);
    public List<Instrument> testList = Arrays.asList(testArray);
    public Period myPeriod = Period.DAILY;
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
    @Configurable(value="Slippage")
    public int slippageForOrder = 1;
    @Configurable(value="Lot Multiplier")
    public double lotMulti = 1;
    public int orderNumber;
    public int check1=0;
    public int check2=0;
    public double accountEQHigh = 0;
    public double accountDefHigh = 0;
    public double accountLeverageHigh = 0;
    public double accountSize = 0;
    public boolean _20PDef = false;
    public boolean _40PDef = false;
    public boolean _50PDef = false;
    public boolean _60PDef = false;
    public boolean _80PDef = false;
    public boolean _90PDef = false;
    
    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
        accountSize = history.getEquity();
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
        accountDefHigh= accountDefHigh*100;
        console.getOut().println("check value 1: " + check1);
        console.getOut().println("20%: " + _20PDef + " 40%: " + _40PDef + " 50%: " + _50PDef + " 60%: " + _60PDef + " 80%: " + _80PDef + " 90%: " + _90PDef);
        console.getOut().println("Percentage Diff: " + accountDefHigh);
        console.getOut().println("Used Leverage High: " + accountLeverageHigh);
        console.getOut().println("Account Balance: " + history.getEquity());


    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }
    
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (!testList.contains(instrument) || !period.equals(myPeriod)){
                return;
        }
        IAccount thisAccount = context.getAccount();
        double tempLeverage = thisAccount.getUseOfLeverage();
        if (tempLeverage > accountLeverageHigh){
            accountLeverageHigh = tempLeverage;
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
            //_20PDef = false;
            //_40PDef = false;
            //_50PDef = false;
            //_60PDef = false;
            //_80PDef = false;
            //_90PDef = false;
        }        
        
        double percentageDif = (accountEQHigh/accountEQ) - 1;
        if (percentageDif > accountDefHigh){
            accountDefHigh =  percentageDif;
        }
        if (accountEQ < accountEQHigh - accountSize){
            check1++;
            //console.getOut().println("Current acc balance: " + accountEQ + " Check amount: " + check1++);
        }
        if (accountEQ < accountEQHigh - (accountEQHigh*0.20)){
            _20PDef = true;
        }
        if (accountEQ < accountEQHigh - (accountEQHigh*0.40)){
            _40PDef = true;
        }
        if (accountEQ < accountEQHigh - (accountEQHigh*0.50)){
            _50PDef = true;
        }
        if (accountEQ < accountEQHigh - (accountEQHigh*0.60)){
            _60PDef = true;
        }
        if (accountEQ < accountEQHigh - (accountEQHigh*0.80)){
            _80PDef = true;
        }
        if (accountEQ < accountEQHigh - (accountEQHigh*0.90)){
            _90PDef = true;
        }
        
        if (accountEQ > 1000){
            orderAmount = 0.001 * lotMulti;        
        }
        if (accountEQ > 2500){
            orderAmount = 0.0025 * lotMulti;        
        }         
        if (accountEQ > 5000){
            orderAmount = 0.005 * lotMulti;        
        } 
        if (accountEQ > 10000){
            orderAmount = 0.01 * lotMulti;        
        }  
        if (accountEQ > 25000){
            orderAmount = 0.025 * lotMulti;        
        }  
        if (accountEQ > 50000){
            orderAmount = 0.05 * lotMulti;        
        }  
        if (accountEQ > 100000){
            orderAmount = 0.075 * lotMulti;        
        }        
        if (accountEQ > 200000){
            orderAmount = 0.15 * lotMulti;        
        }
        if (accountEQ > 300000){
            orderAmount = 0.25 * lotMulti;        
        }
        if (accountEQ > 400000){
            orderAmount = 0.3 * lotMulti;
        }
        if (accountEQ > 500000){
            orderAmount = 0.35 * lotMulti;
        }
        if (accountEQ > 600000){
            orderAmount = 0.4 * lotMulti;
        }
        if (accountEQ > 700000){
            orderAmount = 0.45 * lotMulti;
        }
        if (accountEQ > 850000){
            orderAmount = 0.5 * lotMulti;
        }
        if (accountEQ > 1000000){
            orderAmount = 0.6 * lotMulti;
        }
        if (accountEQ > 1500000){
            orderAmount = 0.65 * lotMulti;
        }
        if (accountEQ > 2000000){
            orderAmount = 0.7 * lotMulti;
        }
        if (accountEQ > 2500000){
            orderAmount = 0.75 * lotMulti;
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
                    
                    engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUY, orderAmount, 0, slippageForOrder, shortSL, shortTP);             
                    
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

                    engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELL, orderAmount, 0, slippageForOrder, shortSL, shortTP);
                }
                

            }        
        }
        
    }
}