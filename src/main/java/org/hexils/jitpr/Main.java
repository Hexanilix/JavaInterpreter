package org.hexils.jitpr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

    public static class Team implements MapperProvider {
        public static HashSet<Team> teams = new HashSet<>();

        private static final Mapper ROOT_MAPPER;
        static {
            Mapper ADD_MAPPER = new Mapper(Map.of(
                    "member", vars -> {
                        System.out.println("Addedm maembre");
                        return vars.at(-2, Team.class).members.add(vars.at(1));
                    }
            ));
            Mapper REM_MAPPER = new Mapper(Map.of(
                    "member", vars ->
                            vars.at(-2, Team.class).members.remove(vars.at(1, User.class))
            ));

            HashMap<String, VarFunc> mappings = new HashMap<>();
            mappings.put("add", (vars -> ADD_MAPPER));
            mappings.put("remove", (vars -> REM_MAPPER));
            ROOT_MAPPER = new Mapper(mappings);
        }

        UUID id = UUID.randomUUID();
        String name;
        HashSet<User> members = new HashSet<>();
        public Team(@NotNull String s) {
            this.name = s;
            teams.add(this);
        }

        public UUID getId() {
            return id;
        }

        @Override
        public String toString() {
            return "Team{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    '}';
        }

        @Override
        public Mapper getCall() {
            return ROOT_MAPPER;
        }
    }

    public static class User implements MapperProvider {
        public static HashSet<User> users = new HashSet<>();
        public static @Nullable User find(UUID id) {
            for (User u : users) {
                if (u.getId() == id) {
                    return u;
                }
            }
            return null;
        }

        UUID id = UUID.randomUUID();
        String username;
        public User(@NotNull String s) {
            this.username = s;
            users.add(this);
        }

        String getUsername() {
            return username;
        }

        public UUID getId() {
            return id;
        }

        @Override
        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", username='" + username + '\'' +
                    '}';
        }

        @Override
        public Mapper getCall() {
            return null;
        }
    }

    public static void main(String[] ignore) {

        Interpreter itpr = new Interpreter();
        itpr.rootCommand("create", Map.of(
                "team", vars -> {
                    Team team = new Team(vars.at(0));
                    vars.setMsg("Created team " + team.name);
                    return team;
                },
                "user", vars -> new User(vars.at(0))
        ));
        itpr.rootCommand("list", Map.of(
                "team", vars -> Team.teams,
                "user", vars -> User.users
        ));
        itpr.rootCommand("delete", Map.of(
                "team", vars -> Team.teams.remove(vars.at(0, Team.class)),
                "user", vars -> User.users.remove(vars.at(0, User.class))
        ));
        itpr.rootCommand("print", vars -> {
            StringBuilder sb = new StringBuilder();
            boolean com = false;
            for (int i = 0; i < vars.length(); i++) {
                if (com) sb.append(", ");
                sb.append(Objects.toString(vars.at(i)));
                com = true;
            }
            return sb.toString();
        });

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String in;
            System.out.print("=> ");
            while (!"exit".equalsIgnoreCase(in = reader.readLine())) {
                itpr.handle(in);
                System.out.println(itpr.getLastMsg());
                System.out.print("=> ");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}