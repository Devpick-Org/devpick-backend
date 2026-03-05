package com.devpick.domain.user.entity;

import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "tags", indexes = {
        @Index(name = "idx_tags_name", columnList = "name")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Tag extends BaseCreatedEntity {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(length = 50, nullable = false, unique = true)
    private String name;
}
