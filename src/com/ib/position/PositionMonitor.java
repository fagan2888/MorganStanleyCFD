/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.position;

import com.ib.api.IBClient;
import com.ib.client.Types;
import com.ib.config.ConfigReader;
import com.ib.config.Configs;
import com.ib.order.OrderManager;
import org.apache.log4j.Logger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author Siteng Jin
 */
public class PositionMonitor implements Runnable{
    private static final Logger LOG = Logger.getLogger(PositionMonitor.class);
    
    private static final int POSITIONMONITORFREQUENCY = 10000; // In milliseconds
    
    private static OrderManager m_orderManager = null;
    private static PositionManager m_positionManager = null;
    private static ConfigReader m_configReader = null;
    
    private double stopQuotingSize = Double.MAX_VALUE;
    
    private AtomicBoolean stopQuotingSizeReached = new AtomicBoolean(false);
    
    private IBClient m_client = null;
    
    public PositionMonitor(IBClient client){
        m_client = client;
        if(m_orderManager == null){
            m_orderManager = client.getOrderManager();
        }
        if(m_positionManager == null){
            m_positionManager = client.getPositionManager();
        }
        if(m_configReader == null){
            m_configReader = ConfigReader.getInstance();
        }
        if(stopQuotingSize == Double.MAX_VALUE){
            stopQuotingSize = Double.parseDouble(m_configReader.getConfig(Configs.STOP_QUOTING_SIZE));
        }
    }
    
    @Override
    public void run(){
        LOG.debug("Starting position monitor");
        this.startMonitor();
    }
    
    private void startMonitor(){
        while(true){
            LOG.debug("Checking current position...");
            if(stopQuotingSize == Double.MAX_VALUE){
                stopQuotingSize = Double.parseDouble(m_configReader.getConfig(Configs.STOP_QUOTING_SIZE));
            }
            
            fetchOrderManager();
            
            fetchPositionManager();
            
            double currentPosition = m_positionManager.getPosition();
            
            if(!stopQuotingSizeReached.get()){
                LOG.debug("Position was within quoting size band");
                if(Double.compare(Math.abs(currentPosition), stopQuotingSize) >= 0){
                    stopQuotingSizeReached.set(true);
                    
                    // Cancel order accordingly
                    
                    if(currentPosition > 0.0){
                        int orderId = m_orderManager.getCurrentOrderId(Types.Action.BUY);
                        if(orderId < Integer.MAX_VALUE){
                            LOG.debug("StopQuotingSize reached, cancelling buy order = " + orderId);
                            m_orderManager.cancelCurrentOrder(orderId);
                        } else {
                            LOG.debug("StopQuotingSize reached, but no buy order is found");
                        }
                    } else if(currentPosition < 0.0){
                        int orderId = m_orderManager.getCurrentOrderId(Types.Action.SELL);
                        if(orderId < Integer.MAX_VALUE){
                            LOG.debug("StopQuotingSize reached, cancelling sell order = " + orderId);
                            m_orderManager.cancelCurrentOrder(orderId);
                        } else {
                            LOG.debug("StopQuotingSize reached, but no sell order is found");
                        }
                    }
                } else {
                    // Do nothing
                    LOG.debug("Position is still within quoting size band, do noting");
                }
            } else {
                LOG.debug("Position was NOT within quoting size band");
                if(Double.compare(Math.abs(currentPosition), stopQuotingSize) >= 0){
                    // Do nothing
                    LOG.debug("Position is still not within quoting size band, do nothing");
                } else {
                    LOG.debug("StopQuotingSize is released, placing new order accordingly");
                    stopQuotingSizeReached.set(false);
                    
                    m_orderManager.triggerOrderMonitor();
                }
            }
            
            try{
                Thread.sleep(POSITIONMONITORFREQUENCY);
            } catch (Exception e){
                LOG.error(e.getMessage(), e);
            }
        }
    }
    
    public boolean getStopQuotingSizeReached(){
        return stopQuotingSizeReached.get();
    }
    
    // Fetchers
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
}
