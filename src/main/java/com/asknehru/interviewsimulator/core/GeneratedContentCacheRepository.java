package com.asknehru.interviewsimulator.core;

import com.asknehru.interviewsimulator.core.GeneratedContentCache;
import com.asknehru.interviewsimulator.syllabus.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GeneratedContentCacheRepository extends JpaRepository<GeneratedContentCache, Long> {
    Optional<GeneratedContentCache> findByTopicAndContentTypeAndKey(Topic topic, String contentType, String key);
}
