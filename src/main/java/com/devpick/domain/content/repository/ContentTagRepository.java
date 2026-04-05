package com.devpick.domain.content.repository;

import com.devpick.domain.content.entity.ContentTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContentTagRepository extends JpaRepository<ContentTag, UUID> {
}
