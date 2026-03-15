package com.devpick.domain.content.collector.velog;

/**
 * Velog GraphQL API 요청 바디.
 * POST https://v3.velog.io/graphql
 *
 * <p>trendingPosts 쿼리는 limit, offset, timeframe 을 flat하게 넘긴다.
 * timeframe 은 반드시 명시해야 데이터가 반환된다 (day/week/month/year).
 */
public record VelogGraphQlRequest(
        String operationName,
        String query,
        Variables variables
) {

    private static final String TRENDING_POSTS_QUERY = """
            query TrendingPosts($limit: Int, $offset: Int, $timeframe: String) {
              trendingPosts(limit: $limit, offset: $offset, timeframe: $timeframe) {
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
                new Variables(limit, offset, timeframe)
        );
    }

    public record Variables(int limit, int offset, String timeframe) {}
}
