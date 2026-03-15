package com.devpick.domain.content.collector.velog;

/**
 * Velog GraphQL API 요청 바디.
 * POST https://v2.velog.io/graphql
 *
 * <p>variables 에는 cursor(String), limit(int) 를 넣는다.
 * cursor 가 null 이면 첫 페이지부터 수집한다.
 */
public record VelogGraphQlRequest(
        String query,
        Variables variables
) {

    private static final String TRENDING_POSTS_QUERY = """
            query TrendingPosts($input: TrendingPostsInput!) {
              trendingPosts(input: $input) {
                id
                title
                short_description
                url_slug
                thumbnail
                released_at
                updated_at
                tags
                user {
                  username
                }
              }
            }
            """;

    /**
     * 트렌딩 게시물 수집용 요청 객체를 생성한다.
     *
     * @param offset 페이지 오프셋 (0부터 시작)
     * @param limit  한 번에 가져올 게시물 수
     */
    public static VelogGraphQlRequest trendingPosts(int offset, int limit) {
        return new VelogGraphQlRequest(
                TRENDING_POSTS_QUERY,
                new Variables(new TrendingPostsInput(offset, limit))
        );
    }

    public record Variables(TrendingPostsInput input) {}

    public record TrendingPostsInput(int offset, int limit) {}
}
