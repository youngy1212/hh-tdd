**요구 사항**

- PATCH  `/point/{id}/charge` : 포인트를 충전한다.
  - [ ] Positive : 포인트를 충전한다. 
  - [ ] Negative : 충전할 금액이 음수거나, 0 이다.
  - [ ] Negative : 포인트 충전 금액이 최대 포인트를 초과.
- PATCH `/point/{id}/use` : 포인트를 사용한다.
  - [ ] Positive : 포인트가 사용된다.
  - [ ] Negative : 사용 가능한 포인트 잔액이 부족하다 (실패).
- GET `/point/{id}` : 포인트를 조회한다.
  - [ ] Positive : 포인트가 정상적으로 조회된다.
- GET `/point/{id}/histories` : 포인트 내역을 조회한다.
  - [ ] Positive : 포인트 내역이 정상적으로 조회된다. 
- 잔고가 부족할 경우, 포인트 사용은 실패하여야 한다.
- 동시에 여러 건의 포인트 충전, 이용 요청이 들어올 경우 순차적으로 처리되어야 한다.