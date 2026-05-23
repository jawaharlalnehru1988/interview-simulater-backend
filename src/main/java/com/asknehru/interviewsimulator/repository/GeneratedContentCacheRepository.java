package com.asknehru.interviewsimulator.repository;

import com.asknehru.interviewsimulator.model.GeneratedContentCache;
import com.asknehru.interviewsimulator.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GeneratedContentCacheRepository extends JpaRepository<GeneratedContentCache, Long> {
    Optional<GeneratedContentCache> findByTopicAndContentTypeAndKey(Topic topic, String contentType, String key);
}
