package com.devpick.domain.report.document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "weekly_report_insights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ReportInsightDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("report_id")
    private String reportId;

    @Field("user_id")
    private String userId;

    @Field("well_done")
    private String wellDone;

    @Field("lacking")
    private String lacking;

    @Field("next_week")
    private String nextWeek;

    @Field("generated_at")
    private LocalDateTime generatedAt;
}
