/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.order;

import com.ib.client.Types;

/**
 *
 * @author Siteng Jin
 */
public class OrderidConidAction {
    private int orderId;
    private int conid;
    private Types.Action action;
    
    public OrderidConidAction(int orderId, int conid, Types.Action action){
        this.orderId = orderId;
        this.conid = conid;
        this.action = action;
    }
    
    @Override
    public boolean equals(Object o){
        if(o instanceof OrderidConidAction){
            OrderidConidAction rhs = (OrderidConidAction) o;
            return (rhs.orderId == this.orderId && 
                    rhs.conid == this.conid &&
                    rhs.action == this.action);
        }
        return false;
    }
    
    @Override
    public int hashCode(){
        return orderId;
    }

    public int getOrderId() {
        return orderId;
    }

    public int getConid() {
        return conid;
    }

    public Types.Action getAction() {
        return action;
    }
    
    
}
