package com.devpick.domain.content.collector.velog;

/**
 * Velog GraphQL API 요청 바디.
 * POST https://v3.velog.io/graphql
 *
 * <p>v3.velog.io는 trendingPosts 쿼리에 TrendingPostsInput 래퍼가 필요하다.
 * flat args 방식은 v3에서 빈 응답을 반환한다.
 * timeframe 은 반드시 명시해야 데이터가 반환된다 (day/week/month/year).
 */
public record VelogGraphQlRequest(
        String operationName,
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
                released_at
                tags
                user {
                  username
                }
              }
            }
            """;

    public static VelogGraphQlRequest trendingPosts(int offset, int limit, String timeframe) {
        return new VelogGraphQlRequest(
                "TrendingPosts",
                TRENDING_POSTS_QUERY,
                new Variables(new Variables.Input(limit, offset, timeframe))
        );
    }

    public record Variables(Input input) {
        public record Input(int limit, int offset, String timeframe) {}
    }
}