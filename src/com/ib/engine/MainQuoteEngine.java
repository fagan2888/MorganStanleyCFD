/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.engine;

import org.apache.log4j.Logger;
import com.ib.api.IBClient;
import com.ib.client.EReaderSignal;
import com.ib.client.EReader;

/**
 *
 * @author Siteng Jin
 */
public class MainQuoteEngine implements Runnable{
    public static final Logger LOG = Logger.getLogger(MainQuoteEngine.class);
    
    private static IBClient m_client = null;
    private static EReaderSignal m_signal = null;
            
    private static String HOST = "127.0.0.1";
    private static int PORT = 7496;
    public static int CLIENTID = 0;
    
    @Override
    public void run(){
        m_client.start();
    }
    
    public static void main(String[] args) {
        LOG.info("---------------------------Starting Morgan Stanley Quote Test---------------------------");
        
        m_client = IBClient.getInstance();
        m_signal = m_client.getSignal();
        
        //! [connect]
        m_client.getSocket().eConnect(HOST, PORT, CLIENTID);
        //! [connect]
        //! [ereader]
        final EReader reader = new EReader(m_client.getSocket(), m_signal);
        reader.start();
        new Thread("wrapper") {
            public void run() {
                while (m_client.getSocket().isConnected()) {
                    m_signal.waitForSignal();
                    try {
                        reader.processMsgs();
                    } catch (Exception e) {
                        LOG.error(e.getMessage(), e);
                    }
                }
            }
        }.start();
        
        try{
            while(m_client.getCurrentOrderId() == -1){
                Thread.sleep(200);
            }
            
        new Thread(new MainQuoteEngine(), "trader").start();
            
        } catch (Exception e){
            LOG.error(e.getMessage(), e);
        }
    }
    
}
