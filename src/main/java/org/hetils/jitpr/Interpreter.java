package org.hetils.jitpr;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;


public class Interpreter {

    public static final String VERSION = "0.7.21";

    private static boolean isEmpty(char c) { return c == ' ' || c == '\t' || c == '\r' || c == '\n'; }
    private static boolean isEmpty(char @NotNull [] chars, int start, int end) {
        for (int i = start; i <= end; i++) {
            char c = chars[i];
            if (!isEmpty(c)) return false;
        }
        return true;
    }
    
    @Contract(pure = true)
    private static int functionCount(char @NotNull [] chars, int start, int end) {
        int param_count = 0;

        //trim ends
        while (start < end && isEmpty(chars[start])) start++;
        while (end > start && isEmpty(chars[end])) end--;

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
    private static void countParamPerFunc(char[] chars, int start, int end, @NotNull IntArr arr) {
        int param_count = 0;

        //trim ends
        while (start < end && isEmpty(chars[start])) start++;
        while (end > start && isEmpty(chars[end])) end--;

        int indx = arr.i++;

        int i = start;
        int next_start = start;
        boolean val = false;
        boolean dq = true;
        boolean sq = true;
        while (i <= end) {
            char c = chars[i];

            if (c == '\\') {
                i += 2;
                val = true;
                continue;
            }

            if (c == '\"') {
                dq = !dq;
                val = true;
            }
            else if (c == '\'') {
                sq = !sq;
                val = true;
            }

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
                else if (isEmpty(c) || c == '\"' || c == '\'') {
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

        arr.set(indx, param_count);
    }

    @Contract(pure = true)
    private static int sum(int @NotNull [] arr, int count) {
        int s = 0;
        for (int i = 0; i < count; i++) {
            s += arr[i];
        }
        return s;
    }


    private boolean always_treat_singles_as_cmds = true;
    private boolean always_set_last_output = false;
    private String last_output_var_name = "~";
    private int unqe_margin = 10;

    private final Map<String, VarFunc> commands;
    private final Map<String, Object> global_variables = new HashMap<>();
    public Interpreter() { this(new HashMap<>(), true); }
    public Interpreter(Map<String, VarFunc> commands) { this(commands, true); }
    public Interpreter(Map<String, VarFunc> commands, boolean add_basic_commands) {
        this.commands = commands;
        if (add_basic_commands) addBasicCommands();
    }

    public void alwaysTreatSinglesAsCommands(boolean value) { this.always_treat_singles_as_cmds = value; }
    public void setLastOutputVarName(String name) { this.last_output_var_name = name; }
    public void alwaysSetLastOutput(boolean val) { this.always_set_last_output = val; }
    public void unresolvedQuoteErrorMargin(int margin) { this.unqe_margin = margin; }

    public class VariableSet {
        private final int[] cmd_var_counts;
        private final Object[] vars;
        private int global_cmd_index = 0;
        private int c_cmd = 0;
        private int local_offset = 0;
        private boolean error = false;
        private String msg;
        private int consumed = 0;
        private VariableSet(char @NotNull [] command) {
            this(command, 0, command.length - 1);
        }
        private VariableSet(char @NotNull [] command, int start, int end) {
            int fc = functionCount(command, start, end) + 1;
            IntArr arr = new IntArr(fc);
            countParamPerFunc(command, start, end, arr);
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
            this.consumed = 0;
        }

        private int functionLength(int cmd_index) {
            return cmd_var_counts[cmd_index];
        }

        private int hlocI(int index) { return index+local_offset; }
        private int locI(int index) {
            consumed = Math.max(index+1, consumed);
            return index+local_offset;
        }

        private @Nullable Object trySetLast(Object ctx) {
            if (c_cmd == 0 || always_set_last_output) global_variables.put(last_output_var_name, ctx);
            return ctx;
        }

        public int length() {
            return Math.max(sum(cmd_var_counts, c_cmd) + cmd_var_counts[c_cmd] - local_offset, 0);
        }

        public boolean has(int index) {
            return index >= 0
                    && hlocI(index) < vars.length
                    && index < cmd_var_counts[c_cmd];
        }
        public boolean has(int index, Class<?> type) {
            return index >= 0
                    && hlocI(index) < vars.length
                    && index < cmd_var_counts[c_cmd]
                    && type.isInstance(vars[hlocI(index)]);
        }
        public String require(int index, String error) {
            if (!has(index, String.class))
                throw new IllegalArgumentException(error);
            return (String) vars[locI(index)];
        }
        public <T> T require(int index, Class<T> type, String error) {
            if (!has(index, type))
                throw new IllegalArgumentException(error);
            return type.cast(vars[locI(index)]);
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

        public UUID getUUIDOr(int index, UUID def) {
            if (!has(index)) return def;
            int i = locI(index);
            try {
                if (vars[i] instanceof UUID uuid) {
                    return uuid;
                } else return UUID.fromString(Objects.toString(vars[i]));
            } catch (IllegalArgumentException e) {
                return def;
            }
        }

        // Global vars
        public <T> T get(String variable) { return (T) global_variables.get(variable); }
        public <T> T getOr(String variable, T def) { return (T) global_variables.getOrDefault(variable, def); }
        public <T> T get(String variable, Class<T> cast) { return (T) global_variables.get(variable); }
        public Object getS(String variable) { return global_variables.get(variable); }
        public Object getSOr(String variable, Object def) { return global_variables.getOrDefault(variable, def); }
        public boolean has(String variable) { return global_variables.containsKey(variable); }

        public Object set(String var_name, Object value) { return global_variables.put(var_name, value); }

        // message
        public Object msg(String msg) {
            this.msg = msg;
            return null;
        }
        public Object msg(String msg, Object return_obj) {
            this.msg = msg;
            return return_obj;
        }
        public String getMsg() { return msg; }
        public @Nullable Object err(String error_msg) {
            this.error = true;
            this.msg = error_msg;
            return null;
        }
        public Object err(String error_msg, Object return_object) {
            this.error = true;
            this.msg = error_msg;
            return return_object;
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
        rootCommand("type", vars -> {
            if (vars.length() == 0) return null;
            boolean fn = false;
            boolean t = false;
            boolean n = false;
            StringBuilder sb = new StringBuilder();
            boolean comma = false;
            for (int i = 0; i < vars.length(); i++) {
                Object obj = vars.get(i);
                if (obj instanceof String arg) {
                    switch (arg) {
                        case "-n" -> {
                            n = true;
                            continue;
                        }
                        case "-fn" -> {
                            fn = true;
                            continue;
                        }
                        case "-t" -> {
                            t = true;
                            continue;
                        }
                        case "-fq" -> {
                            t = true;
                            fn = true;
                            continue;
                        }
                    }
                }
                if (comma) sb.append(", ");
                String app = "";
                Class<?> c = obj.getClass();

                if (t) {
                    if (c.isInterface()) app += "interface";
                    else if (c.isRecord()) app += "record";
                    else if (c.isEnum()) app += "enum";
                    else if (c.isAnnotation()) app += "annotation";
                    else app += "class";
                }
                if (!t || fn || n) {
                    if (t) app += " ";
                    app += fn ? c.getName() : c.getSimpleName();
                }
                sb.append(app);
                comma = true;
            }
            return sb.toString();
        });
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
        rootCommand("javaitpr", vars -> VERSION);
    }

    public Map<String, VarFunc> getCommands() {
        return commands;
    }

    public Map<String, Object> getGlobalVariables() {
        return global_variables;
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
        Object r;
        try {
            r = handle(chars, 0, chars.length - 1, vs);
        } catch (Exception e) {
            throw new RuntimeException("Error during execution \"" + input + "\"", e);
        }
        if (!eval.apply(r)) throw new RuntimeException("Bad behavior for \"" + input + "\", got: " + (r != null ? r.getClass().getName() : "") + " " + r);
    }
    public @NotNull String process(@NotNull String input) {
        try {
            char[] chars = input.toCharArray();
            VariableSet vs = new VariableSet(chars);
            Object res = handle(chars, 0, chars.length - 1, vs);
            return vs.msg != null ? vs.msg : Objects.toString(res);
        } catch (Exception e) {
//            return e.getMessage();
            throw new RuntimeException(e);
        }
    }
    public @Nullable Object handle(@NotNull String input) {
        char[] chars = input.toCharArray();
        VariableSet vs = new VariableSet(chars);
        return handle(chars, 0, chars.length-1, vs);
    }
    public @Nullable Object handle(char[] chars, int start, int end) {
        return handle(chars, start, end, new VariableSet(chars, start, end));
    }
    private @Nullable Object handle(char[] chars, int start, int end, VariableSet variables) {
        if (end == 0 || isEmpty(chars, start, end)) return null;

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
        boolean force_add = false;
        int depth = 0;
        StringBuilder buff = new StringBuilder();
        while (i <= end) {
            if (force_add) {
                buff.append(chars[i++]);
                val = true;
                force_add = false;
                continue;
            }
            char c = chars[i];


            if (c == '\\') {
                i++;
                force_add = true;
                continue;
            }

            if (c == '\"') {
                dq = !dq;
                val = true;
                if (dq) next_start = i + 1;
            } else if (c == '\'') {
                sq = !sq;
                val = true;
                if (sq) next_start = i + 1;
            }

            if (dq || sq) {
                if (c != '\"' && c != '\'') buff.append(c);
                i++;
            }
            else {
                if (c == '(') {
                    depth++;
                    next_start = ++i;
                    while (i <= end) {
                        if (chars[i] == '(') depth++;
                        else if (chars[i] == ')') {
                            depth--;
                            if (depth == 0) {
                                break;
                            }
                        }
                        i++;
                    }
                    if (depth != 0) break;

                    // inset command
                    Object obj = handle(chars, next_start, i - 1, variables);
                    if (variables.error) return obj;
                    variables.msg = null;
                    variables.set(
                            cmd_index,
                            param_count++,
                            obj
                    );

                    do i++;
                    while (i <= end && chars[i] == ' ');

                    next_start = i;
                } else if (c == ' ' || c == '\"' || c == '\'') {
                    if (val) {
                        variables.set(
                                cmd_index,
                                param_count++,
//                                new String(chars, next_start, i - next_start) saves 3ns I guess
                                buff.toString()
                        );
                        buff.setLength(0);
                        val = false;
                    }
                    i++;
                    next_start = i;
                } else {
                    if (!val) next_start = i;
                    val = true;
                    buff.append(c);
                    i++;
                }
            }
        }

        // Erros
        if (dq || sq) return syntaxError(chars, variables, next_start, "Unresolved quote at ");
        else if (depth != 0) return syntaxError(chars, variables, next_start, "Unresolved parentheses at ");
        else if (force_add) return variables.err("Empty escape at end");

        String cmd = null;
        if (val) cmd =
//                    new String(chars, next_start, i - next_start); saves 3ns I guess
                    buff.toString();

        if (val && param_count != 0) {
            variables.set(cmd_index, param_count++, cmd);
            cmd = null;
        }

        // make sure variables know what level we're on
        variables.currentCommand(cmd_index);

        // assign first variable (the command), unless there are no other arguments
        if (param_count > 0) {
            if (!variables.has(0)) return null;
            else if (variables.get(0) instanceof String str) {
                cmd = str;
            }
        }

        // $ wildcard operations
        if (cmd != null && cmd.startsWith("$")) {
            // assignment
            if (variables.has(1) && "=".equals(variables.get(1))) {
                String key = cmd.substring(1);
                if (variables.has(2)) {
                    Object obj = variables.get(2);
                    global_variables.put(key, obj);
                    variables.msg("Set " + cmd + " = " + (obj instanceof String s ? "\"" + s + "\"" : obj));
                    return variables.trySetLast(obj);
                }
                // unassign if no other value is present
                else {
                    global_variables.remove(key);
                    variables.msg("Unset " + cmd);
                    return null;
                }
            }
            // value retrieval
            else if (param_count == 0) {
                String key = cmd.substring(1);
                Object v;
                if (global_variables.containsKey(key)) {
                    return variables.msg(cmd + " = " + (v = global_variables.get(key)), v);
                } else return variables.msg("unset");
            }
        }

        // substitute $... for global named variables
        for (int j = 0; j < variables.length(); j++) {
            if (variables.get(j) instanceof String varn && varn.startsWith("$")) {
                String key = varn.substring(1);
                variables.set(cmd_index, j, global_variables.get(key));
                if (j == 0) cmd = null;
            }
        }

        Object ctx = null;

        if (cmd != null) {
            ctx = commands.get(cmd);
            if (ctx == null && always_treat_singles_as_cmds) {
                return variables.err("Unknown command: " + cmd);
            }
        }

        if (ctx == null) {
            if (param_count == 0) return cmd;
            else ctx = variables.get(0);
        }

        variables.currentCommand(cmd_index);
        int len = variables.length();
        int k = 0;
        // check if
        if (len <= 1 && ctx instanceof VarFunc vf) {
            ctx = vf.apply(variables.withLocalOffset(cmd_index, 1));
            // assume vf is a root function and consumes no variables
            variables.consumed = 0;
            k++;
        }
        for (; k < len; k++) {
            // reset depth
            variables.currentCommand(cmd_index);

            // try to get a mapper for subcommands
            Mapper mapper = null;
            if (ctx instanceof MapperProvider mp_prov) {
                mapper = mp_prov.getMapper();
            } else if (ctx instanceof Mapper mp) {
                mapper = mp;
            }

            // try to route to a sub command
            if (mapper != null && k > 0 && variables.has(k) && variables.get(k) instanceof String route) {
                VarFunc f = mapper.get(route);
                if (f != null) ctx = f;
                else return variables.err("Unknown command \"" + route + "\" in \"" + variables.get(k-1) + "\"");
            }
            // reset "consumed" because the previous get added
            variables.consumed = 0;

            // if current context is a function (both sub commands and command executions), run it
            if (ctx instanceof VarFunc vf) {
                ctx = vf.apply(variables.withLocalOffset(cmd_index, k+1));
                // ensure
                k += variables.consumed;
                variables.consumed = 0;
            }
        }

        // last output var
        return variables.trySetLast(ctx);
    }

    private @Nullable Object syntaxError(char @NotNull [] chars, @NotNull VariableSet variables, int next_start, String msg) {
        int starti = Math.max(next_start - unqe_margin, 0);
        int st_len = next_start - starti;
        int endi = Math.min(chars.length, next_start + unqe_margin);
        int count = endi - starti;
        String base =  + next_start + ": ";
        return variables.err(base +
                new String(
                        chars,
                        starti,
                        count
                ) + msg + "\n" + " ".repeat(base.length() + st_len - 1) + "^");
    }
}
