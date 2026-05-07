package com.devpick.domain.user.repository;

import com.devpick.domain.user.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByNameIn(List<String> names);

    List<Tag> findByNameIgnoreCaseIn(List<String> names);

    java.util.Optional<Tag> findByName(String name);

    java.util.Optional<Tag> findByNameIgnoreCase(String name);
}
