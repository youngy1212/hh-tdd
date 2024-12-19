


# **동시성 제어 방식 분석 및 결과 보고서**

### 1. 문제배경

동일 사용자가 동시에 포인트를 충전하거나 사용할 경우 데이터 불일치가 발생할 수 있다.

이를 해결하기 위해 동시성 제어 방식을 검토하고 테스트를 진행하였다.

조건 1 : 동일한 유저에 동시에 충전, 이용 요청이 들어올 경우 순차적으로 처리되어야 한다.

조건 2 :  다른 유저들은 병렬적으로 처리되어야 한다.

## 2. 동시성 제어 방식 및 테스트 결과

### **2.1 synchronized  방식**
- synchronized  키워드를 사용하여 메서드나 특정 블록을 임계영역으로 지정.
- 한 스레드가 작업을 완료 할 때까지 다른 스레드가 해당 임계영역에 접근하지 못하도록 제한.

```java
public synchronized  UserPoint chargeUserPoint(long id, long amount) {
	 //로직 생략
}

public synchronized  UserPoint useUserPoint(long id, long amount) {
	 //로직 생략
}
```

### **2.2. synchronized 문제점**

synchronized를 사용할 경우 동시성 문제는 해결된다.

- synchronized 의 경우 모든 스레드에 대해서 글로벌 락이 걸리므로, 한 사용자의 포인트 충전/사용 작업이 **다른 사용자의 작업도 차단**하게 된다.
- 동시성은 제어되지만, 모든 사용자에게 글로벌 락이 걸려 **병렬처리 조건을 만족시킬 수 없다.**

결론: synchronized 는 동시성 제어가 필요할 때 간단히 사용할 수 있으나, 사용자별 동시성을 세분화하거나 성능을 최적화해야 하는 경우 적합하지 않음.

### **2.3 직면한 특이한 오류**

- 해당 로직이 병렬로 처리되는지 확인하기 위해 이러한 코드로 확인을 하였다.

```java
@DisplayName("서로 다른 유저의 포인트 충전 및 사용 시 병렬로 처리가 되어야한다.")
@Test
void testSequentialProcessingForDifferentUsers() throws InterruptedException {
  // given
  Long userId = 3L;
  Long userId2 = 4L;

  ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT); //스레드 풀 생성
  CountDownLatch latch  = new CountDownLatch(THREAD_COUNT); //스레드 시작

  //기초 포인트 설정 : 현재 잔액 1000
  pointService.chargeUserPoint(userId, 1000);
  //기초 포인트 설정 : 현재 잔액 5000
  pointService.chargeUserPoint(userId2, 5000);

  for(int i = 0; i < THREAD_COUNT; i++) {
      executorService.execute(() -> {
          try {
              pointService.chargeUserPoint(userId, 500);
              System.out.println("userId 1 실행");
          } finally {
              latch.countDown();
          }
      });
      executorService.execute(() -> {
          try {
              pointService.useUserPoint(userId2, 200);
              System.out.println("userId 2 실행");
          } finally {
              latch.countDown();
          }
      });
  }

  latch.await();

  // when
  UserPoint userPoint = pointService.getUserPoint(userId);
  UserPoint userPoint2 = pointService.getUserPoint(userId2);
  // then

  assertThat(userPoint.point())
          .isEqualTo(6000);
  assertThat(userPoint2.point())
          .isEqualTo(3000);
}
```

조건 : synchronized 로 동시성을 테스트

문제 : 테스트를 통과하지 못함.

![image](https://github.com/user-attachments/assets/5087436b-c552-419a-a2d5-cd12881d8c11)
가설 : 경쟁 상태에서 동시에 수정된 포인트가 있는 게 아닐까?

- synchronized  의 경우 갱신 손실의 문제가 발생할 수 있다. `synchronized`는  `insertOrUpdate` 이후에 데이터가 업데이트 되고 , 새로  `selectById` 조회해 오는 순간에 Dirty-Read가 발생한게 아닌가?

해답 : 원인은 `CountDownLatch latch  = new CountDownLatch(THREAD_COUNT);`

필요한 스레드는 20개인데, 10개만 할당해둬서 발생한 문제였다.

즉, 이 테스트 코드는 스레드가 10개이니 동시성이 문제가 아닌, 동시성 구현이 되어있더라고 **항상 실패하는 테스트**야 했다.

그런데  assertThat(userPoint.point()).isEqualTo(6000); 는 **테스트를 통과**하였다.

내가 생각한 원인은 다음과 같다.

```java
try {
    pointService.chargeUserPoint(userId, 500);
    System.out.println("userId 1 실행");
} finally {
    latch.countDown();
}
```

1. 병렬 테스트를 확인하고 싶어서 System.*out*.println("userId 1 실행"); 라는 출력을 넣어뒀다.
    - 실행과 스레드 차감 사이에 출력  I/O 라는 이벤트가 존재하게 되었다.
    - 이로 인해 스레드가 10개가 아닌  12~13개 정도의 스레드가 사용되게 되었다
2. 락 획득 문제
    - synchronized  는 락획득 순서를 보장할 수 없다는 문제가 있다.
    - 랜덤으로 락을 획득하면서, user1만 계속 메소드에 진입하게 되었다.

이 두 가지 조건이 결합하여 user1의 검증 테스트를 통과하게 된 것이다.

결론 : synchronized는 대기 자원의 요청이 많다면, 어떤 대기 자원은 무한정으로 대기할 수 있는 공정성 문제를 가져온다고 볼 수 있다.


### 2.4 `ReentrantLock`과 `ConcurrentHashMap`

사용자별로 독립적인 락을 관리하기 위해 `ConcurrentHashMap`을 사용하여 각 사용자에 대해 `ReentrantLock`을 매핑하고, 이를 기반으로 동시성 제어를 구현.

- `ConcurrentHashMap` :  동시 접근이 안전하도록 설계된 해시 맵이다 . 데이터를 여러 스레드가 동시에 접근할 수 있도록 하며, 각 항목에 대해 개별적인 잠금을 제공하여 성능을 최적화
- `ReentrantLock`: 락을 얻은 스레드가 다시 그 락을 얻을 수 있도록 허용하는 재진입 가능한 락이다.

### 2.5 동시성 제어 구현

```java
private final ConcurrentHashMap<Long, Lock> userLockMap = new ConcurrentHashMap<>();

//User 포인트 충전
public UserPoint chargeUserPoint(long id, long amount) {
  Lock lock = getUserLock(id);
  lock.lock();
  try {
    //로직 생략
  } finally {
      lock.unlock();
  }
}

//락을 가져오는 메서드
private Lock getUserLock(long userId){
    return userLockMap.computeIfAbsent(userId, key -> new ReentrantLock(true));
}
```

- 사용자별 락 관리  : `ConcurrentHashMap`  을 사용하여 각 사용자에 대한 고유한 `ReentrantLock` 을 생성하고, 이를 통해 동시성 제어를 구현한다.
- 공정성 모드 (new ReentrantLock(true)) : `ReentrantLock` 을 공정성 모드로 설정하면, 락을 얻을 기회가 생겼을 때, 여러 스레드 중에서 요청한 순서대로 lock을 얻을 수 있다. 이래 인해 대기 큐에 먼저 대기한 스레드가 먼저 lock을 얻어 공정성이 보장된다.

결론 :

1.  `ConcurrentHashMap`과 `ReentrantLock`의 조합으로 사용자 별로 독립적인 락 관리 가능.
2. 사용자자 별로 요청을 순차 처리하고, 다른 사용자의 요청은 병렬로 처리 가능.
3. 락을 얻은 스레드는 순차적으로 메소드에 접근하여 공정성 문제 해결