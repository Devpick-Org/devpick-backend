package com.devpick.domain.point.repository;

import com.devpick.domain.point.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BadgeRepository extends JpaRepository<Badge, String> {

    List<Badge> findAllByOrderBySortOrderAsc();
}