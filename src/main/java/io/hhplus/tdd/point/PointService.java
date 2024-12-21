package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    private static final long USER_MAX_POINT = 100_000L;
    private final Map<Long, Lock> userLockMap = new ConcurrentHashMap<>();

    //락을 가져오는 메서드
    private Lock getUserLock(long userId){
        return userLockMap.computeIfAbsent(userId, key -> new ReentrantLock(true));
    }

    //User 포인트 조회
    public UserPoint getUserPoint(long id) {
        return userPointTable.selectById(id);
    }

    //User 포인트 History 조회
    public List<PointHistory> getUserHistories(long id) {
        return pointHistoryTable.selectAllByUserId(id);
    }

    //User 포인트 충전
    public UserPoint chargeUserPoint(long id, long amount) {

        if (amount <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }

        if (amount > 10000) {
            throw new IllegalArgumentException("충전 금액은 10,000 보다 작아야 합니다.");
        }

        Lock lock = getUserLock(id);
        lock.lock();
        try {

            UserPoint userPoint = userPointTable.selectById(id);
            long updateAmount = (userPoint.point() + amount);

            if (updateAmount > USER_MAX_POINT) {
                throw new IllegalArgumentException("충전 한도를 초과했습니다.");
            }

            return userPointTable.insertOrUpdate(id, updateAmount);

        } finally {
            lock.unlock();
        }
    }

    //User 포인트 사용
    public UserPoint useUserPoint(long id, long amount) {
        Lock lock = getUserLock(id);
        lock.lock();
        try {
            UserPoint userPoint = userPointTable.selectById(id);

            if (userPoint.point() < amount) {
                throw new IllegalArgumentException("사용할 수 있는 포인트가 부족합니다.");
            }

            long updateAmount = (userPoint.point() - amount);

            return userPointTable.insertOrUpdate(id, updateAmount);

        } finally {
            lock.unlock();
        }
    }
}
