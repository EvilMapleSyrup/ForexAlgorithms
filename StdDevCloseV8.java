package jforex;

import java.util.*;
import java.text.*;
import java.math.*;

import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.*;
import com.dukascopy.api.drawings.*;
import com.dukascopy.api.feed.*;
import com.dukascopy.api.feed.util.*;
import com.dukascopy.indicators.*;

public class StdDevCloseV8 implements IStrategy, IFeedListener {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    public Instrument[] instruArray = {Instrument.USDCAD, Instrument.EURUSD, Instrument.GBPUSD, Instrument.USDCHF, Instrument.NZDUSD, Instrument.EURCAD, Instrument.AUDUSD, Instrument.AUDCAD, Instrument.CADCHF, Instrument.GBPCAD, Instrument.GBPAUD};   
    public Instrument[] testArray = {Instrument.USDCAD};   
    public List<Instrument> instruList = Arrays.asList(instruArray);
    public List<Instrument> testList = Arrays.asList(testArray);
    public Period myPeriod = Period.ONE_HOUR;
    @Configurable(value="Sample Size")
    public int sampleSize = 100;
    @Configurable(value="Candle Threshold")
    public double candleThreshold = 0.71;    
    @Configurable(value="Lot Size")
    public double orderAmount = 0.001;
    @Configurable(value="Standard Deviation Multiplier")
    public double sDMult = 0.79;
    @Configurable(value="Max Sl")
    public double maxSLMult = 1500;
    @Configurable(value="Min SL Req")
    public double minSLReqPip = 37;
    @Configurable(value="Min SL")
    public double minSLMult = 100;
    //not sure if sl and tp multipliers work
    @Configurable(value="Sl Mult")
    public double slMult = 1.5;
    @Configurable(value="Tp Mult")
    public double tpMult = 1.543;
    @Configurable(value="Slippage")
    public int slippageForOrder = 1;
    @Configurable(value="Lot Multiplier")
    public double lotMulti = 1;
    @Configurable(value="Account Lot Divisor")
    public double accountLotDivisor = 1050000;
    @Configurable(value="EMA Size")
    public int eMASize = 200;
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
    IFeedDescriptor timePeriodAggregationFeedDescriptor;
    
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
        

        console.getOut().println("20%: " + _20PDef + " 40%: " + _40PDef + " 50%: " + _50PDef + " 60%: " + _60PDef + " 80%: " + _80PDef + " 90%: " + _90PDef);
        console.getOut().println("Percentage Diff: " + accountDefHigh);
        console.getOut().println("Used Leverage High: " + accountLeverageHigh);
        console.getOut().println("Account Balance: " + history.getEquity());
        console.getOut().println("---------------------------");

    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }
    
    @Override
    public void onFeedData(IFeedDescriptor feedDescriptor, ITimedData feedData){
        
    }
    
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (!instruList.contains(instrument) || !period.equals(myPeriod)){
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
        
        DecimalFormat df = new DecimalFormat("#.#####");                
        df.setRoundingMode(RoundingMode.CEILING);
        double accountEQ = history.getEquity();
        
        //EMA calc
        double[] hma200Test = indicators.hma(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, eMASize, Filter.WEEKENDS, 1, recentBar.getTime(),0);
        double hMADouble = Double.valueOf(df.format(hma200Test[0]));
        //engine.broadcast("EMA Value",(orderDescription + " Ema value: " + eMADouble));
      
        //Charting EMA manually
        //IChart chartToDraw = context.getChart(instrument);
        //IChartObjectFactory chartFactory = chartToDraw.getChartObjectFactory();
        //ITextChartObject eMAPoint = chartFactory.createText(("EMA:" + eMADouble + " " + instrument.getName()), recentBar.getTime(), eMADouble);
        //eMAPoint.setText(String.valueOf(new char[] {8226}));
        //chartToDraw.add(eMAPoint);
        
        if (accountEQ>accountEQHigh){
            accountEQHigh = accountEQ;
        }        
        
        double percentageDif = 0;
        if (accountEQ < accountEQHigh){
            double temp = accountEQHigh - accountEQ;
            double tempPerc = (temp/accountEQHigh) * 100;
            percentageDif = tempPerc;
        }
        
        if (accountDefHigh < percentageDif){
            accountDefHigh = percentageDif;
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
        
        
        
        if (accountEQ < 100000){
            double tempOrderNumber = accountEQ/accountLotDivisor;
            orderAmount = Double.valueOf(df.format(tempOrderNumber));
        }
        if (accountEQ > 100000){
            double tempOrderNumber = accountEQ/(accountLotDivisor + 250000);
            orderAmount = Double.valueOf(df.format(tempOrderNumber));
        }
        if (orderAmount > 3){
            orderAmount = 3;
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
                    
                    if (recentBar.getClose() > hMADouble){
                        orderAmount = Double.valueOf(df.format(orderAmount * 1.25));                        
                    }
                    
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

                    if (recentBar.getClose() < hMADouble){
                        orderAmount = Double.valueOf(df.format(orderAmount * 1.25));                        
                    }

                        engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELL, orderAmount, 0, slippageForOrder, shortSL, shortTP);
                   

                    
                }
                

            }        
        }
        
    }
}