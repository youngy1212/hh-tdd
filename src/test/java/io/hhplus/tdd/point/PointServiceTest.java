package io.hhplus.tdd.point;

import static io.hhplus.tdd.point.TransactionType.CHARGE;
import static io.hhplus.tdd.point.TransactionType.USE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    private static final Logger log = LoggerFactory.getLogger(PointServiceTest.class);
    @InjectMocks
    private PointService pointService;

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @DisplayName("유저 포인트를 조회한다.")
    @Test
    void testGetUserPoint() {
        // given
        long userId = 1L;
        UserPoint MockUserPoint = new UserPoint(userId, 7000, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(MockUserPoint);

        // when
        UserPoint getUserPoint = pointService.getUserPoint(userId);

        // then
        assertThat(MockUserPoint.point()).isEqualTo(getUserPoint.point());
    }

    @DisplayName("유저 포인트 내역(충전/사용)을 조회한다.")
    @Test
    void testGetUserHistories() {
        // given
        long userId = 1L;
        List<PointHistory> mockHistories = List.of(
                new PointHistory(2L, userId, 3000, CHARGE,System.currentTimeMillis()),
                new PointHistory(3L, userId, 2000, USE, System.currentTimeMillis())
        );
        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(mockHistories);

        // when
        List<PointHistory> userHistories = pointService.getUserHistories(userId);

        //then
        assertThat(userHistories).hasSize(2); // 총 2개의 이력이 반환
        assertThat(userHistories.get(0).amount()).isEqualTo(3000);
        assertThat(userHistories.get(0).type()).isEqualTo(CHARGE);
        assertThat(userHistories.get(1).amount()).isEqualTo(2000);
        assertThat(userHistories.get(1).type()).isEqualTo(USE);
    }

    @DisplayName("유저 포인트 충전을 성공한다.")
    @Test
    void testChargeUserPoint() {
        // given
        long userId = 1L;
        UserPoint MockUserPoint = new UserPoint(userId, 7000, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(MockUserPoint);
        when(userPointTable.insertOrUpdate(userId, 14000)).thenReturn(new UserPoint(userId, 14000, System.currentTimeMillis()));

        // when
        UserPoint userPoint = pointService.chargeUserPoint(userId, 7000);

        // then
        assertThat(userPoint.point()).isEqualTo(14000);
        verify(userPointTable).insertOrUpdate(userId,14000);
    }

    @DisplayName("유저 포인트 충전금액이 0이하로 충전에 실패한다.")
    @Test
    void testChargeFailZeroOrLess() {
        long userId = 1L;

        // when && then
        assertThatThrownBy(()-> pointService.chargeUserPoint(userId, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("충전 금액은 0보다 커야 합니다.");

    }

    @DisplayName("유저 포인트 충전금액이 최대금액 10000을 초과하여 실패한다.")
    @Test
    void testChargeFailExceedsLimit() {
        // given
        long userId = 1L;
        long changeAmount = 10001;

        // when  // then
        assertThatThrownBy(()-> pointService.chargeUserPoint(userId, changeAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("충전 금액은 10,000 보다 작아야 합니다.");

    }

    @DisplayName("유저 포인트가 정상적으로 사용된다.")
    @Test
    void testUseUserPoint() {
        // given
        long userId = 1L;
        UserPoint MockUserPoint = new UserPoint(userId, 7000, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(MockUserPoint);
        when(userPointTable.insertOrUpdate(userId, 4000)).thenReturn(new UserPoint(userId, 4000, System.currentTimeMillis()));

        // when
        UserPoint userPoint = pointService.useUserPoint(userId, 3000);

        // then
        assertThat(userPoint.point()).isEqualTo(4000);
        verify(userPointTable).insertOrUpdate(userId,4000);

    }

    @DisplayName("유저포인트가 잔액보다 커 포인트 사용에 실패한다.")
    @Test
    void testUseUserPointFailInsufficientBalance() {

        // given
        long userId = 1L;
        UserPoint MockUserPoint = new UserPoint(userId, 7000, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(MockUserPoint);

        // when
        assertThatThrownBy(()-> pointService.useUserPoint(userId, 8000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용할 수 있는 포인트가 부족합니다.");
    }

}

