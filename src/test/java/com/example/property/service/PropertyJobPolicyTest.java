package com.example.property.service;

import com.example.property.domain.JobDecision;
import com.example.property.domain.PropertyJob;

public final class PropertyJobPolicyTest {
    public static void main(String[] args) {
        PropertyJobPolicy policy = new PropertyJobPolicy();

        JobDecision urgent = policy.decide(new PropertyJob(
                "maintenance_request", "BLDG-204", "REQ-9182", true));
        assertEquals("dispatch_on_call", urgent.action());
        assertEquals("P1", urgent.serviceLevel());
        if (!urgent.requiresAuditRecord()) throw new AssertionError("Urgent dispatch must be auditable");

        JobDecision document = policy.decide(new PropertyJob(
                "tenant_document", "BLDG-204", "DOC-771", false));
        assertEquals("route_compliance_review", document.action());
        if (!document.requiresAuditRecord()) throw new AssertionError("Document review must be auditable");

        JobDecision reminder = policy.decide(new PropertyJob(
                "inspection_reminder", "BLDG-204", "INS-44", false));
        assertEquals("send_inspection_notice", reminder.action());
        if (reminder.requiresAuditRecord()) throw new AssertionError("Routine notice needs no audit record");

        System.out.println("PropertyJobPolicyTest passed");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected=" + expected + " actual=" + actual);
    }
}
