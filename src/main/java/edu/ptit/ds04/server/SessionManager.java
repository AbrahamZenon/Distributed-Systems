package edu.ptit.ds04.server;

import org.json.JSONArray;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> docSubscribers = new ConcurrentHashMap<>();

    public void register(ClientSession session) {
        sessions.put(session.getClientId(), session);
        docSubscribers.computeIfAbsent(session.getDocId(), k -> Collections.synchronizedSet(new HashSet<>()))
                      .add(session.getClientId());
    }

    public ClientSession unregister(String clientId) {
        ClientSession session = sessions.remove(clientId);
        if (session != null) {
            Set<String> set = docSubscribers.get(session.getDocId());
            if (set != null) {
                set.remove(clientId);
                if (set.isEmpty()) {
                    docSubscribers.remove(session.getDocId());
                }
            }
        }
        return session;
    }

    public ClientSession getSession(String clientId) {
        return sessions.get(clientId);
    }

    public List<ClientSession> getSubscribers(String docId) {
        Set<String> clientIds = docSubscribers.get(docId);
        List<ClientSession> list = new ArrayList<>();
        if (clientIds != null) {
            synchronized (clientIds) {
                for (String cid : clientIds) {
                    ClientSession s = sessions.get(cid);
                    if (s != null) {
                        list.add(s);
                    }
                }
            }
        }
        return list;
    }

    public JSONArray getPresenceList(String docId) {
        JSONArray arr = new JSONArray();
        for (ClientSession s : getSubscribers(docId)) {
            arr.put(s.toPresenceJSONObject());
        }
        return arr;
    }

    public List<ClientSession> checkTimeouts(long timeoutMs) {
        long now = System.currentTimeMillis();
        List<ClientSession> timedOut = new ArrayList<>();
        for (ClientSession s : sessions.values()) {
            if (now - s.getLastHeartbeat() > timeoutMs) {
                timedOut.add(s);
            }
        }
        return timedOut;
    }
}
