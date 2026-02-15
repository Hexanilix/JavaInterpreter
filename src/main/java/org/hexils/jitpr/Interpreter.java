package org.hexils.jitpr;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public class Interpreter {

    public static boolean isEmpty(char c) { return c == ' ' || c == '\t' || c == '\r' || c == '\n'; }
    public static boolean isEmpty(char @NotNull [] chars, int start, int end) {
        for (int i = start; i <= end; i++) {
            char c = chars[i];
            if (!isEmpty(c)) return false;
        }
        return true;
    }

    public static boolean isWrapped(char[] chars, int start, int end) {
        // find first opening bracket
        while (start <= end && isEmpty(chars[start])) start++;
        if (chars[start] != '(') return false;

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
    public static int functionCount(char @NotNull [] chars, int start, int end) {
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

    public static class IntArr {
        final int[] data;
        int i = 0;
        public IntArr(int size) {
            this.data = new int[size];
        }
        public void set(int indx, int num) { data[indx] = num; }
    }
    public static void countParamPerFunc(char[] chars, int start, int end, IntArr arr) {
        int param_count = 0;

//        System.out.println("Counting params in " + new String(chars, start, end-start+1));

        while (isWrapped(chars, start, end)) {
            while (start < end && chars[start] != '(') start++;
            start++;
            while (end >= start && chars[end] != ')') end--;
            end--;
        }

        int indx = arr.i++;

//        System.out.println("Counting p params in " + new String(chars, start, end-start+1));

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
                    next_start = i;
                }
                else if (isEmpty(c)) {
                    if (val) {
                        param_count++;
                        val = false;
                    }
                    i++;
                    next_start = i;
                }
                else {
                    if (!val) next_start = i;
                    val = true;
                    i++;
                }
            }
            else i++;
        }

        if (next_start < i) {
            param_count++;
        }

//        System.out.println("Foud " + param_count + " params in " + new String(chars, start, end-start+1));

        arr.set(indx, param_count == 1 ? 0 : param_count);
    }

    private Map<String, Map<String, VarFunc>> mltplx_commands = new HashMap<>();
    private Map<String, VarFunc> singleton_commands = new HashMap<>();
    private String MSG_KEY = "msg";
    private final Map<String, Object> global_variables = new HashMap<>();
    public Interpreter() {}
    public Interpreter(Map<String, Map<String, VarFunc>> mltplx_commands) {
        this.mltplx_commands = mltplx_commands;
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
        private VariableSet(char @NotNull [] command) {
            int end = command.length - 1;
//            int len = paramCount(command, 0, end);
            int fc = functionCount(command, 0, end) + 1;
//            System.out.printf("Counted %d funcs\n", fc);
            IntArr arr = new IntArr(fc);
            countParamPerFunc(command, 0, end, arr);
            cmd_var_counts = arr.data;
//            System.out.println("Lens: " + Arrays.toString(arr.data));
            this.vars = new Object[sum(cmd_var_counts, cmd_var_counts.length)];
        }

        public int next() {
            global_cmd_index++;
            return global_cmd_index-1;
        }

        public void set(int cmd_index, int offset, Object val) {
//            System.out.printf("+ {%s} for %d at %d + %d: ",
//                    val,
//                    cmd_index,
//                    sum(cmd_var_counts, cmd_index),
//                    offset
//            );
            vars[sum(cmd_var_counts, cmd_index) + offset] = val;
//            System.out.println(Arrays.toString(vars));
        }

        public int length() {
            return sum(cmd_var_counts, c_cmd) + cmd_var_counts[c_cmd] - local_offset;
        }

        private int local_offset = 0;
        public VariableSet withLocalOffset(int cmd_index, int offset) {
            this.local_offset = sum(cmd_var_counts, cmd_index) + offset;
            this.c_cmd = cmd_index;
//            System.out.printf("Set vars to start at ")
            return this;
        }

        public void currentCommand(int command) {
            this.local_offset = sum(cmd_var_counts, command);
            this.c_cmd = command;
//            System.out.printf("# Current beggingin for %d is %d\n", curr_cmd, i);
        }

        private int getLocalI(int index) { return index+local_offset; }

        public boolean has(int index) {
            return index >= 0 && index+local_offset < vars.length && index < cmd_var_counts[c_cmd];
        }

        // Getting locals
        public <T> T at(int index) {
//            System.out.printf("> cmd: %d\t i: %d\tloc: %d\n", c_cmd, index, getLocalI(index));
            return (T) vars[getLocalI(index)];
        }
        public <T> T at(int index, Class<T> cast) { return (T) vars[getLocalI(index)]; }
        public Object ats(int index) { return vars[getLocalI(index)]; }

        public Object[] local() { return vars; }


        // Global vars
        public <T> T get(String variable) { return (T) global_variables.get(variable); }
        public <T> T get(String variable, Class<T> cast) { return (T) global_variables.get(variable); }
        public Object ats(String variable) { return global_variables.get(variable); }
        public boolean has(String variable) { return global_variables.containsKey(variable); }

        public Object set(String var_name, Object value) { return global_variables.put(var_name, value); }
        public Object setMsg(String value) {
            msg_set = true;
            return global_variables.put(MSG_KEY, value);
        }
        public String getMsg() { return (String) global_variables.get(MSG_KEY); }
    }

    public Object getVar(String var_name) { return global_variables.get(var_name); }
    public Object setVar(String var_name, Object value) { return global_variables.put(var_name, value); }

    public String getLastMsg() { return (String) global_variables.get(MSG_KEY); }

    public void rootCommand(String cmd, Map<String, VarFunc> mappings) {
        mltplx_commands.put(cmd, mappings);
    }

    public void rootCommand(String cmd, VarFunc func) {
        singleton_commands.put(cmd, func);
    }

    // Execution
    private Object returnWithMsg(Object val) {
        if (!msg_set) {
            global_variables.put(MSG_KEY, Objects.toString(val));
            msg_set = true;
        }
        return val;
    }
    private boolean msg_set = false;
    public Object handle(@NotNull String input) {
        char[] chars = input.toCharArray();
        return handle(chars, 0, chars.length-1, new VariableSet(chars));
    }
    public @Nullable Object handle(char[] chars, int start, int end, VariableSet variables) {
        if (end == 0 || isEmpty(chars, start, end)) return returnWithMsg(null);
        msg_set = false;

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

//        System.out.println("Handling: " + new String(chars, start, end-start+1) + " @" + cmd_index);

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
                if (dq) next_start = i+1;
            }
            else if (c == '\'') {
                sq = !sq;
                if (sq) next_start = i+1;
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
                    Object obj = handle(chars, next_start, i-1, variables);
                    variables.set(
                            cmd_index,
                            param_count++,
                            obj
                    );

                    do i++;
                    while (i <= end && chars[i] == ' ');

                    next_start = i;
                }
                else if (c == ' ') {
                    if (val) {
                        variables.set(
                                cmd_index,
                                param_count++,
                                new String(chars, next_start, i-next_start)
                        );
                        val = false;
                    }
                    i++;
                    next_start = i;
                }
                else {
                    if (!val) next_start = i;
                    val = true;
                    i++;
                }
            }
            else i++;
        }


        String cmd = null;
        if (val) cmd = new String(chars, next_start, i - next_start);

        if (val && param_count != 0) {
            variables.set(cmd_index, param_count++, cmd);
        }

        variables.currentCommand(cmd_index);
//        System.out.printf("cmi: %d\tCmd: %s\tpc %d\thas 0: %b\n", cmd_index, cmd, param_count, variables.has(0));
        if (param_count > 0) {
            if (!variables.has(0)) return returnWithMsg(null);
            else if (variables.at(0) instanceof String str) {
                cmd = str;
            }
        }

        if (cmd != null) {
//            System.out.println("Executing cmd " + cmd);

            VarFunc func = singleton_commands.get(cmd);
            if (func != null) {
                return returnWithMsg(func.apply(variables.withLocalOffset(cmd_index, 1)));
            }
            if (param_count > 1) {
                // try running a root command
                Map<String, VarFunc> maps;
                if ((maps = mltplx_commands.get(cmd)) != null) {
                    VarFunc b = maps.get((String) variables.at((1)));
                    if (b != null) {
                        return returnWithMsg(b.apply(variables.withLocalOffset(cmd_index, 2)));
                    }
                }
            }
        }

        if (param_count == 0) return returnWithMsg(cmd);

        Mapper mapper;
        int len = variables.length();
//        System.out.println("P len: " + len);
        Object ctx = variables.at(0);

        for (int j = 1; j < len; j++) {
            variables.currentCommand(cmd_index);
            Object v = variables.at(j);

            mapper = null;
            if (ctx instanceof MapperProvider mp_prov) {
                mapper = mp_prov.getCall();
            } else if (ctx instanceof Mapper mp) {
                mapper = mp;
            }

//            System.out.printf("Mapper: %s, v: %s\n", mapper, v);

            if (mapper != null && v instanceof String route) {
                VarFunc f = mapper.get(route);
//                System.out.printf("Found %s for %s\n", f, route);
                ctx = Objects.requireNonNullElse(f, v);
            }

            if (ctx instanceof VarFunc vf) {
                ctx = vf.apply(variables.withLocalOffset(cmd_index, j));
//                System.out.println("Applied. Got " + ctx);
            }
        }

        return ctx;
    }

}
