/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.contracts;

import com.ib.client.Contract;
import com.ib.client.Types.SecType;

/**
 *
 * @author Siteng Jin
 */
public class CfdContract {
    public static Contract getIBJP225CFDContract(){
        Contract c = new Contract();
        c.secType(SecType.CFD);
        c.conid(111987469);
        c.exchange("SMART");
        c.currency("JPY");
        return c;
    }
}
