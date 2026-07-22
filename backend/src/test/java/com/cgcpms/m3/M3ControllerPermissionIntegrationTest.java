package com.cgcpms.m3;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class M3ControllerPermissionIntegrationTest {
    private static final String VALID_BODY = """
            {"projectId":1,"planCode":"M3-P","planName":"M3","inspectionType":"QUALITY","frequencyType":"SINGLE",
             "startDate":"2026-07-22","endDate":"2099-12-31","ownerUserId":1,"planId":1,"inspectionCode":"M3-I",
             "inspectionDate":"2026-07-22","location":"A","inspectorUserId":1,"summary":"M3","inspectionId":1,
             "category":"M3","severity":"HIGH","title":"M3","description":"M3","responsibleKind":"INTERNAL",
             "responsibleUserId":1,"dueDate":"2099-12-31","issueId":1,"actionDescription":"M3",
             "plannedCompleteDate":"2099-12-31","result":"PASS","comment":"M3","partnerId":1,"contractId":1,
             "consequenceCode":"M3-C","decisionType":"NONE","fineAmount":0,"reworkCostAmount":0,
             "evaluationScore":80,"evaluationComment":"M3","schemeCode":"M3-S","schemeName":"M3",
             "schemeType":"SPECIAL","plannedEffectiveDate":"2099-12-31","drawingCode":"M3-D","drawingName":"M3",
             "specialty":"M3","sourceOrganization":"M3","versionNo":"A","receivedAt":"2026-07-22T00:00:00",
             "changeSummary":"M3","previousVersionId":1,"sourceRfiId":1,"reviewCode":"M3-R","reviewDate":"2026-07-22",
             "chairUserId":1,"participantSummary":"M3","conclusion":"PASS","reviewSummary":"M3","requiresRfi":false,
             "rfiCode":"M3-RFI","subject":"M3","question":"M3","priority":"HIGH","responseDueDate":"2099-12-31",
             "responseContent":"M3","changeRequired":false,"responderName":"M3","decision":"ACCEPTED","reviewComment":"M3",
             "drawingVersionId":1,"schemeId":1,"disclosureCode":"M3-TD","disclosureTitle":"M3",
             "disclosureDate":"2026-07-22","presenterUserId":1,"recipientSummary":"M3","disclosureContent":"M3",
             "disclosureId":1,"dailyLogId":1,"wbsTaskId":1,"referenceDate":"2026-07-22","workArea":"A",
             "referenceDescription":"M3","constructionReferenceId":1,"qualityInspectionId":1,"archiveCode":"M3-A",
             "acceptanceDate":"2026-07-22","acceptanceConclusion":"PASS","archiveLocation":"A",
             "closeoutCode":"M3-CO","plannedCompletionDate":"2099-12-31","acceptanceCode":"M3-AC",
             "acceptanceName":"M3","organizer":"M3","acceptanceSummary":"M3","ownerSettlementId":1,
             "receivableId":1,"warrantyCode":"M3-W","warrantyAmount":1,"warrantyStartDate":"2026-07-22",
             "warrantyEndDate":"2099-12-31","defectCode":"M3-DF","defectTitle":"M3","defectDescription":"M3",
             "rectificationDeadline":"2099-12-31","rectificationContent":"M3","verificationComment":"M3",
             "transferCode":"M3-AT","transferDate":"2026-07-22","recipientOrganization":"M3","recipientName":"M3",
             "transferScope":"M3","actualCompletionDate":"2026-07-22","reason":"M3"}
            """;
    @Autowired MockMvc mockMvc;
    @Autowired JwtUtils jwtUtils;

    @Test
    void queryOnlyIdentityCannotInvokeAnyM3MutationEndpoint() throws Exception {
        Cookie cookie = new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, jwtUtils.generateToken(
                99199999L, "m3-query-only", 0L, List.of("M3_QUERY"),
                List.of("project:query", "schedule:query", "site:daily:query", "quality:safety:query", "technical:query", "closeout:query")));

        for (Endpoint endpoint : mutationEndpoints()) {
            int status = mockMvc.perform(request(endpoint.method(), endpoint.path())
                            .cookie(cookie)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(endpoint.body()))
                    .andReturn().getResponse().getStatus();
            assertEquals(403, status, endpoint.method() + " " + endpoint.path());
        }
    }

    private List<Endpoint> mutationEndpoints() {
        return List.of(
                endpoint(HttpMethod.POST, "/quality-safety/plans"),
                endpoint(HttpMethod.PUT, "/quality-safety/plans/1"),
                endpoint(HttpMethod.POST, "/quality-safety/plans/1/activate"),
                endpoint(HttpMethod.POST, "/quality-safety/plans/1/complete"),
                endpoint(HttpMethod.POST, "/quality-safety/inspections"),
                endpoint(HttpMethod.POST, "/quality-safety/inspections/1/issues"),
                endpoint(HttpMethod.POST, "/quality-safety/inspections/1/submit"),
                endpoint(HttpMethod.POST, "/quality-safety/rectifications"),
                endpoint(HttpMethod.POST, "/quality-safety/rectifications/1/submit"),
                endpoint(HttpMethod.POST, "/quality-safety/rectifications/1/reinspect"),
                endpoint(HttpMethod.POST, "/quality-safety/consequences"),
                endpoint(HttpMethod.POST, "/quality-safety/consequences/1/post"),
                endpoint(HttpMethod.POST, "/technical-management/schemes"),
                endpoint(HttpMethod.POST, "/technical-management/schemes/1/submit"),
                endpoint(HttpMethod.POST, "/technical-management/drawings"),
                endpoint(HttpMethod.POST, "/technical-management/drawings/1/versions"),
                endpoint(HttpMethod.POST, "/technical-management/drawing-versions/1/reviews"),
                endpoint(HttpMethod.POST, "/technical-management/reviews/1/confirm"),
                endpoint(HttpMethod.POST, "/technical-management/reviews/1/rfis"),
                endpoint(HttpMethod.POST, "/technical-management/rfis/1/submit"),
                endpoint(HttpMethod.POST, "/technical-management/rfis/1/responses"),
                endpoint(HttpMethod.POST, "/technical-management/rfi-responses/1/review"),
                endpoint(HttpMethod.POST, "/technical-management/projects/1/disclosures"),
                endpoint(HttpMethod.POST, "/technical-management/disclosures/1/confirm"),
                endpoint(HttpMethod.POST, "/technical-management/projects/1/construction-references"),
                endpoint(HttpMethod.POST, "/technical-management/projects/1/archives"),
                endpoint(HttpMethod.POST, "/technical-management/archives/1/confirm"),
                endpoint(HttpMethod.POST, "/project-closeouts"),
                endpoint(HttpMethod.POST, "/project-closeouts/1/section-acceptances"),
                endpoint(HttpMethod.POST, "/project-closeouts/section-acceptances/1/confirm"),
                endpoint(HttpMethod.POST, "/project-closeouts/1/final-acceptance"),
                endpoint(HttpMethod.POST, "/project-closeouts/final-acceptances/1/submit"),
                endpoint(HttpMethod.POST, "/project-closeouts/1/final-settlement"),
                endpoint(HttpMethod.POST, "/project-closeouts/1/verify-tail-collection"),
                endpoint(HttpMethod.POST, "/project-closeouts/1/warranties"),
                endpoint(HttpMethod.POST, "/project-closeouts/warranties/1/defects"),
                endpoint(HttpMethod.POST, "/project-closeouts/defects/1/rectify"),
                endpoint(HttpMethod.POST, "/project-closeouts/defects/1/verify"),
                endpoint(HttpMethod.POST, "/project-closeouts/warranties/1/release"),
                endpoint(HttpMethod.POST, "/project-closeouts/1/archive-transfer"),
                endpoint(HttpMethod.POST, "/project-closeouts/archive-transfers/1/accept"),
                endpoint(HttpMethod.POST, "/project-closeouts/1/close"));
    }

    private Endpoint endpoint(HttpMethod method, String path) {
        return new Endpoint(method, path, VALID_BODY);
    }

    private record Endpoint(HttpMethod method, String path, String body) {}
}
