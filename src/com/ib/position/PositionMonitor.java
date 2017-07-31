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
    
    private boolean stopQuotingSizeReached = false;
    
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
            double currentPosition = m_positionManager.getPosition();
            
            if(!stopQuotingSizeReached){
                LOG.debug("Position was within quoting size band");
                if(Double.compare(Math.abs(currentPosition), stopQuotingSize) >= 0){
                    LOG.debug("StopQuotingSize reached, cancelling order accordingly");
                    stopQuotingSizeReached = true;
                    
                    // Cancel order accordingly
                    while(m_orderManager == null){
                        m_orderManager = m_client.getOrderManager();
                        try{
                            Thread.sleep(100);
                        } catch (Exception e){
                            LOG.debug(e.getMessage(), e);
                        }
                    }
                    
                    if(currentPosition > 0.0){
                        m_orderManager.cancelCurrentBuyOrder();
                    } else if(currentPosition < 0.0){
                        m_orderManager.cancelCurrentSellOrder();
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
                    stopQuotingSizeReached = false;
                    
                    // Plcae new order
                    if(m_orderManager == null){
                        m_orderManager = m_client.getOrderManager();
                    }
                    
                    if(currentPosition > 0.0){
                        m_orderManager.placeNewBuyOrder();
                    } else if(currentPosition < 0.0){
                        m_orderManager.placeNewSellOrder();
                    }
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
        return stopQuotingSizeReached;
    }
}
