import http from "k6/http";
import { check, group } from "k6";

export const BASE_URL = (__ENV.BASE_URL || "http://app:8080").replace(/\/$/, "");

export function get(path, name, expectedStatus = 200) {
  const response = http.get(`${BASE_URL}${path}`, {
    tags: { name },
  });

  check(response, {
    [`${name}: status is ${expectedStatus}`]: (res) => res.status === expectedStatus,
  });

  return response;
}

export function runPublicReadScenario() {
  group("public read scenario", () => {
    get("/health", "get_health");
    get("/posts?page=0&size=6", "get_posts");
    get("/contents?page=0&size=6", "get_contents");
    get("/trends/keywords", "get_trend_keywords");
    get("/trends/ecosystem?limit=80&offset=0", "get_trend_ecosystem");
  });
}
