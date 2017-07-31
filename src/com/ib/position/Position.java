/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.position;

import com.ib.client.Contract;

/**
 *
 * @author Siteng Jin
 */
public class Position {
    private String account = null;
    private Contract contract = null;
    private double pos = -1.0;
    private double avgCost = -1.0;
    
    public Position(String account, Contract contract, double pos, double avgCost){
        this.account = account;
        this.contract = contract;
        this.pos = pos;
        this.avgCost = avgCost;
    }

    public String getAccount() {
        return account;
    }

    public Contract getContract() {
        return contract;
    }

    public double getPos() {
        return pos;
    }

    public double getAvgCost() {
        return avgCost;
    }
    
    @Override
    public String toString(){
        return contract.conid() + "-" + pos;
    }
}
