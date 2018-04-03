/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dataone.admin_tool_hazel_structs;

import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import com.hazelcast.client.ClientConfig;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.ISet;
import com.hazelcast.core.Instance;
import com.hazelcast.core.Instance.InstanceType;
import java.util.Collection;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.Template;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.tools.generic.DateTool;
import org.apache.velocity.exception.ResourceNotFoundException;

/**
 *
 * @author vieglais
 * 
 * Connect up to hazelcast and dump the available structures. e.g.
 * 
 * ssh -L5701:localhost:5701 cn-um-1.dataone.org
 * 
 * then:
 * 
 * java -jar target/admin_tool_hazel_structs-1.0-SNAPSHOT-jar-with-dependencies.jar \
 *  -c "127.0.0.1:5701" -g "DataONE" -p "password"
 * 
 * Clusters are:
 *   Storage: 5701, DataONE
 *   Process: 5702, hzProcess
 *   Portal: 
 */
@Command(name="HazelcastStructureList",
        description="List structures and basic properties of a Hazelcast Cluster")
public class HazelcastStructureList implements Runnable {

    @Option(names={"--group","-g"}, 
            description="Group (hzProcess)")
    String group_name = "hzProcess";

    @Option(names={"-p","--password"}, 
            description="Connection password",
            required=true)
    String client_password=null;
    
    @Option(names={"-c","--connection"}, 
            description="Connection target (e.g. '127.0.0.1:5701')",
            required=true)
    String client_connection=null;
    
    @Option(names={"-s","--sizes"},
            description="Report sizes of structures",
            required=false)
    boolean report_sizes = false;

    @Option(names={"--locks","-l"}, 
            description="Include locks in listing")
    boolean include_locks = false;

    @Option(names={"--template"},
            description="Velocity template for output")
    String text_template="render_text.vm";

    @Option(names={"--timeout"},
            description="Connection timeout milliseconds")
    int reconection_timeout = 3000; //milliseconds

    @Option(names={"--retry"},
            description="Connection retries")
    int connection_retry_limit = 3;
    
    int connection_timeout = 3000; //milliseconds
    
    static { System.setProperty("logback.configurationFile", "logback.xml"); }
    static { System.setProperty( "hazelcast.logging.type", "slf4j" ); }
    static final Logger LOG = LoggerFactory.getLogger(HazelcastStructureList.class);
    private HazelcastClient client;
    private boolean done_working;
   
    /**
     * @param args the command line arguments
     * @throws java.lang.Exception
     */
    public static void main(String[] args) throws Exception {
        CommandLine.run(new HazelcastStructureList(), args);
    }

    public HazelcastStructureList() {
        this.done_working = false;
        this.client = null;
    }

    
    public void addClient(String hz_group_name,
                          String hz_group_password,
                          String hz_address) {
        String client_key = hz_address+"/"+hz_group_name;
        LOG.info("Adding client: " + client_key);
        ClientConfig config = new ClientConfig();
        config.addAddress(hz_address);
        config.setConnectionTimeout(this.connection_timeout);
        config.setReConnectionTimeOut(this.reconection_timeout);
        config.setInitialConnectionAttemptLimit(this.connection_retry_limit);
        GroupConfig group_config = new GroupConfig();
        group_config.setName(hz_group_name);
        group_config.setPassword(hz_group_password);
        config.setGroupConfig(group_config);
        this.client = HazelcastClient.newHazelcastClient(config);
    }
    
    
    public Map<String, List<HZStructInfo>> getInstances() {
        LOG.info("Gathering instance information...");
        Map<String, List<HZStructInfo>> info = new HashMap<>();

        HZStructInfo sinfo = null;
        IQueue<Object> queue_obj = null;
        IMap<Object, Object> map_obj = null;
        ISet<Object> set_obj = null;
        int ssize;
        Collection<Instance> instances = this.client.getInstances();
        for (Instance instance : instances) {
            InstanceType t = instance.getInstanceType();
            String id = instance.getId().toString();
            String[] id_parts = id.split(":");
            String instance_name = id_parts[id_parts.length-1];
            ssize = -1;
            if (t.isLock()) {
                if (this.include_locks) {
                    LOG.debug("LOCK instance: " + id);
                    sinfo = new HZStructInfo(id, instance_name, "LOCK", 0);
                    info.computeIfAbsent("LOCK", k -> new ArrayList<>()).add(sinfo);
                }
            } else if (t.isMap()) {
                    LOG.debug("MAP instance: " + id);
                    if (this.report_sizes) {
                        map_obj = this.client.getMap(instance_name);
                        ssize = map_obj.size();
                    }
                    sinfo = new HZStructInfo(id, instance_name, "MAP",ssize);
                    info.computeIfAbsent("MAP", k -> new ArrayList<>()).add(sinfo);
            } else if (t.isMultiMap()) {
                    LOG.debug("MultiMAP instance: " + id);
                    if (this.report_sizes) {
                        map_obj = this.client.getMap(instance_name);
                        ssize = map_obj.size();
                    }
                    sinfo = new HZStructInfo(id, instance_name, "MULTIMAP", ssize);
                    info.computeIfAbsent("MULTIMAP", k -> new ArrayList<>()).add(sinfo);
            } else if (t.isSet()) {
                    LOG.debug("SET instance: " + id);
                    if (this.report_sizes) {
                        set_obj = this.client.getSet(instance_name);
                        ssize = set_obj.size();
                    }
                    sinfo = new HZStructInfo(id, instance_name, "SET", ssize);
                    info.computeIfAbsent("SET", k -> new ArrayList<>()).add(sinfo);
            } else if (t.isList()) {
                    LOG.debug("LIST instance: " + id);
                    if (this.report_sizes) {
                        map_obj = this.client.getMap(instance_name);
                        ssize = map_obj.size();
                    }
                    sinfo = new HZStructInfo(id, instance_name, "LIST", ssize);
                    info.computeIfAbsent("LIST", k -> new ArrayList<>()).add(sinfo);
            } else if (t.isQueue()) {
                    LOG.debug("QUEUE instance: " + id);
                    if (this.report_sizes) {
                        queue_obj = this.client.getQueue(instance_name);
                        ssize = queue_obj.size();
                    }
                    sinfo = new HZStructInfo(id, instance_name, "QUEUE", ssize);
                    info.computeIfAbsent("QUEUE", k -> new ArrayList<>()).add(sinfo);
            }
        }
        return info;
    }
    
    
    public void showInstances(Map<String, List<HZStructInfo>> instances) {
        Velocity.init();
        VelocityContext context = new VelocityContext();
        Template template = null;
        context.put("date", new DateTool());
        context.put("group_name", this.group_name);
        context.put("service_address", this.client_connection);
        context.put("include_locks", this.include_locks);
        context.put("maps", instances.get("MAP"));
        context.put("locks", instances.get("LOCK"));
        context.put("multimaps", instances.get("MULTIMAP"));
        context.put("sets", instances.get("SET"));
        context.put("lists", instances.get("LIST"));
        context.put("queues", instances.get("QUEUE"));
        try {
            template = Velocity.getTemplate(this.text_template);
        } catch ( ResourceNotFoundException rnfe ) {
            LOG.error("Could not find template!");
        }
        StringWriter output = new StringWriter();
        template.merge( context, output);
        System.out.print( output.toString() );
    }
    
    
    @Override    
    public void run() {
        LOG.info("Setting up...");
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
                    LOG.error(ex.getMessage());
                }
            }            
        };
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        addClient(this.group_name,
                  this.client_password,
                  this.client_connection);
        //System.out.println( this.gson.toJson(getInstances()) );
        this.showInstances( this.getInstances() );
        //Shutdown the hazelcast connection if any are open.
        client.shutdown();
        LOG.info("Shutdown.");
        while (client.isActive()) {
            try {
                Thread.sleep(500);
            } catch(InterruptedException ex) {
                LOG.info("Shutdown forced", ex);
                break;
            }
        }
    }

}
