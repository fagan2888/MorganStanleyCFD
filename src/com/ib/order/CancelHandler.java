/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.order;

import static com.ib.order.Trader.ORDERCANCELMONITORLOCK;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.log4j.Logger;
import com.ib.api.IBClient;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * @author Siteng Jin
 */
public class CancelHandler implements Runnable{
    private static final Logger LOG = Logger.getLogger(CancelHandler.class);
    
    private static OrderManager m_orderManager = null;
    
    private static IBClient m_client = null;
    
    private List<Integer> pendingCancelList = null;
    
    public CancelHandler(IBClient client){
        m_client = client;
        if(m_orderManager == null){
            m_orderManager = m_client.getOrderManager();
        }
        pendingCancelList = new CopyOnWriteArrayList<Integer>();
    }
    
    @Override
    public void run(){
        monitorCancellations();
    }
    
    private void monitorCancellations(){
        while(true){
            synchronized(ORDERCANCELMONITORLOCK){
                try{
                    LOG.debug("Canceller waiting for notification when order is added to cancel list...");
                    ORDERCANCELMONITORLOCK.wait();
                } catch (Exception e){
                    LOG.error(e.getMessage(), e);
                }
                
                LOG.debug("Canceller notified about new order needs to be cancelled");
            }
            
            if(!pendingCancelList.isEmpty()){
                for(Integer orderId : pendingCancelList){
                    m_client.getSocket().cancelOrder(orderId);
                    LOG.debug("Sent cancelOrder(" + orderId + ")");
                    
                    synchronized(Trader.ORDERCANCELMONITORLOCKFORWRAPPER){
                        try{
                            LOG.debug("Canceller waiting for cancellation confirmation for orderId = " + orderId);
                            Trader.ORDERCANCELMONITORLOCKFORWRAPPER.wait();
                        } catch (Exception e){
                            LOG.error(e.getMessage(), e);
                        }
                    }
                    LOG.debug("Order cancellation for orderId = " + orderId + " is confirmed. Removing from pending cancel list and order map");
                }
                
                fetchOrderManager();
                m_orderManager.triggerOrderMonitor();
            } else {
                LOG.debug("PendingCancelList is empty");
            }
        }
    }
    
    public boolean verifyCancel(int orderId){
        LOG.debug("Verifying if orderid = " + orderId + " is cancelled");
        if(!pendingCancelList.contains(orderId)){
            LOG.debug("Verified order is cancelled");
            return true;
        } else {
            LOG.debug("Verified order is NOT cancelled");
            return false;
        }
    }
    
    public void addOrderToPendingCancelList(int orderId){
        if(!pendingCancelList.contains((Integer) orderId)){
            pendingCancelList.add((Integer) orderId);
            LOG.debug("Added orderId = " + orderId + " to pendingCancelList, " + pendingCancelList.toString());
        } else {
            LOG.debug("Cannot add orderId = " + orderId + " to pendingCancelList because it's alread there. " + pendingCancelList.toString());
        }
    }
    
    public void removeOrderFromPendingCancelList(int orderId){
        if(pendingCancelList.contains((Integer) orderId)){
            pendingCancelList.remove((Integer) orderId);
            LOG.debug("Removed orderId = " + orderId + " from pendingCancelList, " + pendingCancelList.toString());
        } else {
            LOG.debug("Cannot remove orderId = " + orderId + " from pendingCancelList because it's not found. " + pendingCancelList.toString());
        }
    }
    
    public boolean pendingCancelListContains(int orderId){
        return pendingCancelList.contains((Integer) orderId);
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
}
