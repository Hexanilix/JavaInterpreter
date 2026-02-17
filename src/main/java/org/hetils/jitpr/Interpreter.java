package org.hetils.jitpr;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;


public class Interpreter {

    private static boolean isEmpty(char c) { return c == ' ' || c == '\t' || c == '\r' || c == '\n'; }
    private static boolean isEmpty(char @NotNull [] chars, int start, int end) {
        for (int i = start; i <= end; i++) {
            char c = chars[i];
            if (!isEmpty(c)) return false;
        }
        return true;
    }

    private static boolean isWrapped(char[] chars, int start, int end) {
        // find first opening bracket
        while (start <= end && isEmpty(chars[start])) start++;
        if (start >= end || chars[start] != '(') return false;

        // find last closing bracket
        while (end > start && isEmpty(chars[end])) end--;

        int i = start;
        int depth = 0;
        while (i <= end) {
            if (chars[i] == '(') depth++;
            else if (chars[i] == ')') {
                depth--;
                if (depth == 0) return i == end;
            }
            i++;
        }
        return false;
    }
    
    @Contract(pure = true)
    private static int functionCount(char @NotNull [] chars, int start, int end) {
        int param_count = 0;

        while (isWrapped(chars, start, end)) {
            while (start < end && chars[start] != '(') start++;
            start++;
            while (end >= start && chars[end] != ')') end--;
            end--;
        }

        int i = start;
        int next_start = start;
        boolean dq = true;
        boolean sq = true;
        while (i <= end) {
            char c = chars[i];

            if (c == '\"') dq = !dq;
            else if (c == '\'') sq = !sq;

            if (dq && sq) {
                if (c == '(') {
                    next_start = ++i;
                    int depth = 0;
                    while (i <= end) {
                        if (chars[i] == '(') depth++;
                        else if (chars[i] == ')') {
                            if (depth == 0) {
                                break;
                            }
                            depth--;
                        }
                        i++;
                    }

                    int res = functionCount(chars, next_start, i-1);
                    param_count += res;

                    do i++;
                    while (i <= end && chars[i] == ' ');

                    param_count++;
                }
                else i++;
            }
            else i++;
        }

        return param_count;
    }

    private static class IntArr {
        final int[] data;
        int i = 0;
        public IntArr(int size) {
            this.data = new int[size];
        }
        public void set(int indx, int num) { data[indx] = num; }
    }
    private static void countParamPerFunc(char[] chars, int start, int end, IntArr arr) {
        int param_count = 0;

        while (isWrapped(chars, start, end)) {
            while (start < end && chars[start] != '(') start++;
            start++;
            while (end >= start && chars[end] != ')') end--;
            end--;
        }

        int indx = arr.i++;

        int i = start;
        int next_start = start;
        boolean val = false;
        boolean dq = true;
        boolean sq = true;
        while (i <= end) {
            char c = chars[i];

            if (c == '\"') dq = !dq;
            else if (c == '\'') sq = !sq;

            if (dq && sq) {
                if (c == '(') {
                    next_start = ++i;
                    int depth = 0;
                    while (i <= end) {
                        if (chars[i] == '(') depth++;
                        else if (chars[i] == ')') {
                            if (depth == 0) {
                                break;
                            }
                            depth--;
                        }
                        i++;
                    }

                    countParamPerFunc(chars, next_start, i-1, arr);

                    do i++;
                    while (i <= end && chars[i] == ' ');

                    param_count++;
                }
                else if (isEmpty(c)) {
                    if (val) {
                        param_count++;
                        val = false;
                    }
                    i++;
                }
                else {
                    val = true;
                    i++;
                }
            }
            else i++;
        }

        if (val) param_count++;

        arr.set(indx, param_count == 1 ? 0 : param_count);
    }

    private final Map<String, VarFunc> commands;
    private final Map<String, Object> global_variables = new HashMap<>();
    public Interpreter() {
        this(new HashMap<>(), true);
    }
    public Interpreter(Map<String, VarFunc> commands) {
        this(commands, true);
    }
    public Interpreter(Map<String, VarFunc> commands, boolean add_basic_commands) {
        this.commands = commands;
        if (add_basic_commands) {
            addBasicCommands();
        }
    }

    private void addBasicCommands() {
        rootCommand("print", vars -> {
            StringBuilder sb = new StringBuilder();
            boolean com = false;
            for (int i = 0; i < vars.length(); i++) {
                if (com) sb.append(", ");
                sb.append(Objects.toString(vars.get(i)));
                com = true;
            }
            return sb.toString();
        }, false);
        rootCommand("time", vars -> {
            int len = vars.length();
            if (len == 0) return Date.from(Instant.now());
            else {
                String sel = vars.get(0);
                return switch (sel) {
                    case "ms" -> System.currentTimeMillis();
                    default -> "Invalid time selection \"" + sel + "\"";
                };
            }
        }, false);
    }

    @Contract(pure = true)
    private static int sum(int @NotNull [] arr, int count) {
        int s = 0;
        for (int i = 0; i < count; i++) {
            s += arr[i];
        }
        return s;
    }

    public class VariableSet {
        private final int[] cmd_var_counts;
        private final Object[] vars;
        private int global_cmd_index = 0;
        private int c_cmd = 0;
        private int local_offset = 0;
        private boolean error = false;
        private String msg;
        private VariableSet(char @NotNull [] command) {
            int end = command.length - 1;
            int fc = functionCount(command, 0, end) + 1;
            IntArr arr = new IntArr(fc);
            countParamPerFunc(command, 0, end, arr);
            cmd_var_counts = arr.data;
            this.vars = new Object[sum(cmd_var_counts, cmd_var_counts.length)];
        }

        private int next() {
            global_cmd_index++;
            return global_cmd_index-1;
        }

        private void set(int cmd_index, int offset, Object val) {
            vars[sum(cmd_var_counts, cmd_index) + offset] = val;
        }


        private VariableSet withLocalOffset(int cmd_index, int offset) {
            this.local_offset = sum(cmd_var_counts, cmd_index) + offset;
            this.c_cmd = cmd_index;
            return this;
        }

        private void currentCommand(int command) {
            this.local_offset = sum(cmd_var_counts, command);
            this.c_cmd = command;
        }

        private int locI(int index) { return index+local_offset; }

        public int length() {
            return Math.max(sum(cmd_var_counts, c_cmd) + cmd_var_counts[c_cmd] - local_offset, 0);
        }

        public boolean has(int index) {
            return index >= 0 && locI(index) < vars.length && index < cmd_var_counts[c_cmd];
        }

        // Getting locals
        public <T> T get(int index) {
            return (T) vars[locI(index)];
        }
        public <T> T get(int index, Class<T> cast) { return (T) vars[locI(index)]; }
        public <T> T getOr(int index, T def, Class<T> cast) {
            return has(index) ? (T) vars[locI(index)] : def;
        }
        public Object getS(int index) { return vars[locI(index)]; }
        public int getInt(int index) {
            if (vars[locI(index)] instanceof Integer i) {
                return i;
            } else return Integer.parseInt(Objects.toString(vars[locI(index)]));
        }
        public long getLong(int index) {
            if (vars[locI(index)] instanceof Long lng) {
                return lng;
            } else return Long.parseLong(Objects.toString(vars[locI(index)]));
        }
        public float getFloat(int index) {
            if (vars[locI(index)] instanceof Float itgr) {
                return itgr;
            } else return Float.parseFloat(Objects.toString(vars[locI(index)]));
        }
        public double getDouble(int index) {
            if (vars[locI(index)] instanceof Double i) {
                return i;
            } else return Double.parseDouble(Objects.toString(vars[locI(index)]));
        }

        public <T> T getOr(int index, T def) {
            return has(index) ? (T) vars[locI(index)] : def;
        }
        public int getIntOr(int index, int def) {
            if (!has(index)) {
                return def;
            }
            int i = locI(index);
            try {
                if (vars[i] instanceof Integer itgr) {
                    return itgr;
                } else return Integer.parseInt(Objects.toString(vars[i]));
            } catch (NumberFormatException e) {
                return def;
            }
        }
        public long getLongOr(int index, long def) {
            if (!has(index)) {
                return def;
            }
            int i = locI(index);
            try {
                if (vars[i] instanceof Long lng) {
                    return lng;
                } else return Long.parseLong(Objects.toString(vars[i]));
            } catch (NumberFormatException e) {
                return def;
            }
        }
        public float getFloatOr(int index, float def) {
            if (!has(index)) return def;
            int i = locI(index);
            try {
                if (vars[i] instanceof Float flt) {
                    return flt;
                } else return Float.parseFloat(Objects.toString(vars[i]));
            } catch (NumberFormatException e) {
                return def;
            }
        }
        public double getDoubleOr(int index, double def) {
            if (!has(index)) return def;
            int i = locI(index);
            try {
                if (vars[i] instanceof Double dble) {
                    return dble;
                } else return Double.parseDouble(Objects.toString(vars[i]));
            } catch (NumberFormatException e) {
                return def;
            }
        }

        // Global vars
        public <T> T get(String variable) { return (T) global_variables.get(variable); }
        public <T> T get(String variable, Class<T> cast) { return (T) global_variables.get(variable); }
        public Object getS(String variable) { return global_variables.get(variable); }
        public boolean has(String variable) { return global_variables.containsKey(variable); }

        public Object set(String var_name, Object value) { return global_variables.put(var_name, value); }

        // message
        public void setMsg(String msg) { this.msg = msg; }
        public String getMsg() { return msg; }
        public void err(String error_msg) {
            this.error = true;
            this.msg = error_msg;
        }
    }

    public Object getVar(String var_name) { return global_variables.get(var_name); }
    public Object setVar(String var_name, Object value) { return global_variables.put(var_name, value); }

    public void rootCommand(String cmd, Map<String, VarFunc> mappings) {
        rootCommand(cmd, mappings, true);
    }
    public void rootCommand(String cmd, Map<String, VarFunc> mappings, boolean override) {
        Mapper mper = new Mapper(mappings);
        if (override) commands.put(cmd, vars -> mper);
        else commands.putIfAbsent(cmd, vars -> mper);
    }

    public void rootCommand(String cmd, VarFunc func) {
        rootCommand(cmd, func, true);
    }
    public void rootCommand(String cmd, VarFunc func, boolean override) {
        if (override) commands.put(cmd, func);
        else commands.putIfAbsent(cmd, func);
    }

    // Execution
    public void assrt(@NotNull String input, @NotNull Function<Object, Boolean> eval) {
        char[] chars = input.toCharArray();
        VariableSet vs = new VariableSet(chars);
        Object r = handle(chars, 0, chars.length-1, vs);
        if (!eval.apply(r)) throw new RuntimeException("Bad behavior for \"" + input + "\"");
    }
    public String process(@NotNull String input) {
        char[] chars = input.toCharArray();
        VariableSet vs = new VariableSet(chars);
        Object res = handle(chars, 0, chars.length-1, vs);
        return vs.msg != null ? vs.msg : Objects.toString(res);
    }
    private @Nullable Object handle(char[] chars, int start, int end, VariableSet variables) {
        try {
            if (end == 0 || isEmpty(chars, start, end)) return null;

            while (isWrapped(chars, start, end)) {
                while (start < end && chars[start] != '(') start++;
                start++;
                while (end >= start && chars[end] != ')') end--;
                end--;
            }
            //trim ends
            while (start < end && isEmpty(chars[start])) start++;
            while (end > start && isEmpty(chars[end])) end--;

            int cmd_index = variables.next();

            int param_count = 0;

            int i = start;
            int next_start = start;
            boolean val = false;
            boolean dq = false;
            boolean sq = false;
            while (i <= end) {
                char c = chars[i];

                if (c == '\"') {
                    dq = !dq;
                    if (dq) next_start = i + 1;
                } else if (c == '\'') {
                    sq = !sq;
                    if (sq) next_start = i + 1;
                }

                if (!(dq || sq)) {
                    if (c == '(') {
                        next_start = ++i;
                        int depth = 0;
                        while (i <= end) {
                            if (chars[i] == '(') depth++;
                            else if (chars[i] == ')') {
                                if (depth == 0) {
                                    break;
                                }
                                depth--;
                            }
                            i++;
                        }

                        // inset command
                        Object obj = handle(chars, next_start, i - 1, variables);
                        if (variables.error) return null;
                        variables.msg = null;
                        variables.set(
                                cmd_index,
                                param_count++,
                                obj
                        );

                        do i++;
                        while (i <= end && chars[i] == ' ');

                        next_start = i;
                    } else if (c == ' ') {
                        if (val) {
                            variables.set(
                                    cmd_index,
                                    param_count++,
                                    new String(chars, next_start, i - next_start)
                            );
                            val = false;
                        }
                        i++;
                        next_start = i;
                    } else {
                        if (!val) next_start = i;
                        val = true;
                        i++;
                    }
                } else i++;
            }

            String cmd = null;
            if (val) cmd = new String(chars, next_start, i - next_start);

            if (val && param_count != 0) {
                variables.set(cmd_index, param_count++, cmd);
            }

            variables.currentCommand(cmd_index);
            for (int j = 0; j < variables.length(); j++) {
                if (variables.get(j) instanceof String varn && varn.startsWith("$")) {
                    if (j == 0 && variables.has(j + 1) && variables.get(j + 1) instanceof String posib_eq && "=".equals(posib_eq)) {
                        j++;
                        continue;
                    }
                    String key = varn.substring(1);
                    variables.set(cmd_index, j, global_variables.get(key));
                }
            }

            if (param_count > 0) {
                if (!variables.has(0)) return null;
                else if (variables.get(0) instanceof String str) {
                    cmd = str;
                }
            }

            if (cmd != null) {
                if (cmd.startsWith("$")) {
                    if (variables.has(1) && "=".equals(variables.get(1))) {
                        String key = cmd.substring(1);
                        if (variables.has(2)) {
                            Object obj = variables.get(2);
                            global_variables.put(key, obj);
                            variables.setMsg("Set " + cmd + " = " + obj);
                            return obj;
                        } else {
                            global_variables.remove(key);
                            variables.setMsg("Unset " + cmd);
                            return null;
                        }
                    }
                }
            }

            Mapper mapper;
            int len = variables.length();
            Object ctx = null;
            if (cmd != null) {
                ctx = commands.get(cmd);
            }

            if (param_count == 0 && ctx == null) return cmd;

            if (ctx == null) {
                ctx = variables.get(0);
            }

            variables.currentCommand(cmd_index);

            if (len <= 1 && ctx instanceof VarFunc vf) {
                ctx = vf.apply(variables.withLocalOffset(cmd_index, 1));
            }
            for (int j = 0; j < len; j++) {
                variables.currentCommand(cmd_index);

                mapper = null;
                if (ctx instanceof MapperProvider mp_prov) {
                    mapper = mp_prov.getCall();
                } else if (ctx instanceof Mapper mp) {
                    mapper = mp;
                }

                if (mapper != null && variables.has(j) && variables.get(j) instanceof String route) {
                    VarFunc f = mapper.get(route);
                    if (f != null) ctx = f;
                }

                if (ctx instanceof VarFunc vf) {
                    ctx = vf.apply(variables.withLocalOffset(cmd_index, j+1));
                }
            }

            return ctx;
        } catch (ClassCastException e) {
            variables.err(e.getMessage());
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
//            variables.err(e.getMessage());
//            return null;
        }
    }

}
