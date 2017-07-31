/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.order;

import org.apache.log4j.Logger;

import com.ib.api.IBClient;
import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderType;
import com.ib.client.Types;
import com.ib.config.ConfigReader;
import com.ib.config.Configs;
import java.util.HashMap;
import java.util.Iterator;
import com.ib.position.PositionMonitor;
import com.ib.position.PositionManager;
import com.ib.quote.QuoteManager;
import java.util.Arrays;

/**
 *
 * @author Siteng Jin
 */
public class NewOrderHandler implements Runnable{
    private static final Logger LOG = Logger.getLogger(NewOrderHandler.class);
    
    private static IBClient m_client = null;
    private static OrderManager m_orderManager = null;
    private static PositionMonitor m_positionMonitor = null;
    private static PositionManager m_positionManager = null;
    private static QuoteManager m_quoteManager = null;
    
    private HashMap<Integer, OrderInfo> m_orderMap = null;
    
    private boolean openOrderEndReceived = false;
    private int tradeConid = Integer.MAX_VALUE;
    private String tradeExchange = null;
    private int orderSizeDefault = Integer.MAX_VALUE;
    private int positionAdjustment = Integer.MAX_VALUE;
    private String account = null;
    
    public NewOrderHandler(IBClient client){
        m_client = client;
        if(m_orderManager == null){
            m_orderManager = m_client.getOrderManager();
        }
        if(m_positionMonitor == null){
            m_positionMonitor = m_client.getPositionMonitor();
        }
        if(m_positionManager == null){
            m_positionManager = m_client.getPositionManager();
        }
        if(m_quoteManager == null){
            m_quoteManager = m_client.getQuoteManager();
        }
    }
    
    @Override
    public void run(){
        monitorNewOrder();
    }
    
    private void monitorNewOrder(){
        synchronized(Trader.NEWORDERMONITORLOCK){
            try{
                    LOG.debug("NewOrderHandler waiting for notification when new order is added the list...");
                    Trader.NEWORDERMONITORLOCK.wait();
                } catch (Exception e){
                    LOG.error(e.getMessage(), e);
                }
                
                LOG.debug("NewOrderHandler notified about new order needs to be placed");
        }
        
        // place new order
        // Fetch current orders first
        fetchOrderMapFromOrderManager();
        
        // First time check, open order end
        checkOpenOrderEnd();
        
        // Fetch tradeConid
        fetchTradeConid();
        
        // Fetch position monitor
        fetchPositionMonitor();
        
        // Verify current orders
        int[] currentOrderIds = verifyOrders();
        
        // Place Orders
        OrderPlacement(currentOrderIds);
    }
    
    private void fetchOrderMapFromOrderManager(){
        synchronized(OrderManager.ORDERACCESSLOCK){
            m_orderMap = m_orderManager.getOrderMap();
        }
    }
    
    private void checkOpenOrderEnd(){
        while(!openOrderEndReceived){
            synchronized(Trader.OPENORDERLOCK){
                try {
                    LOG.debug("Waiting for open order end to be received...");
                    Trader.OPENORDERLOCK.wait();
                } catch (Exception e){
                    LOG.error(e.getMessage(), e);
                }
            }
        }
        LOG.debug("All orders are received, continue to verify order.");
    }
    
    private void OrderPlacement(int[] currentOrderIds){
        int buyOrderId = currentOrderIds[0];
        int sellOrderId = currentOrderIds[1];
        
        if(buyOrderId == Integer.MAX_VALUE){
            LOG.debug("Buy order is missing from map, placing new buy order");
            placeNewBuyOrderIfNecessary();
        }
        
        if(sellOrderId == Integer.MAX_VALUE){
            placeNewSellOrderIfNecessary();
        }
    }
    
    private int[] verifyOrders(){ // Should return int[2] = {buyOrderId, sellOrderId}
        if(m_orderMap.size() > 2){
            LOG.error("More than two orders are detected for source conid " + tradeConid + ", stopping program...");
            System.exit(0);
        }
        
        int buyOrderId = Integer.MAX_VALUE;
        int sellOrderId = Integer.MAX_VALUE;
        
        if(m_orderMap.isEmpty()){
            Iterator it = m_orderMap.keySet().iterator();
            while(it.hasNext()){
                int orderId = (int) it.next();
                OrderInfo tmp = m_orderMap.get(orderId);
                if(tmp.getOrder().action() == Types.Action.BUY){
                    if(buyOrderId < Integer.MAX_VALUE && buyOrderId != orderId){
                        LOG.error("Found more than two BUY orders for conid = " + tradeConid + ", stopping program...");
                        System.exit(0);
                    }
                    buyOrderId = orderId;
                } else if(tmp.getOrder().action() == Types.Action.SELL){
                    if(sellOrderId < Integer.MAX_VALUE && sellOrderId != orderId){
                        LOG.error("Found more than two SELL orders for conid = " + tradeConid + ", stopping program...");
                        System.exit(0);
                    }
                    sellOrderId = orderId;
                }
            }
        }
        
        int[] res = {buyOrderId, sellOrderId};
        LOG.debug("Orders are verified. Returning " + Arrays.asList(res).toString());
        return res;
    }
    
    public void placeNewBuyOrderIfNecessary(){
        // Buy order should be placed on the trade bid price
        fetchPositionManager();
        fetchQuoteManager();
        
        if(m_positionMonitor.getStopQuotingSizeReached() && Double.compare(m_positionManager.getPosition(), 0.0) > 0){
            LOG.debug("Placing buy order ignored: Position size limit reached");
            return;
        }
        
        try{
            double tradeBidPrice = m_quoteManager.getTradeBidPrice();
            while(tradeBidPrice < 0){
                LOG.debug("Cannot get trade bid price. Try again in 200 ms...");
                Thread.sleep(200);
            }
            
            this.fetchOrderSizeDefault();
            
            this.fetchPositionAdjustment();
            
            this.fetchAccount();
            
            int totalQuantity = Integer.MAX_VALUE;
            
            double pos = m_positionManager.getPosition();
            if(Double.compare(pos, 0.0) > 0){
                totalQuantity = orderSizeDefault - positionAdjustment;
            } else {
                totalQuantity = orderSizeDefault;
            }
            
            // Double check order size
            if(totalQuantity < Integer.MAX_VALUE){
                int orderId = m_client.getCurrentOrderIdAndIncrement();
                Contract tradeContract = getTradeContract();
                Order order = getLimitOrder(Types.Action.BUY, totalQuantity, tradeBidPrice, account);
                m_client.getSocket().placeOrder(orderId, tradeContract, order);
                LOG.info("Placed " + order.action() + " order (" + orderId + ") for " + tradeContract.conid() + "@" + tradeContract.exchange() + ": " + 
                        totalQuantity + "@" + tradeBidPrice);
            }
        } catch (Exception e){
            LOG.error(e.getMessage(), e);
        }
    }
    
    public void placeNewSellOrderIfNecessary(){
        // Sell order should be placed on the trade ask price
        fetchPositionManager();
        fetchQuoteManager();
        
        if(m_positionMonitor.getStopQuotingSizeReached() && Double.compare(m_positionManager.getPosition(), 0.0) < 0){
            LOG.debug("Placing sell order ignored: Position size limit reached");
            return;
        }
        
        try{
            double tradeAskPrice = m_quoteManager.getTradeAskPrice();
            while(tradeAskPrice < 0){
                LOG.debug("Cannot get trade bid price. Try again in 200 ms...");
                Thread.sleep(200);
            }
            
            this.fetchOrderSizeDefault();
            
            this.fetchPositionAdjustment();
            
            this.fetchAccount();
            
            int totalQuantity = Integer.MAX_VALUE;
            
            double pos = m_positionManager.getPosition();
            if(Double.compare(pos, 0.0) < 0){
                totalQuantity = orderSizeDefault - positionAdjustment;
            } else {
                totalQuantity = orderSizeDefault;
            }
            
            // Double check order size
            if(totalQuantity < Integer.MAX_VALUE){
                int orderId = m_client.getCurrentOrderIdAndIncrement();
                Contract tradeContract = getTradeContract();
                Order order = getLimitOrder(Types.Action.BUY, totalQuantity, tradeAskPrice, account);
                m_client.getSocket().placeOrder(orderId, tradeContract, order);
                LOG.info("Placed " + order.action() + " order (" + orderId + ") for " + tradeContract.conid() + "@" + tradeContract.exchange() + ": " + 
                        totalQuantity + "@" + tradeAskPrice);
            }
        } catch (Exception e){
            LOG.error(e.getMessage(), e);
        }
    }
    
    // Getters
    
    private Contract getTradeContract(){
        if(tradeConid == Integer.MAX_VALUE){
            fetchTradeConid();
        }
        if(tradeExchange == null){
            fetchTradeExchange();
        }
        Contract tradeContract = new Contract();
        tradeContract.conid(tradeConid);
        tradeContract.exchange(tradeExchange);
        return tradeContract;
    }
    
    private Order getLimitOrder(Types.Action action, double totalQuantity, double lmtPrice, String account){
        Order order = new Order();
        order.action(action);
        order.orderType(OrderType.LMT);
        order.totalQuantity(totalQuantity);
        order.lmtPrice(lmtPrice);
        order.account(account);
        return order;
    }
    
    
    // Fetches
    private void fetchTradeConid(){
        if(tradeConid == Integer.MAX_VALUE){
            tradeConid = Integer.parseInt(ConfigReader.getInstance().getConfig(Configs.TRADE_CONID));
        }
    }
    
    private void fetchTradeExchange(){
        if(tradeExchange == null){
            tradeExchange = ConfigReader.getInstance().getConfig(Configs.TRADE_EXCHANGE);
        }
    }
    
    private void fetchPositionMonitor(){
        while(m_positionMonitor == null){
            m_positionMonitor = m_client.getPositionMonitor();
            try{
                Thread.sleep(100);
            } catch (Exception e){
                LOG.error(e.getMessage(), e);
            }
        }
    }
    
    private void fetchPositionManager(){
        while(m_positionManager == null){
            m_positionManager = m_client.getPositionManager();
            try{
                Thread.sleep(100);
            } catch (Exception e){
                LOG.error(e.getMessage(), e);
            }
        }
    }
    
    private void fetchQuoteManager(){
        while(m_quoteManager == null){
            m_quoteManager = m_client.getQuoteManager();
            try{
                Thread.sleep(100);
            } catch (Exception e){
                LOG.error(e.getMessage(), e);
            }
        }
    }
    
    private void fetchOrderSizeDefault(){
        if(orderSizeDefault == Integer.MAX_VALUE){
            orderSizeDefault = Integer.parseInt(ConfigReader.getInstance().getConfig(Configs.ORDER_SIZE_DEFAULT));
        }
    }
    
    private void fetchPositionAdjustment(){
        if(positionAdjustment == Integer.MAX_VALUE){
            positionAdjustment = Integer.parseInt(ConfigReader.getInstance().getConfig(Configs.POSITION_ADJUSTMENT));
        }
    }
    
    private void fetchAccount(){
        if(account == null){
            account = ConfigReader.getInstance().getConfig(Configs.ACCOUNT);
        }
    }
}
