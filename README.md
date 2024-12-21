
### 요구 사항
- PATCH  `/point/{id}/charge` : 포인트를 충전한다.
  - [x] Positive : 포인트를 충전한다. 
  - [x] Negative : 충전할 금액이 음수거나, 0 이다.
  - [x] Negative : 포인트 충전 금액이 최대 포인트를 초과.
- PATCH `/point/{id}/use` : 포인트를 사용한다.
  - [x] Positive : 포인트가 사용된다.
  - [x] Negative : 사용 가능한 포인트 잔액이 부족하다 (실패).
- GET `/point/{id}` : 포인트를 조회한다.
  - [x] Positive : 포인트가 정상적으로 조회된다.
- GET `/point/{id}/histories` : 포인트 내역을 조회한다.
  - [x] Positive : 포인트 내역이 정상적으로 조회된다. 
- 잔고가 부족할 경우, 포인트 사용은 실패하여야 한다.
- 동시에 여러 건의 포인트 충전, 이용 요청이 들어올 경우 순차적으로 처리되어야 한다.

### 동시성 테스트 
- 같은 유저가 동시에 포인트 충전 시 순차적으로 진행하여 충전 결과가 (초기 잔액 + 충전액) 이다.  <br>
  - 시나리오 : <br>
    ** 스레드 문제로 시나리오 변경 
    1. 현재 잔액은 9000 포인트 
    2이후 10번째 까지의 9000 충전 포인트 (성공) : 잔액 99000원
  - 검증 :
    1. 먼저 처리된 충전이 성공해야함.
    2. Dirty-Read 없이. 총 금액이 초기 잔액 + 충전액 이어야함.

- 같은 유저가 동시에 여러건의 포인트 사용 요청시 순차적으로 처리 후 잔액 부족시 실패
  - 시나리오
    ** 스레드 문제로 시나리오 변경 및 분리
    1. 현재 잔액 : 3000 포인트
    2. 첫 번째 사용 2000 포인트 (성공) : 잔액 1000원
    3. 이후 10번째까지 사용 2000 포인트 (실패) : 잔액 1000원
  - 검증
    1. 먼저 처리된 사용만 성공해야함.
    2. 잔액 부족시 사용 실패해야함.
    3. 성공과 실패 횟수에 대한 검증을 추가 

- 서로 다른 유저의 포인트 충전 및 사용 시 병렬 처리 확인
  - 시나리오
    1. 유저 A : 잔액 1000 포인트
    2. 유저 A : 충전 요청 500 포인트  : 잔액 6000원
    3. 유저 B : 잔액 5000 포인트
    4. 유저 B : 사용 요청 200 포인트  : 잔액 3000원
  - 검증 
    1. 동시에 처리되야함.
    2. 잔액이 정확하게 일치해야함.
    

## 변경사항
### **Before**

- **테스트 로직**

  첫 번째 요청과 두 번째 요청이 **순차적으로 실행되는지**를 검증하는 로직.

- **문제점**
  1. **스레드 개수**를 2개로 설정하여 테스트를 실행.
     - 동일한 코드로 테스트를 실행했음에도 **성공하거나 실패하는 비일관적인 결과**가 발생.

       ![image](https://github.com/user-attachments/assets/c6af1344-7e6b-468a-af6e-1dfff68d3cc1)
       ![image](https://github.com/user-attachments/assets/504a79b5-8d00-4bd0-b00e-085138468b50)

     - **RED 단계**에서 동시성 처리가 미 구현된 상태임에도, **테스트가 성공**하는 잘못된 로직이 존재.
  2. **스레드 개수가 적을 경우**, 운 좋게 테스트가 성공하는 경우가 생김.
     - 동시성 문제를 제대로 검출하지 못함.

### **After**

- 개선 사항
  1. **스레드 개수**를 10개로 증가.
     - 첫 번째부터 열 번째까지의 요청이 동시에 실행되도록 테스트 케이스를 변경.
  2. **테스트 결과**
     - 10개의 스레드가 동시에 요청을 처리하는 과정에서 **Dirty-Read 문제**가 발생.
     - **RED 단계에서 실패**하는 테스트 로직으로 변경되어, 동시성 문제가 명확히 드러남.

### Before

- **테스트 로직:**

  동일한 유저가 여러 포인트 사용 요청 시, 요청을 순차적으로 처리 후 잔액 부족 상태에서 실패하도록 검증.

- **문제점:**
  1. **테스트 성공 오류:**
     - 잔액 부족 상태에서도 테스트가 성공하는 오류 발생.

     ![image](https://github.com/user-attachments/assets/c9e1f38f-a8ee-4c96-b32b-bff680c950ba)

  2. **오류 발생 원인:**
     - `catch (Exception e) {}` 코드 블록이 없어서 예외가 내부에서 처리됨.
     - 예외가 발생해도 테스트가 실패하지 않음.

### After

- **개선 사항:**
  1. **예외 처리 추가:**
     - `catch (Exception e) { failureCount.incrementAndGet(); }` 블록을 추가하여 예외 발생 시 실패 카운트를 증가하도록 수정.
  2. **검증 로직 강화:**
     - 성공과 실패에 대한 횟수를 카운트하여 검증
- **테스트 결과:**
   - 동시성 문제가 명확히 드러나고, 테스트가 올바르게 동작함.
     ![image](https://github.com/user-attachments/assets/8de886b7-eb0d-40a7-ad89-3815c024ae35)

### 추가 개선사항

- **문제점:**
1. 이전 테스트에서는 **순차적 실행**과 **로직 검증**을 한 번에 처리하여 동시성 테스트의 본질이 방해받음.
2. 실패 원인을 명확히 파악하기 어려움.

- **개선 사항:**
  <br>테스트 케이스를 아래와 같이 분리 및 명확화:
  - 포인트 충전 : 
    - Before : 같은 유저가 동시에 포인트 충전 시 순차적으로 진행하여 최대 충전 한도 초과시 실패
    - After : 같은 유저가 동시에 포인트 충전 시 순차적으로 진행하여 충전 결과가 (초기 잔액 + 충전액) 이다.
  - 포인트 사용 : 
    - Before :같은 유저가 동시에 여러건의 포인트 사용 요청시 순차적으로 처리 후 잔액 부족시 실패 
    - After1 : 같은 유저가 동시에 여러건의 포인트 사용 요청시 순차적으로 실행한다.
    - After2 :  같은 유저가 동시에 여러건의 포인트 사용 요청시 순차적으로 실행한다.두 번째 사용에서 잔액 부족으로 실패한다.


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

