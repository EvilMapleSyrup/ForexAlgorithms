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

public class GapTrader implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    private DecimalFormat df = new DecimalFormat("#.#####");                
    public Instrument[] instruArray = {Instrument.USDCAD, Instrument.EURUSD, Instrument.GBPUSD, Instrument.USDCHF, Instrument.NZDUSD, Instrument.EURCAD, Instrument.AUDUSD, Instrument.AUDCAD, Instrument.CADCHF, Instrument.GBPCAD, Instrument.GBPAUD};   
    public Instrument[] testArray = {Instrument.USDCAD};   
    public List<Instrument> instruList = Arrays.asList(instruArray);
    public List<Instrument> testList = Arrays.asList(testArray);
    public Period myPeriod = Period.ONE_HOUR;
    int orderNumber = 0;
    @Configurable(value="Min Size")
    public double minSize = 5;
    @Configurable(value="Max SL")
    public int maxSL = 40;
    public double orderAmount = 0.001;
    public double accountEQHigh = 0;
    public double accountDefHigh = 0;
    public double accountLeverageHigh = 0;
    public double accountSize = 0;
    @Configurable(value="Account Lot Divisor")
    public double accountLotDivisor = 1000000;        
    
    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
        df.setRoundingMode(RoundingMode.CEILING);
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
    
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (!testList.contains(instrument) || !period.equals(myPeriod)){
                return;
        }        
        
        //stats keeping
        double accountEQ = history.getEquity();        
        double tempOrderNumber = accountEQ/accountLotDivisor;
        orderAmount = Double.valueOf(df.format(tempOrderNumber));

        IAccount thisAccount = context.getAccount();
        double tempLeverage = thisAccount.getUseOfLeverage();
        if (tempLeverage > accountLeverageHigh){
            accountLeverageHigh = tempLeverage;
        }

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
        
        String orderDescription = instrument.getName();
        Random rand = new Random();
        int randomOrderNumber = rand.nextInt(101);
        String alteredOD = orderDescription.substring(0, 3) + orderDescription.substring(4, 7) + randomOrderNumber;  
        
        //logic
                        
        IBar recentGapMINBar = history.getBar(instrument, Period.ONE_MIN, OfferSide.BID, 1);
        IBar prevToGapMINBar = history.getBar(instrument, Period.ONE_MIN, OfferSide.BID, 2);
        IBar recentGapBar = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
        IBar prevToGapBar = history.getBar(instrument, myPeriod, OfferSide.BID, 2);                 
        
        double difference = recentGapBar.getOpen() - prevToGapBar.getClose();
        double gapTP = 0;
        double gapSL = 0;
        if (difference < 0) { difference *= -1; }
        
        if (difference > (instrument.getPipValue()*minSize)){
            if (recentGapBar.getOpen() > prevToGapBar.getClose()){ //BEAR Gap
                gapTP = ((recentGapBar.getOpen() - prevToGapBar.getLow())/2) + prevToGapBar.getLow();
                gapTP = Double.valueOf(df.format(gapTP));
                gapSL = (recentGapBar.getOpen() + (instrument.getPipValue()*maxSL));
                gapSL = Double.valueOf(df.format(gapSL));
                orderNumber++;
                
                engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.SELL, orderAmount, 0, 1, gapSL, gapTP);
                
            }
            if (recentGapBar.getOpen() < prevToGapBar.getClose()){ //BULL Gap
                gapTP = ((prevToGapBar.getHigh() - recentGapBar.getOpen())/2) + recentGapBar.getOpen(); 
                gapTP = Double.valueOf(df.format(gapTP));
                gapSL = (recentGapBar.getOpen() - (instrument.getPipValue()*maxSL));                
                gapSL = Double.valueOf(df.format(gapSL));
                orderNumber++;

                engine.submitOrder("OrderN" + orderNumber + "_" + alteredOD, instrument, OrderCommand.BUY, orderAmount, 0, 1, gapSL, gapTP);
                                                
            }
        }
    }
    
    public double StandardDevCalc (Instrument instrument, double current, double previous) throws JFException{
        double result = 0;
        return result;
    }
}