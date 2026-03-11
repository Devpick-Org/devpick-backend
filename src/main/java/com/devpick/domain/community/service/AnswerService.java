package com.devpick.domain.community.service;

import com.devpick.domain.community.dto.AnswerCreateRequest;
import com.devpick.domain.community.dto.AnswerResponse;
import com.devpick.domain.community.dto.AnswerUpdateRequest;
import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.repository.AnswerRepository;
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
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public AnswerResponse createAnswer(UUID userId, UUID postId, AnswerCreateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        Answer answer = Answer.builder()
                .post(post)
                .user(user)
                .content(request.content())
                .build();

        return AnswerResponse.of(answerRepository.save(answer));
    }

    @Transactional
    public AnswerResponse updateAnswer(UUID userId, UUID postId, UUID answerId, AnswerUpdateRequest request) {
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND));

        if (!answer.getPost().getId().equals(postId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND);
        }
        if (!answer.getUser().getId().equals(userId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_UNAUTHORIZED_ANSWER_ACTION);
        }

        answer.update(request.content());
        return AnswerResponse.of(answer);
    }

    @Transactional
    public void deleteAnswer(UUID userId, UUID postId, UUID answerId) {
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND));

        if (!answer.getPost().getId().equals(postId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND);
        }
        if (!answer.getUser().getId().equals(userId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_UNAUTHORIZED_ANSWER_ACTION);
        }

        answerRepository.delete(answer);
    }

    @Transactional
    public AnswerResponse adoptAnswer(UUID userId, UUID postId, UUID answerId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        if (!answerRepository.findAdoptedByPostIdForUpdate(postId).isEmpty()) {
            throw new DevpickException(ErrorCode.COMMUNITY_ALREADY_ADOPTED);
        }

        if (!post.getUser().getId().equals(userId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_ONLY_POST_AUTHOR_CAN_ADOPT);
        }

        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND));

        if (!answer.getPost().getId().equals(postId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND);
        }

        answer.adopt();
        return AnswerResponse.of(answer);
    }
}
