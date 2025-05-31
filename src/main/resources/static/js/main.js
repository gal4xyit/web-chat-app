'use strict';

var chatPage = document.querySelector('#chat-page');
var messageForm = document.querySelector('#messageForm');
var messageInput = document.querySelector('#message');
var messageArea = document.querySelector('#messageArea');
var connectingElement = document.querySelector('.connecting');
var connectedUsersList = document.querySelector('#connectedUsersList');
var userCountElement = document.querySelector('#user-count');
var authControls = document.querySelector('#auth-controls');

var stompClient = null;
var username = null;
var idToken = null;
var isAuthenticated = false;

var colors = [
    '#2196F3', '#32c787', '#00BCD4', '#ff5652',
    '#ffc107', '#ff85af', '#FF9800', '#39bbb0'
];

async function initializeApp() {
    if (!chatPage || !connectingElement || !authControls) {
        console.error("Essential page elements for initialization are missing.");
        if (document.body) document.body.innerHTML = "<p style='color:red; text-align:center;'>Error: Chat UI components missing.</p>";
        return;
    }

    document.body.classList.remove('user-authenticated');
    if (chatPage) chatPage.style.display = 'none';
    if (connectingElement) connectingElement.classList.add('hidden');

    isAuthenticated = false;
    updateAuthUI_unauthenticated_display();

    try {
        const response = await fetch('/api/user');
        if (response.ok) {
            const user = await response.json();
            username = user.username;
            idToken = user.idToken;
            isAuthenticated = true;

            updateAuthUI();

            if (connectingElement) {
                connectingElement.classList.remove('hidden');
                connectingElement.textContent = 'Connecting...';
                connectingElement.style.color = '#777';
            }
            connectWebSocket();
        } else {
            isAuthenticated = false;
            updateAuthUI();
            if (response.status !== 401) {
                console.error("Error fetching user info, status:", response.status, await response.text());
                if (authControls) authControls.innerHTML = '<p style="color:red;">Error loading user data.</p>';
            }
        }
    } catch (error) {
        isAuthenticated = false;
        updateAuthUI();
    }
}

function updateAuthUI_unauthenticated_display() {
    if (!authControls) return;
    authControls.innerHTML = '';
    const loginButton = document.createElement('button');
    loginButton.textContent = 'Login with Keycloak';
    loginButton.className = 'button primary';
    loginButton.onclick = () => { window.location.href = '/oauth2/authorization/keycloak'; };
    authControls.appendChild(loginButton);
}

function updateAuthUI() {
    if (!authControls) return;

    if (isAuthenticated) {
        document.body.classList.add('user-authenticated');
        if(chatPage) chatPage.style.display = 'flex';

        authControls.innerHTML = '';
        const userInfoSpan = document.createElement('span');
        userInfoSpan.textContent = `Logged in as: ${username} `;
        authControls.appendChild(userInfoSpan);

        const logoutButton = document.createElement('button');
        logoutButton.textContent = 'Logout';
        logoutButton.className = 'button accent';
        logoutButton.onclick = () => { window.location.href = '/logout'; };
        authControls.appendChild(logoutButton);
    } else {
        document.body.classList.remove('user-authenticated');
        if(chatPage) chatPage.style.display = 'none';

        updateAuthUI_unauthenticated_display();
    }
}

function connectWebSocket() {
    if (username && isAuthenticated) {
        var socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.connect({}, onConnected, onError);
    } else {
        if (connectingElement) connectingElement.classList.add('hidden');
    }
}

function onConnected() {
    if (connectingElement) connectingElement.classList.add('hidden');
    if (!stompClient) return;

    stompClient.subscribe('/topic/public', onMessageReceived);

    loadMessageHistory().then(() => {
        stompClient.send("/app/chat.addUser", {}, JSON.stringify({sender: username, type: 'JOIN'}));
    }).catch(error => {
        console.error("Failed to load message history:", error);
        stompClient.send("/app/chat.addUser", {}, JSON.stringify({sender: username, type: 'JOIN'}));
    });
}

async function loadMessageHistory() {
    if (!messageArea) {
        console.error("Message area not found for history.");
        return;
    }

    try {
        const response = await fetch('/api/messages/history');
        if (response.ok) {
            const historyMessages = await response.json();
            if (historyMessages && historyMessages.length > 0) {
                historyMessages.forEach(message => {
                    displayMessage(message, true);
                });
                messageArea.scrollTop = messageArea.scrollHeight;
            }
        } else {
            console.error("Error fetching message history, status:", response.status);
            messageArea.innerHTML = '<li><p class="event-message">Could not load message history.</p></li>';
        }
    } catch (error) {
        console.error("Network error fetching message history:", error);
        if (messageArea) {
            messageArea.innerHTML = '<li><p class="event-message">Failed to load history due to network error.</p></li>';
        }
    }
}

function displayMessage(message, isHistory = false) {
    if (!messageArea) return;

    var messageElement = document.createElement('li');
    var appendMessageToArea = true;

    if (message.type === 'JOIN') {
        if (message.content && message.content.length > 0) {
            messageElement.classList.add('event-message');
            var p = document.createElement('p');
            p.textContent = message.content;
            messageElement.appendChild(p);
        } else {
            appendMessageToArea = false;
        }
    } else if (message.type === 'LEAVE') {
        if (message.content && message.content.length > 0) {
            messageElement.classList.add('event-message');
            var p = document.createElement('p');
            p.textContent = message.content;
            messageElement.appendChild(p);
        } else {
            appendMessageToArea = false;
        }
    } else if (message.type === 'CHAT') {
        messageElement.classList.add('chat-message');
        var avatarElement = document.createElement('i');
        var avatarText = document.createTextNode(message.sender ? message.sender[0] : '?');
        avatarElement.appendChild(avatarText);
        avatarElement.style['background-color'] = getAvatarColor(message.sender);
        messageElement.appendChild(avatarElement);

        var textContentWrapper = document.createElement('div');
        textContentWrapper.classList.add('chat-message-text-content');
        var usernameElement = document.createElement('span');
        var usernameText = document.createTextNode(message.sender);
        usernameElement.appendChild(usernameText);
        textContentWrapper.appendChild(usernameElement);

        var textElement = document.createElement('p');
        var messageText = document.createTextNode(message.content);
        textElement.appendChild(messageText);
        textContentWrapper.appendChild(textElement);
        messageElement.appendChild(textContentWrapper);
    } else {
        appendMessageToArea = false;
    }

    if (appendMessageToArea && messageElement.hasChildNodes()) {
        messageArea.appendChild(messageElement);
    }

    if (!isHistory && message.connectedUsers && (message.type === 'JOIN' || message.type === 'LEAVE')) {
        updateConnectedUsers(message.connectedUsers);
    }
}

function onMessageReceived(payload) {
    var message = JSON.parse(payload.body);
    displayMessage(message, false);

    if(messageArea) messageArea.scrollTop = messageArea.scrollHeight;
}


function onError(error) {
    if (connectingElement) {
        connectingElement.classList.remove('hidden');
        connectingElement.textContent = 'Could not connect to WebSocket. Please refresh.';
        if (typeof error === 'string' && error.includes('Whoops!')) {
            connectingElement.textContent += ' (Lost connection)';
        } else if (error && error.headers && error.headers.message) {
            connectingElement.textContent += ' Server: ' + error.headers.message;
        } else if (typeof error === 'string') {
            connectingElement.textContent += ' Details: ' + error;
        }
        connectingElement.style.color = 'red';
    } else {
        console.error("WS error display element missing. Error:", error);
    }
}

function sendMessage(event) {
    if (event) event.preventDefault();
    if (!messageInput || !stompClient || !stompClient.connected || !isAuthenticated) return;
    var messageContent = messageInput.value.trim();
    if (messageContent) {
        var chatMessage = { sender: username, content: messageInput.value, type: 'CHAT' };
        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
        messageInput.value = '';
    }
}

function updateConnectedUsers(users) {
    if (!connectedUsersList || !userCountElement) return;
    connectedUsersList.innerHTML = '';
    if (users && users.length > 0) {
        userCountElement.textContent = users.length;
        users.sort((a, b) => (a === username ? -1 : b === username ? 1 : a.localeCompare(b)));
        users.forEach(user => {
            var userElement = document.createElement('li');
            userElement.innerHTML = (user === username) ? `<strong>${user} (You)</strong>` : user;
            connectedUsersList.appendChild(userElement);
        });
    } else {
        userCountElement.textContent = 0;
        var noUsersElement = document.createElement('li');
        noUsersElement.textContent = "No users currently online.";
        noUsersElement.style.fontStyle = "italic";
        noUsersElement.style.color = "#888";
        connectedUsersList.appendChild(noUsersElement);
    }
}

function getAvatarColor(messageSender) {
    if (!messageSender || messageSender.length === 0) return '#CCCCCC';
    var hash = 0;
    for (var i = 0; i < messageSender.length; i++) {
        hash = 31 * hash + messageSender.charCodeAt(i);
    }
    return colors[Math.abs(hash % colors.length)];
}

document.addEventListener('DOMContentLoaded', () => {
    if (!document.querySelector('#chat-page') || !document.querySelector('.connecting') ||
        !document.querySelector('#auth-controls')) {
        console.error("Critical UI elements for initialization missing.");
        return;
    }
    initializeApp();
    if (messageForm) {
        messageForm.addEventListener('submit', sendMessage, true);
    } else {
        console.error("ERROR: messageForm element NOT FOUND.");
    }
});