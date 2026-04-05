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

    private static final String POSTS_QUERY = """
            query Posts {
              posts {
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

    public static VelogGraphQlRequest recentPosts() {
        return new VelogGraphQlRequest("Posts", POSTS_QUERY, null);
    }

    public record Variables(int limit, int offset, String timeframe) {}
}
