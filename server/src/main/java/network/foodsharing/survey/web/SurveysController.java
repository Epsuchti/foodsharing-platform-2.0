package network.foodsharing.survey.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.SurveysApi;
import network.foodsharing.api.model.Survey;
import network.foodsharing.api.model.SurveyForm;
import network.foodsharing.api.model.SurveyResponseCreate;
import network.foodsharing.api.model.SurveyResults;
import network.foodsharing.api.model.SurveySummary;
import network.foodsharing.api.model.SurveyWrite;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.survey.mapper.SurveyMapper;
import network.foodsharing.survey.service.SurveyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Surveys (SV1). */
@RestController
public class SurveysController implements SurveysApi {

    private final SurveyService surveys;
    private final SurveyMapper mapper;

    public SurveysController(SurveyService surveys, SurveyMapper mapper) {
        this.surveys = surveys;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Survey> createSurvey(SurveyWrite surveyWrite) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toSurvey(surveys.create(surveyWrite.getTitle(),
                surveyWrite.getDescription(), SurveyMapper.toInputs(surveyWrite), surveyWrite.getCoauthorIds())));
    }

    @Override
    public ResponseEntity<Void> deleteSurvey(UUID surveyId) {
        surveys.delete(surveyId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Survey> getSurvey(UUID surveyId) {
        return ResponseEntity.ok(mapper.toSurvey(surveys.get(surveyId)));
    }

    @Override
    public ResponseEntity<SurveyForm> getSurveyForm(UUID surveyId, UUID storeId) {
        return ResponseEntity.ok(mapper.toForm(surveys.form(surveyId, storeId)));
    }

    @Override
    public ResponseEntity<SurveyResults> getSurveyResults(UUID surveyId) {
        return ResponseEntity.ok(mapper.toResults(surveys.results(surveyId)));
    }

    @Override
    public ResponseEntity<List<UserSummary>> listSurveyCoauthorCandidates(String q) {
        return ResponseEntity.ok(mapper.toUsers(surveys.coauthorCandidates(q)));
    }

    @Override
    public ResponseEntity<List<SurveySummary>> listSurveys() {
        return ResponseEntity.ok(mapper.toSummaries(surveys.list()));
    }

    @Override
    public ResponseEntity<Void> submitSurveyResponse(UUID surveyId, SurveyResponseCreate surveyResponseCreate) {
        surveys.submit(surveyId, surveyResponseCreate.getStoreId(),
                SurveyMapper.toAnswers(surveyResponseCreate.getAnswers()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Survey> updateSurvey(UUID surveyId, SurveyWrite surveyWrite) {
        return ResponseEntity.ok(mapper.toSurvey(surveys.update(surveyId, surveyWrite.getTitle(),
                surveyWrite.getDescription(), SurveyMapper.toInputs(surveyWrite), surveyWrite.getCoauthorIds())));
    }
}
