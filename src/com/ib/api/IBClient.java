package com.ib.api;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import com.ib.client.*;
import com.ib.config.ConfigReader;
import com.ib.config.Configs;
import com.ib.quote.QuoteManager;
import com.ib.position.*;
import com.ib.order.*;
//import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class IBClient implements EWrapper {
    private static final Logger LOG = Logger.getLogger(IBClient.class);
    
    protected static IBClient _instance = null;
    protected static QuoteManager m_quoteManager = null;
    protected static PositionManager m_positionManager = null;
    protected static OrderManager m_orderManager = null;
    protected static Trader m_trader = null;
    protected static PositionMonitor m_positionMonitor = null;
    protected static CancelHandler m_cancelHandler = null;
    protected static OrderHandler m_orderHandler = null;
    
    protected EReaderSignal readerSignal;
    protected EClientSocket _clientSocket = null;
    protected AtomicInteger currentOrderId = new AtomicInteger(-1);
    
    protected int sourceConid = Integer.MAX_VALUE;
    protected int tradeConid = Integer.MAX_VALUE;
    
    private List firstOpenOrderExecRecord = null;
    
    private IBClient() {
        if(readerSignal == null){
            readerSignal = new EJavaSignal();
        }
        if(_clientSocket == null){
            _clientSocket = new EClientSocket(this, readerSignal);
        }
        
        m_quoteManager = new QuoteManager(this);
        m_positionManager = new PositionManager(this);
        m_orderManager = new OrderManager(this);
        m_trader = new Trader(this);
        m_positionMonitor = new PositionMonitor(this);
        m_cancelHandler = new CancelHandler(this);
        m_orderHandler = new OrderHandler(this);
        firstOpenOrderExecRecord = new CopyOnWriteArrayList<Integer>();
        
        fetchSourceConid();
        fetchTradeConid();
    }
    //! [socket_init]
    public static IBClient getInstance() {
        if(_instance == null){
            _instance = new IBClient();
        }
        return _instance;
    }
    
    public EClientSocket getSocket(){
        return _clientSocket;
    }
    
    public EReaderSignal getSignal() {
        return readerSignal;
    }
    
    public List getFirstOpenOrderExecRecord(){
        return firstOpenOrderExecRecord;
    }
    
    public int getCurrentOrderId(){
        return currentOrderId.get();
    }
    
    public int getCurrentOrderIdAndIncrement() {
        int orderId = currentOrderId.getAndIncrement();
        return orderId;
    }
    
    public PositionManager getPositionManager(){
        return m_positionManager;
    }
    
    public QuoteManager getQuoteManager(){
        return m_quoteManager;
    }
    
    public OrderManager getOrderManager(){
        return m_orderManager;
    }
    
    public PositionMonitor getPositionMonitor(){
        return m_positionMonitor;
    }
    
    public CancelHandler getCancelHandler(){
        return m_cancelHandler;
    }
    
    public OrderHandler getOrderHandler(){
        return m_orderHandler;
    }
    
    // Test start
    public void start(){
        LOG.info("Starting Trader thread");
        m_trader.startTrade();
    }
    
    //! [tickprice]
    @Override
    public void tickPrice(int tickerId, int field, double price, TickAttr attribs) {
        if(TickType.get(field).equals(TickType.BID) && tickerId == QuoteManager.TICKERID){
            LOG.debug("Received Bid Price = " + price);
            m_quoteManager.updateBidPrice(price);
            synchronized(QuoteManager.QUOTELOCK){
                QuoteManager.QUOTELOCK.notifyAll();
                LOG.debug("Received more tick price, notifying QuoteManager");
            }
        } else if (TickType.get(field).equals(TickType.ASK) && tickerId == QuoteManager.TICKERID){
            LOG.debug("Received Ask Price = " + price);
            m_quoteManager.updateAskPrice(price);
            synchronized(QuoteManager.QUOTELOCK){
                QuoteManager.QUOTELOCK.notifyAll();
                LOG.debug("Received more tick price, notifying QuoteManager");
            }
        }
        //System.out.println("Tick Price. Ticker Id:"+tickerId+", Field: "+field+", Price: "+price+", CanAutoExecute: "+ attribs.canAutoExecute()
        //+ ", pastLimit: " + attribs.pastLimit() + ", pre-open: " + attribs.preOpen());
    }
    //! [tickprice]
    
    //! [ticksize]
    @Override
    public void tickSize(int tickerId, int field, int size) {
        //System.out.println("Tick Size. Ticker Id:" + tickerId + ", Field: " + field + ", Size: " + size);
    }
    //! [ticksize]
    
    //! [tickoptioncomputation]
    @Override
    public void tickOptionComputation(int tickerId, int field,
            double impliedVol, double delta, double optPrice,
            double pvDividend, double gamma, double vega, double theta,
            double undPrice) {
        //System.out.println("TickOptionComputation. TickerId: "+tickerId+", field: "+field+", ImpliedVolatility: "+impliedVol+", Delta: "+delta
        //+", OptionPrice: "+optPrice+", pvDividend: "+pvDividend+", Gamma: "+gamma+", Vega: "+vega+", Theta: "+theta+", UnderlyingPrice: "+undPrice);
    }
    //! [tickoptioncomputation]
    
    //! [tickgeneric]
    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
        //System.out.println("Tick Generic. Ticker Id:" + tickerId + ", Field: " + TickType.getField(tickType) + ", Value: " + value);
    }
    //! [tickgeneric]
    
    //! [tickstring]
    @Override
    public void tickString(int tickerId, int tickType, String value) {
        //System.out.println("Tick string. Ticker Id:" + tickerId + ", Type: " + tickType + ", Value: " + value);
    }
    //! [tickstring]
    @Override
    public void tickEFP(int tickerId, int tickType, double basisPoints,
            String formattedBasisPoints, double impliedFuture, int holdDays,
            String futureLastTradeDate, double dividendImpact,
            double dividendsToLastTradeDate) {
        //System.out.println("TickEFP. "+tickerId+", Type: "+tickType+", BasisPoints: "+basisPoints+", FormattedBasisPoints: "+
        //	formattedBasisPoints+", ImpliedFuture: "+impliedFuture+", HoldDays: "+holdDays+", FutureLastTradeDate: "+futureLastTradeDate+
        //	", DividendImpact: "+dividendImpact+", DividendsToLastTradeDate: "+dividendsToLastTradeDate);
    }
    //! [orderstatus]
    @Override
    public void orderStatus(int orderId, String status, double filled,
            double remaining, double avgFillPrice, int permId, int parentId,
            double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
        //System.out.println("OrderStatus. Id: "+orderId+", Status: "+status+", Filled"+filled+", Remaining: "+remaining
        //+", AvgFillPrice: "+avgFillPrice+", PermId: "+permId+", ParentId: "+parentId+", LastFillPrice: "+lastFillPrice+
        //", ClientId: "+clientId+", WhyHeld: "+whyHeld+", MktCapPrice: "+mktCapPrice);
        if(orderId > 0){ // Only cares about API orders
            LOG.debug("OrderStatus. Id: "+orderId+", Status: "+status+", Filled: "+filled+", Remaining: "+remaining
                    +", AvgFillPrice: "+avgFillPrice+", PermId: "+permId+", ParentId: "+parentId+", LastFillPrice: "+lastFillPrice+
                    ", ClientId: "+clientId+", WhyHeld: "+whyHeld+", MktCapPrice: "+mktCapPrice);
            
            if(status.equalsIgnoreCase("Cancelled")){
                LOG.debug("Removing orderId = " + orderId + " from map and pending. Notifying cancel handler");
                synchronized(Trader.ORDERCANCELMONITORLOCKFORWRAPPER){
                    m_cancelHandler.removeOrderFromPendingCancelList(orderId);
                    m_orderManager.removeOrderFromMap(orderId);
                    
                    Trader.ORDERCANCELMONITORLOCKFORWRAPPER.notifyAll();
                    LOG.debug("Received order cancel status, notifying Cancel Handler.");
                }
            } else if (status.equalsIgnoreCase("Filled")){
                LOG.debug("Removing orderId = " + orderId + "from map and pending. Triggerring order monitor");
                m_cancelHandler.removeOrderFromPendingCancelList(orderId);
                m_orderManager.removeOrderFromMap(orderId);
                
                m_orderManager.triggerOrderMonitor();
            } else {
                m_orderManager.updateOrderStatus(orderId, filled, remaining);
            }
        }
    }
    //! [orderstatus]
    
    //! [openorder]
    @Override
    public void openOrder(int orderId, Contract contract, Order order,
            OrderState orderState) {
        //System.out.println("OpenOrder. ID: "+orderId+", "+contract.symbol()+", "+contract.secType()+" @ "+contract.exchange()+": "+
        //	order.action()+", "+order.orderType()+" "+order.totalQuantity()+", "+orderState.status());
        if(orderId > 0){ // Only take cares of API orders
            LOG.debug("OpenOrder. ID: "+orderId+", "+contract.symbol()+", "+contract.secType()+" @ "+contract.exchange()+": "+
                    order.action()+" "+order.totalQuantity()+", "+order.orderType()+"@"+order.lmtPrice()+", "+orderState.status());
            if(contract.conid() == tradeConid){
                // Only takes order info for the TRADE CONID
                if(!firstOpenOrderExecRecord.contains((Integer) orderId)){
                    synchronized(Trader.FIRSTOPENORDERRECOREXECDLOCK){
                        firstOpenOrderExecRecord.add((Integer) orderId);
                        Trader.FIRSTOPENORDERRECOREXECDLOCK.notifyAll();
                    }
                    LOG.debug("First open order/exec is received for orderId = " + orderId + ", notifying Order Handler");
                }
                m_orderManager.updateOpenOrder(orderId, order);
            }
        }
    }
    //! [openorder]
    
    //! [openorderend]
    @Override
    public void openOrderEnd() {
        //System.out.println("OpenOrderEnd");
        LOG.debug("OpenOrderEnd");
        
        synchronized(Trader.OPENORDERENDLOCK){
            m_orderHandler.setOpenOrderEndReceived(true);
            Trader.OPENORDERENDLOCK.notifyAll();
            LOG.debug("Received OpenOrderEnd, notifying Order Manager.");
        }
    }
    //! [openorderend]
    
    //! [updateaccountvalue]
    @Override
    public void updateAccountValue(String key, String value, String currency,
            String accountName) {
        System.out.println("UpdateAccountValue. Key: " + key + ", Value: " + value + ", Currency: " + currency + ", AccountName: " + accountName);
    }
    //! [updateaccountvalue]
    
    //! [updateportfolio]
    @Override
    public void updatePortfolio(Contract contract, double position,
            double marketPrice, double marketValue, double averageCost,
            double unrealizedPNL, double realizedPNL, String accountName) {
        System.out.println("UpdatePortfolio. "+contract.symbol()+", "+contract.secType()+" @ "+contract.exchange()
                +": Position: "+position+", MarketPrice: "+marketPrice+", MarketValue: "+marketValue+", AverageCost: "+averageCost
                +", UnrealizedPNL: "+unrealizedPNL+", RealizedPNL: "+realizedPNL+", AccountName: "+accountName);
    }
    //! [updateportfolio]
    
    //! [updateaccounttime]
    @Override
    public void updateAccountTime(String timeStamp) {
        System.out.println("UpdateAccountTime. Time: " + timeStamp+"\n");
    }
    //! [updateaccounttime]
    
    //! [accountdownloadend]
    @Override
    public void accountDownloadEnd(String accountName) {
        System.out.println("Account download finished: "+accountName+"\n");
    }
    //! [accountdownloadend]
    
    //! [nextvalidid]
    @Override
    public void nextValidId(int orderId) {
        currentOrderId.set(orderId);
        LOG.debug("Next Valid Id: ["+orderId+"]");
    }
    //! [nextvalidid]
    
    //! [contractdetails]
    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        System.out.println("ContractDetails. ReqId: ["+reqId+"] - ["+contractDetails.contract().symbol()+"], ["+contractDetails.contract().secType()+"], ConId: ["+contractDetails.contract().conid()+"] @ ["+contractDetails.contract().exchange()+"]");
    }
    //! [contractdetails]
    @Override
    public void bondContractDetails(int reqId, ContractDetails contractDetails) {
        System.out.println("bondContractDetails");
    }
    //! [contractdetailsend]
    @Override
    public void contractDetailsEnd(int reqId) {
        System.out.println("ContractDetailsEnd. "+reqId+"\n");
    }
    //! [contractdetailsend]
    
    //! [execdetails]
    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {
        //System.out.println("ExecDetails. "+reqId+" - ["+contract.symbol()+"], ["+contract.secType()+"], ["+contract.currency()+"], ["+execution.execId()+"], ["+execution.orderId()+"], ["+execution.shares()+"]");
        int orderId = execution.orderId();
        if(orderId > 0){
            LOG.debug("ExecDetails. "+reqId+" - ["+contract.symbol()+"], ["+contract.secType()+"], ["+contract.currency()+"], ["+execution.execId()+"], ["+execution.orderId()+"], ["+execution.shares()+"]");
            
            if(contract.conid() == tradeConid){
                // Only takes order info for the TRADE CONID
                if(!firstOpenOrderExecRecord.contains((Integer) orderId)){
                    synchronized(Trader.FIRSTOPENORDERRECOREXECDLOCK){
                        firstOpenOrderExecRecord.add((Integer) orderId);
                        Trader.FIRSTOPENORDERRECOREXECDLOCK.notifyAll();
                    }
                    LOG.debug("First open order/exec is received for orderId = " + orderId + ", notifying Order Handler");
                }
                m_orderManager.processExecDetails(orderId, execution.cumQty());
            }
        }
    }
    //! [execdetails]
    
    //! [execdetailsend]
    @Override
    public void execDetailsEnd(int reqId) {
        //System.out.println("ExecDetailsEnd. "+reqId+"\n");
    }
    //! [execdetailsend]
    
    //! [updatemktdepth]
    @Override
    public void updateMktDepth(int tickerId, int position, int operation,
            int side, double price, int size) {
        //System.out.println("UpdateMarketDepth. "+tickerId+" - Position: "+position+", Operation: "+operation+", Side: "+side+", Price: "+price+", Size: "+size+"");
    }
    //! [updatemktdepth]
    
    //! [updatemktdepthl2]
    @Override
    public void updateMktDepthL2(int tickerId, int position,
            String marketMaker, int operation, int side, double price, int size) {
        //System.out.println("UpdateMarketDepthL2. "+tickerId+" - Position: "+position+", Operation: "+operation+", Side: "+side+", Price: "+price+", Size: "+size+"");
    }
    //! [updatemktdepthl2]
    
    //! [updatenewsbulletin]
    @Override
    public void updateNewsBulletin(int msgId, int msgType, String message,
            String origExchange) {
        //System.out.println("News Bulletins. "+msgId+" - Type: "+msgType+", Message: "+message+", Exchange of Origin: "+origExchange+"\n");
    }
    //! [updatenewsbulletin]
    
    //! [managedaccounts]
    @Override
    public void managedAccounts(String accountsList) {
        //System.out.println("Account list: " +accountsList);
        LOG.info("Account list: " +accountsList);
    }
    //! [managedaccounts]
    
    //! [receivefa]
    @Override
    public void receiveFA(int faDataType, String xml) {
        //System.out.println("Receiving FA: "+faDataType+" - "+xml);
    }
    //! [receivefa]
    
    //! [historicaldata]
    @Override
    public void historicalData(int reqId, Bar bar) {
        //System.out.println("HistoricalData. "+reqId+" - Date: "+bar.time()+", Open: "+bar.open()+", High: "+bar.high()+", Low: "+bar.low()+", Close: "+bar.close()+", Volume: "+bar.volume()+", Count: "+bar.count()+", WAP: "+bar.wap());
    }
    //! [historicaldata]
    
    //! [historicaldataend]
    @Override
    public void historicalDataEnd(int reqId, String startDateStr, String endDateStr) {
        //System.out.println("HistoricalDataEnd. "+reqId+" - Start Date: "+startDateStr+", End Date: "+endDateStr);
    }
    //! [historicaldataend]
    
    
    //! [scannerparameters]
    @Override
    public void scannerParameters(String xml) {
        //System.out.println("ScannerParameters. "+xml+"\n");
    }
    //! [scannerparameters]
    
    //! [scannerdata]
    @Override
    public void scannerData(int reqId, int rank,
            ContractDetails contractDetails, String distance, String benchmark,
            String projection, String legsStr) {
        //System.out.println("ScannerData. "+reqId+" - Rank: "+rank+", Symbol: "+contractDetails.contract().symbol()+", SecType: "+contractDetails.contract().secType()+", Currency: "+contractDetails.contract().currency()
        //        +", Distance: "+distance+", Benchmark: "+benchmark+", Projection: "+projection+", Legs String: "+legsStr);
    }
    //! [scannerdata]
    
    //! [scannerdataend]
    @Override
    public void scannerDataEnd(int reqId) {
        //System.out.println("ScannerDataEnd. "+reqId);
    }
    //! [scannerdataend]
    
    //! [realtimebar]
    @Override
    public void realtimeBar(int reqId, long time, double open, double high,
            double low, double close, long volume, double wap, int count) {
        //System.out.println("RealTimeBars. " + reqId + " - Time: " + time + ", Open: " + open + ", High: " + high + ", Low: " + low + ", Close: " + close + ", Volume: " + volume + ", Count: " + count + ", WAP: " + wap);
    }
    //! [realtimebar]
    @Override
    public void currentTime(long time) {
        //System.out.println("currentTime");
    }
    //! [fundamentaldata]
    @Override
    public void fundamentalData(int reqId, String data) {
        //System.out.println("FundamentalData. ReqId: ["+reqId+"] - Data: ["+data+"]");
    }
    //! [fundamentaldata]
    @Override
    public void deltaNeutralValidation(int reqId, DeltaNeutralContract underComp) {
        //System.out.println("deltaNeutralValidation");
    }
    //! [ticksnapshotend]
    @Override
    public void tickSnapshotEnd(int reqId) {
        //System.out.println("TickSnapshotEnd: "+reqId);
    }
    //! [ticksnapshotend]
    
    //! [marketdatatype]
    @Override
    public void marketDataType(int reqId, int marketDataType) {
        //System.out.println("MarketDataType. ["+reqId+"], Type: ["+marketDataType+"]\n");
    }
    //! [marketdatatype]
    
    //! [commissionreport]
    @Override
    public void commissionReport(CommissionReport commissionReport) {
        //System.out.println("CommissionReport. ["+commissionReport.m_execId+"] - ["+commissionReport.m_commission+"] ["+commissionReport.m_currency+"] RPNL ["+commissionReport.m_realizedPNL+"]");
    }
    //! [commissionreport]
    
    //! [position]
    @Override
    public void position(String account, Contract contract, double pos,
            double avgCost) {
        //System.out.println("Position. "+account+" - Symbol: "+contract.symbol()+", SecType: "+contract.secType()+", Currency: "+contract.currency()+", Position: "+pos+", Avg cost: "+avgCost);
        LOG.debug("Position. "+account+" - Symbol: "+contract.symbol()+", SecType: "+contract.secType()+", Currency: "+contract.currency()+", Position: "+pos+", Avg cost: "+avgCost);
        if(contract.conid() == tradeConid || contract.conid() == sourceConid){
            // Only takes position info of the tradeConid and the sourceConid
            // sourceConid position is counted to monitor hedge position
            Position position = new Position(account, contract, pos, avgCost);
            m_positionManager.updatePosition(position);
        }
    }
    //! [position]
    
    //! [positionend]
    @Override
    public void positionEnd() {
        //System.out.println("PositionEnd \n");
        synchronized(PositionManager.POSITIONLOCK){
            PositionManager.POSITIONLOCK.notifyAll();
            LOG.debug("Received PositionEnd, notifying Position Manager.");
        }
    }
    //! [positionend]
    
    //! [accountsummary]
    @Override
    public void accountSummary(int reqId, String account, String tag,
            String value, String currency) {
        //System.out.println("Acct Summary. ReqId: " + reqId + ", Acct: " + account + ", Tag: " + tag + ", Value: " + value + ", Currency: " + currency);
    }
    //! [accountsummary]
    
    //! [accountsummaryend]
    @Override
    public void accountSummaryEnd(int reqId) {
        //System.out.println("AccountSummaryEnd. Req Id: "+reqId+"\n");
    }
    //! [accountsummaryend]
    @Override
    public void verifyMessageAPI(String apiData) {
        //System.out.println("verifyMessageAPI");
    }
    
    @Override
    public void verifyCompleted(boolean isSuccessful, String errorText) {
        //System.out.println("verifyCompleted");
    }
    
    @Override
    public void verifyAndAuthMessageAPI(String apiData, String xyzChallenge) {
        //System.out.println("verifyAndAuthMessageAPI");
    }
    
    @Override
    public void verifyAndAuthCompleted(boolean isSuccessful, String errorText) {
        //System.out.println("verifyAndAuthCompleted");
    }
    //! [displaygrouplist]
    @Override
    public void displayGroupList(int reqId, String groups) {
        //System.out.println("Display Group List. ReqId: "+reqId+", Groups: "+groups+"\n");
    }
    //! [displaygrouplist]
    
    //! [displaygroupupdated]
    @Override
    public void displayGroupUpdated(int reqId, String contractInfo) {
        //System.out.println("Display Group Updated. ReqId: "+reqId+", Contract info: "+contractInfo+"\n");
    }
    //! [displaygroupupdated]
    @Override
    public void error(Exception e) {
        //System.out.println("Exception: "+e.getMessage());
        LOG.error("Exception: "+e.getMessage());
    }
    @Override
    public void error(String str) {
        //System.out.println("Error STR");
        LOG.error("Error str: " + str);
    }
    //! [error]
    @Override
    public void error(int id, int errorCode, String errorMsg) {
        //System.out.println("Error. Id: " + id + ", Code: " + errorCode + ", Msg: " + errorMsg + "\n");
        LOG.error("Error. Id: " + id + ", Code: " + errorCode + ", Msg: " + errorMsg);
        
        if((errorCode == 201 || errorCode == 202 || errorCode == 10147 || errorCode == 10148)&& m_cancelHandler.pendingCancelListContains(id)){
            LOG.debug("Removing orderId = " + id + " from map and pending. Notifying cancel handler");
            synchronized(Trader.ORDERCANCELMONITORLOCKFORWRAPPER){
                m_cancelHandler.removeOrderFromPendingCancelList(id);
                m_orderManager.removeOrderFromMap(id);
                
                Trader.ORDERCANCELMONITORLOCKFORWRAPPER.notifyAll();
                LOG.debug("Received order cancel status, notifying Cancel Handler.");
            }
        }
    }
    //! [error]
    @Override
    public void connectionClosed() {
        LOG.debug("Connection lost");
    }
    
    //! [connectack]
    @Override
    public void connectAck() {
        if (_clientSocket.isAsyncEConnect()) {
            System.out.println("Acknowledging connection");
            _clientSocket.startAPI();
        }
    }
    //! [connectack]
    
    //! [positionmulti]
    @Override
    public void positionMulti(int reqId, String account, String modelCode,
            Contract contract, double pos, double avgCost) {
        //System.out.println("Position Multi. Request: " + reqId + ", Account: " + account + ", ModelCode: " + modelCode + ", Symbol: " + contract.symbol() + ", SecType: " + contract.secType() + ", Currency: " + contract.currency() + ", Position: " + pos + ", Avg cost: " + avgCost + "\n");
    }
    //! [positionmulti]
    
    //! [positionmultiend]
    @Override
    public void positionMultiEnd(int reqId) {
        //System.out.println("Position Multi End. Request: " + reqId + "\n");
    }
    //! [positionmultiend]
    
    //! [accountupdatemulti]
    @Override
    public void accountUpdateMulti(int reqId, String account, String modelCode,
            String key, String value, String currency) {
        //System.out.println("Account Update Multi. Request: " + reqId + ", Account: " + account + ", ModelCode: " + modelCode + ", Key: " + key + ", Value: " + value + ", Currency: " + currency + "\n");
    }
    //! [accountupdatemulti]
    
    //! [accountupdatemultiend]
    @Override
    public void accountUpdateMultiEnd(int reqId) {
        //System.out.println("Account Update Multi End. Request: " + reqId + "\n");
    }
    //! [accountupdatemultiend]
    
    //! [securityDefinitionOptionParameter]
    @Override
    public void securityDefinitionOptionalParameter(int reqId, String exchange,
            int underlyingConId, String tradingClass, String multiplier,
            Set<String> expirations, Set<Double> strikes) {
        //System.out.println("Security Definition Optional Parameter. Request: "+reqId+", Trading Class: "+tradingClass+", Multiplier: "+multiplier+" \n");
    }
    //! [securityDefinitionOptionParameter]
    
    //! [securityDefinitionOptionParameterEnd]
    @Override
    public void securityDefinitionOptionalParameterEnd(int reqId) {
        //System.out.println("Security Definition Optional Parameter End. Request: " + reqId);
    }
    //! [securityDefinitionOptionParameterEnd]
    
    //! [softDollarTiers]
    @Override
    public void softDollarTiers(int reqId, SoftDollarTier[] tiers) {
        for (SoftDollarTier tier : tiers) {
            //System.out.print("tier: " + tier.toString() + ", ");
        }
        
        //System.out.println();
    }
    //! [softDollarTiers]
    
    //! [familyCodes]
    @Override
    public void familyCodes(FamilyCode[] familyCodes) {
        for (FamilyCode fc : familyCodes) {
            //System.out.print("Family Code. AccountID: " + fc.accountID() + ", FamilyCode: " + fc.familyCodeStr());
        }
        
        //System.out.println();
    }
    //! [familyCodes]
    
    //! [symbolSamples]
    @Override
    public void symbolSamples(int reqId, ContractDescription[] contractDescriptions) {
        //System.out.println("Contract Descriptions. Request: " + reqId + "\n");
        for (ContractDescription  cd : contractDescriptions) {
            Contract c = cd.contract();
            StringBuilder derivativeSecTypesSB = new StringBuilder();
            for (String str : cd.derivativeSecTypes()) {
                derivativeSecTypesSB.append(str);
                derivativeSecTypesSB.append(",");
            }
            //System.out.print("Contract. ConId: " + c.conid() + ", Symbol: " + c.symbol() + ", SecType: " + c.secType() +
            //        ", PrimaryExch: " + c.primaryExch() + ", Currency: " + c.currency() +
            //        ", DerivativeSecTypes:[" + derivativeSecTypesSB.toString() + "]");
        }
        
        //System.out.println();
    }
    //! [symbolSamples]
    
    //! [mktDepthExchanges]
    @Override
    public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {
        for (DepthMktDataDescription depthMktDataDescription : depthMktDataDescriptions) {
            //System.out.println("Depth Mkt Data Description. Exchange: " + depthMktDataDescription.exchange() +
            //        ", ListingExch: " + depthMktDataDescription.listingExch() +
            //        ", SecType: " + depthMktDataDescription.secType() +
            //        ", ServiceDataType: " + depthMktDataDescription.serviceDataType() +
            //        ", AggGroup: " + depthMktDataDescription.aggGroup()
            //);
        }
    }
    //! [mktDepthExchanges]
    
    //! [tickNews]
    @Override
    public void tickNews(int tickerId, long timeStamp, String providerCode, String articleId, String headline, String extraData) {
        //System.out.println("Tick News. TickerId: " + tickerId + ", TimeStamp: " + timeStamp + ", ProviderCode: " + providerCode + ", ArticleId: " + articleId + ", Headline: " + headline + ", ExtraData: " + extraData + "\n");
    }
    //! [tickNews]
    
    //! [smartcomponents]
    @Override
    public void smartComponents(int reqId, Map<Integer, Entry<String, Character>> theMap) {
        //System.out.println("smart components req id:" + reqId);
        
        for (Map.Entry<Integer, Entry<String, Character>> item : theMap.entrySet()) {
            //System.out.println("bit number: " + item.getKey() +
            //        ", exchange: " + item.getValue().getKey() + ", exchange letter: " + item.getValue().getValue());
        }
    }
    //! [smartcomponents]
    
    //! [tickReqParams]
    @Override
    public void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) {
        //System.out.println("Tick req params. Ticker Id:" + tickerId + ", Min tick: " + minTick + ", bbo exchange: " + bboExchange + ", Snapshot permissions: " + snapshotPermissions);
        LOG.info("Tick req params. Ticker Id:" + tickerId + ", Min tick: " + minTick + ", bbo exchange: " + bboExchange + ", Snapshot permissions: " + snapshotPermissions);
    }
    //! [tickReqParams]
    
    //! [newsProviders]
    @Override
    public void newsProviders(NewsProvider[] newsProviders) {
        for (NewsProvider np : newsProviders) {
            //System.out.print("News Provider. ProviderCode: " + np.providerCode() + ", ProviderName: " + np.providerName() + "\n");
        }
        
        //System.out.println();
    }
    //! [newsProviders]
    
    //! [newsArticle]
    @Override
    public void newsArticle(int requestId, int articleType, String articleText) {
        //System.out.println("News Article. Request Id: " + requestId + ", ArticleType: " + articleType +
        //        ", ArticleText: " + articleText);
    }
    //! [newsArticle]
    
    //! [historicalNews]
    @Override
    public void historicalNews(int requestId, String time, String providerCode, String articleId, String headline) {
        //System.out.println("Historical News. RequestId: " + requestId + ", Time: " + time + ", ProviderCode: " + providerCode + ", ArticleId: " + articleId + ", Headline: " + headline + "\n");
    }
    //! [historicalNews]
    
    //! [historicalNewsEnd]
    @Override
    public void historicalNewsEnd(int requestId, boolean hasMore) {
        //System.out.println("Historical News End. RequestId: " + requestId + ", HasMore: " + hasMore + "\n");
    }
    //! [historicalNewsEnd]
    
    //! [headTimestamp]
    @Override
    public void headTimestamp(int reqId, String headTimestamp) {
        //System.out.println("Head timestamp. Req Id: " + reqId + ", headTimestamp: " + headTimestamp);
    }
    //! [headTimestamp]
    
    //! [histogramData]
    @Override
    public void histogramData(int reqId, List<HistogramEntry> items) {
        //System.out.println(EWrapperMsgGenerator.histogramData(reqId, items));
    }
    //! [histogramData]
    
    //! [historicalDataUpdate]
    @Override
    public void historicalDataUpdate(int reqId, Bar bar) {
        //System.out.println("HistoricalDataUpdate. "+reqId+" - Date: "+bar.time()+", Open: "+bar.open()+", High: "+bar.high()+", Low: "+bar.low()+", Close: "+bar.close()+", Volume: "+bar.volume()+", Count: "+bar.count()+", WAP: "+bar.wap());
    }
    //! [historicalDataUpdate]
    
    //! [rerouteMktDataReq]
    @Override
    public void rerouteMktDataReq(int reqId, int conId, String exchange) {
        //System.out.println(EWrapperMsgGenerator.rerouteMktDataReq(reqId, conId, exchange));
    }
    //! [rerouteMktDataReq]
    
    //! [rerouteMktDepthReq]
    @Override
    public void rerouteMktDepthReq(int reqId, int conId, String exchange) {
        //System.out.println(EWrapperMsgGenerator.rerouteMktDepthReq(reqId, conId, exchange));
    }
    //! [rerouteMktDepthReq]
    
    //! [marketRule]
    @Override
    public void marketRule(int marketRuleId, PriceIncrement[] priceIncrements) {
        DecimalFormat df = new DecimalFormat("#.#");
        df.setMaximumFractionDigits(340);
        //System.out.println("Market Rule Id: " + marketRuleId);
        for (PriceIncrement pi : priceIncrements) {
            //System.out.println("Price Increment. Low Edge: " + df.format(pi.lowEdge()) + ", Increment: " + df.format(pi.increment()));
        }
    }
    //! [marketRule]
    
    //! [pnl]
    @Override
    public void pnl(int reqId, double dailyPnL, double unrealizedPnL) {
        //System.out.println(EWrapperMsgGenerator.pnl(reqId, dailyPnL, unrealizedPnL));
    }
    //! [pnl]
    
    //! [pnlsingle]
    @Override
    public void pnlSingle(int reqId, int pos, double dailyPnL, double unrealizedPnL, double value) {
        //System.out.println(EWrapperMsgGenerator.pnlSingle(reqId, pos, dailyPnL, unrealizedPnL, value));
    }
    //! [pnlsingle]
    
    //! [historicalticks]
    @Override
    public void historicalTicks(int reqId, List<HistoricalTick> ticks, boolean done) {
        for (HistoricalTick tick : ticks) {
            //System.out.println(EWrapperMsgGenerator.historicalTick(reqId, tick.time(), tick.price(), tick.size()));
        }
    }
    //! [historicalticks]
    
    //! [historicalticksbidask]
    @Override
    public void historicalTicksBidAsk(int reqId, List<HistoricalTickBidAsk> ticks, boolean done) {
        for (HistoricalTickBidAsk tick : ticks) {
            //System.out.println(EWrapperMsgGenerator.historicalTickBidAsk(reqId, tick.time(), tick.mask(), tick.priceBid(), tick.priceAsk(), tick.sizeBid(),
            //        tick.sizeAsk()));
        }
    }
    //! [historicalticksbidask]
    
    @Override
    //! [historicaltickslast]
    public void historicalTicksLast(int reqId, List<HistoricalTickLast> ticks, boolean done) {
        for (HistoricalTickLast tick : ticks) {
            //System.out.println(EWrapperMsgGenerator.historicalTickLast(reqId, tick.time(), tick.mask(), tick.price(), tick.size(), tick.exchange(),
            //        tick.specialConditions()));
        }
    }
    //! [historicaltickslast]
    
    private void fetchSourceConid(){
        if(sourceConid == Integer.MAX_VALUE){
            sourceConid = Integer.parseInt(ConfigReader.getInstance().getConfig(Configs.SOURCE_CONID));
        }
    }
    
    private void fetchTradeConid(){
        if(tradeConid == Integer.MAX_VALUE){
            tradeConid = Integer.parseInt(ConfigReader.getInstance().getConfig(Configs.TRADE_CONID));
        }
    }
}
