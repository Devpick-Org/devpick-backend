package com.devpick.domain.community.entity;

import com.devpick.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "post_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class PostAttachment extends BaseTimeEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 512)
    private String fileName;

    /** 미리보기용: image, file 등 */
    @Column(length = 32)
    private String type;
}
