package com.platform.crbtcommunitylibrary.repository;

import com.platform.crbtcommunitylibrary.entity.Mood;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoodRepository extends JpaRepository<Mood, Long> {
}
