/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dataone.admin_tool_hazel_structs;

/**
 *
 * @author vieglais
 */
public class HZStructInfo{
    private String sid = "";
    private String sname="";
    private String stype="";
    private int size=0;
    
    public HZStructInfo(String sid, String sname, String stype, int ssize) {
        this.sid = sid;
        this.sname = sname;
        this.stype = stype;
        this.size = ssize;
    }
    
    public String getId() {
        return this.sid;
    }
    
    public String getName() {
        return this.sname;
    }
    
    public String getType() {
        return this.stype;
    }
    
    public int getSize() {
        return this.size;
    }
}
