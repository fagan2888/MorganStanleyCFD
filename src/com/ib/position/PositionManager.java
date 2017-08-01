/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/
package com.ib.position;

import com.ib.api.IBClient;
import com.ib.config.ConfigReader;
import com.ib.config.Configs;
import com.ib.order.OrderManager;
import org.apache.log4j.Logger;
import java.util.concurrent.ConcurrentHashMap;
import com.ib.quote.QuoteManager;

/**
 *
 * @author Siteng Jin
 */
public class PositionManager {
    private static final Logger LOG = Logger.getLogger(PositionManager.class);
    private static OrderManager m_orderManager = null;
    private static QuoteManager m_quoteManager = null;
    
    public static final Object POSITIONLOCK = new Object();
    public static final Object DYNAMICOFFSETACCESSLOCK = new Object();
    
    private int sourceConid = Integer.MAX_VALUE;
    private int tradeConid = Integer.MAX_VALUE;
    private double hedgeMultiplier = Double.MAX_VALUE;
    private double priceDistortionRate = Double.MAX_VALUE;
    private double dynamicOffset = Double.MAX_VALUE;
    
    private IBClient m_client = null;
    
    private ConcurrentHashMap<Integer, Position> m_positions = null; // Map conid to position
    
    public PositionManager(IBClient client){
        LOG.debug("Initializing Position Manager");
        m_client = client;
        m_positions = new ConcurrentHashMap<Integer, Position>();
        
        if(m_orderManager == null){
            m_orderManager = m_client.getOrderManager();
        }
        if(m_quoteManager == null){
            m_quoteManager = m_client.getQuoteManager();
        }
        
        fetchSourceConid();
        fetchTradeConid();
        fetchHedgeMultiplier();
        fetchPriceDistortionRate();
    }
    
    public void requestPosition(){
        m_client.getSocket().reqPositions();
        LOG.info("Sent reqPositions()");
    }
    
    public double getHedgedTradePosition(){
        fetchHedgeMultiplier();
        double tradePosition = getTradePosition();
        double hedgePosition = getHedgePosition();
        double hedgedTradePosition = tradePosition  + hedgeMultiplier * hedgePosition;
        LOG.debug("Returning Hedged Trade Position = " + tradePosition + "(tradePosition) + " + hedgeMultiplier +
                "(hedgeMultiplier) * " + hedgePosition + "(hedgePoisition) = " + hedgedTradePosition);
        return hedgedTradePosition;
    }
    
    public double getTradePosition(){
        fetchTradeConid();
        Position pos = m_positions.get(tradeConid);
        double res = (pos == null ? 0.0 : pos.getPos());
        LOG.debug("Returning current trade position = " + res);
        return res;
    }
    
    public double getHedgePosition(){
        fetchSourceConid();
        Position pos = m_positions.get(sourceConid);
        double res = (pos == null ? 0.0 : pos.getPos());
        LOG.debug("Returning current source position = " + res);
        return res;
    }
    
    public synchronized void updatePosition(Position pos){
        int conid = pos.getContract().conid();
        if(m_positions.containsKey(conid)){
            m_positions.replace(conid, pos);
        } else {
            m_positions.put(conid, pos);
        }
        LOG.info("Updated position:" + m_positions.get(pos.getContract().conid()).toString());
        
        calculateDynamicOffset();
    }
    
    public boolean confirmAllPositionReceived(){
        LOG.debug("Verifying positions...");
        
        synchronized(POSITIONLOCK){
            try {
                LOG.debug("Waiting for position end to be received...");
                POSITIONLOCK.wait();
            } catch (Exception e){
                LOG.error(e.getMessage(), e);
                return false;
            }
        }
        
        LOG.debug("PositionEnd is received, calculating dynamicOffset");
        
        calculateDynamicOffset();
        
        return true;
    }
    
    private void calculateDynamicOffset(){ 
        fetchPriceDistortionRate();
        
        synchronized(PositionManager.DYNAMICOFFSETACCESSLOCK){
            double previousDynamicOffset = dynamicOffset;
            double currentPosition = getHedgedTradePosition();
            dynamicOffset = -1.0 * currentPosition * priceDistortionRate;
            
            LOG.debug("Calculated dynamicOffset = -1.0 * " + currentPosition + " * " + priceDistortionRate + " = "+ dynamicOffset);
            
            if(Double.compare(previousDynamicOffset, dynamicOffset) != 0){
                LOG.debug("Dynamic Offset has been changed: " + previousDynamicOffset + " -> " + dynamicOffset + ". Triggering Order Monitor");
                
                m_quoteManager.calculateTradeBidPrice();
                m_quoteManager.calculateTradeAskPrice();
                
                fetchOrderManager();
                m_orderManager.triggerOrderMonitor();
            } else {
                LOG.debug("Dynamic Offset remains the same: " + previousDynamicOffset + " -> " + dynamicOffset);
            }
        }
    }
    
    public double getDynamicOffset(){
        synchronized(PositionManager.DYNAMICOFFSETACCESSLOCK){
            if(dynamicOffset == Double.MAX_VALUE){
                calculateDynamicOffset();
            }
            LOG.debug("Returning dynamicOffset = " + dynamicOffset);
            return dynamicOffset;
        }
    }
    
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
    
    private void fetchHedgeMultiplier(){
        if(hedgeMultiplier == Double.MAX_VALUE){
            hedgeMultiplier = Double.parseDouble(ConfigReader.getInstance().getConfig(Configs.HEDGE_MULTIPLIER));
        }
    }
    
    private void fetchPriceDistortionRate(){
        if(priceDistortionRate == Double.MAX_VALUE){
            priceDistortionRate = Double.parseDouble(ConfigReader.getInstance().getConfig(Configs.PRICE_DISTORTION_RATE));
        }
    }
    
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
}
