package com.capacitix.repository;

import com.capacitix.entity.ChatMessage;
import com.capacitix.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByEmployeeOrderByCreatedAtAsc(
            User employee
    );

    void deleteByEmployee(
            User employee
    );
}