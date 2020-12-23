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

public class IchiTrader implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    public Instrument[] instruArray = {Instrument.USDCAD, Instrument.EURUSD, Instrument.GBPUSD, Instrument.USDCHF, Instrument.NZDUSD, Instrument.EURCAD, Instrument.AUDUSD, Instrument.AUDCAD, Instrument.CADCHF, Instrument.GBPCAD, Instrument.GBPAUD};   
    public Instrument[] testArray = {Instrument.USDCAD, Instrument.EURUSD, Instrument.GBPUSD, Instrument.NZDUSD, Instrument.USDCHF, Instrument.XAUUSD, Instrument.AUDUSD};   
    //public Instrument[] testArray = {Instrument.XAUUSD};   
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
        
        IchiLogic(instrument, alteredOD);
        ProfitCalcs();
        
    }
    
    public void IchiLogic(Instrument instrument, String alteredOD) throws JFException{
        
        //Assignment
        IBar recentBar = history.getBar(instrument, myPeriod, OfferSide.BID, 1);
        double recentOpen = recentBar.getOpen();
        double recentClose = recentBar.getClose();
        double recentHigh = recentBar.getHigh();
        double recentLow = recentBar.getLow();
        
        //Gather the previous bars before Chinkour Span to see if price hits it
        IBar[] prevBars = new IBar[10];        
        for (int i = 0; i < prevBars.length; i++){
            int shift = 27 + i;
            IBar tempBar = history.getBar(instrument, myPeriod, OfferSide.BID, shift);
            prevBars[i] = tempBar;
        }
        
        //values on the ICHI arrays: 1, Tenkan Sen 2, Ki-jun Sen 3, Chinkou Span 4, Senkou A 5, Senkou B
        //Senkou A/B make the cloud
        //Chinkou Span is the lagging indicator much behind price
        //Ki-jun is the slower line
        //Tenkan is the faster line
        double[] currentIchi = indicators.ichimoku(instrument, myPeriod, OfferSide.BID, 9, 26, 52, 1);
        double currentTenkan = currentIchi[0];
        double currentKiJun = currentIchi[1];
        double currentChinkou = currentIchi[2];
        double currentSenkA = currentIchi[3];
        double currentSenkB = currentIchi[4];
        
        double[] prevIchi = indicators.ichimoku(instrument, myPeriod, OfferSide.BID, 9, 26, 52, 2);
        double prevTenkan = prevIchi[0];
        double prevKiJun = prevIchi[1];
        double prevChinkou = prevIchi[2];
        double prevSenkA = prevIchi[3];
        double prevSenkB = prevIchi[4];
        
        //Logic
        
        if (recentClose < currentSenkA && recentClose > currentSenkB || recentClose > currentSenkA && recentClose < currentSenkB){
            return;            
        }
        if (prevTenkan < prevKiJun && currentTenkan > currentKiJun){
            //bullish
            
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
}