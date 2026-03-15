package com.devpick.domain.community.service;

import com.devpick.domain.community.dto.CommentCreateRequest;
import com.devpick.domain.community.dto.CommentResponse;
import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.Comment;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.CommentRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final AnswerRepository answerRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public CommentResponse createComment(UUID userId, UUID postId, UUID answerId,
            CommentCreateRequest request) {
        // 게시글 존재 검증
        if (!postRepository.existsById(postId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }

        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND));

        // 답변이 해당 게시글에 속하는지 검증
        if (!answer.getPost().getId().equals(postId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND);
        }

        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        Comment comment = Comment.builder()
                .answer(answer)
                .user(user)
                .content(request.content())
                .build();

        return CommentResponse.of(commentRepository.save(comment));
    }

    @Transactional
    public void deleteComment(UUID userId, UUID postId, UUID answerId, UUID commentId) {
        // 게시글 존재 검증
        if (!postRepository.existsById(postId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }

        // 답변 존재 및 게시글 귀속 검증
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND));

        if (!answer.getPost().getId().equals(postId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND);
        }

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));

        // 댓글이 해당 답변에 속하는지 검증
        if (!comment.getAnswer().getId().equals(answerId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
        }

        // 작성자만 삭제 가능
        if (!comment.getUser().getId().equals(userId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_UNAUTHORIZED_COMMENT_ACTION);
        }

        commentRepository.delete(comment);
    }
}
