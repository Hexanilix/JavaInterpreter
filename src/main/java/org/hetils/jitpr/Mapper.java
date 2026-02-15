package org.hetils.jitpr;

import java.util.HashMap;
import java.util.Map;

public class Mapper {
    private Map<String, VarFunc> mappings;

    public Mapper() { this(new HashMap<>()); }
    public Mapper(Map<String, VarFunc> mappings) {
        this.mappings = mappings;
    }

    public void setMappings(Map<String, VarFunc> mappings) { this.mappings = mappings; }
    protected void addMapping(String route, VarFunc func) { mappings.put(route, func); }
    public VarFunc get(String route) {
        return mappings.get(route);
    }

    @Override
    public String toString() {
        return "Mapper" + mappings.toString();
    }
}
