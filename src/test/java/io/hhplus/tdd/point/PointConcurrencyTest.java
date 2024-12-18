package io.hhplus.tdd.point;


import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PointConcurrencyTest {

    @Autowired
    private  PointService pointService;

    private final int THREAD_COUNT = 10;

    @DisplayName("같은 유저가 동시에 포인트 충전 시 순차적으로 실행한다.")
    @Test
    void testConcurrentPointChargingFailsMaxLimit() throws InterruptedException {
        // given
        long userId = 1L;

        //기초 포인트 설정 : 현재 잔액 1000
        pointService.chargeUserPoint(userId, 9000);

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT); //스레드 풀 생성
        CountDownLatch latch  = new CountDownLatch(THREAD_COUNT); //스레드 시작

        for(int i = 0; i < THREAD_COUNT; i++) {
            executorService.execute(() -> {
                try {
                    pointService.chargeUserPoint(userId, 9000);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); //대기
        executorService.shutdownNow();

        // when
        UserPoint userPoint = pointService.getUserPoint(userId);

        // then
        assertThat(userPoint.point()).isEqualTo(99000);

    }


    @DisplayName("같은 유저가 동시에 여러건의 포인트 사용 요청시 순차적으로 실행한다.")
    @Test
    void testConcurrentPointUserUse() throws InterruptedException {
        // given
        long userId = 2L;

        //기초 포인트 설정 : 현재 잔액 3000
        pointService.chargeUserPoint(userId, 9000);

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT); //스레드 풀 생성
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT); //스레드 시작

        for(int i = 0; i < THREAD_COUNT; i++) {
            executorService.execute(() -> {
                try {
                    pointService.useUserPoint(userId, 500);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); //대기
        executorService.shutdownNow();

        // when
        UserPoint userPoint = pointService.getUserPoint(userId);

        // then
        assertThat(userPoint.point()).isEqualTo(4000);

    }


    @DisplayName("같은 유저가 동시에 여러건의 포인트 사용 요청시 순차적으로 실행한다.두 번째 사용에서 잔액 부족으로 실패한다.")
    @Test
    void testConcurrentPointUserUseFailInsufficientBalance() throws InterruptedException {
        // given
        long userId = 3L;

        //기초 포인트 설정 : 현재 잔액 3000
        pointService.chargeUserPoint(userId, 3000);

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT); //스레드 풀 생성
        CountDownLatch latch  = new CountDownLatch(THREAD_COUNT); //스레드 시작

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for(int i = 0; i < THREAD_COUNT; i++) {
            executorService.execute(() -> {
                try {
                    pointService.useUserPoint(userId, 2000);
                    successCount.incrementAndGet();
                }  catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); //대기
        executorService.shutdown();

        // when
        UserPoint userPoint = pointService.getUserPoint(userId);

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(9);
        assertThat(userPoint.point()).isEqualTo(1000);

    }

    @DisplayName("서로 다른 유저의 포인트 충전 및 사용 시 병렬로 처리가 되어야한다.")
    @Test
    void testSequentialProcessingForDifferentUsers() throws InterruptedException {
        // given
        long userId = 4L;
        long userId2 = 5L;

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT); //스레드 풀 생성
        CountDownLatch latch  = new CountDownLatch(THREAD_COUNT*2); //스레드 시작

        //기초 포인트 설정 : 현재 잔액 1000
        pointService.chargeUserPoint(userId, 1000);
        //기초 포인트 설정 : 현재 잔액 9900
        pointService.chargeUserPoint(userId2, 9900);


        for(int i = 0; i < THREAD_COUNT; i++) {
            executorService.execute(() -> {
                try {
                    pointService.chargeUserPoint(userId, 5000);
                } finally {
                    latch.countDown();
                }
            });
            executorService.execute(() -> {
                try {
                    pointService.useUserPoint(userId2, 500);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // when
        UserPoint userPoint = pointService.getUserPoint(userId);
        UserPoint userPoint2 = pointService.getUserPoint(userId2);
        // then

        assertThat(userPoint.point())
                .isEqualTo(51000);
        assertThat(userPoint2.point())
                .isEqualTo(4900);

    }


}
