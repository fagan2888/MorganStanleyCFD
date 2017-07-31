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
    
    private List pendingCancelList = null;
    
    public CancelHandler(IBClient client){
        m_client = client;
        m_orderManager = m_client.getOrderManager();
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
            
            List<Integer> pendingCancelList = (CopyOnWriteArrayList<Integer>) m_orderManager.getPendingCancelList();
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
                    pendingCancelList.remove((Integer) orderId);
                    LOG.debug("Order cancellation for orderId = " + orderId + " is confirmed. Removing from pending cancel list");
                }
                synchronized(Trader.NEWORDERMONITORLOCK){
                    Trader.NEWORDERMONITORLOCK.notifyAll();
                    LOG.debug("Notify NewOrderHandler to check if new order needs to be placed");
                }
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
        pendingCancelList.add(orderId);
    }
}
