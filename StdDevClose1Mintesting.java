package jforex;

import java.util.*;
import java.text.*;
import java.math.*;
import java.awt.Color;

import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.*;
import com.dukascopy.api.IOrder.*;
import com.dukascopy.api.drawings.*;
import com.dukascopy.api.feed.*;
import com.dukascopy.api.feed.util.*;
import com.dukascopy.indicators.*;

public class StdDevClose1Mintesting implements IStrategy, IFeedListener {
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
    public Period myPeriod = Period.ONE_MIN;
    @Configurable(value="Average Candle Size Min")
    public int averageCandleSizeMin = 40;
    @Configurable(value="tpCandleSizeDev")
    public double tpCandleSizeDev = 2;
    @Configurable(value="HighLowCheckSize")
    public int highLowCheckSize = 48;
    @Configurable(value="STDev Average Min")
    public double sTDevAverageMin = 75;
    @Configurable(value="Sample Size")
    public int sampleSize = 20;
    @Configurable(value="Candle Threshold")
    public double candleThreshold = 50;    
    @Configurable(value="Lot Size")
    public double orderAmount = 0.001;
    @Configurable(value="Standard Deviation Multiplier")
    public double sDMult = 0.79;
    @Configurable(value="Max Sl")
    public double maxSLMult = 1500;
    @Configurable(value="Min SL Req")
    public double minSLReqPip = 37;
    @Configurable(value="Min SL")
    public double minSL = 40;
    //not sure if sl and tp multipliers work
    //@Configurable(value="Sl Mult")
    public double slMult = 1.5;
    //@Configurable(value="Tp Mult")
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
    DecimalFormat df = new DecimalFormat("#.#####"); 
    
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
        

        console.getOut().println("Percentage Diff: " + accountDefHigh);
        console.getOut().println("Used Leverage High: " + accountLeverageHigh);
        console.getOut().println("Account Balance: " + history.getEquity());
        console.getOut().println("Account Balance 2: " + context.getAccount().getBalance());
        console.getOut().println("---------------------------");

    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }
    
    @Override
    public void onFeedData(IFeedDescriptor feedDescriptor, ITimedData feedData){
        
    }
    
    public double[] sTDevCalc(Instrument instrument, int barSelect, boolean differenceCheck) throws JFException{
                
        List<Double> stdevList = new ArrayList<>();            
        
        for (int i=2;i<sampleSize+1;i++){
                IBar tempBar = history.getBar(instrument, myPeriod, OfferSide.BID, i);
                IBar prevToTempBar = history.getBar(instrument, myPeriod, OfferSide.BID, i+1);
                
                double tempBarPart = 0;
                double prevToTempPart = 0;
                
                if (barSelect == 1){                    
                    tempBarPart = tempBar.getClose();
                    prevToTempPart = prevToTempBar.getClose();                    
                }
                if (barSelect == 2){                    
                    tempBarPart = tempBar.getOpen();
                    prevToTempPart = prevToTempBar.getOpen();                    
                }
                if (barSelect == 3){                    
                    tempBarPart = tempBar.getHigh();
                    prevToTempPart = prevToTempBar.getHigh();                    
                }                
                if (barSelect == 4){                    
                    tempBarPart = tempBar.getLow();
                    prevToTempPart = prevToTempBar.getLow();                    
                }
                if (barSelect == 5){
                    tempBarPart = ((tempBar.getHigh() - tempBar.getLow())/instrument.getPipValue());
                    if (tempBarPart < sTDevAverageMin){
                        tempBarPart = sTDevAverageMin;
                    }
                    //console.getOut().println("tbp: " + tempBarPart);
                    prevToTempPart = (prevToTempBar.getHigh() - prevToTempBar.getLow());
                    
                    if (tempBarPart < 0){
                        tempBarPart *= -1;
                    }
                    if (prevToTempPart < 0){
                        prevToTempPart *= -1;
                    }
                }
                if (differenceCheck == true){                                    
                    double difference = tempBarPart - prevToTempPart;
                    stdevList.add(difference);
                }
                if (differenceCheck == false){
                    stdevList.add(tempBarPart);
                    //console.getOut().println("temp bar part: " + tempBarPart);                    
                }


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
        double[] result = new double[2];
        result[0] = standardDeviation;
        result[1] = average;
        
        return result;
    }
    
    
    public void OrderCleanup(List<Instrument> instruList)throws JFException {
        for (Instrument instrument : instruList){
            List<IOrder> orderList = null;
            orderList = engine.getOrders(instrument);
            
            DecimalFormat df = new DecimalFormat("#.#####");                
            df.setRoundingMode(RoundingMode.CEILING);
            double accountEQ = history.getEquity();
            
            IBar recentBar = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
            IBar previousBar = history.getBar(instrument, myPeriod, OfferSide.BID, 2);
            
            double[] cEMA = indicators.ema(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, eMASize, Filter.WEEKENDS, 1, recentBar.getTime(),0);
            double cEMADouble = Double.valueOf(df.format(cEMA[0]));
            double[] cHMA = indicators.hma(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, eMASize, Filter.WEEKENDS, 1, recentBar.getTime(),0);
            double cHMADouble = Double.valueOf(df.format(cHMA[0])); 
            
            for (IOrder currentOrder : orderList){
                if (currentOrder.getStopLossPrice() == cEMADouble || currentOrder.getStopLossPrice() == cHMADouble){
                    return;
                }
                if (currentOrder.isLong() == true){
                    
                    
                    if (currentOrder.getStopLossPrice() < cEMADouble && cHMADouble > cEMADouble){
                        try{                                                                                  
                            currentOrder.setStopLossPrice(cEMADouble);
                        } catch(Exception e) {
                            return;
                        }
                    }else if (currentOrder.getStopLossPrice() < cEMADouble){
                        try{                                                                                  
                            currentOrder.setStopLossPrice(cHMADouble);
                        } catch(Exception e) {
                            return;
                        }                        
                    }
                }
                if (currentOrder.isLong() == false){
                    if (currentOrder.getStopLossPrice() > cEMADouble && cHMADouble < cEMADouble){
                        try{                                                                                  
                            currentOrder.setStopLossPrice(cEMADouble);
                        } catch(Exception e) {
                            return;
                        }
                    }else if (currentOrder.getStopLossPrice() > cEMADouble){
                        try{                                                                                  
                            currentOrder.setStopLossPrice(cHMADouble);
                        } catch(Exception e) {
                            return;
                        }                        
                    }
                }
            }
            
        }
    }
    
   
   public boolean HighLowCheck (Instrument instrument, boolean bullOrBear, int candleCheckNumber) throws JFException{
       
       IBar currentCandle = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
       double currentHigh = currentCandle.getHigh();
       double currentLow = currentCandle.getLow();
       
       
       for (int i = 2; i<candleCheckNumber; i++){
           IBar checkCandle = history.getBar(instrument, myPeriod, OfferSide.BID, i);
           double cHigh = checkCandle.getHigh();
           double cLow = checkCandle.getLow();
           
           if (bullOrBear == true && currentHigh < cHigh){
               return false;
           }
           if (bullOrBear == false && currentLow > cLow){
               return false;
           }                      
       }
       
       return true;

       
   }
   
    public void AverageCandleSTDev(double sTDev, double average, String alteredOD, Instrument instrument) throws JFException{
        
        IBar recentCandle = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
        IBar previousCandle = history.getBar(instrument, myPeriod, OfferSide.BID, 2);
        //average *= instrument.getPipScale();
        double candleSize = (recentCandle.getHigh()-recentCandle.getLow())/instrument.getPipValue();
        double shortSL = 0;
        double shortTP = 0;
        double currentClosePrice = recentCandle.getClose();
        double sTDevAndAverage = sTDev + average;
        
        double[] cEMA = indicators.ema(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, eMASize, Filter.WEEKENDS, 1, recentCandle.getTime(),0);
        double cEMADouble = Double.valueOf(df.format(cEMA[0]));
        double[] cHMA = indicators.hma(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, eMASize, Filter.WEEKENDS, 1, recentCandle.getTime(),0);
        double cHMADouble = Double.valueOf(df.format(cHMA[0]));
        
        if (candleSize > 0 && candleSize > averageCandleSizeMin){
            
            if (recentCandle.getClose() > cEMADouble){
                if (recentCandle.getClose() > (sTDevAndAverage * instrument.getPipValue()) + previousCandle.getClose() && HighLowCheck(instrument, true, highLowCheckSize) == true){
                    orderNumber++;
                    //console.getOut().println("candle size: " + candleSize + " average: " + average + " stDev: " + sTDev + " sTDev and average: " + (average + sTDev));
                    shortSL = Double.valueOf(df.format((recentCandle.getClose() - (minSL * instrument.getPipValue()))));
                    shortTP = Double.valueOf(df.format(((candleSize / tpCandleSizeDev) * instrument.getPipValue()) + recentCandle.getClose()));
                    engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUY, orderAmount, 0, slippageForOrder, shortSL, shortTP);                 
                }

            }
                                    
           if (recentCandle.getClose() < cEMADouble){
                if (recentCandle.getClose() < previousCandle.getClose() - (sTDevAndAverage * instrument.getPipValue()) && HighLowCheck(instrument, false, highLowCheckSize) == true){
                    orderNumber++;
                    //console.getOut().println("candle size: " + candleSize + " average: " + average + " stDev: " + sTDev + " sTDev and average: " + (average + sTDev));
                    shortSL = Double.valueOf(df.format((recentCandle.getClose() + (minSL * instrument.getPipValue()))));
                    shortTP = Double.valueOf(df.format((recentCandle.getClose() - (candleSize / tpCandleSizeDev) * instrument.getPipValue())));
                    engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELL, orderAmount, 0, slippageForOrder, shortSL, shortTP);                                     
                } 
            }
        }       
    }
    
    
    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (!testList.contains(instrument) || !period.equals(myPeriod)){
                return;
        }
        IAccount thisAccount = context.getAccount();
        double tempLeverage = thisAccount.getUseOfLeverage();
        if (tempLeverage > accountLeverageHigh){
            accountLeverageHigh = tempLeverage;
        }
        
        //orderCleanup(testList);

        IBar recentBar = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
        IBar previousBar = history.getBar(instrument, myPeriod, OfferSide.BID, 2);        
        
        String orderDescription = instrument.getName();
        Random rand = new Random();
        int randomOrderNumber = rand.nextInt(101);
        String alteredOD = orderDescription.substring(0, 3) + orderDescription.substring(4, 7) + randomOrderNumber;            
        
        //DecimalFormat df = new DecimalFormat("#.#####");                
        df.setRoundingMode(RoundingMode.CEILING);
        double accountEQ = history.getEquity();
        
        //EMA calc
        double[] ema200 = indicators.ema(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, eMASize, Filter.WEEKENDS, 1, recentBar.getTime(),0);
        double eMA200Double = Double.valueOf(df.format(ema200[0]));
        double[] hma200 = indicators.hma(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, eMASize, Filter.WEEKENDS, 1, recentBar.getTime(),0);
        double hMA200Double = Double.valueOf(df.format(hma200[0]));               
            
            
        
        /*
        IChart chartToDraw = context.getChart(instrument);
        IChartObjectFactory chartFactory = chartToDraw.getChartObjectFactory();
        ITextChartObject eMA200Point = chartFactory.createText(("EMA:" + eMA200Double + " " + instrument.getName()), recentBar.getTime(), eMA200Double);
        eMA200Point.setText(String.valueOf(new char[] {8226}));
        eMA200Point.setFontColor(Color.red);
        
        chartToDraw.add(eMA200Point);

        */

        
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
        /*

        List<Double> stdevList = new ArrayList<>();            
        
        for (int i=2;i<sampleSize+1;i++){
                IBar tempBar = history.getBar(instrument, myPeriod, OfferSide.BID, i);
                IBar prevToTempBar = history.getBar(instrument, myPeriod, OfferSide.BID, i+1);
                double tempClose = tempBar.getClose();
                double prevToTempClose = prevToTempBar.getClose();
                
                double difference = tempClose - prevToTempClose;

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
        */
        
        double[] sTDevCalcClose = sTDevCalc(instrument, 5, false);
        //1 = close, 2 = open, 3 = high, 4 = low
        double standardDeviation = sTDevCalcClose[0];
        double average = sTDevCalcClose[1];
        
        AverageCandleSTDev(standardDeviation, average, alteredOD, instrument);
      
        /*
        if (differenceOfRecent > (instrument.getPipValue()*candleThreshold) || differenceOfRecent*-1>(instrument.getPipValue()*candleThreshold)){                       
            
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
                        shortSL = recentBar.getLow() - (instrument.getPipValue()*minSL);
                        //console.getOut().println("ping");                        
                    }                    

                    if ((recentBar.getClose() - shortSL)/instrument.getPipValue() > maxSLMult){
                        shortSL = recentBar.getClose() - (instrument.getPipValue()*maxSLMult);
                        //console.getOut().println("ping");
                    }
                    //console.getOut().println("SL current: " + shortSL + " Projected SL: " + inputMaxSLPrice);
                    
                                                                                                    
                    if (recentBar.getClose() > hMA200Double){
                        orderAmount = Double.valueOf(df.format(orderAmount * 1.15));                        
                    }
                    if (recentBar.getClose() > hMA200Double && recentBar.getClose() > eMA200Double){
                        orderAmount = Double.valueOf(df.format(orderAmount * 1));
                    }
                    if (recentBar.getClose() > eMA200Double && recentBar.getClose() > hMA200Double){
                        engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUY, orderAmount, 0, slippageForOrder, shortSL, shortTP);                                     
                    }
                    

                    

                    
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
                        shortSL = recentBar.getHigh() + (instrument.getPipValue()*minSL);
                    }
                    if ((shortSL - recentBar.getClose())/instrument.getPipValue() > maxSLMult){
                        shortSL = recentBar.getClose() + (instrument.getPipValue()*maxSLMult);
                        //console.getOut().println("ping");
                    }
                   

                    if (recentBar.getClose() < hMA200Double){
                        orderAmount = Double.valueOf(df.format(orderAmount * 1.15));                        
                    }
                    
                    if (recentBar.getClose() < hMA200Double && recentBar.getClose() < eMA200Double){
                        orderAmount = Double.valueOf(df.format(orderAmount * 1));
                    }

                    if (recentBar.getClose() < eMA200Double && recentBar.getClose() < hMA200Double){                    
                        engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELL, orderAmount, 0, slippageForOrder, shortSL, shortTP);
                    }

                    
                }
                

            }        
        } 
       */ 
    }
}