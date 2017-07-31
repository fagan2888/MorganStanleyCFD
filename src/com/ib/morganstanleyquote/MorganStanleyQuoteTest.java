/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.morganstanleyquote;

import com.ib.client.*;
import com.ib.api.IBClient;
import org.apache.log4j.Logger;

/**
 *
 * @author Siteng Jin
 */
public class MorganStanleyQuoteTest {
    public static final Logger LOG = Logger.getLogger(MorganStanleyQuoteTest.class);
    
    private static String HOST = "127.0.0.1";
    private static int PORT = 7496;
    public static int CLIENTID = 0;
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        LOG.info("Starting Morgan Stanley Quote Test");
        
        final IBClient m_client = IBClient.getInstance();
        final EReaderSignal m_signal = m_client.getSignal();
        //! [connect]
        m_client.getSocket().eConnect(HOST, PORT, CLIENTID);
        //! [connect]
        //! [ereader]
        final EReader reader = new EReader(m_client.getSocket(), m_signal);
        reader.start();
        new Thread() {
            public void run() {
                while (m_client.getSocket().isConnected()) {
                    m_signal.waitForSignal();
                    try {
                        reader.processMsgs();
                    } catch (Exception e) {
                        System.out.println("Exception: "+e.getMessage());
                    }
                }
            }
        }.start();
        
    }
    
}
