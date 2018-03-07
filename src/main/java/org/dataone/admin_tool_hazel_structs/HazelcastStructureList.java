/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dataone.admin_tool_hazel_structs;

import java.util.concurrent.Callable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import com.hazelcast.client.ClientConfig;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.Instance;
import com.hazelcast.core.Instance.InstanceType;
import com.hazelcast.core.ItemEvent;
import com.hazelcast.core.ItemListener;
import java.util.Collection;

/**
 *
 * @author vieglais
 */
@Command(name="HazelcastStructureList")
public class HazelcastStructureList implements Callable<Void>{

    @Option(names={"--group","-g"}, 
            description="Group (hzProcess)")
    String group_name = "hzProcess";

    @Option(names={"-p","--password"}, 
            description="Connection password")
    String client_password;
    
    @Option(names={"-c","--connection"}, 
            description="Connection target (e.g. '127.0.0.1:5701')")
    String client_connection;
    
    static final Logger LOG = LoggerFactory.getLogger(HazelcastStructureList.class);
    private HazelcastClient client = null;
    private boolean done_working = false;
    private Gson gson = new GsonBuilder().create();

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        System.setProperty( "hazelcast.logging.type", "slf4j" );        
        CommandLine.call( new HazelcastStructureList(), System.out, args);
    }

    
    public void addClient(String hz_group_name,
                          String hz_group_password,
                          String hz_address) {
        String client_key = hz_address+"/"+hz_group_name;
        LOG.info("Adding client: " + client_key);
        ClientConfig config = new ClientConfig();
        config.addAddress(hz_address);
        GroupConfig group_config = new GroupConfig();
        group_config.setName(hz_group_name);
        group_config.setPassword(hz_group_password);
        config.setGroupConfig(group_config);
        this.client = HazelcastClient.newHazelcastClient(config);
    }
    
    
    public HashMap getInstances() {
        HashMap info = new HashMap();
        Collection<Instance> instances = this.client.getInstances();
        for (Instance instance : instances) {
            InstanceType t = instance.getInstanceType();
            String id = instance.getId().toString();
            if (null != t) switch (t) {
                case LOCK:
                    break;
                case MAP:
                    LOG.debug("MAP instance: " + id);
                    info.put(id, "MAP");
                    break;
                case SET:
                    LOG.debug("SET instance: " + id);
                    info.put(id, "SET");
                    break;
                case QUEUE:
                    LOG.debug("QUEUE instance: " + id);
                    info.put(id, "QUEUE");
                    break;
                default:
                    break;
            }
        }
        return info;
    }
    
    
    @Override    
    public Void call() {
        LOG.debug(this.gson.toJson("Starting call()."));
        // Add shutdown hook to close Hazelcast connections if interrupted
        Thread mainThread=Thread.currentThread();
        Thread shutdownHook = new Thread() {
            @Override
            public void run() {
                try {
                    LOG.debug("\"Shutdown hook: shutting down stat\"");
                    if (client != null) {
                        //LifecycleService life = client.getLifecycleService();
                        //if (life.isRunning()) {
                        //    life.shutdown();
                        //}                        
                    } 
                    done_working = true;
                    mainThread.join();
                } catch (InterruptedException ex) {
                    java.util.logging.Logger.getLogger(HazelcastStructureList.class.getName()).log(Level.SEVERE, null, ex);
                }
            }            
        };
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        addClient(this.group_name,
                  this.client_password,
                  this.client_connection);
        System.out.println( this.gson.toJson(getInstances()) );
        //Shutdown the hazelcast connection if any are open.
        client.shutdown();
        while (client.isActive()) {
            try {
                Thread.sleep(500);
            } catch(InterruptedException ex) {
                LOG.info("Shutdown forced", ex);
                break;
            }
        }
        return null;
    }
}
