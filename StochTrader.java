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

public class StochTrader implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    public Instrument[] instruArray = {Instrument.USDCAD, Instrument.EURUSD, Instrument.GBPUSD, Instrument.USDCHF, Instrument.NZDUSD, Instrument.EURCAD, Instrument.AUDUSD, Instrument.AUDCAD, Instrument.CADCHF, Instrument.GBPCAD, Instrument.GBPAUD};   
    public Instrument[] testArray = {Instrument.AUDCAD};   
    public List<Instrument> instruList = Arrays.asList(instruArray);
    public List<Instrument> testList = Arrays.asList(testArray);
    public Period myPeriod = Period.FOUR_HOURS;
    
    @Configurable(value="Primed Threshold")
    public double primedThresh = 13;
    public boolean bearPrimed = false;
    public boolean bullPrimed = false;
    @Configurable(value="Slippage")
    public int slippageForOrder = 1;
    @Configurable(value="Account Lot Divisor")
    public double accountLotDivisor = 100000;    
    public int orderNumber;
    public double orderAmount = 0.001;
    public double accountEQHigh = 0;
    public double accountEQLow = 0;
    public double accountDefHigh = 0;
    public double accountLeverageHigh = 0;
    public double accountSize = 0;        
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
        
        IBar recentBar = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
        double recentClose = recentBar.getClose();
        
        /*for (IOrder order : engine.getOrders()){
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
        }*/
      StochTradeManager(instrument,alteredOD);
    }
    
    public void StochTradeManager(Instrument instrument, String alteredOD) throws JFException{
        
        IBar recentBar = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
        IBar previousBar = history.getBar(instrument, myPeriod, OfferSide.BID, 2);
        
        double[] stochArray = indicators.stoch(instrument, myPeriod, OfferSide.BID, 14, 3, IIndicators.MaType.SMA, 5, IIndicators.MaType.SMA, 1);
        double[] stochPrevArray = indicators.stoch(instrument, myPeriod, OfferSide.BID, 14, 3, IIndicators.MaType.SMA, 5, IIndicators.MaType.SMA, 2);
        double slowK = stochArray[0];
        double slowD = stochArray[1];
        double slowKM1 = stochPrevArray[0];
        double slowDM1 = stochPrevArray[1];
        //double slowKM2 = stochArray[2][0];
        //console.getOut().println("sk1 : " + slowK + " sk2: " + slowKM1);                
        double middleLine = 50;
        double buffer = 2;
        
        double[] eMA200 = indicators.ema(instrument, myPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, Filter.WEEKENDS, 1, recentBar.getTime(), 0);
        double eMA200Double = eMA200[0];
        

        
        if (slowK > 100 - primedThresh){
            bearPrimed = true;
        }
        if (slowK < 0 + primedThresh){
            bullPrimed = true;
        }
        
        if (recentBar.getClose() > eMA200Double){
            if (slowKM1 > slowDM1 && (slowK - buffer) < slowD){
                for (IOrder order : engine.getOrders()){
                    if (order.getInstrument() == instrument && order.isLong() == true){                        
                        order.close();
                    }                                            
                }
            }
            
            for (IOrder bearOrder : engine.getOrders()){
                if (bearOrder.getInstrument() == instrument && bearOrder.isLong() == false){
                    bearOrder.close();
                }
            }
            
            if (slowK > slowKM1 && slowK > slowD && slowK > middleLine && slowKM1 < middleLine && slowK > 0 && bullPrimed == true){
                orderNumber++;
                engine.submitOrder(alteredOD, instrument, OrderCommand.BUY, orderAmount, 0, slippageForOrder);
                bullPrimed = false;
            }
        }
        if (recentBar.getClose() < eMA200Double){
            if (slowKM1 < slowDM1 && (slowK + buffer) > slowD){
                for (IOrder order : engine.getOrders()){
                    if (order.getInstrument() == instrument && order.isLong() != true){                        
                        order.close();
                    }
                }
            }
            
            for (IOrder bullOrder : engine.getOrders()){
                if (bullOrder.getInstrument() == instrument && bullOrder.isLong() == true){
                    bullOrder.close();
                }
            }            
            
            if (slowK < slowKM1 && slowK < slowD && slowK < middleLine && slowKM1 > middleLine && slowK > 0 && bearPrimed == true){
                orderNumber++;
                engine.submitOrder(alteredOD, instrument, OrderCommand.SELL, orderAmount, 0, slippageForOrder);
                bearPrimed = false;
            }                 
        }
       
    }
}