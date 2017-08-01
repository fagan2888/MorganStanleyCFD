/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Enumeration;
import java.util.HashMap;
import org.apache.log4j.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 *
 * @author Siteng Jin
 */
public class ConfigReader {
    private static final String FILENAME = "resources/config.properties";    
    private static final Logger LOG = Logger.getLogger(ConfigReader.class);
    
    protected static ConfigReader _instance = null;
    
    private HashMap<String, String> m_config = null;
    
    private Properties m_prop = new Properties();
    private InputStream input = null;
    
    private ConfigReader(){
        readProperties();
    }
    
    public void readProperties(){
        try{
            File file = new File(FILENAME);
            FileInputStream fileInput = new FileInputStream(file);
            m_prop = new Properties();
            m_prop.load(fileInput);
            fileInput.close();
            
            if(m_config == null){
                m_config = new HashMap<String, String>();
            }
            
            Enumeration enuKeys = m_prop.keys();
            while(enuKeys.hasMoreElements()){
                String key = (String) enuKeys.nextElement();
                String value = m_prop.getProperty(key);
                if(m_config.containsKey(key)){
                    m_config.replace(key, value);
                } else {
                    m_config.put(key, value);
                }
            }
            LOG.debug("Successfully loaded config: " + m_config.toString());
        } catch (IOException ex){
            LOG.error(ex.getMessage(), ex);
        }
    }
    
    /*
    public void readProperties(){
        try{
            input = this.getClass().getClassLoader().getResourceAsStream(FILENAME);
            if(input == null){
                LOG.error("Unable to read config.properties file");
                return;
            }
            
            m_prop.load(input);
            
            if(m_config == null){
                m_config = new HashMap<String, String>();
            }
            
            Enumeration<?> e = m_prop.propertyNames();
            while(e.hasMoreElements()){
                String key = (String) e.nextElement();
                String value = m_prop.getProperty(key);
                if(m_config.containsKey(key)){
                    m_config.replace(key, value);
                } else {
                    m_config.put(key, value);
                }
            }
            LOG.debug("Successfully loaded config: " + m_config.toString());
        } catch (IOException ex){
            LOG.error(ex.getMessage(), ex);
        }
    }
*/
    
    public String getConfig(String key){
        // null is returned if no key is found
        if(m_config == null){
            readProperties();
        }
        return m_config.get(key);
    }
    
    public static ConfigReader getInstance(){
        if(_instance == null){
            _instance = new ConfigReader();
        }
        return _instance;
    }
}
