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

public class EMAScalp implements IStrategy {
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
    public Period myPeriod = Period.FIFTEEN_MINS;
    public Period higherPeriod = Period.ONE_HOUR;
    
    @Configurable(value="Lower EMA Addition")
    public int lowerEMAAdd = 28;
    @Configurable(value="Higher EMA Addition")
    public int higherEMAAdd = 6;
    @Configurable(value="Diff In Pips Min")
    public double diffInPipsMin = 14;
    @Configurable(value="Min EMA Slope")
    public double minEMASlope = 1;
    //@Configurable(value="EMA Diff")
    public double emaDiffAmount = 0.0001;
    @Configurable(value="Slippage")
    public int slippageForOrder = 1;
    @Configurable(value="Account Lot Divisor")
    public double accountLotDivisor = 100000;
    public boolean engulfingBool = false;
    public int orderNumber;
    public double orderAmount = 0.001;
    public double accountEQHigh = 0;
    public double accountEQLow = 0;
    public double accountDefHigh = 0;
    public double accountLeverageHigh = 0;
    public double accountSize = 0;
    public boolean tradeActiveX = false;
    public List<String> halvedList = new ArrayList<>();
    DecimalFormat df = new DecimalFormat("#.#####"); 
    DecimalFormat dfFine = new DecimalFormat("#.######"); 
    
    
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
        df.setRoundingMode(RoundingMode.CEILING);
        
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
    }

    public void onStop() throws JFException {
        console.getOut().println("Percentage Diff: " + accountDefHigh);
        console.getOut().println("Used Leverage High: " + accountLeverageHigh);
        console.getOut().println("Account Balance High: " + accountEQHigh);
        console.getOut().println("Account Balance Low: " + accountEQLow);        
        console.getOut().println("Account Balance: " + history.getEquity());        
        console.getOut().println("---------------------------");               
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
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
    
    public void Scalper(Instrument instrument, String alteredOD) throws JFException{
        
        IBar recentLowerBar = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
        IBar recentHigherBar = history.getBar(instrument, higherPeriod, OfferSide.BID, 1);        
        IBar recentDailyBar = history.getBar(instrument, Period.DAILY, OfferSide.BID, 1);
        IBar recentWeeklyBar = history.getBar(instrument, Period.WEEKLY, OfferSide.BID, 1);
        
        double recentLowerBarHigh = recentLowerBar.getHigh();
        double recentLowerBarLow = recentLowerBar.getLow();
        double recentLowerBarClose = recentLowerBar.getClose();
        double recentLowerBarOpen = recentLowerBar.getOpen();
        
        double recentHigherBarClose = recentHigherBar.getClose();  
        double recentHigherBarOpen = recentHigherBar.getOpen();
        
        double recentWeeklyClose = recentWeeklyBar.getClose();
        double recentWeeklyOpen = recentWeeklyBar.getOpen();
        
        double recentDailyClose = recentDailyBar.getClose();
        double recentDailyOpen = recentDailyBar.getOpen();

        boolean slopeCheck = false;
        
        //Lower Chart EMAs
        double[] eMALower8 = indicators.ema(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 8 + lowerEMAAdd, Filter.WEEKENDS, 2, recentLowerBar.getTime(),0);
        double eMALower8Double = Double.valueOf(df.format(eMALower8[0]));
        double[] eMALower13 = indicators.ema(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 13 + lowerEMAAdd, Filter.WEEKENDS, 2, recentLowerBar.getTime(),0);
        double eMALower13Double = Double.valueOf(df.format(eMALower13[0]));        
        double[] eMALower21 = indicators.ema(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 21 + lowerEMAAdd, Filter.WEEKENDS, 2, recentLowerBar.getTime(),0);
        double eMALower21Double = Double.valueOf(df.format(eMALower21[0]));        
        //Higher Chart EMAs
        double[] emaHigher8 = indicators.ema(instrument, higherPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 8 + higherEMAAdd, Filter.WEEKENDS, 1, recentLowerBar.getTime(),0);
        double eMAHigher8Double = Double.valueOf(df.format(emaHigher8[0]));        
        double[] emaHigher21 = indicators.ema(instrument, higherPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 21 + higherEMAAdd, Filter.WEEKENDS, 1, recentLowerBar.getTime(),0);
        double eMAHigher21Double = Double.valueOf(df.format(emaHigher21[0]));    
        
        double[] eMAWeekly8 = indicators.ema(instrument, Period.WEEKLY, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 8 + higherEMAAdd, Filter.WEEKENDS, 1, recentWeeklyBar.getTime(),0);
        double eMAWeekly8Double = Double.valueOf(df.format(eMAWeekly8[0]));        
        double[] eMAWeekly21 = indicators.ema(instrument, Period.WEEKLY, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 21 + higherEMAAdd, Filter.WEEKENDS, 1, recentWeeklyBar.getTime(),0);
        double eMAWeekly21Double = Double.valueOf(df.format(eMAWeekly21[0]));
        
        double[] eMADaily8 = indicators.ema(instrument, Period.DAILY, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 8 + higherEMAAdd, Filter.WEEKENDS, 1, recentDailyBar.getTime(),0);
        double eMADaily8Double = Double.valueOf(df.format(eMADaily8[0]));        
        double[] eMADaily21 = indicators.ema(instrument, Period.DAILY, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 21 + higherEMAAdd, Filter.WEEKENDS, 1, recentDailyBar.getTime(),0);
        double eMADaily21Double = Double.valueOf(df.format(eMADaily21[0]));
        
        double averageEMA = Double.valueOf(df.format((eMALower8Double + eMALower13Double + eMALower21Double)/3));
        double averagePreviousEMA = Double.valueOf(df.format((eMALower8[1] + eMALower13[1] + eMALower21[1])/3));
        
        //console.getOut().println("Average EMA 0: " + averageEMA + " Average EMA 1: " + averagePreviousEMA);
        
        if (averageEMA > averagePreviousEMA){ //bullish
            if (averageEMA - averagePreviousEMA > (instrument.getPipValue()*minEMASlope)){
                slopeCheck = true;
            }
        }
        
        if (averageEMA < averagePreviousEMA){ //bearish
            if (averagePreviousEMA - averageEMA > (instrument.getPipValue()*minEMASlope)){
                slopeCheck = true;
            }
        }
        
        if (recentLowerBarClose < eMALower21Double){
            for (IOrder bullOrders : engine.getOrders()){
                if(bullOrders.isLong() == true && bullOrders.getInstrument() == instrument){
                    bullOrders.close();
                }
            }
        }
        if (recentLowerBarClose > eMALower21Double){
            for (IOrder bearOrders : engine.getOrders()){
                if(bearOrders.isLong() == false && bearOrders.getInstrument() == instrument){
                    bearOrders.close();
                }
            }
        }
        //engulfing breakthrough
        
        if (recentLowerBarClose > recentLowerBarOpen && engulfingBool == true) { //bullish
            if (recentLowerBarOpen < eMALower21Double && recentLowerBarClose > eMALower21Double){
                double orderEntry = recentLowerBarHigh;
                double difference = (recentLowerBarHigh - recentLowerBarLow) / instrument.getPipValue();
                double sL = recentLowerBarLow;                
                double tP = Double.valueOf(dfFine.format(((recentLowerBarHigh - recentLowerBarLow) / instrument.getPipValue()) + orderEntry));
                double tP2 = Double.valueOf(dfFine.format(((recentLowerBarHigh - recentLowerBarLow) / instrument.getPipValue()) + tP));
                String commentString = tP + " " + tP2;
                orderNumber++;
                if (difference > diffInPipsMin){
                    engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUYSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP, 0, commentString);                
                }
            }
        }
        if (recentLowerBarClose < recentLowerBarOpen && engulfingBool == true) { //bearish
            if (recentLowerBarOpen > eMALower21Double && recentLowerBarClose < eMALower21Double){
                double orderEntry = recentLowerBarLow;
                double difference = (recentLowerBarHigh - recentLowerBarLow) / instrument.getPipValue();
                double sL = recentLowerBarHigh;                
                double tP = Double.valueOf(df.format((orderEntry - (recentLowerBarHigh - recentLowerBarLow) / instrument.getPipValue())));
                double tP2 = Double.valueOf(df.format((tP - (recentLowerBarHigh - recentLowerBarLow) / instrument.getPipValue())));
                String commentString = tP + " " + tP2;
                orderNumber++;
                if (difference > diffInPipsMin){
                    engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELLSTOP, orderAmount, orderEntry, slippageForOrder, sL, tP, 0, commentString);
                }
            }
        }
        
        
        if (recentHigherBarClose > eMAHigher21Double && recentHigherBarOpen > eMAHigher21Double && eMAHigher8Double > eMAHigher21Double){ //Buy
            
            if (recentLowerBarOpen > eMALower21Double && recentLowerBarLow <= eMALower8Double){
                double entryZone = 0;
                for (int i = 2; i<=6; i++){
                    IBar recentCheckBar = history.getBar(instrument, myPeriod, OfferSide.BID, i);
                    if (recentCheckBar.getHigh() > entryZone || entryZone == 0){
                        entryZone = recentCheckBar.getHigh();
                    }
                }
                if (recentDailyClose > eMADaily21Double && recentDailyOpen > eMADaily21Double && eMADaily8Double > eMADaily21Double){
                    orderAmount = Double.valueOf(df.format((orderAmount * 2)));
                }
                double preBuyStopPrice = entryZone + (1 * instrument.getPipValue());
                double buyStopPrice = Double.valueOf(df.format(preBuyStopPrice));
                
                double differenceInPips = (entryZone - recentLowerBarLow) / instrument.getPipValue();
                double preSL = recentLowerBarLow - (1 * instrument.getPipValue());
                double sL = Double.valueOf(df.format(preSL));
                
                double preTPZ1 = ((differenceInPips + 1) * instrument.getPipValue()) + entryZone;
                double preTPZ2 = (differenceInPips * instrument.getPipValue()) + preTPZ1;
                double tPZ1 = Double.valueOf(df.format(preTPZ1));
                double tPZ2 = Double.valueOf(df.format(preTPZ2));
                String commentString = tPZ1 + " " + tPZ2;
                orderNumber++;
                if (differenceInPips > diffInPipsMin && slopeCheck == true){
                    engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUYSTOP, orderAmount, buyStopPrice, slippageForOrder, sL, tPZ2, 0, commentString);                  
                }
                
            }
        }
        if (recentHigherBarClose < eMAHigher21Double && recentHigherBarOpen < eMAHigher21Double && eMAHigher8Double < eMAHigher21Double){ //Sell
            

            if (recentLowerBarClose < eMALower21Double && recentLowerBarHigh >= eMALower8Double){
                double entryZone = 0;
                for (int i = 2; i<=6; i++){
                    IBar recentCheckBar = history.getBar(instrument, myPeriod, OfferSide.BID, i);
                    if (recentCheckBar.getLow() < entryZone || entryZone == 0){
                        entryZone = recentCheckBar.getLow();
                    }
                }
                if (recentDailyClose < eMADaily21Double && recentDailyOpen < eMADaily21Double && eMADaily8Double < eMADaily21Double){
                    orderAmount = Double.valueOf(df.format((orderAmount * 2)));
                }
                //console.getOut().println("entryZone: " + entryZone);
                double preSellStopPrice = entryZone - (1 * instrument.getPipValue());
                double sellStopPrice = Double.valueOf(df.format(preSellStopPrice));
                
                double differenceInPips = (recentLowerBarHigh - entryZone) / instrument.getPipValue();
                
                double preSL = recentLowerBarHigh + (1 * instrument.getPipValue());
                double sL = Double.valueOf(df.format(preSL));
                
                double preTPZ1 =  entryZone - ((differenceInPips + 1) * instrument.getPipValue());
                double preTPZ2 = preTPZ1 - (differenceInPips * instrument.getPipValue());
                double tPZ1 = Double.valueOf(df.format(preTPZ1));
                double tPZ2 = Double.valueOf(df.format(preTPZ2));
                String commentString = tPZ1 + " " + tPZ2;
                orderNumber++;
                if(differenceInPips > diffInPipsMin && slopeCheck == true){
                    engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELLSTOP, orderAmount, sellStopPrice, slippageForOrder, sL, tPZ2, 0, commentString);                                    
                }
            }
        }
        
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
        
        if (accountEQ < 100000){
            double tempOrderNumber = accountEQ/accountLotDivisor;
            orderAmount = Double.valueOf(df.format(tempOrderNumber));
        }
        if (accountEQ > 100000){
            double tempOrderNumber = accountEQ/(accountLotDivisor + 250000);
            orderAmount = Double.valueOf(df.format(tempOrderNumber));
        }
        Scalper(instrument, alteredOD);
        SLManager(instrument);
    }
}