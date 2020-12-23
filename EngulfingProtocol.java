package jforex;

import java.util.*;
import java.text.*;

import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.*;

public class EngulfingProtocol implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    private IOrder order;
    private int orderNumber;
    public Instrument[] instruArray = {Instrument.USDCAD, Instrument.XAUUSD, Instrument.EURUSD, Instrument.USDJPY, Instrument.GBPUSD, Instrument.USDCHF};
    public List<Instrument> instruList = Arrays.asList(instruArray);
    public Period myPeriod = Period.ONE_HOUR;
    
    private int check1 = 0;
    private int check2 = 0;
    
    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
        
        Set<Instrument> instrumentHash = new HashSet<Instrument>();
        
        context.setSubscribedInstruments(instrumentHash, true);
        
        for (Instrument instr : instruArray){
            //console.getOut().println(instr + " Check");
        }
        
        
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
    }
    
    
    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }

    public void onStop() throws JFException {
    }
    
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
            
        if (!instruList.contains(instrument) || !period.equals(myPeriod)){
            return;
        }     
            //console.getOut().println(instrument + " Loaded");
            
            IBar currentBar = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
            IBar previousBar = history.getBar(instrument, myPeriod, OfferSide.BID, 2);
            
            //console.getOut().println("Current bar: " + currentBar + " Previous bar: " + previousBar);
            boolean notCalculated = true;
            double bodyOfCurrent = 0;
            double bodyOfPrevious = 0;
            double currentClose = currentBar.getClose();
            double previousClose = previousBar.getClose();
            double currentHigh = currentBar.getHigh();
            double previousHigh = previousBar.getHigh();
            double currentLow = currentBar.getLow();
            double previousLow = previousBar.getLow();
            double currentOpen = currentBar.getOpen();
            double previousOpen = previousBar.getOpen();

            
            if(previousOpen > previousClose && notCalculated == true){
                bodyOfPrevious = previousOpen - previousClose;
                notCalculated = false;
            }else if (previousOpen < previousClose && notCalculated == true){
                bodyOfPrevious = previousClose - previousOpen;
                notCalculated = false;
            }
            
            if (currentOpen > currentClose && notCalculated == true){
                bodyOfCurrent = currentOpen - currentClose;
                notCalculated = false;
            }else if (currentOpen < currentClose && notCalculated == true){
                bodyOfCurrent = currentClose - currentOpen;
                notCalculated = false;
            }                       
            
            if (bodyOfCurrent < (bodyOfPrevious * 2.5)){
                return;
            }
            
            
            //if (bodyOfCurrent < 0){
            //    if (bodyOfCurrent > -0.001){
            //        return;
            //    }                
            //}else if (bodyOfCurrent > 0){
             //   if (bodyOfCurrent < 0.001){
              //      return;
              //  }
            //}else if (bodyOfCurrent == 0){
            //    return;
            //}
            
            
            if (currentClose > previousClose){
                if (currentOpen - currentLow >= (bodyOfCurrent * 0.1)){
                    return;
                }

                double alteredTP = currentClose *  1.001;
                //placing Bullish order
                //console.getOut().println("Bull ORDER");
                orderNumber++;
                                
                engine.submitOrder("orderN"+orderNumber, instrument, OrderCommand.BUY, 0.3, 0, 0, currentLow, alteredTP);               
                check1++;
                //IOrder orderBuy = engine.submitOrder("OrderSimple" + orderNumber, instrument, OrderCommand.BUY, 0.01);
                //orderBuy.setStopLossPrice(currentLow);
                //orderBuy.setTakeProfitPrice(alteredHigh);

            }if (currentClose < previousClose){
                if (currentClose - currentHigh >= (bodyOfCurrent * 0.1)){
                    return;
                }

                double alteredTP = currentClose / 1.001;
                //placing Bearish order

                orderNumber++;
                
                engine.submitOrder("orderN"+orderNumber, instrument, OrderCommand.SELL, 0.3, 0, 0, currentHigh, alteredTP);
                check2++;
                //IOrder orderSell = engine.submitOrder("OrderSimpleSell" + orderNumber, instrument, OrderCommand.SELL, 0.01);
                //orderSell.setStopLossPrice(currentHigh);
                //orderSell.setTakeProfitPrice(alteredLow);

                //console.getOut().println("Bear Order");
            }
        orderNumber++;
        console.getOut().println(orderNumber);
        console.getOut().println(check1 + " " + check2);
    }
}