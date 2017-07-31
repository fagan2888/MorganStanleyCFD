/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.order;

import com.ib.client.Order;

/**
 *
 * @author sitengjin
 */
public class OrderInfo {
    private Order order = null;
    private double filled = Double.MAX_VALUE;
    private double remaining = Double.MAX_VALUE;
    
    public OrderInfo(Order order, double filled,double remaining){
        this.order = order;
        this.filled = filled;
        this.remaining = remaining;
    }
    
    public Order getOrder(){
        return order;
    }
    
    public double getFilled(){
        return filled;
    }
    
    public double getRemaining(){
        return remaining;
    }
    
    public void setOrder(Order order){
        if(order != null){
            this.order = order;
        }
    }
    
    public void setFilled(double filled){
        this.filled = filled;
    }
    
    public void setRemaining(double remaining){
        this.remaining = remaining;
    }
}
