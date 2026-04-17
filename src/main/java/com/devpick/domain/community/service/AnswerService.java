package com.devpick.domain.community.service;

import com.devpick.domain.community.dto.AnswerCreateRequest;
import com.devpick.domain.community.dto.AnswerListResponse;
import com.devpick.domain.community.dto.AnswerResponse;
import com.devpick.domain.community.dto.AnswerUpdateRequest;
import com.devpick.domain.community.dto.AnswerWithCommentsResponse;
import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.Comment;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.repository.AnswerLikeRepository;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.CommentRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.service.PointService;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final HistoryRepository historyRepository;
    private final PointService pointService;
    private final CommentRepository commentRepository;
    private final AnswerLikeRepository answerLikeRepository;

    @Transactional(readOnly = true)
    public AnswerListResponse getAnswers(UUID postId) {
        postRepository.findById(postId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        List<Answer> answers = answerRepository.findByPost_IdOrderByCreatedAtAsc(postId);
        List<AnswerWithCommentsResponse> result = answers.stream()
                .map(answer -> {
                    List<Comment> comments = commentRepository.findByAnswer_IdOrderByCreatedAtAsc(answer.getId());
                    return AnswerWithCommentsResponse.of(answer, comments);
                })
                .toList();
        return new AnswerListResponse(result);
    }

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

        Answer savedAnswer = answerRepository.save(answer);
        historyRepository.save(History.builder()
                .user(user)
                .actionType("answer_written")
                .post(post)
                .answer(savedAnswer)
                .build());
        pointService.earn(user, PointAction.ANSWER_WRITE);
        return AnswerResponse.of(savedAnswer);
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

        // 포인트 환불: ANSWER_WRITE (항상), ANSWER_ADOPTED (채택된 경우)
        pointService.refundAnswerPoints(answer.getUser(), Boolean.TRUE.equals(answer.getIsAdopted()));

        // 댓글 삭제 전에 comment_created 등 answer·comment를 참조하는 history 제거 (DP-324)
        historyRepository.deleteByAnswerId(answerId);
        commentRepository.deleteByAnswerId(answerId);
        answerLikeRepository.deleteByAnswerId(answerId);
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
        pointService.earn(answer.getUser(), PointAction.ANSWER_ADOPTED);
        return AnswerResponse.of(answer);
    }
}
