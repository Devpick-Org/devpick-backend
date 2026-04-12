package com.devpick.domain.community.controller;

import com.devpick.domain.community.dto.PostAttachmentDTO;
import com.devpick.global.common.response.ApiResponse;
import com.devpick.global.storage.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Community", description = "커뮤니티 게시글·첨부")
@RestController
@RequestMapping("/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final FileStorageService fileStorageService;

    @Operation(summary = "게시글 첨부 업로드", description = "이미지 또는 PDF를 S3에 업로드하고 URL을 반환합니다. 게시글 작성 시 attachmentUrls에 포함하세요.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PostAttachmentDTO> upload(
            @AuthenticationPrincipal UUID userId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(fileStorageService.uploadPostAttachment(userId, file));
    }
}
