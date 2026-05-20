import { runPublicReadScenario } from "./lib/scenarios.js";

export const options = {
  stages: [
    { duration: "1m", target: 25 },
    { duration: "3m", target: 25 },
    { duration: "1m", target: 50 },
    { duration: "3m", target: 50 },
    { duration: "1m", target: 100 },
    { duration: "3m", target: 100 },
    { duration: "1m", target: 150 },
    { duration: "3m", target: 150 },
    { duration: "1m", target: 0 },
  ],
  thresholds: {
    // Stress test는 한계를 찾는 용도라 threshold는 중단 조건이 아니라 관찰 기준으로 둔다.
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<2000", "p(99)<5000"],
  },
};

export default function () {
  runPublicReadScenario();
}
