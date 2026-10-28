package network.foodsharing.survey.web;

import network.foodsharing.api.SurveysApi;
import network.foodsharing.api.model.Survey;
import network.foodsharing.api.model.SurveyForm;
import network.foodsharing.api.model.SurveyResponseCreate;
import network.foodsharing.api.model.SurveyResults;
import network.foodsharing.api.model.SurveySummary;
import network.foodsharing.api.model.SurveyWrite;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

/** Surveys (SV1). Operations answer 501 until their task implements them (docs/plan-m5.md). */
@RestController
public class SurveysController implements SurveysApi {

    @Override
    public ResponseEntity<Survey> createSurvey(SurveyWrite surveyWrite) {
        throw ApiException.notImplemented("createSurvey");
    }

    @Override
    public ResponseEntity<Void> deleteSurvey(UUID surveyId) {
        throw ApiException.notImplemented("deleteSurvey");
    }

    @Override
    public ResponseEntity<Survey> getSurvey(UUID surveyId) {
        throw ApiException.notImplemented("getSurvey");
    }

    @Override
    public ResponseEntity<SurveyForm> getSurveyForm(UUID surveyId, UUID storeId) {
        throw ApiException.notImplemented("getSurveyForm");
    }

    @Override
    public ResponseEntity<SurveyResults> getSurveyResults(UUID surveyId) {
        throw ApiException.notImplemented("getSurveyResults");
    }

    @Override
    public ResponseEntity<List<UserSummary>> listSurveyCoauthorCandidates(String q) {
        throw ApiException.notImplemented("listSurveyCoauthorCandidates");
    }

    @Override
    public ResponseEntity<List<SurveySummary>> listSurveys() {
        throw ApiException.notImplemented("listSurveys");
    }

    @Override
    public ResponseEntity<Void> submitSurveyResponse(UUID surveyId, SurveyResponseCreate surveyResponseCreate) {
        throw ApiException.notImplemented("submitSurveyResponse");
    }

    @Override
    public ResponseEntity<Survey> updateSurvey(UUID surveyId, SurveyWrite surveyWrite) {
        throw ApiException.notImplemented("updateSurvey");
    }
}
