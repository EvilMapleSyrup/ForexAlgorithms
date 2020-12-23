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



public class EMA50Trader implements IStrategy {
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
    
    @Configurable(value="S/R Look-Back")
    public int sRLook = 50;
    @Configurable(value="EMA Size")
    public int eMASize = 50;
    @Configurable(value="Slippage")
    public int slippageForOrder = 1;
    @Configurable(value="Account Lot Divisor")
    public double accountLotDivisor = 1050000;
    public double orderAmount = 0;        
    public double accountEQHigh = 0;
    public double accountDifHigh = 0;
    public double accountEQLow = 0;
    public double accountLeverageHigh = 0;
    public double accountSize = 0;    
    public int orderNumber = 0;
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
        df.setRoundingMode(RoundingMode.CEILING);
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
    }

    public void onStop() throws JFException {
        console.getOut().println("Percentage Diff: " + accountDifHigh);
        console.getOut().println("Used Leverage High: " + accountLeverageHigh);
        console.getOut().println("Account Balance High: " + accountEQHigh);
        console.getOut().println("Account Balance Low: " + accountEQLow);        
        console.getOut().println("Account Balance: " + history.getEquity());
        console.getOut().println("---------------------------");        
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
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
        
        double percentageDif = 0;
        if (accountEQ < accountEQHigh){
            double temp = accountEQHigh - accountEQ;
            double tempPerc = (temp/accountEQHigh) * 100;
            percentageDif = tempPerc;
        }
        
        if (accountDifHigh < percentageDif){
            accountDifHigh = percentageDif;
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
    }
    
    public void MainTrader(Instrument instrument) throws JFException{
        
    }
    
    public double[] SRCheck (Instrument instrument) throws JFException{
        //first value is price value, second is amount of candles it fit
        IBar recentBar = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
        
        double recentClose = recentBar.getClose();
        
        double[] eMA50 = indicators.ema(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, eMASize, Filter.WEEKENDS, 5, recentBar.getTime(),0);
        double eMA50p0 = Double.valueOf(df.format(eMA50[0]));
        double eMA50p1 = Double.valueOf(df.format(eMA50[1]));
        double eMA50p2 = Double.valueOf(df.format(eMA50[2]));
        double eMA50p3 = Double.valueOf(df.format(eMA50[3]));
        double eMA50p4 = Double.valueOf(df.format(eMA50[4]));
    }
    
}