package edu.whu.developer.reminder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    List<Reminder> findAllByOrderByIdDesc();
}
