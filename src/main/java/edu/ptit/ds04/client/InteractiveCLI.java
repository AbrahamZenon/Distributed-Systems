package edu.ptit.ds04.client;

import org.json.JSONObject;

import java.util.List;
import java.util.Scanner;

/**
 * Interactive Command Line Interface for real-time document collaboration.
 * Enhanced by: Nguyen Duy Phuc (B23DCVT341)
 */
public class InteractiveCLI {
    public static void main(String[] args) {
        String docId = "doc-1";
        String userName = "Phuc";
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

            System.out.println("=====================================================================");
            System.out.println(" DS04 COLLABORATIVE DOCUMENT EDITOR (CLI CLIENT)");
            System.out.println(" Connected to [" + docId + "] as [" + userName + "]");
            System.out.println(" Jupiter State Machine & Offline Sync Enabled");
            System.out.println(" Commands: ");
            System.out.println("   ins <pos> <text>  - Insert text at index");
            System.out.println("   del <pos> <len>   - Delete characters from index");
            System.out.println("   view              - Print current document text & version");
            System.out.println("   status            - Inspect Jupiter State Machine & offline queue");
            System.out.println("   users             - List active collaborators");
            System.out.println("   cursor <pos>      - Broadcast cursor position");
            System.out.println("   offline           - Simulate network disconnect");
            System.out.println("   online            - Restore network & catch-up sync");
            System.out.println("   help              - Show this menu");
            System.out.println("   exit              - Quit application");
            System.out.println("=====================================================================");

            final String currentUserName = userName;
            client.setOnChangeCallback((content, version) -> {
                System.out.printf("%n[LIVE UPDATE] (v=%d): \"%s\"%n[%s]> ", version, content, currentUserName);
            });

            Scanner scanner = new Scanner(System.in);
            while (client.isOpen() || client.getOfflineManager().isOffline()) {
                System.out.print("[" + userName + "][" + client.getJupiterState() + "]> ");
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+", 3);
                String cmd = parts[0].toLowerCase();

                if ("exit".equals(cmd)) {
                    break;
                } else if ("view".equals(cmd)) {
                    System.out.printf("Content (v=%d, State=%s): '%s'%n",
                        client.getVersion(), client.getJupiterState(), client.getContent());
                } else if ("status".equals(cmd)) {
                    System.out.println("--- JUPITER CLIENT STATUS ---");
                    System.out.println("Document ID   : " + client.getJupiter().getDocId());
                    System.out.println("Client ID     : " + client.getJupiter().getClientId());
                    System.out.println("Revision      : v=" + client.getVersion());
                    System.out.println("Jupiter State : " + client.getJupiterState());
                    System.out.println("Pending Op    : " + (client.getJupiter().getPendingOp() != null ? client.getJupiter().getPendingOp().toJSONObject() : "None"));
                    System.out.println("Buffered Ops  : " + client.getJupiter().getBufferOps().size());
                    System.out.println("Network Mode  : " + (client.getOfflineManager().isOffline() ? "OFFLINE" : "ONLINE"));
                    System.out.println("Offline Queue : " + client.getOfflineManager().getOfflineCount() + " operations");
                    System.out.println("-----------------------------");
                } else if ("users".equals(cmd)) {
                    List<JSONObject> users = client.getActiveUsers();
                    System.out.println("--- ACTIVE COLLABORATORS (" + users.size() + ") ---");
                    for (JSONObject u : users) {
                        System.out.printf(" - %s (%s) [Role: %s, Color: %s, Cursor: %d]%n",
                            u.optString("user_name"), u.optString("client_id"),
                            u.optString("role"), u.optString("color"), u.optInt("cursor_position", 0));
                    }
                    System.out.println("-------------------------------------");
                } else if ("offline".equals(cmd)) {
                    client.setSimulatedOffline(true);
                    System.out.println("[OFFLINE MODE ENABLED] Edits will now be held in Offline Queue.");
                } else if ("online".equals(cmd)) {
                    client.setSimulatedOffline(false);
                    System.out.println("[ONLINE MODE RESTORED] Synchronizing missed changes and flushing queue...");
                } else if ("cursor".equals(cmd) && parts.length >= 2) {
                    int pos = Integer.parseInt(parts[1]);
                    client.sendCursor(pos);
                    System.out.println("Cursor broadcasted at index " + pos);
                } else if ("ins".equals(cmd) && parts.length >= 3) {
                    int pos = Integer.parseInt(parts[1]);
                    String text = parts[2];
                    client.submitInsert(pos, text);
                } else if ("del".equals(cmd) && parts.length >= 3) {
                    int pos = Integer.parseInt(parts[1]);
                    int len = Integer.parseInt(parts[2]);
                    client.submitDelete(pos, len);
                } else if ("help".equals(cmd)) {
                    System.out.println("Commands: ins <pos> <text> | del <pos> <len> | view | status | users | cursor <pos> | offline | online | exit");
                } else {
                    System.out.println("Unknown command format. Type 'help' for usage.");
                }
            }

            if (client.isOpen()) {
                client.closeBlocking();
            }
        } catch (Exception e) {
            System.err.println("Client encountered error: " + e.getMessage());
        }
    }
}
