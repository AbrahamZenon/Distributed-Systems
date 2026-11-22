package edu.ptit.ds04.client;

import java.util.Scanner;

public class InteractiveCLI {
    public static void main(String[] args) {
        String docId = "doc-1";
        String userName = "User1";
        String serverUri = "ws://127.0.0.1:8765";

        for (int i = 0; i < args.length; i++) {
            if ("--doc".equals(args[i]) && i + 1 < args.length) docId = args[++i];
            else if ("--user".equals(args[i]) && i + 1 < args.length) userName = args[++i];
            else if ("--uri".equals(args[i]) && i + 1 < args.length) serverUri = args[++i];
        }

        try {
            ClientEngine client = new ClientEngine(serverUri, docId, userName.toLowerCase(), userName);
            boolean ok = client.connectAndJoin(5);
            if (!ok) {
                System.err.println("Could not connect to server at " + serverUri);
                return;
            }

            System.out.println("=================================================");
            System.out.println(" Connected to [" + docId + "] as [" + userName + "]");
            System.out.println(" Commands: ins <pos> <text> | del <pos> <len> | view | exit");
            System.out.println("=================================================");

            Scanner scanner = new Scanner(System.in);
            while (client.isOpen()) {
                System.out.print("[" + userName + "]> ");
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+", 3);
                String cmd = parts[0].toLowerCase();

                if ("exit".equals(cmd)) {
                    break;
                } else if ("view".equals(cmd)) {
                    System.out.printf("Content (v=%d): '%s'%n", client.getVersion(), client.getContent());
                } else if ("ins".equals(cmd) && parts.length >= 3) {
                    int pos = Integer.parseInt(parts[1]);
                    String text = parts[2];
                    client.submitInsert(pos, text);
                } else if ("del".equals(cmd) && parts.length >= 3) {
                    int pos = Integer.parseInt(parts[1]);
                    int len = Integer.parseInt(parts[2]);
                    client.submitDelete(pos, len);
                } else {
                    System.out.println("Unknown command format.");
                }
            }

            client.closeBlocking();
        } catch (Exception e) {
            System.err.println("Client encountered error: " + e.getMessage());
        }
    }
}
