package com.devpick.domain.job.event;

import java.util.UUID;

public record MockInterviewFinalizeEvent(UUID sessionId, boolean early) {}
