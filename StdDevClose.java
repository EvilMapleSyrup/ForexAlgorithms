package jforex;

import java.util.*;
import java.text.*;

import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.*;

public class StdDevClose implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    public Instrument[] instruArray = {Instrument.USDCAD, Instrument.EURUSD, Instrument.GBPUSD, Instrument.USDCHF};   
    public Instrument[] testArray = {Instrument.USDCAD};   
    public List<Instrument> instruList = Arrays.asList(instruArray);
    public List<Instrument> testList = Arrays.asList(testArray);
    public Period myPeriod = Period.ONE_HOUR;
    @Configurable(value="Sample Size")
    public int sampleSize = 0;
    @Configurable(value="Candle Threshold")
    public double candleThreshold = 0;    
    @Configurable(value="Lot Size")
    public double orderAmount = 0;
    public int orderNumber;
    
    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
        //sub to instruments
        Set<Instrument> instrumentHash = new HashSet<Instrument>();        
        context.setSubscribedInstruments(instrumentHash, true);

        
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
    }

    public void onStop() throws JFException {
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }
    
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (!instruList.contains(instrument) || !period.equals(myPeriod)){
                return;
        }   
        IBar recentBar = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
        IBar previousBar = history.getBar(instrument, myPeriod, OfferSide.BID, 2);
        //double accountEQ = history.getEquity();
        //double orderMultiplier = 1;


        //if (accountEQ >= 250000){
        //    orderMultiplier = 2;      
        //}
        //if (accountEQ < 150000){
        //    orderMultiplier = 1;
        //}
        //double orderAmount = 0.3 * orderMultiplier;
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
            
            if (differenceOfRecent > average + standardDeviation){
                double topWick = recentBar.getHigh() - recentBar.getClose();
                double bottomWick = recentBar.getOpen() - recentBar.getLow();
                
                if (bottomWick <= (topWick * 0.95)){
                   orderNumber++;
                    double slNumber = recentBar.getLow() - ((recentBar.getOpen() - recentBar.getClose()) * 1.5);
                    double tpNumber = recentBar.getClose() + ((recentBar.getClose() - recentBar.getOpen()) * 1.5);
                    if ((recentBar.getLow() - slNumber) < 0.002){
                        slNumber = recentBar.getLow() - 0.005;
                    }
                    engine.submitOrder("OrderN" + orderNumber, instrument, OrderCommand.BUY, orderAmount, 0, 0, slNumber, tpNumber);
                    //console.getOut().println("OrderN: " + orderNumber + " Order Open: " + recentBar.getClose() + " SL: " + slNumber); 
                }
                

            }
            if (differenceOfRecent < average - standardDeviation){
                double topWick = recentBar.getHigh() - recentBar.getOpen();
                double bottomWick = recentBar.getClose() - recentBar.getLow();
                
                if (topWick <= (bottomWick * 0.95)){
                    orderNumber++;
                    double slNumber = recentBar.getHigh() + ((recentBar.getClose() - recentBar.getOpen()) * 1.5);
                    double tpNumber = recentBar.getClose() - ((recentBar.getOpen() - recentBar.getClose()) * 1.5);
                    if ((slNumber - recentBar.getHigh()) < 0.002){
                        slNumber = recentBar.getHigh() + 0.005;
                    }
                    engine.submitOrder("OrderN" + orderNumber, instrument, OrderCommand.SELL, orderAmount, 0, 0, slNumber, tpNumber);
                }
                

            }        
        }
        
    }
}