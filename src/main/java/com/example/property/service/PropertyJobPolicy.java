package com.example.property.service;

import com.example.property.domain.JobDecision;
import com.example.property.domain.PropertyJob;

public final class PropertyJobPolicy {
    public JobDecision decide(PropertyJob job) {
        return switch (job.type()) {
            case "maintenance_request" -> job.urgent()
                    ? new JobDecision("dispatch_on_call", "P1", true)
                    : new JobDecision("open_work_order", "P3", true);
            case "tenant_document" -> new JobDecision("route_compliance_review", "P2", true);
            case "inspection_reminder" -> new JobDecision("send_inspection_notice", "P3", false);
            default -> throw new IllegalArgumentException("Unknown property job type: " + job.type());
        };
    }
}
