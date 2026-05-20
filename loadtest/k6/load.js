import { runPublicReadScenario } from "./lib/scenarios.js";

export const options = {
  stages: [
    { duration: "1m", target: 10 },
    { duration: "5m", target: 10 },
    { duration: "1m", target: 30 },
    { duration: "10m", target: 30 },
    { duration: "1m", target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<1000", "p(99)<2000"],
    "http_req_duration{name:get_health}": ["p(95)<200"],
    "http_req_duration{name:get_posts}": ["p(95)<800"],
    "http_req_duration{name:get_contents}": ["p(95)<800"],
    "http_req_duration{name:get_trend_keywords}": ["p(95)<800"],
    "http_req_duration{name:get_trend_ecosystem}": ["p(95)<1500"],
  },
};

export default function () {
  runPublicReadScenario();
}
