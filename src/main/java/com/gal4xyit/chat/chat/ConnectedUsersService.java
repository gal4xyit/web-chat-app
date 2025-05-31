package com.gal4xyit.chat.chat;

import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConnectedUsersService {

    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    public boolean addUserSession(String username, String sessionId) {
        if (username == null || sessionId == null) {
            return false;
        }
        Set<String> sessions = userSessions.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet());
        boolean firstSessionOverall = sessions.isEmpty();
        sessions.add(sessionId);
        return firstSessionOverall;
    }

    public boolean removeUserSession(String username, String sessionId) {
        if (username == null || sessionId == null) {
            return false;
        }
        Set<String> sessions = userSessions.get(username);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                userSessions.remove(username);
                return true;
            }
        }
        return false;
    }

    public Set<String> getConnectedUsers() {
        return Collections.unmodifiableSet(new HashSet<>(userSessions.keySet()));
    }

    public boolean isUserOnline(String username) {
        return userSessions.containsKey(username) && !userSessions.get(username).isEmpty();
    }
}