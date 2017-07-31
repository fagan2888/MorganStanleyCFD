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
import java.util.HashMap;

/**
 *
 * @author Siteng Jin
 */
public class PositionManager {
    private static final Logger LOG = Logger.getLogger(PositionManager.class);
    private static ConfigReader m_configReader = null;
    private static OrderManager m_orderManager = null;
    
    public static final Object POSITIONLOCK = new Object();
    private static final Object POSITIONACCESSLOCK = new Object();
    
    private int sourceConid = Integer.MAX_VALUE;
    private double priceDistortionRate = Double.MAX_VALUE;
    private double dynamicOffset = Double.MAX_VALUE;
    
    private IBClient m_client = null;
    
    private HashMap<Integer, Position> m_positions = null; // Map conid to position
    
    public PositionManager(IBClient client){
        LOG.debug("Initializing Position Manager");
        m_client = client;
        m_positions = new HashMap<Integer, Position>();
        
        if(m_configReader == null){
            m_configReader = ConfigReader.getInstance();
        }
        if(m_orderManager == null){
            m_orderManager = m_client.getOrderManager();
        }
    }
    
    public void requestPosition(){
        m_client.getSocket().reqPositions();
        LOG.info("Sent reqPositions()");
    }
    
    public double getPosition(){
        synchronized(POSITIONACCESSLOCK){
            if(sourceConid == Integer.MAX_VALUE){
                sourceConid = Integer.parseInt(m_configReader.getConfig(Configs.TRADE_CONID));
            }
            Position pos = m_positions.get(sourceConid);
            return pos == null ? 0.0 : pos.getPos();
        }
    }
    
    public synchronized void updatePosition(Position pos){
        synchronized(POSITIONACCESSLOCK){
            int conid = pos.getContract().conid();
            if(m_positions.containsKey(conid)){
                m_positions.replace(conid, pos);
            } else {
                m_positions.put(conid, pos);
            }
            LOG.info("Updated position:" + m_positions.get(pos.getContract().conid()).toString());
        }
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
        
        return calculateDynamicOffset();
    }
    
    private boolean calculateDynamicOffset(){
        if(priceDistortionRate == Double.MAX_VALUE){
            priceDistortionRate = Double.parseDouble(m_configReader.getConfig(Configs.PRICE_DISTORTION_RATE));
        }
        
        dynamicOffset = -1.0 * getPosition() * priceDistortionRate;
        
        LOG.debug("Calculated dynamicOffset = " + dynamicOffset);
        
        return true;
    }
    
    public double getDynamicOffset(){
        return dynamicOffset;
    }
}
