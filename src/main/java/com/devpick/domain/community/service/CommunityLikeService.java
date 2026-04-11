package com.devpick.domain.community.service;

import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.AnswerLike;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.entity.PostLike;
import com.devpick.domain.community.repository.AnswerLikeRepository;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.PostLikeRepository;
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
public class CommunityLikeService {

    private final PostRepository postRepository;
    private final AnswerRepository answerRepository;
    private final PostLikeRepository postLikeRepository;
    private final AnswerLikeRepository answerLikeRepository;
    private final UserRepository userRepository;

    @Transactional
    public void addPostLike(UUID userId, UUID postId) {
        User user = requireUser(userId);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        if (postLikeRepository.existsByPost_IdAndUser_Id(postId, userId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_POST_ALREADY_LIKED);
        }
        postLikeRepository.save(PostLike.builder().post(post).user(user).build());
    }

    @Transactional
    public void removePostLike(UUID userId, UUID postId) {
        requireUser(userId);
        postRepository.findById(postId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        PostLike like = postLikeRepository.findByPost_IdAndUser_Id(postId, userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_POST_NOT_LIKED));
        postLikeRepository.delete(like);
    }

    @Transactional
    public void addAnswerLike(UUID userId, UUID postId, UUID answerId) {
        User user = requireUser(userId);
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND));
        if (!answer.getPost().getId().equals(postId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND);
        }
        if (answerLikeRepository.existsByAnswer_IdAndUser_Id(answerId, userId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_ANSWER_ALREADY_LIKED);
        }
        answerLikeRepository.save(AnswerLike.builder().answer(answer).user(user).build());
    }

    @Transactional
    public void removeAnswerLike(UUID userId, UUID postId, UUID answerId) {
        requireUser(userId);
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND));
        if (!answer.getPost().getId().equals(postId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND);
        }
        AnswerLike like = answerLikeRepository.findByAnswer_IdAndUser_Id(answerId, userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_LIKED));
        answerLikeRepository.delete(like);
    }

    private User requireUser(UUID userId) {
        return userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
    }
}
