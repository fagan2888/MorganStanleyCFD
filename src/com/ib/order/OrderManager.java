/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/
package com.ib.order;

import com.ib.api.IBClient;
import org.apache.log4j.Logger;
import java.util.HashMap;
import com.ib.client.Order;
import com.ib.client.Types;
import com.ib.config.*;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Siteng Jin
 */
public class OrderManager{
    private static final Logger LOG = Logger.getLogger(OrderManager.class);
    
    private static CancelHandler m_cancelHandler = null;
    
    public static final Object OPENORDERLOCK = new Object();
    public static final Object CANCELORDERLOCK = new Object();
    
    private IBClient m_client = null;
    
    private ConcurrentHashMap<Integer, OrderInfo> m_orderMap = null;
    
    public OrderManager( IBClient client){
        LOG.debug("Initializing Order Manager");
        m_client = client;
        if(m_orderMap == null){
            m_orderMap = new ConcurrentHashMap<Integer, OrderInfo>();
        }
        if(m_cancelHandler == null){
            m_cancelHandler = m_client.getCancelHandler();
        }
    }
    
    public void requestOpenOrder(){
        m_client.getSocket().reqOpenOrders();
        LOG.debug("Sent reqOpenOrders()");
    }
    
    public void requestAutoOpenOrder(){
        // make sure "Use negative numbers to bind automatic orders" is checked in API settings
        m_client.getSocket().reqAutoOpenOrders(true);
        LOG.debug(("Sent reqAutoOpenOrders()"));
    }
    
    public void updateOpenOrder(int orderId , Order order){
        //LOG.debug("updateOpenOrder() Acquiring ORDERACCESSLOCK");
        //LOG.debug("updateOpenOrder() Acquired ORDERACCESSLOCK");
        if(m_orderMap.containsKey((Integer) orderId)){
            OrderInfo o = m_orderMap.get((Integer) orderId);
            o.setOrder(order);
            m_orderMap.replace((Integer) orderId, o);
            LOG.debug("Updated open order. Order: " + m_orderMap.get((Integer) orderId).getOrder().action() + " " +
                    m_orderMap.get((Integer) orderId).getOrder().totalQuantity() + ", Filled: " + m_orderMap.get((Integer) orderId).getFilled());
        } else {
            m_orderMap.put((Integer) orderId, new OrderInfo(order, Double.MAX_VALUE, Double.MAX_VALUE));
            
            LOG.debug("Added open order. Order: " + m_orderMap.get((Integer) orderId).getOrder().action() + " " +
                    m_orderMap.get((Integer) orderId).getOrder().totalQuantity() + ". " + m_orderMap.toString());
        }
        //LOG.debug("updateOpenOrder() Released ORDERACCESSLOCK");
    }
    
    public void updateOrderStatus(int orderId, double filled, double remaining){
        //LOG.debug("updateOrderStatus() Acquiring ORDERACCESSLOCK");
        boolean detectedPartialFill = false;
        boolean detectedFullFill = false;
        
        //LOG.debug("updateOrderStatus() Acquired ORDERACCESSLOCK");
        if(m_orderMap.containsKey((Integer) orderId)){
            OrderInfo o = m_orderMap.get((Integer) orderId);
            o.setFilled(filled);
            o.setRemaining(remaining);
            m_orderMap.replace((Integer) orderId, o);
            LOG.debug("Updated order status. Order: " + m_orderMap.get((Integer) orderId).getOrder().action() + " " +
                    m_orderMap.get((Integer) orderId).getOrder().totalQuantity() + ", Filled: " + m_orderMap.get((Integer) orderId).getFilled());
            
            if(Double.compare(filled, 0.0) > 0){
                if(Double.compare(remaining, 0.0) > 0){
                    // Partial fill occurred
                    detectedPartialFill = true;
                } else if (Double.compare(remaining, 0.0) == 0){
                    detectedFullFill = true;
                }
            }
            // Do not update status if no orderid is found
        }
        //LOG.debug("updateOrderStatus() Released ORDERACCESSLOCK");
        
        if(detectedPartialFill){
            LOG.debug("Partial fill detected. Cancelling order");
            
            cancelCurrentOrder(orderId);
        }
        
        if(detectedFullFill){
            LOG.debug("Full fill detected. Removing order from map and trigger Order Monitor");
            fetchCancelHandler();
            if(m_orderMap.containsKey((Integer) orderId)){
                m_cancelHandler.removeOrderFromPendingCancelList(orderId);
                removeOrderFromMap((Integer) orderId);
                triggerOrderMonitor();
            } else {
                LOG.debug("Filled orderId = " + orderId + " is not in order map");
            }
        }
    }
    
    public void processExecDetails(int orderId, double cumQty){
        if(m_orderMap.containsKey((Integer) orderId)){
            OrderInfo o = m_orderMap.get((Integer) orderId);
            
            if(Double.compare(cumQty, o.getOrder().totalQuantity()) >= 0){
                LOG.debug("Full fill detected. Removing order from map and trigger Order Monitor");
                fetchCancelHandler();
                m_cancelHandler.removeOrderFromPendingCancelList(orderId);
                removeOrderFromMap((Integer) orderId);
                triggerOrderMonitor();
            } else {
                LOG.debug("Partial fill detected. Cancelling order");
                
                cancelCurrentOrder(orderId);
            }
        }
    }
    
    // New designs
    public void triggerOrderMonitor(){
        synchronized(Trader.ORDERMONITORLOCK){
            Trader.ORDERMONITORLOCK.notifyAll();
            LOG.debug("Triggerring Order Monitor");
        }
    }
    
    public void cancelCurrentOrder(int orderId){
        // returns the Action of the canceled order for placing new order
        if(!m_orderMap.containsKey((Integer) orderId)){
            this.requestOpenOrder();
            try{
                Thread.sleep(200);
            } catch (Exception e){
                LOG.error(e.getMessage(), e);
            }
        }
        
        fetchCancelHandler();
        
        synchronized(Trader.ORDERCANCELMONITORLOCK){
            if(!m_cancelHandler.pendingCancelListContains(orderId)){
                m_cancelHandler.addOrderToPendingCancelList(orderId);
                Trader.ORDERCANCELMONITORLOCK.notifyAll();
                LOG.debug("Sending notification to cancel handler");
            } else {
                LOG.debug("OrderId = " + orderId + " already exists in pendingCancelList");
            }
        }
    }
    
    public int getCurrentOrderId(Types.Action action){
        if(!m_orderMap.isEmpty()){
            Iterator it = m_orderMap.keySet().iterator();
            while(it.hasNext()){
                Integer orderId = (Integer) it.next();
                OrderInfo o = m_orderMap.get((Integer) orderId);
                if(o.getOrder().action() == action){
                    return orderId;
                }
            }
            LOG.debug("No " + action + " order is found, returning Max Integer");
            return Integer.MAX_VALUE;
        } else {
            LOG.debug("Returning Max Integer because no active order is found");
            return Integer.MAX_VALUE;
        }
    }
    
    public void removeOrderFromMap(int orderId){
        if(m_orderMap.containsKey((Integer) orderId)){
            m_orderMap.remove((Integer) orderId);
            LOG.debug("OrderId = " + orderId + " is removed from order map. " + m_orderMap.toString());
        } else {
            LOG.debug("OrderId = " + orderId + " cannot be removed because it's not in the order map. " + m_orderMap.toString()) ;
        }
    }
    
    public ConcurrentHashMap<Integer, OrderInfo> getOrderMap(){
        return m_orderMap;
    }
    
    // Fetchers
    private void fetchCancelHandler(){
        while(m_cancelHandler == null){
            m_cancelHandler = m_client.getCancelHandler();
            try{
                Thread.sleep(100);
            } catch (Exception e){
                LOG.error(e.getMessage(), e);
            }
        }
    }
}
