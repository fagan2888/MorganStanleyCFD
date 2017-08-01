/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.quote;

import com.ib.api.IBClient;
import org.apache.log4j.Logger;
import com.ib.config.*;
import com.ib.client.Contract;
import com.ib.position.*;
import com.ib.order.OrderManager;

/**
 *
 * @author Siteng Jin
 */
public class QuoteManager {
    public static final int TICKERID = 1000;
    
    private static final Logger LOG = Logger.getLogger(QuoteManager.class);
    
    private static OrderManager m_orderManager = null;
    private static PositionManager m_positionManager = null;
    
    public static final Object QUOTELOCK = new Object();
    private static final Object QUOTEACCESSLOCK = new Object();
    
    private IBClient m_client = null;
    
    private double sourceBidPrice = -1.0;
    private double sourceAskPrice = -1.0;
    private double sourceMidpoint = -1.0;
    private double tradeBidPrice = -1.0;
    private double tradeAskPrice = -1.0;
    
    private int sourceConid = Integer.MAX_VALUE;
    private String sourceExchange = null;
    private double staticOffset = Double.MAX_VALUE;
    private double defaultSpread = Double.MAX_VALUE;
    
    private boolean firstSourceBidPriceReceived = false;
    private boolean firstSourceAskPriceReceived = false;
    
    public QuoteManager(IBClient client){ 
        LOG.debug("Initializing Quote Manager");
        m_client = client;
        
        if(m_orderManager == null){
            m_orderManager = m_client.getOrderManager();
        }
        if(m_positionManager == null){
            m_positionManager = m_client.getPositionManager();
        }
        
        fetchStaticOffset();
        fetchDefaultSpread();
        fetchSourceConid();
        fetchSourceExchange();
    }
    
    public void requestSourceData(){
        Contract sourceContract = this.getSourceContract();
        m_client.getSocket().reqMktData(TICKERID, sourceContract, "", false, false, null);
        LOG.debug("Sent market data request for source contract. ConId = " + sourceContract.conid());
    }
    
    public void updateBidPrice(double price){
        boolean detectPriceChanged = false;
        
        synchronized(QUOTEACCESSLOCK){
            if(Double.compare(sourceBidPrice, price) != 0){
                LOG.debug("Detected source Bid Price is changed");
                sourceBidPrice = price;
                if(!firstSourceBidPriceReceived){
                    firstSourceBidPriceReceived = true;
                }
                if(sourceAskPrice != -1.0){
                    sourceMidpoint = (sourceBidPrice + sourceAskPrice)/2.0;
                }
                detectPriceChanged = true;
                LOG.debug("Updated info: sourceBidPrice = " + sourceBidPrice + ", sourceMidpoint = " + sourceMidpoint);
            }
        }
        
        if(detectPriceChanged && firstSourceBidPriceReceived){
            calculateTradeBidPrice();
            fetchOrderManager();
            
            LOG.debug("Triggering order monitor to update trade bid price");
            m_orderManager.triggerOrderMonitor();
        }
    }
    
    public void updateAskPrice(double price){
        boolean detectPriceChanged = false;
        
        synchronized(QUOTEACCESSLOCK){
            LOG.debug("Detected source Ask Price is changed");
            if(Double.compare(sourceAskPrice, price) != 0){
                sourceAskPrice = price;
                if(!firstSourceAskPriceReceived){
                    firstSourceAskPriceReceived = true;
                }
                if(sourceBidPrice != -1.0){
                    sourceMidpoint = (sourceBidPrice + sourceAskPrice)/2.0;
                }
                detectPriceChanged = true;
                LOG.debug("Updated info: sourceAskPrice = " + sourceAskPrice + ", sourceMidpoint = " + sourceMidpoint);
            }
        }
        
        if(detectPriceChanged && firstSourceAskPriceReceived){
            calculateTradeAskPrice();
            fetchOrderManager();
            
            LOG.debug("Triggering order monitor to update trade ask price");
            m_orderManager.triggerOrderMonitor();
        }
    }
    
    public boolean calculateTradeBidPrice(){
        synchronized(QUOTEACCESSLOCK){
            fetchStaticOffset();
            fetchDefaultSpread();
            
            double dynamicOffset = m_client.getPositionManager().getDynamicOffset();
            if(sourceBidPrice != -1 && dynamicOffset != Double.MAX_VALUE){
                tradeBidPrice = Math.floor(sourceBidPrice + staticOffset + dynamicOffset - defaultSpread);
                LOG.debug("Calculated trade bid price = " + sourceBidPrice + "(sourceBidPrice) + " +
                        staticOffset + "(staticOffset) + " + dynamicOffset + "(dynamicOffset) - " + defaultSpread +
                        "(defaultSpread) = " + tradeBidPrice + " (rounded down)");
                return true;
            } else {
                LOG.debug("Failed to calculate trade bid price because either source bid price or dynamic offset is missing");
                return false;
            }
        }
    }
    
    public boolean calculateTradeAskPrice(){
        synchronized(QUOTEACCESSLOCK){
            fetchStaticOffset();
            fetchDefaultSpread();
            fetchPositionManager();
            
            double dynamicOffset = m_positionManager.getDynamicOffset();
            if(sourceAskPrice != -1 && dynamicOffset != Double.MAX_VALUE){
                tradeAskPrice = Math.ceil(sourceAskPrice + staticOffset + dynamicOffset + defaultSpread);
                LOG.debug("Calculated trade ask price = " + sourceAskPrice + "(sourceAskPrice) + " +
                        staticOffset + "(staticOffset) + " + dynamicOffset + "(dynamicOffset) + " + defaultSpread +
                        "(defaultSpread) = " + tradeAskPrice + " (rounded up)");
                return true;
            } else {
                LOG.debug("Failed to calculate trade ask price because either source ask price or dynamic offset is missing");
                return false;
            }
        }
    }
    
    public double getTradeBidPrice(){
        synchronized(QUOTEACCESSLOCK){
            return this.tradeBidPrice;
        }
    }
    
    public double getTradeAskPrice(){
        synchronized(QUOTEACCESSLOCK){
            return this.tradeAskPrice;
        }
    }
    
    private Contract getSourceContract(){
        fetchSourceConid();
        fetchSourceExchange();
        
        Contract sourceContract = new Contract();
        sourceContract.conid(sourceConid);
        sourceContract.exchange(sourceExchange);
        return sourceContract;
    }
    
    public boolean confirmTickTypesReceived(){
        LOG.debug("Verifying if all tick types are received for order placement...");
        
        synchronized(QUOTELOCK){
            while(sourceBidPrice == -1.0 || sourceAskPrice == -1.0 || sourceMidpoint == -1.0 ||
                    tradeBidPrice == -1.0 || tradeAskPrice == -1.0){
                try {
                    LOG.debug("Waiting for more quotes to be received...");
                    QUOTELOCK.wait();
                } catch (Exception e){
                    LOG.error(e.getMessage(), e);
                    return false;
                }
            }
        }
        
        LOG.debug("Confirm all tick types are received.");
        return true;
    }
    
    // Fetchers
    private void fetchOrderManager(){
        while(m_orderManager == null){
            m_orderManager = m_client.getOrderManager();
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
    
    private void fetchStaticOffset(){
        if(staticOffset == Double.MAX_VALUE){
            staticOffset = Double.parseDouble(ConfigReader.getInstance().getConfig(Configs.STATIC_OFFSET));
        }
    }
    
    private void fetchDefaultSpread(){
        if(defaultSpread == Double.MAX_VALUE){
            defaultSpread = Double.parseDouble(ConfigReader.getInstance().getConfig(Configs.DEFAULT_SPREAD));
        }
    }
    
    private void fetchSourceConid(){
        if(sourceConid == Integer.MAX_VALUE){
            sourceConid = Integer.parseInt(ConfigReader.getInstance().getConfig(Configs.SOURCE_CONID));
        }
    }
    
    private void fetchSourceExchange(){
        if(sourceExchange == null){
            sourceExchange = ConfigReader.getInstance().getConfig(Configs.SOURCE_EXCHANGE);
        }
    }
}
