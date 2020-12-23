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

public class VWAPScalper implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    public Instrument[] instruArray = {Instrument.USDCAD, Instrument.EURUSD, Instrument.GBPUSD, Instrument.USDCHF, Instrument.NZDUSD, Instrument.EURCAD, Instrument.AUDUSD, Instrument.AUDCAD, Instrument.CADCHF, Instrument.GBPCAD, Instrument.GBPAUD};   
    public Instrument[] testArray = {Instrument.EURUSD};   
    public List<Instrument> instruList = Arrays.asList(instruArray);
    public List<Instrument> testList = Arrays.asList(testArray);
    public Period myPeriod = Period.FIFTEEN_MINS; 
    
    @Configurable(value="Min ATR")
    public double minATR = 0.0006;
    double minTR = 0;
    @Configurable(value="Min Total Volume")
    public double minTotalVolume = 3500;
    @Configurable(value="TP Multiplier")
    public double tpMult = 1;
    @Configurable(value="ATR Increase")
    public double aTRIncrease = 0.002;
    @Configurable(value="Slippage")
    public int slippageForOrder = 1;
    @Configurable(value="Account Lot Divisor")
    public double accountLotDivisor = 100000;    
    @Configurable(value="Lot Multiplier")
    public double lotMulti = 1;
    double avgVolume = 0;
    double avgATR = 0;
    double avgTR = 0;
    int counterForAVG = 0;
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
        minTR = LinearFunc(minATR, 0, false);
        
        //sub to instruments
        Set<Instrument> instrumentHash = new HashSet<Instrument>();
        for (Instrument instru : testList){
            instrumentHash.add(instru);
        }                        
        context.setSubscribedInstruments(instrumentHash, true);   
        df.setRoundingMode(RoundingMode.CEILING);
        dfFine.setRoundingMode(RoundingMode.CEILING);
    
    }
    
    public double LinearFunc(double x, double y, boolean findingX) throws JFException{

        double m = 4.1666666666666666667;
        double b = 13.75;
        // y = 4.1666666~x - 13.75
        // y = TR, x = ATR
        if (findingX == true){
            y *= 10000;
            double pt1 = y + b;
            double pt2 = pt1 / m;
            double result = pt2 / 10000;
            return result;
        }else{
            x *= 10000;
            double pt1 = x * m;
            double pt2 = pt1 - b;        
            double result = pt2 / 10000;
            return result;
        }
        
        
        
        
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
    }
    
    @Override
    public void onStop() throws JFException {
        
        avgVolume = avgVolume / counterForAVG;
        avgATR = avgATR / counterForAVG;
        avgTR = avgTR / counterForAVG;
    
            
        console.getOut().println("AVG TR: " + avgTR + " ATR: " + avgATR + " Volume: " + avgVolume);
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
        
        double tempOrderNumber = accountEQ/accountLotDivisor;
        orderAmount = Double.valueOf(df.format(tempOrderNumber)); 
        
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
        //VariableManager(instrument);  
        VWAPTrade(instrument, alteredOD);
    }
    
    public void VariableManager(Instrument instrument) throws JFException{
        if (instrument.equals(Instrument.GBPUSD)) {
            minATR = 0.0013;
            minTotalVolume = 7500;
            tpMult = 0.5;
        }
        if (instrument.equals(Instrument.EURUSD)) {
            minATR = 0.0005;
            minTotalVolume = 11500;
            tpMult = 1;
        }        
        if (instrument.equals(Instrument.USDCAD)) {
            minATR = 0.00065;
            minTotalVolume = 3750;
            tpMult = 1;
        }        
    }
    
    public void VWAPTrade(Instrument instrument, String alteredOD) throws JFException {
        IBar recentBar = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
        double recentOpen = recentBar.getOpen();
        double recentClose = recentBar.getClose();
        double recentHigh = recentBar.getHigh();
        double recentLow = recentBar.getLow();
        double recentDiff = recentHigh - recentLow;
        
        
        double currentTrueRange = (indicators.trange(instrument, myPeriod, OfferSide.BID, 1));
        double p1TrueRange = (indicators.trange(instrument, myPeriod, OfferSide.BID, 2));
        double p2TrueRange = (indicators.trange(instrument, myPeriod, OfferSide.BID, 3));
        
        double currentATR = indicators.atr(instrument, myPeriod, OfferSide.BID, 14, 1);
        double p1ATR = indicators.atr(instrument, myPeriod, OfferSide.BID, 14, 2);
        double p2ATR = indicators.atr(instrument, myPeriod, OfferSide.BID, 14, 3);                
        
        double vWAP = indicators.volumeWAP2(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.TYPICAL_PRICE, 1);
        //console.getOut().println("vwap: " + vWAP);
        
        double[] eMA200 = indicators.ema(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, Filter.WEEKENDS, 1, recentBar.getTime(), 0);
        double eMA200Double = eMA200[0];
        
        double askVolume = indicators.volume(instrument, myPeriod, OfferSide.ASK, 1);
        double bidVolume = indicators.volume(instrument, myPeriod, OfferSide.BID, 1);
        double volumeDiff = askVolume - bidVolume;
        double totalVolume = askVolume + bidVolume;
        //console.getOut().println("volume diff: " + volumeDiff);
        
        avgVolume += totalVolume;
        avgATR += currentATR;
        avgTR += currentTrueRange;
        counterForAVG++;
        double currentATRCalced = LinearFunc(currentATR,0,false);
        double p1ATRCalced = LinearFunc(p1ATR,0,false);
        //logic
        
        if (currentTrueRange > minTR && currentTrueRange > 0.0005 && totalVolume > minTotalVolume && currentTrueRange > currentATRCalced && p1TrueRange < p1ATRCalced ){
            if (volumeDiff < 0 && recentClose < vWAP){ // bear
                double tp = recentClose - (recentDiff * tpMult);
                double sl = recentClose + recentDiff;
                orderNumber++;
                engine.submitOrder(alteredOD, instrument, OrderCommand.SELL, orderAmount, 0, slippageForOrder, sl, tp);
            }
            if (volumeDiff > 0 && recentClose > vWAP){ // bear
                double tp = recentClose + (recentDiff * tpMult);
                double sl = recentClose - recentDiff;
                orderNumber++;
                engine.submitOrder(alteredOD, instrument, OrderCommand.BUY, orderAmount, 0, slippageForOrder, sl, tp);
            }            
        }
        
        
        
        

    }
}