package com.gal4xyit.chat.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop100ByOrderByTimestampDesc();
    List<ChatMessage> findByTypeOrderByTimestampAsc(MessageType type);
    // List<ChatMessage> findAllByOrderByTimestampDesc(Pageable pageable);
}