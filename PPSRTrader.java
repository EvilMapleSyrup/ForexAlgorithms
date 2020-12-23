package jforex;

import java.util.*;
import java.text.*;
import java.math.*;
import java.awt.Color;

import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.*;
import com.dukascopy.api.IOrder.*;
import static com.dukascopy.api.IOrder.State.*;
import com.dukascopy.api.drawings.*;
import com.dukascopy.api.feed.*;
import com.dukascopy.api.feed.util.*;
import com.dukascopy.indicators.*;

public class PPSRTrader implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    public Instrument[] instruArray = {Instrument.USDCAD, Instrument.EURUSD, Instrument.GBPUSD, Instrument.USDCHF, Instrument.NZDUSD, Instrument.EURCAD, Instrument.AUDUSD, Instrument.AUDCAD, Instrument.CADCHF, Instrument.GBPCAD, Instrument.GBPAUD};   
    //public Instrument[] testArray = {Instrument.USDCAD, Instrument.EURUSD, Instrument.GBPUSD, Instrument.NZDUSD, Instrument.USDCHF, Instrument.XAUUSD, Instrument.AUDUSD};   
    public Instrument[] testArray = {Instrument.USDCAD};   
    public List<Instrument> instruList = Arrays.asList(instruArray);
    public List<Instrument> testList = Arrays.asList(testArray);
    public Period myPeriod = Period.ONE_HOUR;    
    @Configurable(value="Compare")
    public int compareValue = 0;
    @Configurable(value="Min PP Size")
    public double minPPSize = 70;
    @Configurable(value="SMA2 Buffer Pips")
    public double bufferPips = 13;
    @Configurable(value="PivotPoint Buffer")
    public double pPBuffer = 10.5;
    @Configurable(value="Min Wick Size")
    public double minWickSizePips = 16;
    @Configurable(value="Min Candle Size")
    public double minCandleSizePips = 15;
    @Configurable(value="EMA200 Pos SL")
    public double eMA200SL = 15;
    @Configurable(value="TP Multiplier")
    public double tPMult = 1;
    @Configurable(value="SL Divisor")
    public double sLDiv = 1;
    @Configurable(value="Max SL")
    public double maxSL = 100;
    @Configurable(value="Slippage")
    public int slippageForOrder = 1;
    @Configurable(value="Account Lot Divisor")
    public double accountLotDivisor = 100000;    
    @Configurable(value="Lot Multiplier")
    public double lotMulti = 1;
    public int orderNumber;
    double grossGain;
    double grossLoss;
    double cagr;
    double profitFactor;
    public double orderAmount = 0.001;
    public double accountEQHigh = 0;
    public double accountEQLow = 0;
    public double accountDefHigh = 0;
    public double accountLeverageHigh = 0;
    public double accountStartSize = 0;
    public double prevAccountEQ = 0;
    public List<String> halvedList = new ArrayList<>();
    public List<IOrder> orderList = new ArrayList<IOrder>();
    public int profitCalcCount = 0;
    DecimalFormat df = new DecimalFormat("#.#####"); 
    DecimalFormat dfFine = new DecimalFormat("#.######"); 
    
    @Override
    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
        accountStartSize = history.getEquity();
        grossGain = 0;
        grossLoss = 0;
        cagr = 0;
        profitFactor= 0;
        
        //sub to instruments
        Set<Instrument> instrumentHash = new HashSet<Instrument>();
        for (Instrument instru : testList){
            instrumentHash.add(instru);
        }                        
        context.setSubscribedInstruments(instrumentHash, true);   
        df.setRoundingMode(RoundingMode.CEILING);
        dfFine.setRoundingMode(RoundingMode.CEILING);
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
    }
    
    @Override
    public void onStop() throws JFException {  
        console.getOut().println("Profit Factor: " + profitFactor);
        console.getOut().println("CARG: " + cagr);
        console.getOut().println("Percentage Diff: " + accountDefHigh);
        console.getOut().println("Used Leverage High: " + accountLeverageHigh);
        console.getOut().println("Account Balance High: " + accountEQHigh);
        console.getOut().println("Account Balance Low: " + accountEQLow);        
        console.getOut().println("Account Balance: " + history.getEquity());        
        console.getOut().println("---------------------------");             
    }    

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }
    
    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (!testList.contains(instrument) || !period.equals(myPeriod)){
                return;
        }
        //VariableManager(instrument);
        IAccount thisAccount = context.getAccount();
        double tempLeverage = thisAccount.getUseOfLeverage();
        if (tempLeverage > accountLeverageHigh){
            accountLeverageHigh = tempLeverage;
        }
        
        String orderDescription = instrument.getName();
        Random rand = new Random();
        int randomOrderNumber = rand.nextInt(101);
        String alteredOD = orderDescription.substring(0, 3) + orderDescription.substring(4, 7) + randomOrderNumber;            
        
        
        double accountEQ = history.getEquity();
        if (accountEQLow == 0){
            accountEQLow = history.getEquity();
        }
        
        if (accountEQ>accountEQHigh){
            accountEQHigh = accountEQ;
        }        
        if (accountEQ<accountEQLow){
            accountEQLow = accountEQ;
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
        
        IBar recentBar = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
        double recentClose = recentBar.getClose();
        
        for (IOrder order : engine.getOrders()){
            if (order.getInstrument() == instrument){                            
                double sl = order.getStopLossPrice();
                if (order.getState() == State.OPENED || order.getState() == State.FILLED){                            
                    if (order.isLong() == true){
                        if (recentClose < sl){
                            order.close();
                        }
                    }
                    if (order.isLong() == false){
                        if (recentClose > sl){
                            order.close();
                        }
                    }
                }
            }
        }
        
        

        PPSRMain(instrument,alteredOD);
        ProfitCalcs();
    }
    
    public void VariableManager(Instrument instrument) throws JFException{
        if (instrument.equals(Instrument.USDCAD)){
            pPBuffer = 10;
            bufferPips = 13;
            minWickSizePips = 5;
            minCandleSizePips = 5;
            tPMult = 1;
            accountLotDivisor = 100000 * lotMulti;
            maxSL = 100;
            minPPSize = 70;
            eMA200SL = 15;
        }
        if (instrument.equals(Instrument.EURUSD)){
            pPBuffer = 7.5;
            bufferPips = 9.5;
            minWickSizePips = 16;
            minCandleSizePips = 15;
            tPMult = 1;
            accountLotDivisor = 100000 * lotMulti;
            maxSL = 100;
            minPPSize = 70;
            eMA200SL = 15;
        }
        if (instrument.equals(Instrument.GBPUSD)){
            pPBuffer = 22;
            bufferPips = 16;
            minWickSizePips = 25;
            minCandleSizePips = 10;
            tPMult = 1;
            accountLotDivisor = 100000 * lotMulti;
            maxSL = 100;
            minPPSize = 70;
            eMA200SL = 15;
        }
        if (instrument.equals(Instrument.USDCHF)){
            pPBuffer = 16.5;
            bufferPips = 7;
            minWickSizePips = 14;
            minCandleSizePips = 25;
            tPMult = 1.95;
            accountLotDivisor = 100000 * lotMulti;
            maxSL = 100;
            minPPSize = 70;
            eMA200SL = 15;
        }        
        if (instrument.equals(Instrument.NZDUSD)){
            pPBuffer = 1;
            bufferPips = 4;
            minWickSizePips = 18;
            minCandleSizePips = 29;
            tPMult = 1;
            accountLotDivisor = 100000 * lotMulti;
            maxSL = 100;
            minPPSize = 70;
            eMA200SL = 15;
        }
        if (instrument.equals(Instrument.XAUUSD)){
            
            switch (compareValue){
                case 0:
                pPBuffer = 100;
                bufferPips = 400;
                minWickSizePips = 200;
                minCandleSizePips = 100;
                tPMult = 1;
                accountLotDivisor = 100000000 * lotMulti;
                maxSL = 1000;
                minPPSize = 750;
                eMA200SL = 150;                 
                break;
                
                case 1:
                pPBuffer = 250;
                bufferPips = 0;
                minWickSizePips = 500;
                minCandleSizePips = 450;
                tPMult = 1;
                accountLotDivisor = 100000000 * lotMulti;
                maxSL = 1000;
                minPPSize = 750;
                eMA200SL = 150;                
                break;
            }
            /*
            pPBuffer = 100;
            bufferPips = 400;
            minWickSizePips = 200;
            minCandleSizePips = 100;
            tPMult = 1;
            accountLotDivisor = 100000000;
            maxSL = 1000;
            minPPSize = 750;
            eMA200SL = 150;                      
            // below is more aggressive
            pPBuffer = 250;
            bufferPips = 0;
            minWickSizePips = 500;
            minCandleSizePips = 400;
            tPMult = 1;
            accountLotDivisor = 100000000;
            maxSL = 1000;
            minPPSize = 750;
            eMA200SL = 150;            
            
        }  
        if (instrument.equals(Instrument.AUDUSD)){
            pPBuffer = 1;
            bufferPips = 4;
            minWickSizePips = 8;
            minCandleSizePips = 11;
            tPMult = 1;
            accountLotDivisor = 100000 * lotMulti;
            maxSL = 100;
            minPPSize = 70;
            eMA200SL = 15;
        }*/
        }
        if (instrument.equals(Instrument.AUDUSD)){
            pPBuffer = 1;
            bufferPips = 16;
            minWickSizePips = 8;
            minCandleSizePips = 5;
            tPMult = 1;
            accountLotDivisor = 100000 * lotMulti;
            maxSL = 100;
            minPPSize = 70;
            eMA200SL = 15;
        }        
        double accountEQ = history.getEquity();
        double tempOrderNumber = accountEQ/accountLotDivisor;
        orderAmount = Double.valueOf(df.format(tempOrderNumber));      
    }
    
    public void OrderManager () throws JFException {
        for (IOrder order : orderList){
            if (order.getState() == IOrder.State.CLOSED){
                orderList.remove(order);
                //ProfitCalcs();
            }            
        }
    }
    
    public void ProfitCalcs() throws JFException{
        double tempGain = 0;
        double tempLoss = 0;
        for (IOrder order : engine.getOrders()){
            if (order.getState() == IOrder.State.FILLED){                            
                double profitLoss = order.getProfitLossInUSD();            
                if (profitLoss >= 0){
                    tempGain += profitLoss;
                }
                if (profitLoss <= 0){
                    profitLoss *= -1;
                    tempLoss += profitLoss;
                }
            }
        }
        if (tempGain > grossGain){
            grossGain = tempGain;
        }
        if (tempLoss > grossLoss){
            grossLoss = tempLoss;            
        }
        profitFactor = grossGain/grossLoss;
        double eq = history.getEquity();
        
        double x = eq/100000;
        double power = 0.2;        
        double preResult = Math.pow(x, power); //change 5 for different amounts of years

        cagr = Double.valueOf(df.format(preResult - 1));
        
    }    
    
    public void PPSRMain (Instrument instrument, String alteredOD) throws JFException {
        
        //Variable assignment + small logic
        VariableManager(instrument);
        IBar recentBar = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
        double recentClose = recentBar.getClose();
        double recentOpen = recentBar.getOpen();
        double recentHigh = recentBar.getHigh();
        double recentLow = recentBar.getLow();
        double difference = recentHigh - recentLow;
        double tpDifference = difference*tPMult;
        double differenceInPips = difference / instrument.getPipValue();
        double minWickSize = minWickSizePips * instrument.getPipValue();
        double wickSize = 0;
        
        double[] eMA200 = indicators.ema(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, Filter.WEEKENDS, 1, recentBar.getTime(), 0);
        double eMA200Double = eMA200[0];
        
        double[] sMA2High = indicators.sma(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.HIGH, 2, Filter.WEEKENDS, 1, recentBar.getTime(), 0);
        double sMA2HighDouble = (sMA2High[0] + (instrument.getPipValue() * bufferPips));
        double[] sMA2Low = indicators.sma(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.LOW, 2, Filter.WEEKENDS, 1, recentBar.getTime(), 0);
        double sMA2LowDouble = (sMA2Low[0] - (instrument.getPipValue() * bufferPips));
        
        double[] pivotPoints = indicators.pivot2(instrument, Period.DAILY, OfferSide.BID, 0); //mid levels are available, see documentation
        double pivotP = pivotPoints[0]; // center pivot point
        double pivotR1 = pivotPoints[1]; // resistance level 1 (over center)
        double pivotS1 = pivotPoints[2]; // support level 1 (under center)
        double pivotR2 = pivotPoints[3];
        double pivotS2 = pivotPoints[4];
        double pivotR3 = pivotPoints[5];
        double pivotS3 = pivotPoints[6];        
        
        //Logic
                
        int locationInt = 0;
        double pPBufferDec = pPBuffer * instrument.getPipValue();

        if (pivotR3 - pivotS3 > (instrument.getPipValue() * minPPSize) && differenceInPips > minCandleSizePips){
            if (recentHigh > sMA2HighDouble && eMA200Double > recentClose){
                
                if (recentClose > recentOpen){
                    wickSize = recentHigh - recentClose;
                }else if (recentClose < recentOpen){
                    wickSize = recentHigh - recentOpen;
                }
                if (recentHigh > pivotS3 - pPBufferDec && recentHigh < pivotS3 + pPBufferDec){ //this is correct
                    locationInt = 1;
                }
                if (recentHigh > pivotS2 - pPBufferDec && recentHigh < pivotS2 + pPBufferDec){
                    locationInt = 2;
                }
                if (recentHigh > pivotS1 - pPBufferDec && recentHigh < pivotS1 + pPBufferDec){
                    locationInt = 3;
                }
                if (recentHigh > pivotP - pPBufferDec && recentHigh < pivotP + pPBufferDec){
                    locationInt = 4;
                }
                if (recentHigh > pivotR1 - pPBufferDec && recentHigh < pivotR1 + pPBufferDec){
                    locationInt = 5;
                }                
                if (recentHigh > pivotR2 - pPBufferDec && recentHigh < pivotR2 + pPBufferDec){
                    locationInt = 6;
                }                
                if (recentHigh > pivotR3 - pPBufferDec && recentHigh < pivotR3 + pPBufferDec){
                    locationInt = 7;
                }            
                if (recentHigh > eMA200Double - pPBufferDec && recentHigh < eMA200Double + pPBufferDec){
                    locationInt = 8;
                }                
                switch(locationInt){                    
                    case 1:
                        if (pivotS3 > sMA2HighDouble && wickSize > (minWickSize)){
                            double orderEntry = recentLow;
                            double tP1 = orderEntry - tpDifference;
                            //double sL = pivotS3 + ((pivotS2 - pivotS3) / sLDiv);
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = (maxSL * instrument.getPipValue()) + orderEntry;
                            }else{
                                sL = difference + orderEntry;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELLSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;
                    case 2:
                        if (pivotS2 > sMA2HighDouble && wickSize > minWickSize){
                            double orderEntry = recentLow;
                            double tP1 = orderEntry - tpDifference;
                            //double sL = pivotS2 + ((pivotS1 - pivotS2) / sLDiv);
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = (maxSL * instrument.getPipValue()) + orderEntry;
                            }else{
                                sL = difference + orderEntry;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELLSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;                        
                    case 3:
                        if (pivotS1 > sMA2HighDouble && wickSize > minWickSize){
                            double orderEntry = recentLow;
                            double tP1 = orderEntry - tpDifference;
                            //double sL = pivotS1 + ((pivotP - pivotS1) / sLDiv);
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = (maxSL * instrument.getPipValue()) + orderEntry;
                            }else{
                                sL = difference + orderEntry;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELLSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;
                    case 4:
                        if (pivotP > sMA2HighDouble && wickSize > minWickSize){
                            double orderEntry = recentLow;
                            double tP1 = orderEntry - tpDifference;
                            //double sL = pivotP + ((pivotR1 - pivotP) / sLDiv);
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = (maxSL * instrument.getPipValue()) + orderEntry;
                            }else{
                                sL = difference + orderEntry;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELLSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;  
                    case 5:
                        if (pivotR1 > sMA2HighDouble && wickSize > minWickSize){
                            double orderEntry = recentLow;
                            double tP1 = orderEntry - tpDifference;
                            //double sL = pivotR1 + ((pivotR2 - pivotR1) / sLDiv);
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = (maxSL * instrument.getPipValue()) + orderEntry;
                            }else{
                                sL = difference + orderEntry;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELLSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;
                    case 6:
                        if (pivotR2 > sMA2HighDouble && wickSize > minWickSize){
                            double orderEntry = recentLow;
                            double tP1 = orderEntry - tpDifference;
                            //double sL = pivotR2 + ((pivotR3 - pivotR2) / sLDiv);
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = (maxSL * instrument.getPipValue()) + orderEntry;
                            }else{
                                sL = difference + orderEntry;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELLSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;
                    case 7:
                        if (pivotR3 > sMA2HighDouble && wickSize > minWickSize){
                            double orderEntry = recentLow;
                            double tP1 = orderEntry - tpDifference;
                            //double sL = pivotR3 + ((pivotR3 - pivotR2) / sLDiv);
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = (maxSL * instrument.getPipValue()) + orderEntry;
                            }else{
                                sL = difference + orderEntry;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELLSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;     
                    case 8:
                        if (eMA200Double > sMA2HighDouble && wickSize > minWickSize){
                            double orderEntry = recentLow;
                            double tP1 = orderEntry - tpDifference;
                            //double sL = eMA200Double + (eMA200SL * instrument.getPipValue());
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = (maxSL * instrument.getPipValue()) + orderEntry;
                            }else{
                                sL = difference + orderEntry;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELLSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;
                }
                    
            }
            if (recentLow < sMA2LowDouble && eMA200Double < recentClose){

                if (recentClose > recentOpen){
                    wickSize = recentOpen - recentLow;
                }else if (recentClose < recentOpen){
                    wickSize = recentClose - recentLow;
                }                
                if (recentLow > pivotS3 - pPBufferDec && recentLow < pivotS3 + pPBufferDec){ //this is correct
                    locationInt = 1;
                }
                if (recentLow > pivotS2 - pPBufferDec && recentLow < pivotS2 + pPBufferDec){
                    locationInt = 2;
                }
                if (recentLow > pivotS1 - pPBufferDec && recentLow < pivotS1 + pPBufferDec){
                    locationInt = 3;
                }
                if (recentLow > pivotP - pPBufferDec && recentLow < pivotP + pPBufferDec){
                    locationInt = 4;
                }
                if (recentLow > pivotR1 - pPBufferDec && recentLow < pivotR1 + pPBufferDec){
                    locationInt = 5;
                }                
                if (recentLow > pivotR2 - pPBufferDec && recentLow < pivotR2 + pPBufferDec){
                    locationInt = 6;
                }                
                if (recentLow > pivotR3 - pPBufferDec && recentLow < pivotR3 + pPBufferDec){
                    locationInt = 7;
                }            
                if (recentLow > eMA200Double - pPBufferDec && recentLow < eMA200Double + pPBufferDec){
                    locationInt = 8;
                }
                switch(locationInt){                    
                    case 1:
                        if (pivotS3 < sMA2LowDouble && wickSize > minWickSize){
                            double orderEntry = recentHigh;
                            double tP1 = orderEntry + difference;
                            //double sL = pivotS3 - ((pivotS2 - pivotS3) / sLDiv);
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = orderEntry - (maxSL * instrument.getPipValue());
                            }else{
                                sL = orderEntry - difference;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUYSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;
                    case 2:
                        if (pivotS2 < sMA2LowDouble && wickSize > minWickSize){
                            double orderEntry = recentHigh;
                            double tP1 = orderEntry + tpDifference;
                            //double sL = pivotS2 - ((pivotS1 - pivotS2) / sLDiv);
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = orderEntry - (maxSL * instrument.getPipValue());
                            }else{
                                sL = orderEntry - difference;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUYSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;                        
                    case 3:
                        if (pivotS1 < sMA2LowDouble && wickSize > minWickSize){
                            double orderEntry = recentHigh;
                            double tP1 = orderEntry + tpDifference;
                            //double sL = pivotS1 - ((pivotP - pivotS1) / sLDiv);
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = orderEntry - (maxSL * instrument.getPipValue());
                            }else{
                                sL = orderEntry - difference;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUYSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;
                    case 4:
                        if (pivotP < sMA2LowDouble && wickSize > minWickSize){
                            double orderEntry = recentHigh;
                            double tP1 = orderEntry + tpDifference;
                            //double sL = pivotP - ((pivotR1 - pivotP) / sLDiv);
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = orderEntry - (maxSL * instrument.getPipValue());
                            }else{
                                sL = orderEntry - difference;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUYSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;  
                    case 5:
                        if (pivotR1 < sMA2LowDouble && wickSize > minWickSize){
                            double orderEntry = recentHigh;
                            double tP1 = orderEntry + tpDifference;
                            //double sL = pivotR1 - ((pivotR2 - pivotR1) / sLDiv);
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = orderEntry - (maxSL * instrument.getPipValue());
                            }else{
                                sL = orderEntry - difference;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUYSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;
                    case 6:
                        if (pivotR2 < sMA2LowDouble && wickSize > minWickSize){
                            double orderEntry = recentHigh;
                            double tP1 = orderEntry + tpDifference;
                            //double sL = pivotR2 - ((pivotR3 - pivotR2) / sLDiv);
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = orderEntry - (maxSL * instrument.getPipValue());
                            }else{
                                sL = orderEntry - difference;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUYSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;
                    case 7:
                        if (pivotR3 < sMA2LowDouble && wickSize > minWickSize){
                            double orderEntry = recentHigh;
                            double tP1 = orderEntry + tpDifference;
                            //double sL = pivotR3 - ((pivotR3 - pivotR2) / sLDiv);
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = orderEntry - (maxSL * instrument.getPipValue());
                            }else{
                                sL = orderEntry - difference;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUYSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);
                        }
                        break;     
                    case 8:
                        if (eMA200Double < sMA2LowDouble && wickSize > minWickSize){
                            double orderEntry = recentHigh;
                            double tP1 = orderEntry + tpDifference;
                            //double sL = eMA200Double - (eMA200SL * instrument.getPipValue());
                            double sL;
                            if (differenceInPips > maxSL){
                                sL = orderEntry - (maxSL * instrument.getPipValue());
                            }else{
                                sL = orderEntry - difference;
                            }
                            
                            orderNumber++;
                            IOrder newOrder = engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUYSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP1);
                            orderList.add(newOrder);                            
                        }
                        break;
                }
            }
        }

    }
    
    public void SLManager(Instrument instrument) throws JFException{
        for (IOrder orders : engine.getOrders()){
            if (orders.getState() == IOrder.State.FILLED){
                IBar recentCandle = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
                IBar slCandle = history.getBar(instrument, myPeriod, OfferSide.BID, 4);
            
                String[] tpArray = orders.getComment().split(" ", 2);
                String orderLabel = orders.getLabel();
                boolean dupCheck = halvedList.contains(orderLabel);
                if (dupCheck == true){
                    return;
                }            
                double openPrice = orders.getOpenPrice();
                double tPZ1 = Double.valueOf(tpArray[0]);
                double tPZ2 = Double.valueOf(tpArray[1]);
                double partialAmount = (orders.getAmount()/2);
                
                if (halvedList.contains(orders.getLabel())){
                    return;
                }else{                                    
                    if (orders.isLong() == true){                    
                        if (recentCandle.getClose() > tPZ1){
                        
                            if (orders.getStopLossPrice() < openPrice){
                                
                                orders.close(partialAmount);                                    
                                orders.setStopLossPrice(openPrice, OfferSide.BID);   
                                if (slCandle.getLow() > orders.getStopLossPrice()){
                                    orders.setStopLossPrice(slCandle.getLow());
                                }                     
                                if (orders.getTakeProfitPrice() != 0){
                                    orders.setTakeProfitPrice(0);
                                }
                                halvedList.add(orders.getLabel());
                            }
                        }
                    }
                    if (orders.isLong() == false){
                        if (recentCandle.getClose() < tPZ1){
                            if (orders.getStopLossPrice() > openPrice){
                                orders.close(partialAmount);            
                                orders.setStopLossPrice(openPrice, OfferSide.BID);
                                if (slCandle.getHigh() < orders.getStopLossPrice()){
                                    orders.setStopLossPrice(slCandle.getHigh());
                                }            
                                if (orders.getTakeProfitPrice() != 0){
                                    orders.setTakeProfitPrice(0);         
                                }          
                                halvedList.add(orders.getLabel());         
                            }
                        }
                    }
                }            
            }
        }
    }
}