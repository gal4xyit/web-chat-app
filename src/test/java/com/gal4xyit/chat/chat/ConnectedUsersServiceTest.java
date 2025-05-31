package com.gal4xyit.chat.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.Set;

class ConnectedUsersServiceTest {

    private ConnectedUsersService connectedUsersService;

    @BeforeEach
    void setUp() {
        connectedUsersService = new ConnectedUsersService();
    }

    @Test
    void whenUserSessionAdded_UserShouldBeOnline() {
        String username = "testuser";
        String sessionId = "session1";

        connectedUsersService.addUserSession(username, sessionId);
        boolean isOnline = connectedUsersService.isUserOnline(username);
        Set<String> users = connectedUsersService.getConnectedUsers();

        assertThat(isOnline).isTrue();
        assertThat(users).isNotNull().hasSize(1).containsExactly("testuser");
    }

    @Test
    void whenMultipleSessionsForSameUserAdded_UserCountShouldBeOne() {
        String username = "testuser";
        String session1 = "session1";
        String session2 = "session2";

        connectedUsersService.addUserSession(username, session1);
        connectedUsersService.addUserSession(username, session2);
        Set<String> users = connectedUsersService.getConnectedUsers();

        assertThat(users).isNotNull().hasSize(1).containsExactly("testuser");
        assertThat(connectedUsersService.isUserOnline(username)).isTrue();
    }

    @Test
    void addUserSession_shouldReturnTrueForFirstSession() {
        String username = "user1";
        String sessionId = "sessionA";

        boolean isFirstSession = connectedUsersService.addUserSession(username, sessionId);

        assertThat(isFirstSession).isTrue();
    }

    @Test
    void addUserSession_shouldReturnFalseForSubsequentSessionOfSameUser() {
        String username = "user1";
        String sessionA = "sessionA";
        String sessionB = "sessionB";

        connectedUsersService.addUserSession(username, sessionA);
        boolean isFirstForSessionB = connectedUsersService.addUserSession(username, sessionB);

        assertThat(isFirstForSessionB).isFalse();
    }


    @Test
    void whenUserSessionRemoved_AndItWasLastSession_UserShouldBeOffline() {
        String username = "testuser";
        String sessionId = "session1";
        connectedUsersService.addUserSession(username, sessionId);
        assertThat(connectedUsersService.isUserOnline(username)).isTrue();

        boolean wasLastSession = connectedUsersService.removeUserSession(username, sessionId);
        boolean isOnline = connectedUsersService.isUserOnline(username);
        Set<String> users = connectedUsersService.getConnectedUsers();

        assertThat(wasLastSession).isTrue();
        assertThat(isOnline).isFalse();
        assertThat(users).isNotNull().isEmpty();
    }

    @Test
    void whenUserSessionRemoved_ButOtherSessionsExist_UserShouldStillBeOnline() {
        String username = "testuser";
        String session1 = "session1";
        String session2 = "session2";
        connectedUsersService.addUserSession(username, session1);
        connectedUsersService.addUserSession(username, session2);
        assertThat(connectedUsersService.isUserOnline(username)).isTrue();

        boolean wasLastSession = connectedUsersService.removeUserSession(username, session1);
        boolean isOnline = connectedUsersService.isUserOnline(username);
        Set<String> users = connectedUsersService.getConnectedUsers();

        assertThat(wasLastSession).isFalse();
        assertThat(isOnline).isTrue();
        assertThat(users).isNotNull().hasSize(1).containsExactly("testuser");
    }

    @Test
    void removeUserSession_shouldReturnTrueWhenUserBecomesFullyOffline() {
        String username = "user1";
        String sessionId = "sessionA";
        connectedUsersService.addUserSession(username, sessionId);

        boolean isFullyOffline = connectedUsersService.removeUserSession(username, sessionId);

        assertThat(isFullyOffline).isTrue();
        assertThat(connectedUsersService.isUserOnline(username)).isFalse();
    }

    @Test
    void removeUserSession_shouldReturnFalseWhenUserStillHasOtherSessions() {
        String username = "user1";
        String sessionA = "sessionA";
        String sessionB = "sessionB";
        connectedUsersService.addUserSession(username, sessionA);
        connectedUsersService.addUserSession(username, sessionB);

        boolean isFullyOffline = connectedUsersService.removeUserSession(username, sessionA);

        assertThat(isFullyOffline).isFalse();
        assertThat(connectedUsersService.isUserOnline(username)).isTrue();
    }


    @Test
    void getConnectedUsers_shouldReturnCorrectUsernames() {
        connectedUsersService.addUserSession("user1", "s1");
        connectedUsersService.addUserSession("user2", "s2");
        connectedUsersService.addUserSession("user1", "s3");

        Set<String> users = connectedUsersService.getConnectedUsers();

        assertThat(users).isNotNull().hasSize(2).containsExactlyInAnyOrder("user1", "user2");
    }

    @Test
    void isUserOnline_shouldReturnFalseForNonExistentUser() {
        connectedUsersService.addUserSession("user1", "s1");

        assertThat(connectedUsersService.isUserOnline("nonexistentuser")).isFalse();
    }

    @Test
    void addUserSession_withNullUsernameOrSessionId_shouldNotAddAndReturnFalse() {
        boolean addedNullUser = connectedUsersService.addUserSession(null, "s1");
        boolean addedNullSession = connectedUsersService.addUserSession("user1", null);

        Set<String> users = connectedUsersService.getConnectedUsers();

        assertThat(addedNullUser).isFalse();
        assertThat(addedNullSession).isFalse();
        assertThat(users).isEmpty();
    }

    @Test
    void removeUserSession_withNullUsernameOrSessionId_orNonExistentUser_shouldDoNothingAndReturnFalse() {
        connectedUsersService.addUserSession("user1", "s1");

        boolean removedNullUser = connectedUsersService.removeUserSession(null, "s1");
        boolean removedNullSession = connectedUsersService.removeUserSession("user1", null);
        boolean removedNonExistentUser = connectedUsersService.removeUserSession("user2", "s2");
        boolean removedNonExistentSession = connectedUsersService.removeUserSession("user1", "sNonExistent");


        assertThat(removedNullUser).isFalse();
        assertThat(removedNullSession).isFalse();
        assertThat(removedNonExistentUser).isFalse();
        assertThat(removedNonExistentSession).isFalse();
        assertThat(connectedUsersService.getConnectedUsers()).hasSize(1).contains("user1");
    }
}