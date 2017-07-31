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
import java.text.DecimalFormat;
import com.ib.position.*;
import com.ib.order.OrderManager;

/**
 *
 * @author Siteng Jin
 */
public class QuoteManager {
    public static final int TICKERID = 1000;
    private static ConfigReader m_configReader = null;
    
    private static final Logger LOG = Logger.getLogger(QuoteManager.class);
    
    private static OrderManager m_orderManager = null;
    
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
    
    private DecimalFormat df = new DecimalFormat("#.##");
    
    public QuoteManager(IBClient client){ 
        LOG.debug("Initializing Quote Manager");
        m_client = client;
        if(m_configReader == null){
            m_configReader = ConfigReader.getInstance();
        }
        if(m_orderManager == null){
            m_orderManager = m_client.getOrderManager();
        }
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
                    sourceMidpoint = Double.valueOf(df.format(sourceMidpoint));
                }
                detectPriceChanged = true;
                LOG.debug("Updated info: sourceBidPrice = " + sourceBidPrice + ", tradeBidPrice = " + tradeBidPrice +
                        ", sourceMidpoint = " + sourceMidpoint);
            }
        }
        
        if(detectPriceChanged && firstSourceBidPriceReceived){
            calculateTradeBidPrice();
            if(m_orderManager == null){
                m_orderManager = m_client.getOrderManager();
            }
            if(m_orderManager.hasBuyOrder()){
                LOG.debug("Active Buy order found. Updating current buy order");
                m_orderManager.updateCurrentBuyOrder();
            }
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
                    sourceMidpoint = Double.valueOf(df.format(sourceMidpoint));
                }
                detectPriceChanged = true;
                LOG.debug("Updated info: sourceAskPrice = " + sourceAskPrice + ", tradeAskPrice = " + tradeAskPrice +
                        ", sourceMidpoint = " + sourceMidpoint);
            }
        }
        
        if(detectPriceChanged && firstSourceAskPriceReceived){
            calculateTradeAskPrice();
            if(m_orderManager == null){
                m_orderManager = m_client.getOrderManager();
            }
            if(m_orderManager.hasSellOrder()){
                LOG.debug("Active SELL order found. Updating current sell order");
                m_orderManager.updateCurrentSellOrder();
            }
        }
    }
    
    public boolean calculateTradeBidPrice(){
        if(staticOffset == Double.MAX_VALUE){
            staticOffset = Double.parseDouble(m_configReader.getConfig(Configs.STATIC_OFFSET));
        }
        if(defaultSpread == Double.MAX_VALUE){
            defaultSpread = Double.parseDouble(m_configReader.getConfig(Configs.DEFAULT_SPREAD));
        }
        
        double dynamicOffset = m_client.getPositionManager().getDynamicOffset();
        if(sourceBidPrice != -1 && dynamicOffset != Double.MAX_VALUE){
            tradeBidPrice = Double.valueOf(df.format(sourceBidPrice + staticOffset + dynamicOffset - defaultSpread)); 
            LOG.debug("Calculated trade bid price = " + sourceBidPrice + "(sourceBidPrice) + " + 
                    staticOffset + "(staticOffset) + " + dynamicOffset + "(dynamicOffset) - " + defaultSpread + 
                    "(defaultSpread) = " + tradeBidPrice);
            return true;
        } else {
            LOG.debug("Failed to calculate trade bid price because either source bid price or dynamic offset is missing");
            return false;
        }
    }
    
    public boolean calculateTradeAskPrice(){
        if(staticOffset == Double.MAX_VALUE){
            staticOffset = Double.parseDouble(m_configReader.getConfig(Configs.STATIC_OFFSET));
        }
        
        double dynamicOffset = m_client.getPositionManager().getDynamicOffset();
        if(sourceAskPrice != -1 && dynamicOffset != Double.MAX_VALUE){
            tradeAskPrice = Double.valueOf(df.format(sourceAskPrice + staticOffset + dynamicOffset + defaultSpread)); 
            LOG.debug("Calculated trade ask price = " + sourceAskPrice + "(sourceAskPrice) + " + 
                    staticOffset + "(staticOffset) + " + dynamicOffset + "(dynamicOffset) + " + defaultSpread + 
                    "(defaultSpread) = " + tradeAskPrice);
            return true;
        } else {
            LOG.debug("Failed to calculate trade ask price because either source ask price or dynamic offset is missing");
            return false;
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
        if(sourceConid == Integer.MAX_VALUE){
            sourceConid = Integer.parseInt(m_configReader.getConfig(Configs.SOURCE_CONID));
        }
        if(sourceExchange == null){
            sourceExchange = m_configReader.getConfig(Configs.SOURCE_EXCHANGE);
        }
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
}
