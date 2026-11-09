package com.lms.dubbing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.config.AiWorkerConfig;
import com.lms.common.enums.JobStatus;
import com.lms.common.enums.Role;
import com.lms.common.enums.TrackStatus;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ConflictException;
import com.lms.common.exception.QuotaExceededException;
import com.lms.dubbing.dto.DubbingDto.RequestReq;
import com.lms.dubbing.dto.DubbingDto.Res;
import com.lms.dubbing.entity.AiJob;
import com.lms.dubbing.entity.AudioTrack;
import com.lms.dubbing.entity.VoiceMapping;
import com.lms.dubbing.repository.AiJobRepository;
import com.lms.dubbing.repository.AudioTrackRepository;
import com.lms.dubbing.repository.VoiceMappingRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** BR-DUB-09 → 07 → 04 → 05 → 06, theo đúng thứ tự tiền điều kiện của UC18. */
@ExtendWith(MockitoExtension.class)
class DubbingRequestServiceTest {

    private static final String STUDENT_EMAIL = "student@lms.local";
    private static final String INSTRUCTOR_EMAIL = "instructor@lms.local";
    private static final Long LESSON_ID = 30L;

    @Mock private LessonRepository lessonRepository;
    @Mock private UserRepository userRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private VoiceMappingRepository voiceMappingRepository;
    @Mock private AudioTrackRepository audioTrackRepository;
    @Mock private AiJobRepository aiJobRepository;
    @Mock private DubbingLockService dubbingLockService;
    @Mock private DubbingQuotaService dubbingQuotaService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ListOperations<String, String> listOperations;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private RestTemplate restTemplate;
    @Mock private AiWorkerConfig aiWorkerConfig;

    @InjectMocks
    private DubbingRequestService dubbingRequestService;

    private User student;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dubbingRequestService, "queueKey", "lms:dubbing:jobs");
        ReflectionTestUtils.setField(dubbingRequestService, "progressChannel", "lms:dubbing:progress");
        ReflectionTestUtils.setField(dubbingRequestService, "internalApiToken", "dev-internal-token");

        student = new User();
        student.setId(1L);
        student.setEmail(STUDENT_EMAIL);
        student.setRole(Role.STUDENT);

        User instructor = new User();
        instructor.setId(2L);
        instructor.setEmail(INSTRUCTOR_EMAIL);
        instructor.setRole(Role.INSTRUCTOR);

        Course course = new Course();
        course.setId(100L);
        course.setInstructor(instructor);

        Chapter chapter = new Chapter();
        chapter.setId(20L);
        chapter.setCourse(course);

        lesson = new Lesson();
        lesson.setId(LESSON_ID);
        lesson.setChapter(chapter);
        lesson.setSourceLanguage("vi-VN");
        lesson.setVideoUrl("https://cdn.example.com/videos/30/x.mp4");

        lenient().when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.of(lesson));
        lenient().when(userRepository.findByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        lenient().when(userRepository.findByEmail(INSTRUCTOR_EMAIL)).thenReturn(Optional.of(instructor));
        lenient().when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 100L)).thenReturn(true);
        lenient().when(voiceMappingRepository.findByLanguageAndIsActiveTrue("en-US"))
                .thenReturn(List.of(new VoiceMapping()));
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(aiJobRepository.save(any(AiJob.class))).thenAnswer(inv -> {
            AiJob job = inv.getArgument(0);
            job.setId(999L);
            return job;
        });
        lenient().when(dubbingLockService.tryLock(LESSON_ID, "en-US")).thenReturn(true);
        lenient().when(aiWorkerConfig.getBaseUrl()).thenReturn("http://ai-api:8000");
    }

    private RequestReq req() {
        return new RequestReq("en-US", null);
    }

    @Test
    void requestDubbing_throwsWhenCallerNeitherEnrolledNorOwner() {
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 100L)).thenReturn(false);

        assertThatThrownBy(() -> dubbingRequestService.requestDubbing(STUDENT_EMAIL, LESSON_ID, req()))
                .isInstanceOf(AccessDeniedDomainException.class);
        verify(aiJobRepository, never()).save(any());
    }

    @Test
    void requestDubbing_allowsOwningInstructorWithoutEnrollmentRow() {
        Res result = dubbingRequestService.requestDubbing(INSTRUCTOR_EMAIL, LESSON_ID, req());

        assertThat(result.status()).isEqualTo("CREATED");
        verify(enrollmentRepository, never()).existsByUser_IdAndCourse_Id(any(), any());
    }

    @Test
    void requestDubbing_throwsWhenTargetLanguageEqualsSourceLanguage() {
        assertThatThrownBy(() -> dubbingRequestService.requestDubbing(STUDENT_EMAIL, LESSON_ID, new RequestReq("vi-VN", null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("BR-DUB-09");
        verify(aiJobRepository, never()).save(any());
    }

    @Test
    void requestDubbing_throwsWhenLanguageNotActive() {
        when(voiceMappingRepository.findByLanguageAndIsActiveTrue("en-US")).thenReturn(List.of());

        assertThatThrownBy(() -> dubbingRequestService.requestDubbing(STUDENT_EMAIL, LESSON_ID, req()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("BR-DUB-07");
        verify(dubbingQuotaService, never()).consume(any(), any());
    }

    @Test
    void requestDubbing_returnsAvailableWhenAudioTrackAlreadyCompleted() {
        AudioTrack track = new AudioTrack();
        track.setStatus(TrackStatus.COMPLETED);
        track.setFinalUrl("https://cdn.example.com/audio/30/en-US/final.mp3");
        when(audioTrackRepository.findByLesson_IdAndLanguage(LESSON_ID, "en-US")).thenReturn(Optional.of(track));

        Res result = dubbingRequestService.requestDubbing(STUDENT_EMAIL, LESSON_ID, req());

        assertThat(result.status()).isEqualTo("AVAILABLE");
        assertThat(result.audioUrl()).isEqualTo("https://cdn.example.com/audio/30/en-US/final.mp3");
        verify(dubbingQuotaService, never()).consume(any(), any());
        verify(aiJobRepository, never()).save(any());
    }

    @Test
    void requestDubbing_doesNotBlockOnPartialAudioTrack() {
        AudioTrack track = new AudioTrack();
        track.setStatus(TrackStatus.PARTIAL);
        when(audioTrackRepository.findByLesson_IdAndLanguage(LESSON_ID, "en-US")).thenReturn(Optional.of(track));

        Res result = dubbingRequestService.requestDubbing(STUDENT_EMAIL, LESSON_ID, req());

        assertThat(result.status()).isEqualTo("CREATED");
    }

    @Test
    void requestDubbing_returnsProcessingWhenJobAlreadyActive_andDoesNotConsumeQuota() {
        AiJob activeJob = new AiJob();
        activeJob.setId(555L);
        when(aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(LESSON_ID, "en-US", 1))
                .thenReturn(Optional.of(activeJob));

        Res result = dubbingRequestService.requestDubbing(STUDENT_EMAIL, LESSON_ID, req());

        assertThat(result.status()).isEqualTo("PROCESSING");
        assertThat(result.jobId()).isEqualTo(555L);
        verify(dubbingQuotaService, never()).consume(any(), any());
        verify(dubbingLockService, never()).tryLock(any(), any());
        verify(aiJobRepository, never()).save(any());
    }

    @Test
    void requestDubbing_throwsWhenQuotaExceeded() {
        org.mockito.Mockito.doThrow(new QuotaExceededException("yêu cầu lồng tiếng/ngày", 15L))
                .when(dubbingQuotaService).consume(1L, Role.STUDENT);

        assertThatThrownBy(() -> dubbingRequestService.requestDubbing(STUDENT_EMAIL, LESSON_ID, req()))
                .isInstanceOf(QuotaExceededException.class);
        verify(dubbingLockService, never()).tryLock(any(), any());
        verify(aiJobRepository, never()).save(any());
    }

    @Test
    void requestDubbing_withValidVoiceName_setsVoiceMappingOnJob() {
        VoiceMapping femaleVoice = new VoiceMapping();
        femaleVoice.setLanguage("en-US");
        femaleVoice.setVoiceName("en-US-JennyNeural");
        femaleVoice.setGender("FEMALE");
        when(voiceMappingRepository.findByLanguageAndIsActiveTrue("en-US")).thenReturn(List.of(femaleVoice));

        Res result = dubbingRequestService.requestDubbing(STUDENT_EMAIL, LESSON_ID, new RequestReq("en-US", "en-US-JennyNeural"));

        assertThat(result.status()).isEqualTo("CREATED");
        var jobCaptor = org.mockito.ArgumentCaptor.forClass(AiJob.class);
        verify(aiJobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getVoiceMapping()).isSameAs(femaleVoice);
    }

    @Test
    void requestDubbing_withVoiceNameNotAvailableForLanguage_throws() {
        assertThatThrownBy(() -> dubbingRequestService.requestDubbing(
                STUDENT_EMAIL, LESSON_ID, new RequestReq("en-US", "khong-ton-tai")))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(aiJobRepository, never()).save(any());
    }

    @Test
    void requestDubbing_withoutVoiceName_leavesJobVoiceMappingNull() {
        Res result = dubbingRequestService.requestDubbing(STUDENT_EMAIL, LESSON_ID, req());

        assertThat(result.status()).isEqualTo("CREATED");
        var jobCaptor = org.mockito.ArgumentCaptor.forClass(AiJob.class);
        verify(aiJobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getVoiceMapping()).isNull();
    }

    @Test
    void requestDubbing_createsJobAndPushesToQueueInOrder() {
        Res result = dubbingRequestService.requestDubbing(STUDENT_EMAIL, LESSON_ID, req());

        assertThat(result.status()).isEqualTo("CREATED");
        assertThat(result.jobId()).isEqualTo(999L);

        var inOrder = org.mockito.Mockito.inOrder(dubbingQuotaService, dubbingLockService, aiJobRepository, listOperations);
        inOrder.verify(dubbingQuotaService).consume(1L, Role.STUDENT);
        inOrder.verify(dubbingLockService).tryLock(LESSON_ID, "en-US");
        inOrder.verify(aiJobRepository).save(any(AiJob.class));
        inOrder.verify(listOperations).leftPush(anyString(), anyString());
    }

    @Test
    void requestDubbing_returnsProcessingWhenLockAcquisitionRacesWithAnotherRequest() {
        when(dubbingLockService.tryLock(LESSON_ID, "en-US")).thenReturn(false);
        AiJob raceJob = new AiJob();
        raceJob.setId(777L);
        when(aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(LESSON_ID, "en-US", 1))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raceJob));

        Res result = dubbingRequestService.requestDubbing(STUDENT_EMAIL, LESSON_ID, req());

        assertThat(result.status()).isEqualTo("PROCESSING");
        assertThat(result.jobId()).isEqualTo(777L);
        verify(aiJobRepository, never()).save(any());
    }

    // ── UC45 retryJob ────────────────────────────────────────────────

    private AiJob failedJob(long id, int retryCount) {
        AiJob job = new AiJob();
        job.setId(id);
        job.setLesson(lesson);
        job.setTargetLanguage("en-US");
        job.setStatus(JobStatus.FAILED);
        job.setRetryCount(retryCount);
        job.setDoneChunks(3);
        job.setErrorMessage("Chunk 1: loi tam thoi");
        return job;
    }

    @Test
    void retryJob_throwsWhenStatusNotFailed() {
        AiJob job = failedJob(400L, 1);
        job.setStatus(JobStatus.COMPLETED);
        when(aiJobRepository.findById(400L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> dubbingRequestService.retryJob(400L))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(dubbingLockService, never()).tryLock(any(), any());
    }

    @Test
    void retryJob_throwsWhenSkipped_neverRetried() {
        AiJob job = failedJob(401L, 0);
        job.setStatus(JobStatus.SKIPPED);
        when(aiJobRepository.findById(401L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> dubbingRequestService.retryJob(401L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void retryJob_throwsWhenRetryCountExhausted() {
        AiJob job = failedJob(402L, 3);
        when(aiJobRepository.findById(402L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> dubbingRequestService.retryJob(402L))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(dubbingLockService, never()).tryLock(any(), any());
    }

    @Test
    void retryJob_throwsWhenAnotherJobAlreadyActiveForSamePair() {
        AiJob job = failedJob(403L, 1);
        AiJob otherActiveJob = new AiJob();
        otherActiveJob.setId(999L);
        when(aiJobRepository.findById(403L)).thenReturn(Optional.of(job));
        when(aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(LESSON_ID, "en-US", 1))
                .thenReturn(Optional.of(otherActiveJob));

        assertThatThrownBy(() -> dubbingRequestService.retryJob(403L))
                .isInstanceOf(ConflictException.class);
        verify(dubbingLockService, never()).tryLock(any(), any());
    }

    @Test
    void retryJob_throwsWhenLockAcquisitionFails() {
        AiJob job = failedJob(404L, 1);
        when(aiJobRepository.findById(404L)).thenReturn(Optional.of(job));
        when(aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(LESSON_ID, "en-US", 1))
                .thenReturn(Optional.empty());
        when(dubbingLockService.tryLock(LESSON_ID, "en-US")).thenReturn(false);

        assertThatThrownBy(() -> dubbingRequestService.retryJob(404L))
                .isInstanceOf(ConflictException.class);
        verify(aiJobRepository, never()).save(any());
    }

    @Test
    void retryJob_resetsFieldsAndPushesToQueueWithSameJobId() {
        AiJob job = failedJob(405L, 1);
        when(aiJobRepository.findById(405L)).thenReturn(Optional.of(job));
        when(aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(LESSON_ID, "en-US", 1))
                .thenReturn(Optional.empty());
        when(dubbingLockService.tryLock(LESSON_ID, "en-US")).thenReturn(true);
        when(aiJobRepository.save(any(AiJob.class))).thenAnswer(inv -> inv.getArgument(0));

        Res result = dubbingRequestService.retryJob(405L);

        assertThat(result.status()).isEqualTo("CREATED");
        assertThat(result.jobId()).isEqualTo(405L); // TAI SU DUNG job id cu, khong tao job moi
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getActiveFlag()).isEqualTo(1);
        assertThat(job.getDoneChunks()).isZero();
        assertThat(job.getErrorMessage()).isNull();
        assertThat(job.getRetryCount()).isEqualTo(2);
        verify(listOperations).leftPush(anyString(), anyString());
    }

    // ── UC20 cancelDubbing ───────────────────────────────────────────

    private AiJob activeJob(long id, JobStatus status, String celeryTaskId) {
        AiJob job = new AiJob();
        job.setId(id);
        job.setLesson(lesson);
        job.setTargetLanguage("en-US");
        job.setStatus(status);
        job.setActiveFlag(1);
        job.setCeleryTaskId(celeryTaskId);
        return job;
    }

    @Test
    void cancelDubbing_throwsWhenNoActiveJob() {
        when(aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(LESSON_ID, "en-US", 1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> dubbingRequestService.cancelDubbing(STUDENT_EMAIL, LESSON_ID, "en-US"))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(dubbingLockService, never()).release(any(), any());
    }

    @Test
    void cancelDubbing_throwsWhenJobAlreadyFinished() {
        AiJob job = activeJob(500L, JobStatus.COMPLETED, "celery-task-1");
        when(aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(LESSON_ID, "en-US", 1))
                .thenReturn(Optional.of(job));

        assertThatThrownBy(() -> dubbingRequestService.cancelDubbing(STUDENT_EMAIL, LESSON_ID, "en-US"))
                .isInstanceOf(ConflictException.class);
        verify(dubbingLockService, never()).release(any(), any());
        verify(aiJobRepository, never()).save(any());
    }

    @Test
    void cancelDubbing_setsCancelledStatusReleasesLockAndPublishesWsEvent() {
        AiJob job = activeJob(501L, JobStatus.PROCESSING, "celery-task-2");
        when(aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(LESSON_ID, "en-US", 1))
                .thenReturn(Optional.of(job));

        Res result = dubbingRequestService.cancelDubbing(STUDENT_EMAIL, LESSON_ID, "en-US");

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.getActiveFlag()).isNull();
        assertThat(job.getErrorMessage()).isEqualTo("Bị huỷ thủ công bởi học viên");
        assertThat(job.getFinishedAt()).isNotNull();
        verify(aiJobRepository).save(job);
        verify(dubbingLockService).release(LESSON_ID, "en-US");
        verify(redisTemplate).convertAndSend(eq("lms:dubbing:progress"), contains("\"status\":\"CANCELLED\""));
    }

    @Test
    void cancelDubbing_skipsAiWorkerCallWhenJobNeverStarted() {
        AiJob job = activeJob(502L, JobStatus.PENDING, null); // chua co celery_task_id
        when(aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(LESSON_ID, "en-US", 1))
                .thenReturn(Optional.of(job));

        dubbingRequestService.cancelDubbing(STUDENT_EMAIL, LESSON_ID, "en-US");

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void cancelDubbing_callsAiWorkerCancelEndpointWithCeleryTaskId() {
        AiJob job = activeJob(503L, JobStatus.PROCESSING, "celery-task-3");
        when(aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(LESSON_ID, "en-US", 1))
                .thenReturn(Optional.of(job));
        // aiJobRepository.save(...) trong setUp() mock ghi de ID thanh 999L (dung chung cho moi
        // test) — doc lai ID THAT SU cua job sau khi cancel de khop dung URL da goi.
        dubbingRequestService.cancelDubbing(STUDENT_EMAIL, LESSON_ID, "en-US");

        verify(restTemplate).postForEntity(
                eq("http://ai-api:8000/admin/dubbing-jobs/" + job.getId() + "/cancel"),
                any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void cancelDubbing_doesNotFailWhenAiWorkerCallThrows() {
        AiJob job = activeJob(504L, JobStatus.PROCESSING, "celery-task-4");
        when(aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(LESSON_ID, "en-US", 1))
                .thenReturn(Optional.of(job));
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenThrow(new RestClientException("AI Worker khong ket noi duoc"));

        // best-effort: loi goi AI Worker KHONG duoc lam hong ket qua huy o DB (da la nguon su that)
        Res result = dubbingRequestService.cancelDubbing(STUDENT_EMAIL, LESSON_ID, "en-US");

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
    }
}
